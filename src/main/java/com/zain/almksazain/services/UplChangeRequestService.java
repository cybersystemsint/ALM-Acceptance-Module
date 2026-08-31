package com.zain.almksazain.services;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.model.AccessRole;
import com.zain.almksazain.model.StdWorkflow;
import com.zain.almksazain.model.StdWorkflowApprovalLevel;
import com.zain.almksazain.model.UplActionType;
import com.zain.almksazain.model.UplChangeRequest;
import com.zain.almksazain.model.UplChangeRequestDecision;
import com.zain.almksazain.model.UplChangeRequestStatus;
import com.zain.almksazain.model.UplDecision;
import com.zain.almksazain.model.UplInAppNotification;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.repo.AccessRoleRepo;
import com.zain.almksazain.repo.DccLineRepo;
import com.zain.almksazain.repo.StdWorkflowApprovalApproverRepo;
import com.zain.almksazain.repo.StdWorkflowApprovalLevelRepo;
import com.zain.almksazain.repo.StdWorkflowModuleRepo;
import com.zain.almksazain.repo.StdWorkflowRepo;
import com.zain.almksazain.repo.UplChangeRequestDecisionRepo;
import com.zain.almksazain.repo.UplChangeRequestRepo;
import com.zain.almksazain.repo.UplInAppNotificationRepo;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.tbPurchaseOrderRepo;
import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;

/**
 * Owns the whole UPL edit/delete/approval lifecycle: fail-fast validation on
 * create, level-by-level advancement and re-validation on decide, and
 * apply-on-final-approve. See design doc "UPL Edit, Delete & Approval —
 * Design System" (RQ: 2-102025).
 *
 * Approval levels themselves are NOT owned here — they're configured through
 * the existing Standard Workflow screens (Configurations > Approval Workflow,
 * then > Approval Levels), same as every other module: create a workflow for
 * module "Unified Price List" with action type Update, another with Delete,
 * then add levels + approvers to each. This service only ever reads that
 * configuration (via the StdWorkflow* mirror entities) to resolve how many
 * levels a request needs and who's approving the current one.
 *
 * Deliberately not built on the DCC/ScopeApproval stack (region/vendor
 * routing this feature doesn't need) — see the design doc for why. The
 * request/decision/apply machinery below IS the part that stack's Standard
 * Workflow half never finished (its own approve-workflow step never actually
 * applies anything); this service is what makes that half work for UPL.
 */
@Service
public class UplChangeRequestService {

    private static final Logger logger = LoggerFactory.getLogger(UplChangeRequestService.class);

    /** Matches the module row seeded for UPL — see sql/upl_approval_workflow_schema.sql. */
    private static final String MODULE_NAME = "Unified Price List";
    private static final int ACTIVE_STATUS = 1;

    /** Same definition combinedPurchaseOrderView uses for POAcceptanceQty/poPendingQuantity,
     *  plus "returned" - a returned PAC didn't go through either, so it shouldn't count against
     *  the UPL line's already-accepted quantity floor. */
    private static final List<String> DCC_STATUSES_NOT_COUNTING_AS_PAC = Arrays.asList("incomplete", "rejected", "returned");

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";

