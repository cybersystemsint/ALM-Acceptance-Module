package com.zain.almksazain.services;

import java.math.BigDecimal;
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

    // Mirrors UPLApprovalGrid.js's FIELD_LABELS / ExportsController's UPL_CHANGE_FIELD_LABELS, so
    // the "approval needed" email's grid table reads the same way the page and export do.
    private static final Map<String, String> EMAIL_FIELD_LABELS = new LinkedHashMap<>();
    static {
        EMAIL_FIELD_LABELS.put("activeOrPassive", "Active/Passive");
        EMAIL_FIELD_LABELS.put("uplItemSerialized", "Serialized");
        EMAIL_FIELD_LABELS.put("uplLineUnitPrice", "UPL Unit Price");
        EMAIL_FIELD_LABELS.put("uplLineQuantity", "UPL Line Qty");
        EMAIL_FIELD_LABELS.put("uplLineDescription", "UPL Line Description");
        EMAIL_FIELD_LABELS.put("projectName", "Project Name");
        EMAIL_FIELD_LABELS.put("uplLineItemCode", "UPL Line-Item Code");
    }

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

        List<PreparedItem> preparedItems = new ArrayList<>();
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
                preparedItems.add(prepareDiff(item));
            } catch (UplValidationException ex) {
                failures.add(new UplChangeRequestFailure(item.getUplRecordNo(), ex.getMessage()));
            }
        }

        // Other lines under the same PO+line may be edited by a different row in this same
        // submission - collect every line's proposed quantity/price up front so the line-total
        // check below reflects the combined effect of the whole batch, not just one row
        // validated in isolation against everyone else's stale, pre-edit DB values.
        Map<Long, double[]> batchProposedByRecordNo = new java.util.HashMap<>();
        for (PreparedItem p : preparedItems) {
            if (p.diff != null
                    && (p.diff.containsKey("uplLineQuantity") || p.diff.containsKey("uplLineUnitPrice"))) {
                double qty = diffValue(p.diff, "uplLineQuantity", p.uplLine.getUplLineQuantity());
                double price = diffValue(p.diff, "uplLineUnitPrice", p.uplLine.getUplLineUnitPrice());
                batchProposedByRecordNo.put(p.uplLine.getRecordNo(), new double[]{qty, price});
            }
        }

        List<UplChangeRequest> prepared = new ArrayList<>();
        for (PreparedItem p : preparedItems) {
            try {
                if (p.diff != null) {
                    validateAgainstLineTotalAndPac(p.uplLine, p.diff, batchProposedByRecordNo);
                }
                prepared.add(buildChangeRequest(p, requester, batchId));
            } catch (UplValidationException ex) {
                failures.add(new UplChangeRequestFailure(p.item.getUplRecordNo(), ex.getMessage()));
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

    /**
     * Loads the UPL line, does existence/pending-change checks, and builds the whitelisted diff —
     * everything that doesn't depend on what else is in this same batch submission. Cross-line
     * validation (line total vs PO ceiling) happens afterwards, once every item's diff is known,
     * so it can see every line's proposed values, not just this one.
     */
    private PreparedItem prepareDiff(UplChangeRequestItem item) {
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

        if (item.getChangeType() == UplActionType.DELETE) {
            validateNoPac(uplLine);
            return new PreparedItem(item, uplLine, null);
        }
        Map<String, Map<String, Object>> diff = buildDiff(uplLine, item.getFields());
        if (diff.isEmpty()) {
            throw new UplValidationException("No changes to submit for UPL line " + uplLine.getUplLine());
        }
        return new PreparedItem(item, uplLine, diff);
    }

    /** Finishes building the (unsaved) UplChangeRequest once cross-line validation has passed. */
    private UplChangeRequest buildChangeRequest(PreparedItem p, User requester, String batchId) {
        String fieldChangesJson = p.diff != null ? writeJson(p.diff) : null;
        int totalLevels = requireLevelCount(p.item.getChangeType());

        UplChangeRequest cr = new UplChangeRequest();
        cr.setBatchId(batchId);
        cr.setUplRecordNo(p.item.getUplRecordNo());
        cr.setChangeType(p.item.getChangeType());
        cr.setFieldChanges(fieldChangesJson);
        cr.setTotalLevels(totalLevels);
        cr.setCurrentLevelNo(1);
        cr.setStatus(UplChangeRequestStatus.PENDING);
        cr.setRequestedBy(requester.getUserId());
        cr.setRequestedByName(requester.getFullName());
        // requestedAt is @Column(insertable = false, updatable = false) - the DB assigns it via
        // DEFAULT CURRENT_TIMESTAMP and there's no setter, so this in-memory object never carries
        // it. notifyLevelApprovers() re-queries via findAssignedToApprover() (a real SELECT) to
        // build its email, which picks up the DB-assigned value correctly - don't read
        // cr.getRequestedAt() on THIS just-built object, it will be null.
        return cr;
    }

    /** Holds one item's loaded UPL line and computed diff between {@link #prepareDiff} and
     *  {@link #buildChangeRequest}. {@code diff} is null for DELETE items. */
    private static final class PreparedItem {
        final UplChangeRequestItem item;
        final tb_PurchaseOrderUPL uplLine;
        final Map<String, Map<String, Object>> diff;

        PreparedItem(UplChangeRequestItem item, tb_PurchaseOrderUPL uplLine, Map<String, Map<String, Object>> diff) {
            this.item = item;
            this.uplLine = uplLine;
            this.diff = diff;
        }
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

    private void validateAgainstLineTotalAndPac(tb_PurchaseOrderUPL uplLine, Map<String, Map<String, Object>> diff,
            Map<Long, double[]> batchProposedByRecordNo) {
        if (!diff.containsKey("uplLineQuantity") && !diff.containsKey("uplLineUnitPrice")) {
            return;
        }
        double proposedQty = diffValue(diff, "uplLineQuantity", uplLine.getUplLineQuantity());
        validateLineTotal(uplLine, batchProposedByRecordNo);
        if (diff.containsKey("uplLineQuantity") && proposedQty < uplLine.getUplLineQuantity()) {
            validatePacQtyFloor(uplLine, proposedQty);
        }
    }

    // ============================================================
    // Validation rules
    // ============================================================

    private void validateLineTotal(tb_PurchaseOrderUPL uplLine, Map<Long, double[]> batchProposedByRecordNo) {
        tbPurchaseOrder po = poRepo.findTopByPoNumberAndLineNumber(uplLine.getPoNumber(), uplLine.getPoLineNumber());
        if (po == null) {
            return; // PO existence is enforced when the line is first created; nothing further to check here
        }
        double ceiling = po.getLinePriceInSAR() > 0 ? po.getLinePriceInSAR() : po.getLinePriceInPoCurrency();
        List<tb_PurchaseOrderUPL> siblings = uplRepo.findByPoNumberAndPoLineNumberAndStatus(
                uplLine.getPoNumber(), uplLine.getPoLineNumber(), ACTIVE);
        double total = 0;
        for (tb_PurchaseOrderUPL sibling : siblings) {
            // A sibling line may also be edited by a different row in this same batch submission -
            // use its proposed (not-yet-saved) quantity/price instead of its stale DB value, so the
            // combined total reflects the whole batch's effect, not just this one line in isolation
            // (which previously understated the total whenever more than one sibling was edited
            // together, since only the line being validated got its new value substituted in).
            //
            // A sibling NOT part of this same call's batch map is read straight from
            // tb_PurchaseOrderUPL - deliberately never from another sibling's own still-PENDING,
            // not-yet-approved change request. That pending value isn't real yet: it could be
            // rejected, so trusting it here could let a decision through on a number that never
            // actually materializes. A single decide() call only validates against what's
            // currently true in the database; if a batch of siblings needs to be evaluated
            // together, it needs to be decided together (see decide()'s own re-validation).
            double[] batchValues = batchProposedByRecordNo.get(sibling.getRecordNo());
            if (batchValues != null) {
                total += batchValues[0] * batchValues[1];
            } else {
                total += sibling.getUplLineQuantity() * sibling.getUplLineUnitPrice();
            }
        }
        if (ceiling > 0 && total > ceiling) {
            double difference = total - ceiling;
            throw new UplValidationException("UPL Line total cannot exceed PO Line Total Price. The combined total "
                    + "of all UPL line(s) under PO " + uplLine.getPoNumber() + " line " + uplLine.getPoLineNumber()
                    + " would be " + formatQty(total) + ", which exceeds the PO line's total price of "
                    + formatQty(ceiling) + " by " + formatQty(difference) + ".");
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
     * ("48,454,782.10373945" / "1,025") instead of double's raw toString, which switches to
     * scientific notation ("4.845478210373945E7") once the magnitude passes ~10^7. Deliberately
     * not rounded to a fixed number of decimals - the message must show the exact value being
     * compared, not an approximation of it, so validation failures can be verified against the
     * database value as-is.
     */
    private String formatQty(double value) {
        // BigDecimal(Double.toString(value)) - not `new BigDecimal(value)` - to get the shortest
        // decimal that round-trips to this exact double (what Double.toString shows), rather than
        // the double's full noisy binary expansion.
        BigDecimal exact = new BigDecimal(Double.toString(value));
        DecimalFormat fmt = new DecimalFormat("#,##0.#");
        fmt.setMaximumFractionDigits(340); // effectively unlimited: never truncates/rounds
        return fmt.format(exact);
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

        // Safety-net re-validation happens BEFORE anything is recorded, and only for APPROVED -
        // rejecting never applies anything, so there's nothing to re-check. If this line's own
        // change no longer fits (e.g. a sibling under the same PO line was itself edited/applied
        // since this request was submitted), the whole decide() call fails outright: nothing is
        // persisted (the decision is never recorded as "Approved" only to be silently overridden),
        // the request stays PENDING, and the approver gets a clear error telling them why and what
        // to do about it - they cannot approve this line in isolation right now.
        tb_PurchaseOrderUPL uplLine = null;
        if (decision == UplDecision.APPROVED) {
            uplLine = uplRepo.findByRecordNo(cr.getUplRecordNo());
            if (uplLine == null) {
                throw new UplValidationException(
                        "This UPL line no longer exists, so this request can't be approved. Please reject it instead.");
            }
            try {
                if (cr.getChangeType() == UplActionType.DELETE) {
                    validateNoPac(uplLine);
                } else {
                    Map<String, Map<String, Object>> diff = readDiff(cr.getFieldChanges());
                    // Siblings are read from tb_PurchaseOrderUPL only - never from another
                    // sibling's own still-pending, not-yet-approved request. A single decide()
                    // call only ever knows this one line's proposed value is real; every other
                    // sibling's pending edit might still be rejected, so trusting it here could
                    // let a combined total through that later turns out to be wrong. If this
                    // batch was meant to be validated together, it needs to be approved together.
                    Map<Long, double[]> onlyThisLine = new java.util.HashMap<>();
                    double proposedQty = diffValue(diff, "uplLineQuantity", uplLine.getUplLineQuantity());
                    double proposedPrice = diffValue(diff, "uplLineUnitPrice", uplLine.getUplLineUnitPrice());
                    onlyThisLine.put(uplLine.getRecordNo(), new double[]{proposedQty, proposedPrice});
                    validateAgainstLineTotalAndPac(uplLine, diff, onlyThisLine);
                }
            } catch (UplValidationException ex) {
                logger.info("UPL change request {} blocked from approval at level {}: {}",
                        cr.getRecordId(), cr.getCurrentLevelNo(), ex.getMessage());
                throw new UplValidationException(ex.getMessage()
                        + " This line was likely submitted together with other UPL line(s) under the same PO "
                        + "line that are still pending their own approval - approve or reject those first (or "
                        + "together with this one), then try approving this line again.");
            }
        }

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

    /**
     * Attaches poNumber/poLineNumber/uplLine (looked up from tb_PurchaseOrderUPL) alongside each
     * change request's own fields - UplChangeRequest itself has no such columns since those live
     * on the UPL line, not the request. Mirrors what the Audit Trail's SQL JOIN already surfaces,
     * so the UPL Approval grid/export can show the same PO Number/PO Line/UPL Line columns.
     */
    public List<Map<String, Object>> enrichWithUplLineDetails(List<UplChangeRequest> requests) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UplChangeRequest cr : requests) {
            tb_PurchaseOrderUPL uplLine = cr.getUplRecordNo() != null ? uplRepo.findByRecordNo(cr.getUplRecordNo()) : null;
            // Built field-by-field (not via ObjectMapper.convertValue) so this doesn't depend on
            // this class's local, un-configured ObjectMapper handling LocalDateTime/enum fields -
            // the raw values below are formatted by Spring's own (JavaTimeModule-registered)
            // response serializer, same as returning the entity directly would be.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", cr.getRecordId());
            row.put("batchId", cr.getBatchId());
            row.put("uplRecordNo", cr.getUplRecordNo());
            row.put("changeType", cr.getChangeType());
            row.put("fieldChanges", cr.getFieldChanges());
            row.put("totalLevels", cr.getTotalLevels());
            row.put("currentLevelNo", cr.getCurrentLevelNo());
            row.put("status", cr.getStatus());
            row.put("requestedBy", cr.getRequestedBy());
            row.put("requestedByName", cr.getRequestedByName());
            row.put("requestedAt", cr.getRequestedAt());
            row.put("poNumber", uplLine != null ? uplLine.getPoNumber() : null);
            row.put("poLineNumber", uplLine != null ? uplLine.getPoLineNumber() : null);
            row.put("uplLine", uplLine != null ? uplLine.getUplLine() : null);
            result.add(row);
        }
        return result;
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
        // Plain-text version stays on the in-app bell notification (tb_InApp_Notifications) -
        // only the email body gets the HTML grid table below.
        String message = String.format("%s requested to %s a Unified Price List line — Level %d of %d approval needed",
                cr.getRequestedByName(), cr.getChangeType(), cr.getCurrentLevelNo(), cr.getTotalLevels());
        for (Integer approverId : approverIdsForLevel(level.get().getRecordNo())) {
            userRepository.findById(approverId).ifPresent(u -> {
                insertNotification(cr.getRecordId(), u.getUserId(), message, "UPL_CHANGE_REQUEST");
                if (u.getEmailAddress() != null && !u.getEmailAddress().isBlank()) {
                    // The email lists everything currently assigned to this approver (matching
                    // the UPL Approval page/export), not just the one request that triggered this
                    // particular notification - so a fresh table always reflects their full queue.
                    List<UplChangeRequest> assigned = findAssignedToApprover(approverId);
                    String emailHtml = buildLevelApprovalEmailHtml(assigned, u.getFullName());
                    emailService.sendEmail(u.getEmailAddress(), "UPL approval needed", emailHtml,
                            Collections.emptyList(), null, u.getFullName(), null, null, null, null);
                }
            });
        }
    }

    /**
     * Builds the "UPL approval needed" email body as an HTML page with a grid table listing every
     * request currently assigned to this approver - same column set and one-row-per-changed-field
     * shape as the UPL Approval page's export (Record ID, Type, UPL Line ID, PO Number, PO Line,
     * UPL Line, Level, Field, Old Value, New Value, Requested By). Styling (green header,
     * black-bordered cells, alternating row background, .desc-table summary block, red warning
     * footer) mirrors the SLA reminder emails in SlaNotificationService, so approvers get a
     * visually consistent set of automated emails from this app.
     */
    private String buildLevelApprovalEmailHtml(List<UplChangeRequest> requests, String approverName) {
        String approverDisplay = approverName == null ? "Approver" : approverName;

        String thBase = "style=\"background:#74B72E;color:#ffffff;font-weight:700;padding:10px 8px;"
                + "border:1px solid #000000;text-align:left;white-space:nowrap;\"";
        String tdBase = "style=\"border:1px solid #000000;padding:8px;vertical-align:top;\"";

        StringBuilder table = new StringBuilder(4096);
        table.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"border-collapse:collapse;table-layout:fixed;font-size:12px;word-break:break-word;"
                + "width:100%;border:1px solid #000000;\">");
        table.append("<thead><tr>");
        for (String header : new String[]{"Record ID", "Type", "UPL Line ID", "PO Number", "PO Line", "UPL Line",
                "Level", "Field", "Old Value", "New Value", "Requested By"}) {
            table.append("<th ").append(thBase).append(">").append(header).append("</th>");
        }
        table.append("</tr></thead><tbody>");

        int rowIndex = 0;
        for (UplChangeRequest cr : requests) {
            tb_PurchaseOrderUPL uplLine = uplRepo.findByRecordNo(cr.getUplRecordNo());
            String changeType = cr.getChangeType() != null ? cr.getChangeType().toString() : "";
            String poNumber = uplLine != null ? uplLine.getPoNumber() : "";
            String poLineNumber = uplLine != null ? uplLine.getPoLineNumber() : "";
            String uplLineNo = uplLine != null ? uplLine.getUplLine() : "";
            String levelText = cr.getCurrentLevelNo() + " of " + cr.getTotalLevels();

            for (String[] fieldRow : emailFieldChangeRows(cr)) {
                String rowBg = (rowIndex % 2 == 0) ? "background:#ffffff;" : "background:#fbfff9;";
                rowIndex++;
                table.append("<tr style=\"").append(rowBg).append("\">");
                table.append("<td ").append(tdBase).append(">").append(cr.getRecordId()).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(changeType)).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(cr.getUplRecordNo()).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(poNumber)).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(poLineNumber)).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(uplLineNo)).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(levelText)).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(fieldRow[0])).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(fieldRow[1])).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(fieldRow[2])).append("</td>");
                table.append("<td ").append(tdBase).append(">").append(escapeHtml(cr.getRequestedByName())).append("</td>");
                table.append("</tr>");
            }
        }
        table.append("</tbody></table>");

        String salutation = "<p>Dear " + escapeHtml(approverDisplay) + ",</p>";
        return String.format("""
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width,initial-scale=1"/>
                <style>
                  body { font-family: Arial, Helvetica, sans-serif; color: #333; margin: 0; padding: 0; background: #fff; }
                  table { width: 100%%; border-collapse: collapse; font-size: 13px; margin-top: 8px; }
                  th, td { border: 1px solid #74B72E; padding: 6px; text-align: left; font-size: 12px; }
                  th { background-color: #74B72E; color: #fff; }
                  .desc-table { border: none; }
                  .desc-table td { border: none; padding: 3px 8px 3px 0; }
                  p { font-size: 13px; }
                  .footer { margin-top: 16px; font-size: 11px; color: #9c1b1b; }
                </style>
              </head>
              <body>
                %s
                <table class="desc-table">
                  <tr><td style="font-weight:700;width:160px;">Request(s):</td><td>%d</td></tr>
                  <tr><td style="font-weight:700;">Note:</td><td>The Unified Price List change(s) below are awaiting your approval.</td></tr>
                </table>
                <p>Please review the change(s) below and action these requests.</p>
                <div style='overflow:auto;'>%s</div>
                <p class="footer">Warning: This is an automated email. Please do not reply or forward.</p>
              </body>
            </html>
        """,
        salutation,
        requests.size(),
        table.toString()
        );
    }

    /**
     * One {field label, old value, new value} triple per changed field, built from this class's
     * own already-parsed diff ({@link #readDiff}) - used only for the level-approval-needed
     * email's grid table. A DELETE request still gets exactly one row, describing that instead.
     */
    private List<String[]> emailFieldChangeRows(UplChangeRequest cr) {
        List<String[]> rows = new ArrayList<>();
        if (cr.getChangeType() == UplActionType.DELETE) {
            rows.add(new String[]{"(whole UPL line)", "", "Deleted"});
            return rows;
        }
        Map<String, Map<String, Object>> diff = readDiff(cr.getFieldChanges());
        for (Map.Entry<String, Map<String, Object>> entry : diff.entrySet()) {
            String label = EMAIL_FIELD_LABELS.getOrDefault(entry.getKey(), entry.getKey());
            Object oldValue = entry.getValue().get("old");
            Object newValue = entry.getValue().get("new");
            rows.add(new String[]{
                    label,
                    oldValue != null ? oldValue.toString() : "(empty)",
                    newValue != null ? newValue.toString() : "(empty)",
            });
        }
        if (rows.isEmpty()) {
            rows.add(new String[]{"", "", ""});
        }
        return rows;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
