package com.zain.almksazain.services;

import com.zain.almksazain.model.User;
import com.zain.almksazain.model.supplierdata;
import com.zain.almksazain.model.tbCategoryApprovalRequests;
import com.zain.almksazain.model.tbCategoryApprovals;
import com.zain.almksazain.model.tbDelegation;
import com.zain.almksazain.model.tbScopeApprovalLevelRegions;
import com.zain.almksazain.model.tbScopeApprovalLevels;
import com.zain.almksazain.model.tbScopeApprovalSuppliers;
import com.zain.almksazain.model.tb_Region;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.supplierrepo;
import com.zain.almksazain.repo.tbDelegationRepo;
import com.zain.almksazain.repo.tbRegionRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelRegionsRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelsRepo;
import com.zain.almksazain.repo.tbScopeApprovalSuppliersRepo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local re-implementation of WorkFlow-Management's
 * ScopeApprovalService.initializeApproval()/createScopeApprovalsForRequest(),
 * run in-process so it can share the caller's database transaction instead of
 * going through a separate, best-effort HTTP call to another service. See
 * postdcc()/postdccln() in APIController, which used to fire this off
 * asynchronously and swallow any failure.
 *
 * Kept as close as possible to the original algorithm (same level/region
 * iteration, same first-level supplier-override + delegation handling) to
 * minimize behavioral drift from the tested WorkFlow-Management logic
 * (duplicate-initialization guard, soft-deleted levels skipped, a single
 * resolved level-1 config). levelNumber/departmentId are copied onto each
 * tb_Category_Approvals row at creation time (see tbCategoryApprovals), so a
 * later edit to tb_Scope_Approval_Levels can't retroactively change what an
 * already-created approval row resolves to.
 */