    private static final Set<String> EDITABLE_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "activeOrPassive", "uplItemSerialized", "uplLineUnitPrice", "uplLineQuantity",
            "uplLineDescription", "projectName", "uplLineItemCode"));

    @Autowired private UplChangeRequestRepo changeRequestRepo;
    @Autowired private UplChangeRequestDecisionRepo decisionRepo;
    @Autowired private StdWorkflowModuleRepo moduleRepo;
    @Autowired private StdWorkflowRepo workflowRepo;
    @Autowired private StdWorkflowApprovalLevelRepo stdLevelRepo;
    @Autowired private StdWorkflowApprovalApproverRepo stdApproverRepo;
    @Autowired private tbPurchaseOrderUPLRepo uplRepo;
    @Autowired private tbPurchaseOrderRepo poRepo;
    @Autowired private DccLineRepo dccLineRepo;
    @Autowired private UserRepository userRepository;
    @Autowired private UplInAppNotificationRepo notificationRepo;
    @Autowired private EmailService emailService;
    @Autowired private AccessRoleRepo accessRoleRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // Approval-level resolution (read-only — configured elsewhere)
    // ============================================================

    private Optional<StdWorkflow> findWorkflow(UplActionType actionType) {
        return moduleRepo.findByModuleNameAndStatus(MODULE_NAME, ACTIVE_STATUS)
                .flatMap(module -> workflowRepo.findByModuleIdAndActionTypeAndStatus(
                        module.getRecordNo(), actionType.name(), ACTIVE_STATUS));
    }

    /**
     * Requires a configured, non-empty approval workflow for this action type, distinguishing
     * the two ways that can fail so the user knows exactly what to go set up — no workflow at
     * all, versus a workflow with zero levels — rather than one generic "not configured" message.
     */
    private int requireLevelCount(UplActionType actionType) {
        StdWorkflow workflow = findWorkflow(actionType)
                .orElseThrow(() -> new UplValidationException(
                        "No approval workflow has been created for Unified Price List – " + actionType.name()
                                + " action, so this " + actionType.name().toLowerCase()
                                + " can't proceed. Create one under Configurations > Approval Workflow first."));
        int levelCount = (int) stdLevelRepo.countByWorkflowIdAndStatus(workflow.getRecordNo(), ACTIVE_STATUS);
        if (levelCount == 0) {
            throw new UplValidationException(
                    "The approval workflow for Unified Price List – " + actionType.name()
                            + " action has no approval levels configured yet, so this " + actionType.name().toLowerCase()
                            + " can't proceed. Add at least one level under Configurations > Approval Levels.");
        }
        return levelCount;
    }

    private Optional<StdWorkflowApprovalLevel> findLevel(UplActionType actionType, int levelNo) {
        return findWorkflow(actionType)
                .flatMap(wf -> stdLevelRepo.findByWorkflowIdAndApprovalNumberAndStatus(wf.getRecordNo(), levelNo, ACTIVE_STATUS));
    }

    private List<Integer> approverIdsForLevel(Integer approvalLevelId) {
        return stdApproverRepo.findByApprovalLevelIdAndStatus(approvalLevelId, ACTIVE_STATUS).stream()
                .map(a -> a.getApproverId())
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean isApproverAtLevel(Integer approvalLevelId, Integer userId) {
        return stdApproverRepo.existsByApprovalLevelIdAndApproverIdAndStatus(approvalLevelId, userId, ACTIVE_STATUS);
    }

    /**
     * Admin/SuperAdmin bypass the "must be named on this specific level" restriction — they can
     * see and decide any pending request. This mirrors the frontend's own Admin/SuperAdmin check
     * (SignInPage.js: a case-insensitive "admin" substring on the user's roleName) since there's
     * no dedicated roleId or boolean flag for it; "SuperAdministrator" already contains "admin",
     * so a single substring check covers both roles without needing to tell them apart here.
     * They still need canApprove=true, and the self-approval guard still applies to them too.
     */
    private boolean isAdminOrSuperAdmin(User user) {
        if (user.getRoleId() == null) {
            return false;
        }
        return accessRoleRepo.findById(user.getRoleId())
                .map(AccessRole::getRoleName)
                .map(name -> name != null && name.toLowerCase().contains("admin"))
                .orElse(false);
    }

    // ============================================================
    // Create
    // ============================================================

    /**
     * All-or-nothing: every item in the batch is validated first, with nothing persisted yet.
     * If even one item fails, the whole submission is rejected — no partial batch where some
     * lines go on to approval while others silently don't. The caller gets back the specific
     * failure for every line that didn't pass, not just the first one.
     */
    @Transactional
    public UplChangeRequestBatchResult createChangeRequests(List<UplChangeRequestItem> items, Integer requestedBy,
            String batchId) {
        if (items == null || items.isEmpty()) {
            throw new UplValidationException("Nothing to submit");
        }
        User requester = userRepository.findById(requestedBy)
                .orElseThrow(() -> new UplValidationException("Unknown user"));
        if (!Boolean.TRUE.equals(requester.getCanEdit())) {
            throw new UplValidationException("You don't have permission to do that");
        }

        List<UplChangeRequest> prepared = new ArrayList<>();
        List<UplChangeRequestFailure> failures = new ArrayList<>();
        // Catches the same UPL line appearing twice in one submission — since nothing is saved
        // until every item has passed, the usual "already has a change pending approval" check
        // (which only sees already-persisted requests) can't catch that on its own.
        Set<Long> seenInThisBatch = new java.util.HashSet<>();

        for (UplChangeRequestItem item : items) {
            try {
                if (item.getUplRecordNo() != null && !seenInThisBatch.add(item.getUplRecordNo())) {
                    throw new UplValidationException(
                            "UPL line " + item.getUplRecordNo() + " appears more than once in this submission");
                }
                prepared.add(prepareOne(item, requester, batchId));
            } catch (UplValidationException ex) {
                failures.add(new UplChangeRequestFailure(item.getUplRecordNo(), ex.getMessage()));
            }
        }

        if (!failures.isEmpty()) {
            // Account for every submitted line, not just the ones that actually had a problem —
            // otherwise a line that passed its own validation would just silently vanish from the
            // response with no explanation for why it wasn't submitted.
            List<UplChangeRequestFailure> allFailures = new ArrayList<>(failures);
            for (UplChangeRequest cr : prepared) {
                allFailures.add(new UplChangeRequestFailure(cr.getUplRecordNo(),
                        "Not submitted — this line passed validation, but the whole batch was rejected "
                                + "because other line(s) in the same submission failed."));
            }
            return new UplChangeRequestBatchResult(Collections.emptyList(), allFailures);
        }

        List<UplChangeRequest> created = new ArrayList<>();
        for (UplChangeRequest cr : prepared) {
            created.add(changeRequestRepo.save(cr));
        }

        // Notify once per distinct changeType that just entered level 1.
        created.stream()
                .map(UplChangeRequest::getChangeType)
                .distinct()
                .forEach(type -> notifyLevelApprovers(created.stream()
                        .filter(cr -> cr.getChangeType() == type)
                        .findFirst()
                        .get()));
        return new UplChangeRequestBatchResult(created, failures);
    }

    /** Validates one item and builds its (unsaved) UplChangeRequest — persistence happens only
     *  after every item in the batch has passed this. */
    private UplChangeRequest prepareOne(UplChangeRequestItem item, User requester, String batchId) {
        if (item.getUplRecordNo() == null || item.getChangeType() == null) {
            throw new UplValidationException("Each item needs a uplRecordNo and a changeType");
        }
        tb_PurchaseOrderUPL uplLine = uplRepo.findByRecordNo(item.getUplRecordNo());
        if (uplLine == null || !ACTIVE.equals(uplLine.getStatus())) {
            throw new UplValidationException("UPL line " + item.getUplRecordNo() + " no longer exists");
        }
        if (!changeRequestRepo.findByUplRecordNoAndStatus(item.getUplRecordNo(), UplChangeRequestStatus.PENDING).isEmpty()) {
            throw new UplValidationException(
                    "UPL line " + uplLine.getUplLine() + " already has a change pending approval");
        }

        String fieldChangesJson = null;
        if (item.getChangeType() == UplActionType.DELETE) {
            validateNoPac(uplLine);
        } else {
            Map<String, Map<String, Object>> diff = buildDiff(uplLine, item.getFields());
            if (diff.isEmpty()) {
                throw new UplValidationException("No changes to submit for UPL line " + uplLine.getUplLine());
            }
            validateAgainstLineTotalAndPac(uplLine, diff);
            fieldChangesJson = writeJson(diff);
        }

        int totalLevels = requireLevelCount(item.getChangeType());

        UplChangeRequest cr = new UplChangeRequest();
        cr.setBatchId(batchId);
        cr.setUplRecordNo(item.getUplRecordNo());
        cr.setChangeType(item.getChangeType());
        cr.setFieldChanges(fieldChangesJson);
        cr.setTotalLevels(totalLevels);
        cr.setCurrentLevelNo(1);
        cr.setStatus(UplChangeRequestStatus.PENDING);
        cr.setRequestedBy(requester.getUserId());
        cr.setRequestedByName(requester.getFullName());
        return cr;
    }

    /** Whitelists the 7 approved fields and returns only the ones that actually changed. */
    private Map<String, Map<String, Object>> buildDiff(tb_PurchaseOrderUPL uplLine, Map<String, Object> proposed) {
        Map<String, Map<String, Object>> diff = new LinkedHashMap<>();
        if (proposed == null) {
            return diff;
        }
        for (Map.Entry<String, Object> entry : proposed.entrySet()) {
            String field = entry.getKey();
            if (!EDITABLE_FIELDS.contains(field)) {
                continue; // silently ignore anything outside the whitelist rather than fail the whole batch
            }
            Object oldValue = currentValue(uplLine, field);
            Object newValue = coerce(field, entry.getValue());
            if (!java.util.Objects.equals(String.valueOf(oldValue), String.valueOf(newValue))) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("old", oldValue);
                pair.put("new", newValue);
                diff.put(field, pair);
            }
        }
        return diff;
    }

    private Object currentValue(tb_PurchaseOrderUPL u, String field) {
        switch (field) {
            case "activeOrPassive": return u.getActiveOrPassive();
            case "uplItemSerialized": return u.getUplItemSerialized();
            case "uplLineUnitPrice": return u.getUplLineUnitPrice();
            case "uplLineQuantity": return u.getUplLineQuantity();
            case "uplLineDescription": return u.getUplLineDescription();
            case "projectName": return u.getProjectName();
            case "uplLineItemCode": return u.getUplLineItemCode();
            default: return null;
        }
    }

    private Object coerce(String field, Object value) {
        if (value == null) return null;
        if ("uplLineUnitPrice".equals(field) || "uplLineQuantity".equals(field)) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
        }
        return String.valueOf(value);
    }

    private double diffValue(Map<String, Map<String, Object>> diff, String field, double fallback) {
        Map<String, Object> pair = diff.get(field);
        if (pair == null) return fallback;
        Object v = pair.get("new");
        return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v));
    }

    private void validateAgainstLineTotalAndPac(tb_PurchaseOrderUPL uplLine, Map<String, Map<String, Object>> diff) {
        if (!diff.containsKey("uplLineQuantity") && !diff.containsKey("uplLineUnitPrice")) {
            return;
        }
        double proposedQty = diffValue(diff, "uplLineQuantity", uplLine.getUplLineQuantity());
        double proposedPrice = diffValue(diff, "uplLineUnitPrice", uplLine.getUplLineUnitPrice());
        validateLineTotal(uplLine, proposedQty, proposedPrice);
        if (diff.containsKey("uplLineQuantity") && proposedQty < uplLine.getUplLineQuantity()) {
            validatePacQtyFloor(uplLine, proposedQty);
        }
    }

    // ============================================================
    // Validation rules
    // ============================================================

    private void validateLineTotal(tb_PurchaseOrderUPL uplLine, double proposedQty, double proposedUnitPrice) {
        tbPurchaseOrder po = poRepo.findTopByPoNumberAndLineNumber(uplLine.getPoNumber(), uplLine.getPoLineNumber());
        if (po == null) {
            return; // PO existence is enforced when the line is first created; nothing further to check here
        }
        double ceiling = po.getLinePriceInSAR() > 0 ? po.getLinePriceInSAR() : po.getLinePriceInPoCurrency();
        List<tb_PurchaseOrderUPL> siblings = uplRepo.findByPoNumberAndPoLineNumberAndStatus(
                uplLine.getPoNumber(), uplLine.getPoLineNumber(), ACTIVE);
        double total = 0;
        for (tb_PurchaseOrderUPL sibling : siblings) {
            if (sibling.getRecordNo() == uplLine.getRecordNo()) {
                total += proposedQty * proposedUnitPrice;
            } else {
                total += sibling.getUplLineQuantity() * sibling.getUplLineUnitPrice();
            }
        }
        if (ceiling > 0 && total > ceiling) {
            throw new UplValidationException("UPL Line total cannot exceed PO Line Total Price. The combined total "
                    + "of all UPL line(s) under PO " + uplLine.getPoNumber() + " line " + uplLine.getPoLineNumber()
                    + " would be " + formatQty(total) + ", which exceeds the PO line's total price of "
                    + formatQty(ceiling) + ".");
        }
    }

    private void validatePacQtyFloor(tb_PurchaseOrderUPL uplLine, double proposedQty) {
        Double accepted = dccLineRepo.sumAcceptedQtyForUplLine(
                uplLine.getPoNumber(), uplLine.getPoLineNumber(), uplLine.getUplLine(), DCC_STATUSES_NOT_COUNTING_AS_PAC);
        if (accepted != null && proposedQty < accepted) {
            throw new UplValidationException("New quantity (" + formatQty(proposedQty)
                    + ") is below the total quantity already raised/submitted on active PAC(s) for this UPL line ("
                    + formatQty(accepted) + "). It can't be reduced below that.");
        }
    }

    /**
     * Formats a quantity/price for validation messages as a grouped, human-readable number
     * ("48,454,782.10" / "1,025") instead of double's raw toString, which switches to scientific
     * notation ("4.845478210373945E7") once the magnitude passes ~10^7.
     */ /
    private String formatQty(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return new DecimalFormat("#,##0").format(value);
        }
        return new DecimalFormat("#,##0.00").format(value);
    }

    private void validateNoPac(tb_PurchaseOrderUPL uplLine) {
        long count = dccLineRepo.countActivePacsForUplLine(
                uplLine.getPoNumber(), uplLine.getPoLineNumber(), uplLine.getUplLine(), DCC_STATUSES_NOT_COUNTING_AS_PAC);
        if (count > 0) {
            throw new UplValidationException("This line has a PAC raised against it and can't be deleted");
        }
    }

    // ============================================================
    // Decide (approve / reject a single level)
    // ============================================================

    @Transactional
    public UplChangeRequest decide(Long changeRequestId, Integer deciderId, UplDecision decision, String comments) {
        UplChangeRequest cr = changeRequestRepo.findById(changeRequestId)
                .orElseThrow(() -> new UplValidationException("Change request not found"));
        if (cr.getStatus() != UplChangeRequestStatus.PENDING) {
            throw new UplValidationException("This request has already been decided");
        }
        if (cr.getRequestedBy().equals(deciderId)) {
            throw new UplValidationException("Someone else needs to review this");
        }
        if (decision == UplDecision.REJECTED && (comments == null || comments.isBlank())) {
            throw new UplValidationException("A reason is required to reject");
        }
        User decider = userRepository.findById(deciderId)
                .orElseThrow(() -> new UplValidationException("Unknown user"));
        if (!Boolean.TRUE.equals(decider.getCanApprove())) {
            throw new UplValidationException("You don't have permission to do that");
        }
        StdWorkflowApprovalLevel level = findLevel(cr.getChangeType(), cr.getCurrentLevelNo())
                .orElseThrow(() -> new UplValidationException("Approval level no longer exists"));
        if (!isApproverAtLevel(level.getRecordNo(), deciderId) && !isAdminOrSuperAdmin(decider)) {
            throw new UplValidationException("You're not an approver at this level");
        }

        // Defense-in-depth: a request can only ever be sitting at one level at a
        // time (currentLevelNo advances strictly one level per approval), so this
        // should never trip in practice — but it's cheap insurance against ever
        // deciding a level out of order, especially now that Admin/SuperAdmin can
        // act on levels they aren't individually named on.
        requireLowerLevelsApproved(cr);

        UplChangeRequestDecision decisionRow = new UplChangeRequestDecision();
        decisionRow.setChangeRequestId(cr.getRecordId());
        decisionRow.setLevelNo(cr.getCurrentLevelNo());
        decisionRow.setDecision(decision);
        decisionRow.setDecidedBy(deciderId);
        decisionRow.setDecidedByName(decider.getFullName());
        decisionRow.setComments(comments);
        decisionRepo.save(decisionRow);

        if (decision == UplDecision.REJECTED) {
            cr.setStatus(UplChangeRequestStatus.REJECTED);
            changeRequestRepo.save(cr);
            notifyRequester(cr, "rejected", comments);
            return cr;
        }

        tb_PurchaseOrderUPL uplLine = uplRepo.findByRecordNo(cr.getUplRecordNo());
        if (uplLine == null) {
            cr.setStatus(UplChangeRequestStatus.AUTO_REJECTED);
            changeRequestRepo.save(cr);
            notifyRequester(cr, "auto-rejected", "The UPL line no longer exists");
            return cr;
        }

        try {
            if (cr.getChangeType() == UplActionType.DELETE) {
                validateNoPac(uplLine);
            } else {
                Map<String, Map<String, Object>> diff = readDiff(cr.getFieldChanges());
                validateAgainstLineTotalAndPac(uplLine, diff);
            }
        } catch (UplValidationException ex) {
            cr.setStatus(UplChangeRequestStatus.AUTO_REJECTED);
            changeRequestRepo.save(cr);
            notifyRequester(cr, "auto-rejected", "System: " + ex.getMessage());
            logger.info("UPL change request {} auto-rejected at level {}: {}", cr.getRecordId(), cr.getCurrentLevelNo(), ex.getMessage());
            return cr;
        }

        if (cr.getCurrentLevelNo() < cr.getTotalLevels()) {
            cr.setCurrentLevelNo(cr.getCurrentLevelNo() + 1);
            changeRequestRepo.save(cr);
            notifyLevelApprovers(cr);
            return cr;
        }

        apply(cr, uplLine);
        cr.setStatus(UplChangeRequestStatus.APPROVED);
        changeRequestRepo.save(cr);
        notifyRequester(cr, "approved", comments);
        return cr;
    }

    private void requireLowerLevelsApproved(UplChangeRequest cr) {
        if (cr.getCurrentLevelNo() <= 1) {
            return;
        }
        List<UplChangeRequestDecision> priorDecisions = decisionRepo.findByChangeRequestIdOrderByLevelNoAsc(cr.getRecordId());
        for (int levelNo = 1; levelNo < cr.getCurrentLevelNo(); levelNo++) {
            int checkLevel = levelNo;
            boolean approved = priorDecisions.stream()
                    .anyMatch(d -> d.getLevelNo() == checkLevel && d.getDecision() == UplDecision.APPROVED);
            if (!approved) {
                throw new UplValidationException(
                        "Level " + checkLevel + " must be approved before Level " + cr.getCurrentLevelNo() + " can be decided");
            }
        }
    }

    private void apply(UplChangeRequest cr, tb_PurchaseOrderUPL uplLine) {
        if (cr.getChangeType() == UplActionType.DELETE) {
            uplLine.setStatus(DELETED);
        } else {
            Map<String, Map<String, Object>> diff = readDiff(cr.getFieldChanges());
            for (Map.Entry<String, Map<String, Object>> entry : diff.entrySet()) {
                Object newValue = entry.getValue().get("new");
                switch (entry.getKey()) {
                    case "activeOrPassive": uplLine.setActiveOrPassive((String) newValue); break;
                    case "uplItemSerialized": uplLine.setUplItemSerialized((String) newValue); break;
                    case "uplLineUnitPrice": uplLine.setUplLineUnitPrice(((Number) newValue).doubleValue()); break;
                    case "uplLineQuantity": uplLine.setUplLineQuantity(((Number) newValue).doubleValue()); break;
                    case "uplLineDescription": uplLine.setUplLineDescription((String) newValue); break;
                    case "projectName": uplLine.setProjectName((String) newValue); break;
                    case "uplLineItemCode": uplLine.setUplLineItemCode((String) newValue); break;
                    default: break;
                }
            }
            uplLine.setUplModifiedBy(cr.getRequestedByName());
            uplLine.setUplModifiedDate(new java.sql.Date(System.currentTimeMillis()));
        }
        uplRepo.save(uplLine);
    }

    // ============================================================
    // Reads
    // ============================================================

    public List<UplChangeRequest> findPending(UplActionType changeType, Integer currentLevelNo) {
        List<UplChangeRequest> pending = changeRequestRepo.findByStatusOrderByRequestedAtDesc(UplChangeRequestStatus.PENDING);
        pending.removeIf(cr -> (changeType != null && cr.getChangeType() != changeType)
                || (currentLevelNo != null && !cr.getCurrentLevelNo().equals(currentLevelNo)));
        return pending;
    }

    /**
     * Every request currently sitting at a level this user is named as an approver for —
     * or, for Admin/SuperAdmin, every pending request regardless of level or who's named on it.
     */
    public List<UplChangeRequest> findAssignedToApprover(Integer userId) {
        List<UplChangeRequest> pending = changeRequestRepo.findByStatusOrderByRequestedAtDesc(UplChangeRequestStatus.PENDING);

        User user = userRepository.findById(userId).orElse(null);
        if (user != null && isAdminOrSuperAdmin(user)) {
            return pending;
        }

        List<UplChangeRequest> assigned = new ArrayList<>();
        for (UplChangeRequest cr : pending) {
            Optional<StdWorkflowApprovalLevel> level = findLevel(cr.getChangeType(), cr.getCurrentLevelNo());
            if (level.isPresent() && isApproverAtLevel(level.get().getRecordNo(), userId)) {
                assigned.add(cr);
            }
        }
        return assigned;
    }

    public List<UplChangeRequest> findMine(Integer requestedBy) {
        return changeRequestRepo.findByRequestedByOrderByRequestedAtDesc(requestedBy);
    }

    public List<UplChangeRequest> findByBatch(String batchId) {
        return changeRequestRepo.findByBatchIdOrderByRecordIdAsc(batchId);
    }

    public Optional<UplChangeRequest> findById(Long recordId) {
        return changeRequestRepo.findById(recordId);
    }

    public List<UplChangeRequestDecision> findDecisions(Long changeRequestId) {
        return decisionRepo.findByChangeRequestIdOrderByLevelNoAsc(changeRequestId);
    }

    // ============================================================
    // Notifications — reuses EmailService + the shared tb_InApp_Notifications
    // table the existing bell (WorkFlow-Management's NotificationController)
    // already reads, so no frontend change is needed to surface these.
    // ============================================================

    private void notifyLevelApprovers(UplChangeRequest cr) {
        Optional<StdWorkflowApprovalLevel> level = findLevel(cr.getChangeType(), cr.getCurrentLevelNo());
        if (level.isEmpty()) {
            logger.warn("No approval level {} configured for {} — request {} has no one to notify",
                    cr.getCurrentLevelNo(), cr.getChangeType(), cr.getRecordId());
            return;
        }
        String message = String.format("%s requested to %s a Unified Price List line — Level %d of %d approval needed",
                cr.getRequestedByName(), cr.getChangeType(), cr.getCurrentLevelNo(), cr.getTotalLevels());
        for (Integer approverId : approverIdsForLevel(level.get().getRecordNo())) {
            userRepository.findById(approverId).ifPresent(u -> {
                insertNotification(cr.getRecordId(), u.getUserId(), message, "UPL_CHANGE_REQUEST");
                if (u.getEmailAddress() != null && !u.getEmailAddress().isBlank()) {
                    emailService.sendEmail(u.getEmailAddress(), "UPL approval needed", message,
                            Collections.emptyList(), null, u.getFullName(), null, null, null, null);
                }
            });
        }
    }

    private void notifyRequester(UplChangeRequest cr, String outcome, String comments) {
        userRepository.findById(cr.getRequestedBy()).ifPresent(u -> {
            String message = "Your UPL " + cr.getChangeType().name().toLowerCase() + " request was " + outcome
                    + (comments != null && !comments.isBlank() ? ": " + comments : "");
            insertNotification(cr.getRecordId(), u.getUserId(), message, "UPL_CHANGE_DECISION");
            if (u.getEmailAddress() != null && !u.getEmailAddress().isBlank()) {
                emailService.sendEmail(u.getEmailAddress(), "UPL request " + outcome, message,
                        Collections.emptyList(), null, u.getFullName(), null, null, null, null);
            }
        });
    }

    private void insertNotification(Long changeRequestId, Integer userId, String message, String type) {
        UplInAppNotification n = new UplInAppNotification();
        n.setRequestRecordNo(changeRequestId.intValue());
        n.setApproverId(userId);
        n.setMessage(message);
        n.setNotificationType(type);
        n.setRead(false);
        n.setActive(true);
        notificationRepo.save(n);
    }

    // ============================================================
    // JSON helpers
    // ============================================================

    private String writeJson(Map<String, Map<String, Object>> diff) {
        try {
            return objectMapper.writeValueAsString(diff);
        } catch (Exception e) {
            throw new UplValidationException("Could not encode field changes: " + e.getMessage());
        }
    }

    private Map<String, Map<String, Object>> readDiff(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new UplValidationException("Could not decode field changes: " + e.getMessage());
        }
    }
}
