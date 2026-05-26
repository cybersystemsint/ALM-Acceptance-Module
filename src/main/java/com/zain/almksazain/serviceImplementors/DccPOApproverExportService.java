package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * UNIFIED METHOD: Used by both filter endpoint and export endpoint
     * Fetches ALL user actions first, then applies filters IN MEMORY
     */
    @Async("taskExecutor")
    public CompletableFuture<List<DccPOCombinedViewDTO>> getAllDccPOForApproverExportWithDirectFilters(
            String supplierId, String pendingApprovers, Map<String, String> fieldFilters, String operator) {

        final String finalOperator = operator != null ? operator.toUpperCase() : "AND";

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting approver export with filters - Approver: {}, Filters: {}, Operator: {}",
                        pendingApprovers, fieldFilters.size(), finalOperator);

                // STEP 1: Get ALL DCC records for this approver FIRST (NO FILTERING YET)
                Set<Long> dccRecordNos = new HashSet<>();

                if (pendingApprovers == null || pendingApprovers.isEmpty()) {
                    logger.error("pendingApprovers is required");
                    return new ArrayList<>();
                }

                Integer approverIdAsInt;
                try {
                    approverIdAsInt = Integer.parseInt(pendingApprovers);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse pendingApprovers: {}", pendingApprovers, e);
                    return new ArrayList<>();
                }

                // Get approvals for this user (SAME LOGIC AS DccPOApproverService)
                List<TbCategoryApprovals> approvals = tbCategoryApprovalsRepository
                        .findByApprovedBy(approverIdAsInt).stream()
                        .filter(a -> {
                            if ("pending".equals(a.getStatus())) {
                                return "request-info".equals(a.getApprovalStatus());
                            }
                            return true; // Include approved, rejected, etc.
                        })
                        .collect(Collectors.toList());

                Set<Long> approvalRecordIds = approvals.stream()
                        .map(TbCategoryApprovals::getApprovalRecordId)
                        .collect(Collectors.toSet());

                logger.info("Found {} approval actions for approver {}", approvalRecordIds.size(), pendingApprovers);

                // Get receipts for this user
                List<AcceptanceRequestReceipt> userReceipts =
                        acceptanceRequestReceiptRepository.findByApprovedBy(approverIdAsInt);

                Set<Long> userReceiptIds = userReceipts.stream()
                        .map(r -> r.getCategoryApprovalRequestId().longValue())
                        .collect(Collectors.toSet());

                logger.info("Found {} receipt actions for approver {}", userReceiptIds.size(), pendingApprovers);

                // Combine both (ALL ACTIONS by this user)
                Set<Long> combinedRecordIds = new HashSet<>(approvalRecordIds);
                combinedRecordIds.addAll(userReceiptIds);

                if (combinedRecordIds.isEmpty()) {
                    logger.warn("No approval or receipt actions found for approver: {}", pendingApprovers);
                    return new ArrayList<>();
                }

                logger.info("Total combined actions for approver {}: {}", pendingApprovers, combinedRecordIds.size());

                // Get approval requests
                List<TbCategoryApprovalRequests> requests =
                        tbCategoryApprovalRequestsRepository.findByRecordNoIn(new ArrayList<>(combinedRecordIds));

                // Get DCC record numbers
                dccRecordNos = requests.stream()
                        .filter(r -> r.getAcceptanceRequestRecordNo() != null)
                        .map(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo)
                        .collect(Collectors.toSet());

                if (dccRecordNos.isEmpty()) {
                    logger.warn("No DCC records found for approver: {}", pendingApprovers);
                    return new ArrayList<>();
                }

                logger.info("Found {} DCC records for approver {} (BEFORE filtering)", dccRecordNos.size(), pendingApprovers);

                // STEP 2: Fetch ALL DCC records for this user (NO FILTER YET)
                List<DCC> allDccList = tbDccRepository.findByRecordNoIn(new ArrayList<>(dccRecordNos));

                logger.info("Fetched {} DCC records from database", allDccList.size());

                // STEP 3: Apply filters IN MEMORY (not at database level)
                List<DCC> filteredDccList = applyInMemoryFilters(allDccList, supplierId, fieldFilters, finalOperator);

                logger.info("After in-memory filtering: {} DCC records remain", filteredDccList.size());

                if (filteredDccList.isEmpty()) {
                    logger.info("No records match the filters for approver {}", pendingApprovers);
                    return new ArrayList<>();
                }

                // Validate DCC records
                List<DCC> invalidDccRecords = filteredDccList.stream()
                        .filter(dcc -> dcc.getPoNumber() == null || dcc.getPoNumber().isEmpty())
                        .collect(Collectors.toList());
                if (!invalidDccRecords.isEmpty()) {
                    logger.warn("Found {} invalid DCC records with missing PO numbers", invalidDccRecords.size());
                    throw new DccPOProcessingException("Invalid DCC records with missing PO numbers");
                }

                // STEP 4: Fetch related data and build DTOs
                return buildDTOsWithRelatedData(filteredDccList);

            } catch (Exception e) {
                logger.error("Error in approver filter export", e);
                throw new DccPOProcessingException("Failed approver export: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Apply filters IN MEMORY to the DCC list
     */
    private List<DCC> applyInMemoryFilters(List<DCC> dccList, String supplierId,
                                           Map<String, String> fieldFilters, String operator) {
        // Apply supplierId filter first if present
        if (supplierId != null && !supplierId.trim().isEmpty() && !"0".equals(supplierId.trim())) {
            final String finalSupplierId = supplierId.trim();
            dccList = dccList.stream()
                    .filter(dcc -> finalSupplierId.equalsIgnoreCase(dcc.getVendorNumber()))
                    .collect(Collectors.toList());
            logger.debug("After supplierId filter: {} records", dccList.size());
        }

        // If no field filters, return current list
        if (fieldFilters == null || fieldFilters.isEmpty()) {
            return dccList;
        }

        // Apply field filters
        return dccList.stream()
                .filter(dcc -> {
                    boolean matches = "OR".equalsIgnoreCase(operator) ? false : true;

                    for (Map.Entry<String, String> filter : fieldFilters.entrySet()) {
                        String field = filter.getKey().toLowerCase();
                        String value = filter.getValue().toLowerCase().trim();
                        boolean fieldMatches = false;

                        switch (field) {
                            case "recordno":
                            case "dccrecordno":
                                fieldMatches = dcc.getRecordNo() != null &&
                                        dcc.getRecordNo().toString().toLowerCase().contains(value);
                                break;

                            case "dccponumber":
                            case "ponumber":
                            case "poid":
                                fieldMatches = dcc.getPoNumber() != null &&
                                        dcc.getPoNumber().toLowerCase().contains(value);
                                break;

                            case "projectname":
                            case "newprojectname":
                                String projectName = dcc.getNewProjectName() != null && !dcc.getNewProjectName().isEmpty()
                                        ? dcc.getNewProjectName()
                                        : dcc.getProjectName();
                                fieldMatches = projectName != null &&
                                        projectName.toLowerCase().contains(value);
                                break;

                            case "dccstatus":
                            case "status":
                                fieldMatches = dcc.getStatus() != null &&
                                        dcc.getStatus().equalsIgnoreCase(value); // ← EXACT
                                break;

                            case "dccacceptancetype":
                            case "acceptancetype":
                                fieldMatches = dcc.getAcceptanceType() != null &&
                                        dcc.getAcceptanceType().equalsIgnoreCase(value); // ← EXACT
                                break;

                            case "vendorname":
                                fieldMatches = dcc.getVendorName() != null &&
                                        dcc.getVendorName().toLowerCase().contains(value);
                                break;

                            case "vendornumber":
                            case "supplierid":
                                fieldMatches = dcc.getVendorNumber() != null &&
                                        dcc.getVendorNumber().toLowerCase().contains(value);
                                break;

                            case "vendoremail":
                            case "dccemail":
                                fieldMatches = dcc.getVendorEmail() != null &&
                                        dcc.getVendorEmail().toLowerCase().contains(value);
                                break;

                            case "createdby":
                            case "createdbyname":
                                fieldMatches = dcc.getCreatedBy() != null &&
                                        dcc.getCreatedBy().toLowerCase().contains(value);
                                break;

                            case "vendorcomment":
                            case "vendorcomments":
                                fieldMatches = dcc.getVendorComment() != null &&
                                        dcc.getVendorComment().toLowerCase().contains(value);
                                break;

                            case "dcccurrency":
                            case "currency":
                                fieldMatches = dcc.getCurrency() != null &&
                                        dcc.getCurrency().toLowerCase().contains(value);
                                break;

                            case "dccid":
                                fieldMatches = dcc.getDccId() != null &&
                                        dcc.getDccId().toString().toLowerCase().contains(value);
                                break;

                            // Note: Fields like approverComment, pendingApprovers, approvalCount
                            // are in TbCategoryApprovals, not DCC table, so we can't filter here.
                            // These will be filtered AFTER DTO building if needed.

                            default:
                                logger.debug("Skipping unknown filter field: {}", field);
                                break;
                        }

                        // Apply operator logic
                        if ("OR".equalsIgnoreCase(operator)) {
                            matches = matches || fieldMatches;
                        } else {
                            matches = matches && fieldMatches;
                        }
                    }

                    return matches;
                })
                .collect(Collectors.toList());
    }

    /**
     * Build DTOs with all related data (same as original logic)
     */
    private List<DccPOCombinedViewDTO> buildDTOsWithRelatedData(List<DCC> dccList) {
        // Fetch related data in bulk
        Set<String> poNumbersSet = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
        List<String> poNumbers = new ArrayList<>(poNumbersSet);

        Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));

        Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository.findByPoNumberIn(poNumbers)
                .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));

        List<Long> dccIds = dccList.stream().map(DCC::getRecordNo).collect(Collectors.toList());
        List<DCCLineItem> allDccLn = tbDccLnRepository.findByDccIdIn(
                dccIds.stream().map(String::valueOf).collect(Collectors.toList()));

        Map<Long, DCC> dccMap = dccList.stream().collect(Collectors.toMap(DCC::getRecordNo, Function.identity()));

        // Build delivered map
        Map<String, Double> deliveredMap = allDccLn.stream()
                .filter(dln -> {
                    DCC dd = dccMap.get(Long.parseLong(dln.getDccId()));
                    return dd != null &&
                            !Arrays.asList("incomplete", "rejected").contains(dd.getStatus().toLowerCase()) &&
                            dln.getDeliveredQty() != null;
                })
                .collect(Collectors.groupingBy(
                        dln -> dln.getPoId() + "-" + dln.getLineNumber() + "-" +
                                (dln.getUplLineNumber() != null ? dln.getUplLineNumber() : ""),
                        Collectors.summingDouble(DCCLineItem::getDeliveredQty)
                ));

        List<tb_PurchaseOrderUPL> allUplList = uplMap.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());

        Map<String, Double> acceptanceByPoLine = allUplList.stream()
                .filter(u -> u.getUplLineQuantity() != null && u.getUplLineQuantity() > 0
                        && u.getPoLineQuantity() != null && u.getPoLineQuantity() > 0
                        && u.getPoLineUnitPrice() != null && u.getPoLineUnitPrice() > 0)
                .collect(Collectors.groupingBy(
                        u -> u.getPoNumber() + "-" + u.getPoLineNumber(),
                        Collectors.summingDouble(u -> {
                            double denominator = u.getPoLineQuantity() * u.getPoLineUnitPrice();
                            return denominator == 0 ? 0.0 :
                                    (u.getUplLineQuantity() * u.getPoLineQuantity()) / denominator;
                        })
                ));

        Set<String> hasDccLnSet = allDccLn.stream()
                .map(dln -> dln.getPoId() + "-" + dln.getLineNumber() + "-" +
                        (dln.getUplLineNumber() != null ? dln.getUplLineNumber() : ""))
                .collect(Collectors.toSet());

        // Fetch approval data
        List<TbCategoryApprovalRequests> allRequests = tbCategoryApprovalRequestsRepository
                .findByAcceptanceRequestRecordNoInOrderByAcceptanceRequestRecordNoAscRecordDateTimeDesc(dccIds);

        Map<Long, List<TbCategoryApprovalRequests>> requestsByDccId = allRequests.stream()
                .collect(Collectors.groupingBy(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo));

        Set<Long> allRequestRecordNos = allRequests.stream()
                .map(TbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());

        List<TbCategoryApprovals> allApprovals = tbCategoryApprovalsRepository
                .findByApprovalRecordIdIn(new ArrayList<>(allRequestRecordNos));

        Map<Long, List<TbCategoryApprovals>> approvalsByRequestRecordNo = allApprovals.stream()
                .collect(Collectors.groupingBy(TbCategoryApprovals::getApprovalRecordId));

        SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");

        // Build DTOs
        List<DccPOCombinedViewDTO> result = dccList.parallelStream()
                .flatMap(dcc -> {
                    List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap.getOrDefault(
                            dcc.getPoNumber(), Collections.emptyList());
                    if (purchaseOrderList.isEmpty()) {
                        logger.debug("No PO found for DCC {}", dcc.getRecordNo());
                        return Stream.empty();
                    }

                    tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);
                    List<tb_PurchaseOrderUPL> uplList = uplMap.getOrDefault(
                            dcc.getPoNumber(), Collections.emptyList());
                    List<DCCLineItem> dccLnList = allDccLn.stream()
                            .filter(dln -> dln.getDccId().equals(String.valueOf(dcc.getRecordNo())))
                            .collect(Collectors.toList());

                    List<TbCategoryApprovalRequests> dccRequests = requestsByDccId.getOrDefault(
                            dcc.getRecordNo(), Collections.emptyList());
                    TbCategoryApprovalRequests latestApprovalRequest = dccRequests.stream()
                            .max(Comparator.comparing(TbCategoryApprovalRequests::getRecordDateTime))
                            .orElse(null);

                    List<TbCategoryApprovals> allRelatedApprovals = dccRequests.stream()
                            .flatMap(r -> approvalsByRequestRecordNo.getOrDefault(
                                    r.getRecordNo(), Collections.emptyList()).stream())
                            .collect(Collectors.toList());

                    if (dccLnList.isEmpty() || uplList.isEmpty()) {
                        logger.debug("No line items or UPL for DCC {}", dcc.getRecordNo());
                        return Stream.empty();
                    }

                    return buildDccPOCombinedViewDTOs(dcc, purchaseOrder, uplList, dccLnList,
                            latestApprovalRequest, dccRequests, allRelatedApprovals, deliveredMap,
                            acceptanceByPoLine, hasDccLnSet, dateFormat).stream();
                })
                .collect(Collectors.toList());

        logger.info("Built {} DTOs successfully", result.size());

        // Sort by recordNo DESC (most recent first)
        result.sort(Comparator.comparing(DccPOCombinedViewDTO::getDccRecordNo, Comparator.reverseOrder())
                .thenComparing(DccPOCombinedViewDTO::getLineNumber, Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }

    // All the helper methods remain the same as your original code...

    private List<DccPOCombinedViewDTO> buildDccPOCombinedViewDTOs(
            DCC dcc, tbPurchaseOrder purchaseOrder, List<tb_PurchaseOrderUPL> uplList,
            List<DCCLineItem> dccLnList, TbCategoryApprovalRequests latestApprovalRequest,
            List<TbCategoryApprovalRequests> allRelatedRequests, List<TbCategoryApprovals> allRelatedApprovals,
            Map<String, Double> deliveredMap, Map<String, Double> acceptanceByPoLine,
            Set<String> hasDccLnSet, SimpleDateFormat dateFormat) {

        List<DccPOCombinedViewDTO> dtos = new ArrayList<>();

        Map<String, tb_PurchaseOrderUPL> uplByKey = uplList.stream()
                .collect(Collectors.toMap(
                        u -> (u.getUplLine() != null ? u.getUplLine() : "") + "-" +
                                u.getPoLineNumber() + "-" + u.getPoNumber(),
                        u -> u));

        for (DCCLineItem dccLn : dccLnList) {
            String key = (dccLn.getUplLineNumber() != null ? dccLn.getUplLineNumber() : "") + "-" +
                    dccLn.getLineNumber() + "-" + dcc.getPoNumber();
            tb_PurchaseOrderUPL upl = uplByKey.get(key);
            if (upl == null) {
                logger.debug("No matching UPL record for DCC {}", dcc.getRecordNo());
                continue;
            }

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
            calculateQuantitiesAndApprovals(dto, dcc, purchaseOrder, upl, deliveredMap,
                    acceptanceByPoLine, hasDccLnSet, latestApprovalRequest, allRelatedRequests,
                    allRelatedApprovals);

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
            ZonedDateTime zonedApproved = latestApprovalRequest.getApprovedDate()
                    .atZone(ZoneId.of("Africa/Nairobi"));
            dto.setDateApproved(zonedApproved.format(DATE_FORMATTER));
        } else {
            dto.setDateApproved(null);
        }
    }

    private void populateLineItemFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn,
                                        SimpleDateFormat dateFormat, Set<Long> loggedInvalidLinkIds) {
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

    private void calculateQuantitiesAndApprovals(DccPOCombinedViewDTO dto, DCC dcc,
                                                 tbPurchaseOrder purchaseOrder, tb_PurchaseOrderUPL upl,
                                                 Map<String, Double> deliveredMap, Map<String, Double> acceptanceByPoLine,
                                                 Set<String> hasDccLnSet, TbCategoryApprovalRequests latestApprovalRequest,
                                                 List<TbCategoryApprovalRequests> allRelatedRequests,
                                                 List<TbCategoryApprovals> allRelatedApprovals) {
        String deliveredKey = upl.getPoNumber() + "-" + upl.getPoLineNumber() + "-" +
                (upl.getUplLine() != null ? upl.getUplLine() : "");
        double totalDelivered = deliveredMap.getOrDefault(deliveredKey, 0.0);
        dto.setUPLACPTRequestValue(totalDelivered);

        String poLineKey = upl.getPoNumber() + "-" + upl.getPoLineNumber();
        double poLineAcceptanceQty = acceptanceByPoLine.getOrDefault(poLineKey, 0.0);
        dto.setPOLineAcceptanceQty(poLineAcceptanceQty);

        boolean exists = hasDccLnSet.contains(deliveredKey);
        double poPendingQuantity = exists ? poLineAcceptanceQty :
                (upl.getPoLineQuantity() != null ? upl.getPoLineQuantity() : 0.0);
        dto.setPoPendingQuantity(poPendingQuantity);

        double uplPending = upl.getUplLineQuantity() != null ?
                upl.getUplLineQuantity() - totalDelivered : 0.0;
        dto.setUplPendingQuantity(Math.max(uplPending, 0.0));

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

    private void calculateApprovalFields(DccPOCombinedViewDTO dto,
                                         TbCategoryApprovalRequests latestApprovalRequest,
                                         List<TbCategoryApprovalRequests> allRelatedRequests,
                                         List<TbCategoryApprovals> allRelatedApprovals) {
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime nowLocal = now.toLocalDateTime();

        String totalAging = calculateTotalAging(allRelatedApprovals, latestApprovalRequest, nowLocal);
        String approverComment = getLatestComment(allRelatedApprovals);

        if ("approved".equalsIgnoreCase(latestApprovalRequest.getStatus()) ||
                "rejected".equalsIgnoreCase(latestApprovalRequest.getStatus())) {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        String currentApproverName = allRelatedApprovals.stream()
                .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
                .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .findFirst()
                .map(TbCategoryApprovals::getApproverName)
                .orElse(null);

        long userAging2Minutes = allRelatedApprovals.stream()
                .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
                .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
                .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                .sum();

        if ("request-info".equals(latestApprovalRequest.getStatus())) {
            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                    .filter(a -> "pending".equals(a.getStatus()) &&
                            Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
                    .filter(a -> allRelatedRequests.stream().anyMatch(r ->
                            "request-info".equals(r.getStatus()) &&
                                    r.getRecordNo().equals(a.getApprovalRecordId())))
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

        if ("returned".equals(latestApprovalRequest.getStatus())) {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus()) &&
                        Arrays.asList("pending", "readyForApproval").contains(a.getApprovalStatus()))
                .filter(a -> allRelatedRequests.stream().anyMatch(r ->
                        "pending".equals(r.getStatus()) &&
                                r.getRecordNo().equals(a.getApprovalRecordId())))
                .collect(Collectors.toList());

        dto.setApprovalCount((long) filteredApprovals.size());

        Optional<TbCategoryApprovals> readyForApproval = filteredApprovals.stream()
                .filter(a -> "readyForApproval".equals(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .findFirst();

        String pendingApproverName = readyForApproval
                .map(TbCategoryApprovals::getApproverName)
                .orElseGet(() -> filteredApprovals.stream()
                        .filter(a -> Arrays.asList("pending", "readyForApproval", "request-info")
                                .contains(a.getApprovalStatus()))
                        .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                        .findFirst()
                        .map(TbCategoryApprovals::getApproverName)
                        .orElse(null));
        dto.setPendingApprovers(pendingApproverName);

        long totalPausedUserAgingMinutes = allRelatedApprovals.stream()
                .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .filter(a -> pendingApproverName != null && pendingApproverName.equals(a.getApproverName()))
                .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
                .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                .sum();

        long currentUserAgingMinutes = 0;
        if (pendingApproverName != null) {
            Optional<LocalDateTime> latestReadyForApprovalDate = filteredApprovals.stream()
                    .filter(a -> "readyForApproval".equals(a.getApprovalStatus()) &&
                            pendingApproverName.equals(a.getApproverName()))
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
                .filter(a -> !"pending".equals(a.getApprovalStatus()) &&
                        !"readyForApproval".equals(a.getApprovalStatus()))
                .filter(a -> a.getComments() != null)
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                .map(TbCategoryApprovals::getComments)
                .findFirst()
                .orElse(null);
    }

    private double parsePoOrderQuantity(tbPurchaseOrder purchaseOrder) {
        String poQtyNew = String.valueOf(purchaseOrder.getPoQtyNew());
        return (poQtyNew != null && !poQtyNew.isEmpty()) ?
                Double.parseDouble(poQtyNew) : purchaseOrder.getAmountDueLine();
    }
}