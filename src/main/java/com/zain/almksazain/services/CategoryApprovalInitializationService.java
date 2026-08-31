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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
 * minimize behavioral drift from the tested WorkFlow-Management logic - the
 * one deliberate change is that levelNumber/departmentId are copied onto
 * each tb_Category_Approvals row at creation time (see tbCategoryApprovals),
 * so a later edit to tb_Scope_Approval_Levels can't retroactively change what
 * an already-created approval row resolves to.
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

        tbCategoryApprovalRequests approvalRequest = new tbCategoryApprovalRequests();
        approvalRequest.setRecordDateTime(new java.sql.Date(System.currentTimeMillis()));
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
        parentRecord.setRecordDateTime(new java.sql.Date(System.currentTimeMillis()));

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

    private boolean createCategoryApprovalsForRequest(tbCategoryApprovalRequests approvalRequest, List<String> regionNames,
            String vendorName, int scopeRecordNo) {
        List<tbScopeApprovalLevels> approvalLevels = scopeApprovalLevelsRepo.findByScopeOrderByLevelAsc(scopeRecordNo);
        if (approvalLevels.isEmpty()) {
            logger.warn("No approval levels found for scope: {}", scopeRecordNo);
            return false;
        }

        boolean isFirstLevel = true;
        boolean atLeastOneCreated = false;

        for (tbScopeApprovalLevels level : approvalLevels) {
            User approver = isFirstLevel
                    ? resolveFirstLevelApprover(level, vendorName, scopeRecordNo)
                    : mustFindUser(level.getApproverUserId());

            User originalApprover = approver;
            User delegatedApprover = getDelegatedApprover(approver);
            boolean isDelegated = !originalApprover.getUserId().equals(delegatedApprover.getUserId());

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

                if (isFirstLevel && isDelegated) {
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

                User effectiveApprover = isFirstLevel ? delegatedApprover : originalApprover;
                tbCategoryApprovals categoryApproval = buildBaseApproval(approvalRequest, level, region);
                categoryApproval.setApproverId(effectiveApprover.getUserId());
                categoryApproval.setApproverName(effectiveApprover.getUsername());
                categoryApproval.setStatus("pending");
                categoryApproval.setApprovalStatus(isFirstLevel ? "readyForApproval" : "pending");
                categoryApprovalsRepo.save(categoryApproval);
                logger.info("Created category approval for approverId: {}, level: {}, regionId: {}",
                        effectiveApprover.getUserId(), level.getLevel(), region.getRecordNo());

                atLeastOneCreated = true;
            }

            if (atLeastOneCreated && isFirstLevel) {
                isFirstLevel = false;
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

    private User resolveFirstLevelApprover(tbScopeApprovalLevels level, String vendorName, int scopeRecordNo) {
        try {
            supplierdata supplier = supplierRepo.findBySupplierNameIgnoreCase(vendorName)
                    .orElseThrow(() -> new RuntimeException("Supplier not found for vendor name: " + vendorName));

            List<tbScopeApprovalSuppliers> matches = scopeApprovalSuppliersRepo
                    .findBySupplierAndScopeOrderByRecordNoAsc((int) supplier.getRecordNo(), scopeRecordNo);

            if (!matches.isEmpty()) {
                tbScopeApprovalSuppliers supplierLevelMapping = matches.get(0);
                tbScopeApprovalLevels supplierLevel = scopeApprovalLevelsRepo.findById((long) supplierLevelMapping.getApprovalLevelId())
                        .orElseThrow(() -> new RuntimeException("Approval level not found with recordNo: " + supplierLevelMapping.getApprovalLevelId()));
                return mustFindUser(supplierLevel.getApproverUserId());
            }
            logger.warn("No supplier-level mapping found for vendor: {}, defaulting to level approver", vendorName);
            return mustFindUser(level.getApproverUserId());
        } catch (Exception e) {
            logger.error("Failed to determine first-level approver by supplier: {}, defaulting to level approver", e.getMessage());
            return mustFindUser(level.getApproverUserId());
        }
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
