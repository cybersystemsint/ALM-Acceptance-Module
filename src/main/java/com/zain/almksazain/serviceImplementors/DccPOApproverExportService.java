package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DccPOApproverExportService {

    private static final Logger logger = LogManager.getLogger(DccPOApproverExportService.class);

    @Autowired
    private TbDccRepository tbDccRepository;

    @Autowired
    private TbDccLnRepository tbDccLnRepository;

    @Autowired
    private TbPurchaseOrderRepository tbPurchaseOrderRepository;

    @Autowired
    private TbPurchaseOrderUplRepository tbPurchaseOrderUplRepository;

    @Autowired
    private TbCategoryApprovalRequestsRepository tbCategoryApprovalRequestsRepository;

    @Autowired
    private TbCategoryApprovalsRepository tbCategoryApprovalsRepository;

    @Autowired
    private AcceptanceRequestReceiptRepository acceptanceRequestReceiptRepository;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d-MMM-yyyy").withZone(ZoneId.of("Africa/Nairobi"));

    @Async("taskExecutor")
    public CompletableFuture<List<DccPOCombinedViewDTO>> getAllDccPOForApproverExport(
            String supplierId, String pendingApprovers, String columnName, String searchQuery, String operator) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting approver export with supplierId: {}, pendingApprovers: {}, columnName: {}, searchQuery: {}, operator: {}",
                        supplierId, pendingApprovers, columnName, searchQuery, operator);

                // CRITICAL: Build specification EXACTLY like DccPOApproverService
                Specification<DCC> spec = new DccSpecification(supplierId, null, columnName, searchQuery, operator);

                if (pendingApprovers != null && !pendingApprovers.isEmpty()) {
                    Integer approverIdAsInt;
                    try {
                        approverIdAsInt = Integer.parseInt(pendingApprovers);
                    } catch (NumberFormatException e) {
                        logger.error("Failed to parse pendingApprovers '{}' as Integer", pendingApprovers, e);
                        return new ArrayList<>();
                    }

                    // Step 1: Get approvals where user has TAKEN ACTION
                    List<TbCategoryApprovals> approvals = tbCategoryApprovalsRepository.findByApprovedBy(pendingApprovers).stream()
                            .filter(a -> {
                                if ("pending".equals(a.getStatus())) {
                                    return "request-info".equals(a.getApprovalStatus());
                                }
                                return true;
                            })
                            .collect(Collectors.toList());

                    logger.debug("Raw approvals by user {}: {}", approverIdAsInt, approvals.size());

                    Set<Long> approvalRecordIds = approvals.stream()
                            .map(TbCategoryApprovals::getApprovalRecordId)
                            .collect(Collectors.toSet());

                    logger.debug("Approvals found (actioned only): {} for approver {}", approvalRecordIds.size(), approverIdAsInt);

                    // Step 2: Get receipts by this user
                    List<AcceptanceRequestReceipt> userReceipts =
                            acceptanceRequestReceiptRepository.findByApprovedBy(approverIdAsInt);

                    Set<Long> userReceiptIds = userReceipts.stream()
                            .map(r -> r.getCategoryApprovalRequestId().longValue())
                            .collect(Collectors.toSet());

                    logger.debug("Receipts found for approver {}: {}", pendingApprovers, userReceiptIds.size());

                    // Step 3: Combine approval and receipt record IDs
                    Set<Long> combinedRecordIds = new HashSet<>(approvalRecordIds);
                    combinedRecordIds.addAll(userReceiptIds);

                    logger.debug("Combined record IDs (actioned approvals + receipts): {}", combinedRecordIds.size());

                    if (combinedRecordIds.isEmpty()) {
                        logger.info("No approval or receipt actions found for user: {}", pendingApprovers);
                        return new ArrayList<>();
                    }

                    // Step 4: Fetch related requests
                    List<TbCategoryApprovalRequests> requests =
                            tbCategoryApprovalRequestsRepository.findByRecordNoIn(new ArrayList<>(combinedRecordIds));

                    Set<Long> dccRecordNos = requests.stream()
                            .filter(r -> r.getAcceptanceRequestRecordNo() != null)
                            .map(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo)
                            .collect(Collectors.toSet());

                    if (dccRecordNos.isEmpty()) {
                        logger.info("No DCC records found for user: {}", pendingApprovers);
                        return new ArrayList<>();
                    }

                    logger.debug("Final DCC record count for user {}: {}", pendingApprovers, dccRecordNos.size());

                    // Apply DCC filter to specification
                    spec = spec.and((root, query, cb) -> root.get("recordNo").in(dccRecordNos));
                }

                // Fetch ALL DCC records
                Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "recordNo"));
                Page<DCC> dccPage = tbDccRepository.findAll(spec, pageable);
                List<DCC> dccList = dccPage.getContent();
                long totalFilteredRecords = dccPage.getTotalElements();

                if (dccList.isEmpty()) {
                    logger.info("No records found for approver export");
                    return new ArrayList<>();
                }

                logger.info("Fetched {} DCC records for approver export", dccList.size());

                // Validate no invalid records
                List<DCC> invalidDccRecords = dccList.stream()
                        .filter(dcc -> dcc.getPoNumber() == null || dcc.getPoNumber().isEmpty())
                        .collect(Collectors.toList());
                if (!invalidDccRecords.isEmpty()) {
                    logger.error("Found {} DCC records with missing or invalid poNumber", invalidDccRecords.size());
                    throw new DccPOProcessingException("Invalid DCC records detected with missing poNumber");
                }

                // Bulk fetch all related data
                Set<String> poNumbersSet = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
                List<String> poNumbers = new ArrayList<>(poNumbersSet);

                Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                        .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));

                Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository.findByPoNumberIn(poNumbers)
                        .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));

                List<Long> dccIds = dccList.stream().map(DCC::getRecordNo).collect(Collectors.toList());
                List<DCCLineItem> allDccLn = tbDccLnRepository.findByDccIdIn(dccIds.stream().map(String::valueOf).collect(Collectors.toList()));

                Map<Long, DCC> dccMap = dccList.stream().collect(Collectors.toMap(DCC::getRecordNo, Function.identity()));

                Map<String, Double> deliveredMap = allDccLn.stream()
                        .filter(dln -> {
                            DCC dd = dccMap.get(Long.parseLong(dln.getDccId()));
                            return dd != null && !Arrays.asList("incomplete", "rejected").contains(dd.getStatus().toLowerCase()) && dln.getDeliveredQty() != null;
                        })
                        .collect(Collectors.groupingBy(
                                dln -> dln.getPoId() + "-" + dln.getLineNumber() + "-" + (dln.getUplLineNumber() != null ? dln.getUplLineNumber() : ""),
                                Collectors.summingDouble(DCCLineItem::getDeliveredQty)
                        ));

                List<tb_PurchaseOrderUPL> allUplList = uplMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
                Map<String, Double> acceptanceByPoLine = allUplList.stream()
                        .filter(u -> u.getUplLineQuantity() != null && u.getUplLineQuantity() > 0
                                && u.getPoLineQuantity() != null && u.getPoLineQuantity() > 0
                                && u.getPoLineUnitPrice() != null && u.getPoLineUnitPrice() > 0)
                        .collect(Collectors.groupingBy(
                                u -> u.getPoNumber() + "-" + u.getPoLineNumber(),
                                Collectors.summingDouble(u -> {
                                    double denominator = u.getPoLineQuantity() * u.getPoLineUnitPrice();
                                    if (denominator == 0) {
                                        return 0.0;
                                    }
                                    double numerator = u.getUplLineQuantity() * u.getPoLineQuantity();
                                    return numerator / denominator;
                                })
                        ));

                Set<String> hasDccLnSet = allDccLn.stream()
                        .map(dln -> dln.getPoId() + "-" + dln.getLineNumber() + "-" + (dln.getUplLineNumber() != null ? dln.getUplLineNumber() : ""))
                        .collect(Collectors.toSet());

                List<TbCategoryApprovalRequests> allRequests = tbCategoryApprovalRequestsRepository
                        .findByAcceptanceRequestRecordNoInOrderByAcceptanceRequestRecordNoAscRecordDateTimeDesc(dccIds);

                Map<Long, List<TbCategoryApprovalRequests>> requestsByDccId = allRequests.stream()
                        .collect(Collectors.groupingBy(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo));

                Set<Long> allRequestRecordNos = allRequests.stream().map(TbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());

                List<TbCategoryApprovals> allApprovals = tbCategoryApprovalsRepository.findByApprovalRecordIdIn(new ArrayList<>(allRequestRecordNos));

                Map<Long, List<TbCategoryApprovals>> approvalsByRequestRecordNo = allApprovals.stream()
                        .collect(Collectors.groupingBy(TbCategoryApprovals::getApprovalRecordId));

                SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");

                // Process all records in parallel
                List<DccPOCombinedViewDTO> result = dccList.parallelStream()
                        .flatMap(dcc -> {
                            List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                            if (purchaseOrderList.isEmpty()) {
                                logger.error("No Purchase Order found for poNumber: {} in DCC record: {}.", dcc.getPoNumber(), dcc.getRecordNo());
                                return Stream.empty();
                            }
                            tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);

                            List<tb_PurchaseOrderUPL> uplList = uplMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                            List<DCCLineItem> dccLnList = allDccLn.stream()
                                    .filter(dln -> dln.getDccId().equals(String.valueOf(dcc.getRecordNo())))
                                    .collect(Collectors.toList());

                            List<TbCategoryApprovalRequests> dccRequests = requestsByDccId.getOrDefault(dcc.getRecordNo(), Collections.emptyList());
                            TbCategoryApprovalRequests latestApprovalRequest = dccRequests.stream()
                                    .max(Comparator.comparing(TbCategoryApprovalRequests::getRecordDateTime))
                                    .orElse(null);

                            List<TbCategoryApprovals> allRelatedApprovals = dccRequests.stream()
                                    .flatMap(r -> approvalsByRequestRecordNo.getOrDefault(r.getRecordNo(), Collections.emptyList()).stream())
                                    .collect(Collectors.toList());

                            if (dccLnList.isEmpty() || uplList.isEmpty()) {
                                logger.warn("No DCC_LN or UPL records found for DCC ID: {}. Skipping.", dcc.getRecordNo());
                                return Stream.empty();
                            }

                            return buildDccPOCombinedViewDTOs(dcc, purchaseOrder, uplList, dccLnList, latestApprovalRequest, dccRequests, allRelatedApprovals,
                                    deliveredMap, acceptanceByPoLine, hasDccLnSet, dateFormat).stream();
                        })
                        .collect(Collectors.toList());

                logger.info("Exported {} records successfully for approver", result.size());
                return result;
            } catch (Exception ex) {
                logger.error("Error during approver export of DCC PO Combined View", ex);
                throw new DccPOProcessingException("Failed to export DCC PO Combined View for approvers", ex);
            }
        });
    }

    private List<DccPOCombinedViewDTO> buildDccPOCombinedViewDTOs(
            DCC dcc, tbPurchaseOrder purchaseOrder, List<tb_PurchaseOrderUPL> uplList,
            List<DCCLineItem> dccLnList, TbCategoryApprovalRequests latestApprovalRequest,
            List<TbCategoryApprovalRequests> allRelatedRequests, List<TbCategoryApprovals> allRelatedApprovals, Map<String, Double> deliveredMap,
            Map<String, Double> acceptanceByPoLine, Set<String> hasDccLnSet, SimpleDateFormat dateFormat) {
        List<DccPOCombinedViewDTO> dtos = new ArrayList<>();

        // Optimize matching with maps
        Map<String, tb_PurchaseOrderUPL> uplByKey = uplList.stream()
                .collect(Collectors.toMap(u -> (u.getUplLine() != null ? u.getUplLine() : "") + "-" + u.getPoLineNumber() + "-" + u.getPoNumber(), u -> u));

        for (DCCLineItem dccLn : dccLnList) {
            String key = (dccLn.getUplLineNumber() != null ? dccLn.getUplLineNumber() : "") + "-" + dccLn.getLineNumber() + "-" + dcc.getPoNumber();
            tb_PurchaseOrderUPL upl = uplByKey.get(key);
            if (upl == null) {
                logger.debug("No matching UPL record for DCC recordNo: {}, poNumber: {}, poLineNumber: {}, uplLine: {}",
                        dcc.getRecordNo(), dcc.getPoNumber(), dccLn.getLineNumber(), dccLn.getUplLineNumber());
                continue;
            }

            // Fallback condition
            boolean condition = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                    ? (dccLn.getUplLineNumber().equals(upl.getUplLine()) &&
                    upl.getPoLineNumber().equals(dccLn.getLineNumber()) &&
                    upl.getPoNumber().equals(dcc.getPoNumber()))
                    : (purchaseOrder.getLineNumber().equals(dccLn.getLineNumber()) &&
                    purchaseOrder.getPoNumber().equals(dcc.getPoNumber()));
            if (!condition) continue;

            DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();
            populateDccFields(dto, dcc, dateFormat, latestApprovalRequest);
            populateLineItemFields(dto, dccLn, dateFormat, new HashSet<>());
            populatePurchaseOrderAndUplFields(dto, dccLn, purchaseOrder, upl);
            calculateQuantitiesAndApprovals(dto, dcc, purchaseOrder, upl, deliveredMap, acceptanceByPoLine, hasDccLnSet, latestApprovalRequest, allRelatedRequests, allRelatedApprovals);

            dtos.add(dto);
        }

        return dtos;
    }

    private void populateDccFields(DccPOCombinedViewDTO dto, DCC dcc, SimpleDateFormat dateFormat,
                                   TbCategoryApprovalRequests latestApprovalRequest) {
        dto.setDccRecordNo(dcc.getRecordNo());
        dto.setDccPoNumber(dcc.getPoNumber());
        dto.setDccVendorName(dcc.getVendorName());
        dto.setDccVendorEmail(dcc.getVendorEmail());
        // REMOVED: dto.setDccProjectName(dcc.getProjectName());
        // DO NOT set projectName here - it will be set from PurchaseOrder table in populatePurchaseOrderAndUplFields
        dto.setDccAcceptanceType(dcc.getAcceptanceType());
        dto.setDccStatus(dcc.getStatus());

        if (dcc.getCreatedDate() != null) {
            Instant instant = Instant.ofEpochMilli(dcc.getCreatedDate().getTime());
            dto.setDccCreatedDate(instant.atZone(ZoneId.of("Africa/Nairobi")).format(DATE_FORMATTER));
        } else {
            dto.setDccCreatedDate(null);
        }

        dto.setVendorComment(dcc.getVendorComment());
        dto.setDccId(dcc.getDccId());
        dto.setDccCurrency(dcc.getCurrency());
        dto.setCreatedBy(dcc.getCreatedBy());
        dto.setCreatedByName(dcc.getCreatedBy());

        if (latestApprovalRequest != null && latestApprovalRequest.getApprovedDate() != null) {
            ZonedDateTime zonedApproved = latestApprovalRequest.getApprovedDate().atZone(ZoneId.of("Africa/Nairobi"));
            dto.setDateApproved(zonedApproved.format(DATE_FORMATTER));
        } else {
            dto.setDateApproved(null);
        }
    }

    private void populateLineItemFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn, SimpleDateFormat dateFormat,
                                        Set<Long> loggedInvalidLinkIds) {
        dto.setLnRecordNo(dccLn.getRecordNo());
        dto.setLnProductName(dccLn.getProductName());
        dto.setLnProductSerialNo(dccLn.getSerialNumber());
        dto.setLnDeliveredQty(dccLn.getDeliveredQty());
        dto.setLnLocationName(dccLn.getLocationName());

        if (dccLn.getDateInService() != null) {
            Instant instant = Instant.ofEpochMilli(dccLn.getDateInService().getTime());
            dto.setLnInserviceDate(instant.atZone(ZoneId.of("Africa/Nairobi")).format(DATE_FORMATTER));
        } else {
            dto.setLnInserviceDate(null);
        }

        dto.setLnUnitPrice(dccLn.getUnitPrice() != null ? dccLn.getUnitPrice() : 0.0);
        dto.setLnScopeOfWork(dccLn.getScopeOfWork());
        dto.setLnRemarks(dccLn.getRemarks());
        dto.setLinkId(dccLn.getLinkId());
        dto.setTagNumber(dccLn.getTagNumber());
        dto.setLineNumber(dccLn.getLineNumber());
        dto.setActualItemCode(dccLn.getActualItemCode());
        dto.setUplLineNumber(dccLn.getUplLineNumber());
        dto.setpoAcceptanceQty(dccLn.getpoAcceptanceQty());
    }

    private void populatePurchaseOrderAndUplFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn,
                                                   tbPurchaseOrder purchaseOrder, tb_PurchaseOrderUPL upl) {
        dto.setPoId(purchaseOrder.getPoNumber());

        // CRITICAL FIX: Use newProjectName with fallback to projectName
        dto.setProjectName(
                purchaseOrder.getNewProjectName() != null && !purchaseOrder.getNewProjectName().isEmpty()
                        ? purchaseOrder.getNewProjectName()
                        : purchaseOrder.getProjectName()
        );
        dto.setNewProjectName(purchaseOrder.getNewProjectName());

        dto.setSupplierId(purchaseOrder.getVendorNumber());
        dto.setVendorNumber(purchaseOrder.getVendorNumber());
        dto.setVendorName(purchaseOrder.getVendorName());

        double poOrderQty = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                ? upl.getPoLineQuantity()
                : parsePoOrderQuantity(purchaseOrder);
        dto.setPoLineQuantity(poOrderQty);
        dto.setPoOrderQuantity(poOrderQty);
        dto.setPoLineDescription(upl.getPoLineDescription());
        dto.setUplLineQuantity(upl.getUplLineQuantity());
        dto.setUplLineItemCode(upl.getUplLineItemCode());
        dto.setUplLineDescription(upl.getUplLineDescription());
        dto.setUnitOfMeasure(upl.getUom());
        dto.setActiveOrPassive(upl.getActiveOrPassive());
        dto.setItemCode(upl.getUplLineItemCode());
        dto.setItemPartNumber(upl.getPoLineItemCode());
    }

    private void calculateQuantitiesAndApprovals(DccPOCombinedViewDTO dto, DCC dcc, tbPurchaseOrder purchaseOrder,
                                                 tb_PurchaseOrderUPL upl, Map<String, Double> deliveredMap,
                                                 Map<String, Double> acceptanceByPoLine, Set<String> hasDccLnSet,
                                                 TbCategoryApprovalRequests latestApprovalRequest,
                                                 List<TbCategoryApprovalRequests> allRelatedRequests,
                                                 List<TbCategoryApprovals> allRelatedApprovals) {
        // totalDelivered using precomputed map
        String deliveredKey = upl.getPoNumber() + "-" + upl.getPoLineNumber() + "-" + (upl.getUplLine() != null ? upl.getUplLine() : "");
        double totalDelivered = deliveredMap.getOrDefault(deliveredKey, 0.0);
        dto.setUPLACPTRequestValue(totalDelivered);

        // POLineAcceptanceQty using precomputed
        String poLineKey = upl.getPoNumber() + "-" + upl.getPoLineNumber();
        double poLineAcceptanceQty = acceptanceByPoLine.getOrDefault(poLineKey, 0.0);
        dto.setPOLineAcceptanceQty(poLineAcceptanceQty);

        // poPendingQuantity
        boolean exists = hasDccLnSet.contains(deliveredKey);
        double poPendingQuantity = exists ? poLineAcceptanceQty : (upl.getPoLineQuantity() != null ? upl.getPoLineQuantity() : 0.0);
        dto.setPoPendingQuantity(poPendingQuantity);

        // uplPendingQuantity
        double uplPending = upl.getUplLineQuantity() != null ? upl.getUplLineQuantity() - totalDelivered : 0.0;
        dto.setUplPendingQuantity(Math.max(uplPending, 0.0));

        // Approval fields using pre-fetched allRelatedApprovals
        if (latestApprovalRequest != null) {
            calculateApprovalFields(dto, latestApprovalRequest, allRelatedRequests, allRelatedApprovals);
        } else {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setApproverComment(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging("0 days 0 hrs 0 mins");
        }
    }

    private void calculateApprovalFields(DccPOCombinedViewDTO dto, TbCategoryApprovalRequests latestApprovalRequest,
                                         List<TbCategoryApprovalRequests> allRelatedRequests,
                                         List<TbCategoryApprovals> allRelatedApprovals) {
        List<String> validRequestStatuses = Arrays.asList("pending", "returned");
        List<String> validApprovalStatuses = Arrays.asList("pending", "readyForApproval", "request-info");
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime nowLocal = now.toLocalDateTime();

        // Shared calculations
        String totalAging = calculateTotalAging(allRelatedApprovals, latestApprovalRequest, nowLocal);
        String approverComment = getLatestComment(allRelatedApprovals);

        // Handle terminal statuses: approved, rejected
        if ("approved".equalsIgnoreCase(latestApprovalRequest.getStatus()) || "rejected".equalsIgnoreCase(latestApprovalRequest.getStatus())) {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        // Determine the current approver for the latest approval request
        String currentApproverName = allRelatedApprovals.stream()
                .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
                .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .findFirst()
                .map(TbCategoryApprovals::getApproverName)
                .orElse(null);

        // Calculate userAging2: Historical paused periods for request-info
        long userAging2Minutes = allRelatedApprovals.stream()
                .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
                .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
                .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                .sum();

        // Handle request-info case
        if ("request-info".equals(latestApprovalRequest.getStatus())) {
            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                    .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
                    .filter(a -> allRelatedRequests.stream().anyMatch(r -> "request-info".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
                    .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                    .collect(Collectors.toList());

            dto.setApprovalCount((long) filteredApprovals.size());
            dto.setPendingApprovers(currentApproverName);

            long userAging1Minutes = allRelatedApprovals.stream()
                    .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                    .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
                    .filter(a -> a.getRecordDateTime() != null && a.getApprovedDate() != null)
                    .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                    .sum();

            long totalUserAgingMinutes = userAging1Minutes + userAging2Minutes;
            dto.setUserAging(String.format("%d days %d hrs %d mins",
                    totalUserAgingMinutes / 1440, (totalUserAgingMinutes / 60) % 24, totalUserAgingMinutes % 60));
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        // Handle returned case
        if ("returned".equals(latestApprovalRequest.getStatus())) {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        // Logic for pending status
        List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "readyForApproval").contains(a.getApprovalStatus()))
                .filter(a -> allRelatedRequests.stream().anyMatch(r -> "pending".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                .collect(Collectors.toList());

        dto.setApprovalCount((long) filteredApprovals.size());

        Optional<TbCategoryApprovals> readyForApproval = filteredApprovals.stream()
                .filter(a -> "readyForApproval".equals(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .findFirst();

        String pendingApproverName = readyForApproval
                .map(TbCategoryApprovals::getApproverName)
                .orElseGet(() -> filteredApprovals.stream()
                        .filter(a -> validApprovalStatuses.contains(a.getApprovalStatus()))
                        .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                        .findFirst()
                        .map(TbCategoryApprovals::getApproverName)
                        .orElse(null));
        dto.setPendingApprovers(pendingApproverName);

        // Calculate totalPausedUserAgingMinutes for historical request-info
        long totalPausedUserAgingMinutes = allRelatedApprovals.stream()
                .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .filter(a -> pendingApproverName != null && pendingApproverName.equals(a.getApproverName()))
                .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
                .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                .sum();

        // Calculate currentUserAgingMinutes using latest readyForApproval
        long currentUserAgingMinutes = 0;
        if (pendingApproverName != null) {
            Optional<LocalDateTime> latestReadyForApprovalDate = filteredApprovals.stream()
                    .filter(a -> "readyForApproval".equals(a.getApprovalStatus()) && pendingApproverName.equals(a.getApproverName()))
                    .map(TbCategoryApprovals::getRecordDateTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo);
            currentUserAgingMinutes = latestReadyForApprovalDate
                    .map(date -> Duration.between(date, nowLocal).toMinutes())
                    .orElseGet(() -> latestApprovalRequest.getRecordDateTime() != null
                            ? Duration.between(latestApprovalRequest.getRecordDateTime(), nowLocal).toMinutes()
                            : 0L);
            currentUserAgingMinutes = Math.max(currentUserAgingMinutes, 0);
        }

        long totalUserAgingMinutes = totalPausedUserAgingMinutes + currentUserAgingMinutes;
        dto.setUserAging(String.format("%d days %d hrs %d mins",
                totalUserAgingMinutes / 1440, (totalUserAgingMinutes / 60) % 24, totalUserAgingMinutes % 60));
        dto.setTotalAging(totalAging);
        dto.setApproverComment(approverComment);
    }

    private String calculateTotalAging(List<TbCategoryApprovals> allRelatedApprovals,
                                       TbCategoryApprovalRequests latestApprovalRequest,
                                       LocalDateTime nowLocal) {
        LocalDateTime minRecordDateTime = allRelatedApprovals.stream()
                .map(TbCategoryApprovals::getRecordDateTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(latestApprovalRequest.getRecordDateTime() != null
                        ? latestApprovalRequest.getRecordDateTime()
                        : nowLocal);

        LocalDateTime endDate = allRelatedApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus()) && "pending".equals(a.getApprovalStatus()))
                .findAny()
                .map(a -> nowLocal)
                .orElseGet(() -> allRelatedApprovals.stream()
                        .map(TbCategoryApprovals::getApprovedDate)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(nowLocal));

        long totalAgingMinutes = Math.max(Duration.between(minRecordDateTime, endDate).toMinutes(), 0);
        return String.format("%d days %d hrs %d mins",
                totalAgingMinutes / 1440, (totalAgingMinutes / 60) % 24, totalAgingMinutes % 60);
    }

    private String getLatestComment(List<TbCategoryApprovals> allRelatedApprovals) {
        return allRelatedApprovals.stream()
                .filter(a -> !"pending".equals(a.getApprovalStatus()) && !"readyForApproval".equals(a.getApprovalStatus()))
                .filter(a -> a.getComments() != null)
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                .map(TbCategoryApprovals::getComments)
                .findFirst()
                .orElse(null);
    }

    private double parsePoOrderQuantity(tbPurchaseOrder purchaseOrder) {
        String poQtyNew = String.valueOf(purchaseOrder.getPoQtyNew());
        return (poQtyNew != null && !poQtyNew.isEmpty()) ? Double.parseDouble(poQtyNew) : purchaseOrder.getAmountDueLine();
    }
}