@Service
public class CategoryApprovalInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryApprovalInitializationService.class);

    @Autowired
    private tbScopeApprovalLevelsRepo scopeApprovalLevelsRepo;
    @Autowired
    private tbScopeApprovalLevelRegionsRepo scopeApprovalLevelRegionsRepo;
    @Autowired
    private tbScopeApprovalSuppliersRepo scopeApprovalSuppliersRepo;
    @Autowired
    private tbDelegationRepo delegationRepo;
    @Autowired
    private supplierrepo supplierRepo;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private tbRegionRepo regionRepo;
    @Autowired
    private TbCategoryApprovalRequestsRepository categoryApprovalRequestsRepo;
    @Autowired
    private TbCategoryApprovalsRepository categoryApprovalsRepo;

    /**
     * Creates the tb_Category_Approval_Requests parent row and the first
     * batch of tb_Category_Approvals rows for the given scope. Runs with
     * Propagation.MANDATORY - this must only ever be called from within a
     * transaction the caller already started (postdcc), since a failure
     * here is meant to roll back the DCC/line items created earlier in the
     * same request, not just this method's own writes.
     *
     * @return the first-level approver, so the caller can email them once
     *         the whole transaction has committed (not before - email
     *         should never fire for a request that ends up rolled back).
     * @throws RuntimeException if no approval levels are configured for the
     *         scope/regions, or if any referenced approver/region/supplier
     *         can't be resolved - either way the caller's transaction must
     *         roll back rather than leave a request with no approval chain.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<User> initializeApproval(int acceptanceRequestRecordNo, String vendorName, String requestedBy,
            int scopeRecordNo, Integer createdByUserId, List<String> regionNames, String tableName,
            String poLineItemDescription, String poNumber) {

        assertNotAlreadyInitialized(acceptanceRequestRecordNo, scopeRecordNo);

        tbCategoryApprovalRequests approvalRequest = new tbCategoryApprovalRequests();
        approvalRequest.setRecordDateTime(new Timestamp(System.currentTimeMillis()));
        approvalRequest.setAcceptanceRequestRecordNo(acceptanceRequestRecordNo);
        approvalRequest.setPoNumber(poNumber);
        approvalRequest.setTableName(tableName);
        approvalRequest.setPoLineItemDescription(poLineItemDescription);
        approvalRequest.setVendorName(vendorName);
        approvalRequest.setRequestedBy(requestedBy);
        approvalRequest.setCreatedBy(createdByUserId != null ? createdByUserId : 0);
        approvalRequest.setScope(scopeRecordNo);
        approvalRequest.setStatus("pending");
        approvalRequest.setReceived(false);

        tbCategoryApprovalRequests savedRequest = categoryApprovalRequestsRepo.save(approvalRequest);
        logger.info("Category approval request created with recordNo: {}", savedRequest.getRecordNo());

        boolean childApprovalsCreated = createCategoryApprovalsForRequest(savedRequest, regionNames, vendorName, scopeRecordNo);
        if (!childApprovalsCreated) {
            throw new RuntimeException("No approval levels are configured for the selected regions and scope.");
        }

        return getFirstApproverForRequest(savedRequest.getRecordNo());
    }

    /**
     * Re-activates a request that's sitting at status "request-info" back to
     * "pending" for the specific approver who asked for more information -
     * mirrors ScopeApprovalService.reInitializeRequestForInfo(). Unlike
     * initializeApproval(), this doesn't create a new parent request row; it
     * closes out the one "request-info" child (marking its own status
     * "request-info" as a historical record) and opens a fresh child for the
     * same level/approver/region so that approver sees it as ready again.
     *
     * @return the approver who requested more info (and who should be
     *         re-notified now that the request is back in their queue).
     * @throws RuntimeException if no request-info parent/child record is
     *         found for this acceptanceRequestRecordNo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<User> reInitializeRequestForInfo(int acceptanceRequestRecordNo) {
        tbCategoryApprovalRequests parentRecord = categoryApprovalRequestsRepo
                .findByAcceptanceRequestRecordNoAndStatus(acceptanceRequestRecordNo, "request-info")
                .orElseThrow(() -> new RuntimeException(
                        "Parent record not found with status 'request-info' for acceptanceRequestRecordNo: " + acceptanceRequestRecordNo));

        parentRecord.setStatus("pending");
        parentRecord.setApprovedDate(null);
        parentRecord.setRecordDateTime(new Timestamp(System.currentTimeMillis()));

        List<tbCategoryApprovals> children = categoryApprovalsRepo.findByApprovalRecordId(parentRecord.getRecordNo());

        tbCategoryApprovals infoRequesterChild = null;
        for (tbCategoryApprovals child : children) {
            if ("request-info".equalsIgnoreCase(child.getApprovalStatus()) && "pending".equalsIgnoreCase(child.getStatus())) {
                infoRequesterChild = child;

                // Close out the old child as historical.
                child.setStatus("request-info");
                categoryApprovalsRepo.save(child);

                // Open a fresh child for the same level/approver/region.
                tbCategoryApprovals newChild = new tbCategoryApprovals();
                newChild.setApprovalRecordId(parentRecord.getRecordNo());
                newChild.setApprovalLevelId(child.getApprovalLevelId());
                newChild.setLevelNumber(child.getLevelNumber());
                newChild.setDepartmentId(child.getDepartmentId());
                newChild.setApproverId(child.getApproverId());
                newChild.setApproverName(child.getApproverName());
                newChild.setRegionId(child.getRegionId());
                newChild.setComments(null);
                newChild.setApprovedBy(null);
                newChild.setActionTypeId(null);
                newChild.setApprovalStatus("readyForApproval");
                newChild.setStatus("pending");
                newChild.setApprovedDate(null);
                newChild.setRecordDateTime(LocalDateTime.now());
                newChild.setDisplay(true);
                categoryApprovalsRepo.save(newChild);

                break;
            }
        }

        if (infoRequesterChild == null) {
            throw new RuntimeException("No child record with status 'request-info' found for acceptanceRequestRecordNo: " + acceptanceRequestRecordNo);
        }

        categoryApprovalRequestsRepo.save(parentRecord);

        return userRepository.findById(infoRequesterChild.getApproverId());
    }

    /**
     * Blocks a second active chain for the same acceptance request and scope -
     * mirrors ScopeApprovalService.assertNotAlreadyInitialized(). Returned and
     * rejected requests may be initialized again; pending, approved and
     * request-info may not. Checked per scope (the original checked per
     * acceptance request only) because postdcc() initializes one chain per
     * item category inside a single transaction, so a cross-scope check would
     * roll back every multi-category DCC.
     */
    private void assertNotAlreadyInitialized(int acceptanceRequestRecordNo, int scopeRecordNo) {
        categoryApprovalRequestsRepo.findByAcceptanceRequestRecordNoOrderByRecordDateTimeDescRecordNoDesc(acceptanceRequestRecordNo)
                .stream()
                .filter(request -> request.getScope() == scopeRecordNo)
                .filter(request -> blocksReinitialization(request.getStatus()))
                .findFirst()
                .ifPresent(request -> {
                    throw new RuntimeException("Approval request already initialized for acceptance request "
                            + acceptanceRequestRecordNo + " with status '" + request.getStatus()
                            + "'. Duplicate initialization is not allowed.");
                });
    }

    private boolean blocksReinitialization(String status) {
        if (status == null) {
            return false;
        }
        switch (status.toLowerCase()) {
            case "pending":
            case "approved":
            case "request-info":
                return true;
            default:
                return false;
        }
    }

    private boolean createCategoryApprovalsForRequest(tbCategoryApprovalRequests approvalRequest, List<String> regionNames,
            String vendorName, int scopeRecordNo) {
        List<tbScopeApprovalLevels> approvalLevels = scopeApprovalLevelsRepo.findByScopeAndStatusOrderByLevelAsc(scopeRecordNo, 1);
        if (approvalLevels.isEmpty()) {
            logger.warn("No approval levels found for scope: {}", scopeRecordNo);
            return false;
        }

        tbScopeApprovalLevels resolvedLevelOne = resolveLevelOneApprovalLevel(approvalLevels, vendorName, scopeRecordNo, regionNames);
        boolean atLeastOneCreated = false;
        boolean readyForApprovalAssigned = false;

        for (tbScopeApprovalLevels level : approvalLevels) {
            if (level.getLevel() != null && level.getLevel() == 1 && level.getRecordNo() != resolvedLevelOne.getRecordNo()) {
                logger.info("Skipping level-1 config recordNo {} for vendor {}", level.getRecordNo(), vendorName);
                continue;
            }

            User originalApprover = mustFindUser(level.getApproverUserId());
            User delegatedApprover = getDelegatedApprover(originalApprover);
            boolean isDelegated = !originalApprover.getUserId().equals(delegatedApprover.getUserId());
            boolean assignReadyForApproval = !readyForApprovalAssigned;

            List<tbScopeApprovalLevelRegions> approvalRegions = scopeApprovalLevelRegionsRepo
                    .findByScopeIdAndApprovalLevelId(scopeRecordNo, (int) level.getRecordNo());
            if (approvalRegions.isEmpty()) {
                logger.warn("No regions configured for approval level {} and scope {}", level.getLevel(), scopeRecordNo);
                continue;
            }

            for (String regionName : regionNames) {
                if (regionName == null || regionName.trim().isEmpty()) {
                    continue;
                }
                tb_Region region = regionRepo.findByRegionName(regionName.trim());
                if (region == null) {
                    throw new RuntimeException("Region not found: " + regionName.trim());
                }

                boolean regionConfiguredForLevel = approvalRegions.stream()
                        .anyMatch(r -> r.getRegionId() == region.getRecordNo());
                if (!regionConfiguredForLevel) {
                    logger.info("Region {} is not associated with approval level {}", regionName, level.getLevel());
                    continue;
                }

                if (assignReadyForApproval && isDelegated) {
                    tbCategoryApprovals delegatedEntry = buildBaseApproval(approvalRequest, level, region);
                    delegatedEntry.setApproverId(originalApprover.getUserId());
                    delegatedEntry.setApproverName(originalApprover.getUsername());
                    delegatedEntry.setStatus("delegated");
                    delegatedEntry.setApprovalStatus("delegated");
                    delegatedEntry.setApprovedBy(originalApprover.getUserId());
                    delegatedEntry.setApprovedDate(LocalDateTime.now());
                    delegatedEntry.setComments("Request delegated from user " + originalApprover.getUsername()
                            + " to user " + delegatedApprover.getUsername());
                    categoryApprovalsRepo.save(delegatedEntry);
                    logger.info("Created delegated category approval from {} to {} for region {}",
                            originalApprover.getUsername(), delegatedApprover.getUsername(), regionName);
                }

                User effectiveApprover = assignReadyForApproval ? delegatedApprover : originalApprover;
                tbCategoryApprovals categoryApproval = buildBaseApproval(approvalRequest, level, region);
                categoryApproval.setApproverId(effectiveApprover.getUserId());
                categoryApproval.setApproverName(effectiveApprover.getUsername());
                categoryApproval.setStatus("pending");
                categoryApproval.setApprovalStatus(assignReadyForApproval ? "readyForApproval" : "pending");
                categoryApprovalsRepo.save(categoryApproval);
                logger.info("Created category approval for approverId: {}, level: {}, regionId: {}",
                        effectiveApprover.getUserId(), level.getLevel(), region.getRecordNo());

                atLeastOneCreated = true;
                if (assignReadyForApproval) {
                    readyForApprovalAssigned = true;
                }
            }
        }

        return atLeastOneCreated;
    }

    private tbCategoryApprovals buildBaseApproval(tbCategoryApprovalRequests approvalRequest, tbScopeApprovalLevels level, tb_Region region) {
        tbCategoryApprovals approval = new tbCategoryApprovals();
        approval.setApprovalRecordId(approvalRequest.getRecordNo());
        approval.setApprovalLevelId((int) level.getRecordNo());
        // Snapshot, not a live reference - see tbCategoryApprovals for why.
        approval.setLevelNumber(level.getLevel());
        approval.setDepartmentId(level.getDepartmentId());
        approval.setRegionId((int) region.getRecordNo());
        approval.setComments(null);
        approval.setActionTypeId(null);
        approval.setApprovedDate(null);
        approval.setRecordDateTime(LocalDateTime.now());
        approval.setDisplay(true);
        return approval;
    }

    /**
     * Resolves the single level-1 config to use for this request - mirrors
     * ScopeApprovalService.resolveLevelOneApprovalLevel(). A level-1 config
     * mapped to the request's vendor takes precedence; otherwise the first
     * generic level-1 config (no supplier mappings) covering every requested
     * region is used.
     *
     * @throws RuntimeException if neither exists, so the caller's transaction
     *         rolls back instead of creating a chain with no level 1.
     */
    private tbScopeApprovalLevels resolveLevelOneApprovalLevel(List<tbScopeApprovalLevels> approvalLevels, String vendorName,
            int scopeRecordNo, List<String> regionNames) {
        List<tbScopeApprovalLevels> levelOnes = approvalLevels.stream()
                .filter(level -> level.getLevel() != null && level.getLevel() == 1)
                .collect(Collectors.toList());
        if (levelOnes.isEmpty()) {
            throw new RuntimeException("No level-1 approval config found for scope: " + scopeRecordNo);
        }

        List<Integer> levelOneIds = levelOnes.stream().map(level -> (int) level.getRecordNo()).collect(Collectors.toList());
        List<tbScopeApprovalSuppliers> supplierMappings = scopeApprovalSuppliersRepo.findByApprovalLevelIdIn(levelOneIds);
        Set<Integer> vendorSpecificLevelIds = supplierMappings.stream()
                .map(tbScopeApprovalSuppliers::getApprovalLevelId)
                .collect(Collectors.toSet());

        if (vendorName != null && !vendorName.trim().isEmpty()) {
            Optional<supplierdata> supplier = supplierRepo.findBySupplierNameIgnoreCase(vendorName.trim());
            if (supplier.isPresent()) {
                Optional<tbScopeApprovalLevels> vendorLevel = supplierMappings.stream()
                        .filter(mapping -> mapping.getSupplierId() == supplier.get().getRecordNo())
                        .map(mapping -> levelOnes.stream()
                                .filter(level -> level.getRecordNo() == mapping.getApprovalLevelId())
                                .findFirst())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();
                if (vendorLevel.isPresent()) {
                    logger.info("Resolved level-1 config recordNo {} for vendor {}", vendorLevel.get().getRecordNo(), vendorName);
                    return vendorLevel.get();
                }
            }
            logger.warn("No level-1 vendor mapping for {} on scope {}, falling back to generic level-1 config", vendorName, scopeRecordNo);
        }

        return levelOnes.stream()
                .filter(level -> !vendorSpecificLevelIds.contains((int) level.getRecordNo()))
                .sorted(Comparator.comparingLong(tbScopeApprovalLevels::getRecordNo))
                .filter(level -> levelCoversAllRegions(level, scopeRecordNo, regionNames))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Error! No generic level-1 approval config exists for scope "
                        + scopeRecordNo + ", region(s) " + String.join(", ", regionNames)
                        + " without vendor-specific suppliers."));
    }

    private boolean levelCoversAllRegions(tbScopeApprovalLevels level, int scopeRecordNo, List<String> regionNames) {
        Set<Integer> configuredRegionIds = scopeApprovalLevelRegionsRepo
                .findByScopeIdAndApprovalLevelId(scopeRecordNo, (int) level.getRecordNo())
                .stream()
                .map(r -> (int) r.getRegionId())
                .collect(Collectors.toSet());
        for (String regionName : regionNames) {
            if (regionName == null || regionName.trim().isEmpty()) {
                continue;
            }
            tb_Region region = regionRepo.findByRegionName(regionName.trim());
            if (region == null || !configuredRegionIds.contains((int) region.getRecordNo())) {
                return false;
            }
        }
        return true;
    }

    private User getDelegatedApprover(User approver) {
        Optional<tbDelegation> activeDelegation = delegationRepo.findActiveDelegation(approver.getUserId(), LocalDateTime.now());
        if (activeDelegation.isPresent()) {
            int delegateeId = activeDelegation.get().getDelegateeId();
            Optional<User> delegatee = userRepository.findById(delegateeId);
            if (delegatee.isPresent()) {
                logger.info("User {} has delegated to {}.", approver.getUsername(), delegatee.get().getUsername());
                return delegatee.get();
            }
        }
        return approver;
    }

    private User mustFindUser(Integer userId) {
        if (userId == null) {
            throw new RuntimeException("Approver not found: no approver configured for this approval level");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Approver not found with userId: " + userId));
    }

    private Optional<User> getFirstApproverForRequest(int approvalRequestRecordNo) {
        List<tbCategoryApprovals> approvals = categoryApprovalsRepo.findByApprovalRecordId(approvalRequestRecordNo);
        return approvals.stream()
                .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "readyforapproval".equalsIgnoreCase(a.getApprovalStatus()))
                .findFirst()
                .flatMap(a -> userRepository.findById(a.getApproverId()));
    }
}
