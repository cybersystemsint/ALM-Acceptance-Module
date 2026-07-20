package com.zain.almksazain.services;

import com.zain.almksazain.dto.WorkflowInitResult;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.supplierdata;
import com.zain.almksazain.model.tbCategoryApprovalRequests;
import com.zain.almksazain.model.tbCategoryApprovals;
import com.zain.almksazain.model.tbDelegation;
import com.zain.almksazain.model.tbScope;
import com.zain.almksazain.model.tbScopeApprovalLevelRegion;
import com.zain.almksazain.model.tbScopeApprovalLevels;
import com.zain.almksazain.model.tbScopeApprovalSupplier;
import com.zain.almksazain.model.tb_Region;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.supplierrepo;
import com.zain.almksazain.repo.tbDelegationRepo;
import com.zain.almksazain.repo.tbRegionRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelRegionRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelsRepo;
import com.zain.almksazain.repo.tbScopeApprovalSupplierRepo;
import com.zain.almksazain.repo.tbScopeRepo;
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
 * In-process port of WorkFlow-Management ScopeApprovalService.initializeApproval.
 * Persists approval request/rows only; email is sent separately after commit.
 */
@Service
public class WorkflowInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowInitializationService.class);

    @Autowired
    private TbCategoryApprovalRequestsRepository approvalRequestRepository;
    @Autowired
    private TbCategoryApprovalsRepository categoryApprovalsRepository;
    @Autowired
    private tbScopeRepo scopeRepository;
    @Autowired
    private tbScopeApprovalLevelsRepo scopeApprovalLevelsRepo;
    @Autowired
    private tbScopeApprovalLevelRegionRepo scopeApprovalLevelRegionRepo;
    @Autowired
    private tbScopeApprovalSupplierRepo scopeApprovalSupplierRepo;
    @Autowired
    private tbRegionRepo regionRepo;
    @Autowired
    private supplierrepo supplierRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private tbDelegationRepo delegationRepository;
    @Autowired
    private EmailService emailService;

    /**
     * Creates approval request and level/region approval rows. Joins the caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public WorkflowInitResult initializeApproval(Integer acceptanceRequestRecordNo, String vendorName,
            String requestedBy, String scopeName, String createdBy, List<String> regions,
            String tableName, String poLineItemDescription, String poNumber) {

        assertNotAlreadyInitialized(acceptanceRequestRecordNo);

        logger.info("Creating approval request for acceptanceRequestRecordNo: {}, scope: {}, regions: {}, vendor: {}",
                acceptanceRequestRecordNo, scopeName, regions, vendorName);

        if (scopeName == null || scopeName.isEmpty()) {
            throw new RuntimeException("Scope is required to initialize approval");
        }

        tbScope scope = scopeRepository.findByScope(scopeName);
        if (scope == null) {
            throw new RuntimeException("Scope not found with name: " + scopeName);
        }

        tbCategoryApprovalRequests approvalRequest = new tbCategoryApprovalRequests();
        approvalRequest.setAcceptanceRequestRecordNo(acceptanceRequestRecordNo);
        approvalRequest.setVendorName(vendorName);
        approvalRequest.setRequestedBy(requestedBy);
        approvalRequest.setScope((int) scope.getRecordNo());
        approvalRequest.setStatus("pending");
        approvalRequest.setCreatedBy(createdBy);
        approvalRequest.setPoLineItemDescription(poLineItemDescription);
        approvalRequest.setTableName(tableName);
        approvalRequest.setPoNumber(poNumber);
        approvalRequest.setReceived(false);

        tbCategoryApprovalRequests savedRequest = approvalRequestRepository.save(approvalRequest);
        Integer recordNo = savedRequest.getRecordNo();
        logger.info("Approval request created with recordNo: {}", recordNo);

        boolean childApprovalsCreated = createScopeApprovalsForRequest(savedRequest, scope, regions);
        if (!childApprovalsCreated) {
            logger.error("No approval items were created for Acceptance RequestNo: {}. "
                    + "Possibly due to missing approval levels or region mapping. dccId={}, scope={}, regions={}, vendor={}",
                    acceptanceRequestRecordNo, acceptanceRequestRecordNo, scopeName, regions, vendorName);
            throw new RuntimeException(
                    "Error! Approval request was not created, since no approval levels are configured for the selected regions and scope.");
        }

        Optional<User> firstApproverOpt = getFirstApproverForRequest(recordNo);
        String firstApproverEmail = null;
        String firstApproverName = null;
        if (firstApproverOpt.isPresent()) {
            User firstApprover = firstApproverOpt.get();
            firstApproverEmail = firstApprover.getEmailAddress();
            firstApproverName = firstApprover.getFullName() != null ? firstApprover.getFullName() : firstApprover.getUsername();
        } else {
            logger.warn("No initial approver found for approval request recordNo: {}", recordNo);
        }

        return new WorkflowInitResult(
                acceptanceRequestRecordNo,
                recordNo,
                poNumber,
                vendorName,
                requestedBy,
                scopeName,
                firstApproverEmail,
                firstApproverName);
    }

    /**
     * Sends first-approver notification. Call only after the surrounding transaction has committed.
     */
    public void notifyFirstApprover(WorkflowInitResult result) {
        if (result == null || result.getFirstApproverEmail() == null || result.getFirstApproverEmail().isBlank()) {
            logger.warn("Skipping first-approver email: no approver email for dccId={}",
                    result != null ? result.getDccId() : null);
            return;
        }

        String subject = formatEmailSubject(result);
        String body = constructFirstApproversEmail(result);
        try {
            emailService.sendEmail(
                    result.getFirstApproverEmail(),
                    subject,
                    body,
                    null,
                    null,
                    result.getFirstApproverName(),
                    null,
                    null,
                    null,
                    null);
            logger.info("Queued first-approver email to {} for dccId={}",
                    result.getFirstApproverEmail(), result.getDccId());
        } catch (Exception ex) {
            logger.error("Failed to send first-approver email for dccId={}: {}", result.getDccId(), ex.getMessage(), ex);
        }
    }

    private boolean createScopeApprovalsForRequest(tbCategoryApprovalRequests approvalRequest, tbScope scope,
            List<String> regions) {
        List<tbScopeApprovalLevels> approvalLevels = scopeApprovalLevelsRepo
                .findByScopeOrderByLevelAsc((int) scope.getRecordNo());
        if (approvalLevels.isEmpty()) {
            logger.warn("No approval levels found for scope: {}", scope.getScope());
            return false;
        }

        tbScopeApprovalLevels resolvedLevelOne = resolveLevelOneApprovalLevel(approvalRequest, scope, approvalLevels, regions);
        boolean atLeastOneCreated = false;
        boolean readyForApprovalAssigned = false;

        for (tbScopeApprovalLevels level : approvalLevels) {
            if (level.getLevel() != null && level.getLevel() == 1) {
                if (resolvedLevelOne == null || level.getRecordNo() != resolvedLevelOne.getRecordNo()) {
                    logger.info("Skipping level-1 config recordNo {} for vendor {}",
                            level.getRecordNo(), approvalRequest.getVendorName());
                    continue;
                }
            }

            if (level.getApproverUserId() == null) {
                throw new RuntimeException("Approver not configured on approval level recordNo: " + level.getRecordNo());
            }

            User originalApprover = userRepository.findByUserId(level.getApproverUserId())
                    .orElseThrow(() -> new RuntimeException(
                            "Approver not found with userId: " + level.getApproverUserId()));

            User delegatedApprover = getDelegatedApprover(originalApprover);
            boolean isDelegated = !originalApprover.getUserId().equals(delegatedApprover.getUserId());
            boolean assignReadyForApproval = !readyForApprovalAssigned;

            List<tbScopeApprovalLevelRegion> approvalRegions =
                    scopeApprovalLevelRegionRepo.findByScopeAndScopeApprovalLevel(scope, level);
            if (approvalRegions.isEmpty()) {
                logger.warn("No regions configured for approval level {} and Scope {}",
                        level.getLevel(), scope.getScope());
                continue;
            }

            for (String regionName : regions) {
                tbScopeApprovalLevelRegion matchingRegion = approvalRegions.stream()
                        .filter(region -> region.getRegion() != null
                                && regionName.equals(region.getRegion().getRegionName()))
                        .findFirst()
                        .orElse(null);

                if (matchingRegion == null) {
                    logger.info("Region {} is not associated with approval level {}", regionName, level.getLevel());
                    continue;
                }

                tb_Region region = regionRepo.findByRegionName(regionName);
                if (region == null) {
                    throw new RuntimeException("Region not found: " + regionName);
                }

                if (assignReadyForApproval && isDelegated) {
                    tbCategoryApprovals delegatedEntry = new tbCategoryApprovals();
                    delegatedEntry.setApprovalRecordId(approvalRequest.getRecordNo());
                    delegatedEntry.setApprovalLevelId((int) level.getRecordNo());
                    delegatedEntry.setApproverId(originalApprover.getUserId());
                    delegatedEntry.setRegionId((int) region.getRecordNo());
                    delegatedEntry.setStatus("delegated");
                    delegatedEntry.setApprovalStatus("delegated");
                    delegatedEntry.setApprovedBy(originalApprover.getUserId());
                    delegatedEntry.setApprovedDate(LocalDateTime.now());
                    delegatedEntry.setComments("Request delegated from user " + originalApprover.getUsername()
                            + " to user " + delegatedApprover.getUsername());
                    delegatedEntry.setApproverName(originalApprover.getUsername());
                    delegatedEntry.setActionTypeId(null);
                    delegatedEntry.setDisplay(true);
                    delegatedEntry.setRecordDateTime(LocalDateTime.now());
                    categoryApprovalsRepository.save(delegatedEntry);

                    logger.info("Created delegated CategoryApproval from {} to {} for region {}",
                            originalApprover.getUsername(), delegatedApprover.getUsername(), regionName);
                }

                User approverForRow = assignReadyForApproval ? delegatedApprover : originalApprover;
                tbCategoryApprovals scopeApproval = new tbCategoryApprovals();
                scopeApproval.setApprovalRecordId(approvalRequest.getRecordNo());
                scopeApproval.setApprovalLevelId((int) level.getRecordNo());
                scopeApproval.setApproverId(approverForRow.getUserId());
                scopeApproval.setRegionId((int) region.getRecordNo());
                scopeApproval.setStatus("pending");
                scopeApproval.setApprovalStatus(assignReadyForApproval ? "readyForApproval" : "pending");
                scopeApproval.setApproverName(approverForRow.getUsername());
                scopeApproval.setComments(null);
                scopeApproval.setActionTypeId(null);
                scopeApproval.setApprovedDate(null);
                scopeApproval.setDisplay(true);
                scopeApproval.setRecordDateTime(LocalDateTime.now());
                categoryApprovalsRepository.save(scopeApproval);

                logger.info("Created CategoryApproval for approverId: {}, approvalLevelId: {}, level: {}, regionId: {}",
                        approverForRow.getUserId(), level.getRecordNo(), level.getLevel(), region.getRecordNo());

                atLeastOneCreated = true;
                if (assignReadyForApproval) {
                    readyForApprovalAssigned = true;
                }
            }
        }

        return atLeastOneCreated;
    }

    private void assertNotAlreadyInitialized(Integer acceptanceRequestRecordNo) {
        List<tbCategoryApprovalRequests> existingRequests =
                approvalRequestRepository.findByAcceptanceRequestRecordNoStatuses(acceptanceRequestRecordNo);
        Optional<tbCategoryApprovalRequests> blockingRequest = existingRequests.stream()
                .filter(request -> blocksReinitialization(request.getStatus()))
                .findFirst();

        if (blockingRequest.isPresent()) {
            String status = blockingRequest.get().getStatus();
            throw new RuntimeException(
                    "Approval request already initialized for acceptance request "
                            + acceptanceRequestRecordNo
                            + " with status '"
                            + status
                            + "'. Duplicate initialization is not allowed.");
        }
    }

    private boolean blocksReinitialization(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toLowerCase()) {
            case "pending", "approved", "request-info" -> true;
            default -> false;
        };
    }

    private tbScopeApprovalLevels resolveLevelOneApprovalLevel(
            tbCategoryApprovalRequests approvalRequest,
            tbScope scope,
            List<tbScopeApprovalLevels> approvalLevels,
            List<String> regions) {

        List<tbScopeApprovalLevels> levelOnes = approvalLevels.stream()
                .filter(level -> level.getLevel() != null && level.getLevel() == 1)
                .collect(Collectors.toList());

        if (levelOnes.isEmpty()) {
            throw new RuntimeException("No level-1 approval config found for scope: " + scope.getScope());
        }

        List<Long> levelOneIds = levelOnes.stream()
                .map(tbScopeApprovalLevels::getRecordNo)
                .collect(Collectors.toList());
        List<tbScopeApprovalSupplier> supplierMappings =
                scopeApprovalSupplierRepo.findByScopeApprovalLevelRecordNoIn(levelOneIds);
        Set<Long> vendorSpecificLevelIds = supplierMappings.stream()
                .map(mapping -> mapping.getScopeApprovalLevel().getRecordNo())
                .collect(Collectors.toSet());

        String vendorName = approvalRequest.getVendorName();
        if (vendorName != null && !vendorName.isBlank()) {
            Optional<supplierdata> supplierOpt = supplierRepository.findBySupplierNameIgnoreCase(vendorName.trim());
            if (supplierOpt.isPresent()) {
                Optional<tbScopeApprovalLevels> vendorLevel = supplierMappings.stream()
                        .filter(mapping -> mapping.getSupplier() != null
                                && mapping.getSupplier().getRecordNo() == supplierOpt.get().getRecordNo())
                        .map(tbScopeApprovalSupplier::getScopeApprovalLevel)
                        .findFirst();

                if (vendorLevel.isPresent()) {
                    logger.info("Resolved level-1 config recordNo {} for vendor {}",
                            vendorLevel.get().getRecordNo(), vendorName);
                    return vendorLevel.get();
                }
            }

            logger.warn("No level-1 vendor mapping for {} on scope {}, falling back to generic level-1 config",
                    vendorName, scope.getScope());
        }

        return findGenericLevelOneForRegions(scope, levelOnes, vendorSpecificLevelIds, regions)
                .orElseThrow(() -> new RuntimeException(
                        "Error! No generic level-1 approval config exists for scope "
                                + scope.getScope()
                                + ", region(s) " + String.join(", ", regions)
                                + " without vendor-specific suppliers."));
    }

    private Optional<tbScopeApprovalLevels> findGenericLevelOneForRegions(
            tbScope scope,
            List<tbScopeApprovalLevels> levelOnes,
            Set<Long> vendorSpecificLevelIds,
            List<String> regions) {
        return levelOnes.stream()
                .filter(level -> !vendorSpecificLevelIds.contains(level.getRecordNo()))
                .sorted(Comparator.comparingLong(tbScopeApprovalLevels::getRecordNo))
                .filter(level -> levelCoversAllRegions(scope, level, regions))
                .findFirst();
    }

    private boolean levelCoversAllRegions(tbScope scope, tbScopeApprovalLevels level, List<String> regions) {
        List<tbScopeApprovalLevelRegion> approvalRegions =
                scopeApprovalLevelRegionRepo.findByScopeAndScopeApprovalLevel(scope, level);
        Set<String> configuredRegionNames = approvalRegions.stream()
                .map(regionMapping -> regionMapping.getRegion().getRegionName())
                .collect(Collectors.toSet());
        return regions.stream().allMatch(configuredRegionNames::contains);
    }

    private User getDelegatedApprover(User approver) {
        Optional<tbDelegation> activeDelegation =
                delegationRepository.findActiveDelegation(approver.getUserId(), LocalDateTime.now());
        if (activeDelegation.isPresent()) {
            User delegatee = activeDelegation.get().getDelegatee();
            logger.info("User {} has delegated to {}.", approver.getUsername(), delegatee.getUsername());
            return delegatee;
        }
        return approver;
    }

    private Optional<User> getFirstApproverForRequest(Integer recordNo) {
        List<tbCategoryApprovals> approvals =
                categoryApprovalsRepository.findPendingReadyForApprovalByRequestNo(recordNo);
        return approvals.stream()
                .findFirst()
                .flatMap(approval -> userRepository.findByUserId(approval.getApproverId()));
    }

    private String formatEmailSubject(WorkflowInitResult result) {
        return "Action Required: Approval Acceptance# " + result.getDccId()
                + "/PO# " + result.getPoNumber();
    }

    private String constructFirstApproversEmail(WorkflowInitResult result) {
        String approverDisplay = result.getFirstApproverName() != null ? result.getFirstApproverName() : "Approver";
        return String.format("""
                <html>
                  <body>
                    <p>Dear %s,</p>
                    <p>You have a pending approval request for:</p>
                    <ul>
                      <li><strong>Request Number:</strong> %d</li>
                      <li><strong>Vendor Name:</strong> %s</li>
                      <li><strong>Requested By:</strong> %s</li>
                      <li><strong>Scope:</strong> %s</li>
                      <li><strong>PO Number:</strong> %s</li>
                    </ul>
                    <p>Please log in to ALM to review and action this request.</p>
                  </body>
                </html>
                """,
                approverDisplay,
                result.getDccId(),
                nullSafe(result.getVendorName()),
                nullSafe(result.getRequestedBy()),
                nullSafe(result.getScopeName()),
                nullSafe(result.getPoNumber()));
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
