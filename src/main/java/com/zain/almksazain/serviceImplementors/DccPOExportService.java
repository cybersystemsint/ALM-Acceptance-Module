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
public class DccPOExportService {

    private static final Logger logger = LogManager.getLogger(DccPOExportService.class);

    // BATCH PROCESSING CONFIGURATION - Prevents memory exhaustion
    private static final int BATCH_SIZE = 500; // Process 500 DCC records at a time
    private static final int MAX_PAGES = 200; // Safety limit: max 200 pages (100,000 DCCs)

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d-MMM-yyyy").withZone(ZoneId.of("Africa/Nairobi"));

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

    /**
     * Optimized export with batch processing to prevent memory exhaustion.
     * Processes DCC records in batches to keep memory usage low and stable.
     */
    @Async("taskExecutor")
    public CompletableFuture<List<DccPOCombinedViewDTO>> getAllDccPOForExportV2(
            String supplierId,
            String pendingApprovers,
            String columnName,
            String searchQuery,
            String operator,
            Map<String, String> fieldFilters,
            String createdDateStart,
            String createdDateEnd,
            String approvedDateStart,
            String approvedDateEnd,
            int maxRecords) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting optimized export V2 - supplierId: {}, pendingApprovers: {}, filters: {}, maxRecords: {}",
                        supplierId, pendingApprovers, fieldFilters != null ? fieldFilters.keySet() : "none", maxRecords);

                // Build enhanced specification with ALL filters
                DccSpecification spec = new DccSpecification(
                        supplierId,
                        pendingApprovers,
                        columnName,
                        searchQuery,
                        operator,
                        fieldFilters,
                        createdDateStart,
                        createdDateEnd,
                        approvedDateStart,
                        approvedDateEnd
                );

                // BATCH PROCESSING - Process in chunks to control memory
                List<DccPOCombinedViewDTO> allResults = new ArrayList<>();
                int currentPage = 0;
                int totalDccProcessed = 0;
                boolean shouldContinue = true;

                while (shouldContinue && allResults.size() < maxRecords) {
                    // Fetch one batch of DCC records
                    Pageable pageable = PageRequest.of(currentPage, BATCH_SIZE,
                            Sort.by(Sort.Direction.DESC, "recordNo"));

                    Page<DCC> dccPage = tbDccRepository.findAll(spec, pageable);
                    List<DCC> dccBatch = dccPage.getContent();

                    if (dccBatch.isEmpty()) {
                        logger.info("No more DCC records found at page {}", currentPage);
                        break;
                    }

                    logger.info("Processing batch {} with {} DCC records (total DTOs so far: {})",
                            currentPage, dccBatch.size(), allResults.size());

                    // Validate batch
                    List<DCC> invalidRecords = dccBatch.stream()
                            .filter(dcc -> dcc.getPoNumber() == null || dcc.getPoNumber().isEmpty())
                            .collect(Collectors.toList());

                    if (!invalidRecords.isEmpty()) {
                        logger.error("Found {} invalid DCC records in batch {} with missing poNumber",
                                invalidRecords.size(), currentPage);
                        throw new DccPOProcessingException("Invalid DCC records detected with missing poNumber");
                    }

                    // Process THIS BATCH ONLY (memory-efficient)
                    List<DccPOCombinedViewDTO> batchResults = processDccBatch(dccBatch);

                    // Add results, respecting maxRecords limit
                    int remainingCapacity = maxRecords - allResults.size();
                    if (batchResults.size() <= remainingCapacity) {
                        allResults.addAll(batchResults);
                        totalDccProcessed += dccBatch.size();
                    } else {
                        // Only add what fits within maxRecords
                        allResults.addAll(batchResults.subList(0, remainingCapacity));
                        logger.warn("Reached maxRecords limit of {}. Stopping batch processing.", maxRecords);
                        shouldContinue = false;
                        break;
                    }

                    logger.info("Batch {} complete: added {} DTOs, total now: {}/{} (processed {} DCCs)",
                            currentPage, batchResults.size(), allResults.size(), maxRecords, totalDccProcessed);

                    // Check if more pages exist
                    if (!dccPage.hasNext()) {
                        logger.info("No more pages available. Processed {} total DCC records.", totalDccProcessed);
                        shouldContinue = false;
                    }

                    currentPage++;

                    // Safety check: prevent infinite loops
                    if (currentPage >= MAX_PAGES) {
                        logger.warn("Reached maximum page limit of {}. Stopping for safety.", MAX_PAGES);
                        break;
                    }
                }

                logger.info("Export V2 complete: {} DTOs generated from {} DCC records (max allowed: {})",
                        allResults.size(), totalDccProcessed, maxRecords);

                return allResults;

            } catch (Exception ex) {
                logger.error("Error during optimized export V2 of DCC PO Combined View", ex);
                throw new DccPOProcessingException("Failed to export DCC PO Combined View V2", ex);
            }
        });
    }

    /**
     * Process a single batch of DCC records and return DTOs.
     * This method fetches ONLY the related data for THIS BATCH to minimize memory usage.
     */
    private List<DccPOCombinedViewDTO> processDccBatch(List<DCC> dccBatch) {
        // Extract IDs from THIS BATCH only
        Set<String> poNumbersSet = dccBatch.stream()
                .map(DCC::getPoNumber)
                .collect(Collectors.toSet());
        List<String> poNumbers = new ArrayList<>(poNumbersSet);

        List<Long> dccIds = dccBatch.stream()
                .map(DCC::getRecordNo)
                .collect(Collectors.toList());

        logger.debug("Fetching related data for batch: {} unique PO numbers, {} DCC IDs",
                poNumbers.size(), dccIds.size());

        // Fetch related data for THIS BATCH ONLY (not all records)
        // Purchase Orders
        Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository
                .findByPoNumberIn(poNumbers)
                .stream()
                .collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));

        // UPLs
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository
                .findByPoNumberIn(poNumbers)
                .stream()
                .collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));

        // CRITICAL: Only fetch line items for THIS BATCH (prevents loading millions of rows)
        List<DCCLineItem> allDccLn = tbDccLnRepository
                .findByDccIdIn(dccIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList()));

        logger.debug("Fetched {} line items for this batch of {} DCCs",
                allDccLn.size(), dccBatch.size());

        // DCC Map for status lookup
        Map<Long, DCC> dccMap = dccBatch.stream()
                .collect(Collectors.toMap(DCC::getRecordNo, Function.identity()));

        // Precompute delivered sums for THIS BATCH
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

        // All UPL for acceptance calculations for THIS BATCH
        List<tb_PurchaseOrderUPL> allUplList = uplMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

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

        // Set of UPL keys that have DCC_LN
        Set<String> hasDccLnSet = allDccLn.stream()
                .map(dln -> dln.getPoId() + "-" + dln.getLineNumber() + "-" +
                        (dln.getUplLineNumber() != null ? dln.getUplLineNumber() : ""))
                .collect(Collectors.toSet());

        // All Approval Requests for these DCCs
        List<TbCategoryApprovalRequests> allRequests = tbCategoryApprovalRequestsRepository
                .findByAcceptanceRequestRecordNoInOrderByAcceptanceRequestRecordNoAscRecordDateTimeDesc(dccIds);

        // Group requests by DCC ID
        Map<Long, List<TbCategoryApprovalRequests>> requestsByDccId = allRequests.stream()
                .collect(Collectors.groupingBy(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo));

        // All request recordNos
        Set<Long> allRequestRecordNos = allRequests.stream()
                .map(TbCategoryApprovalRequests::getRecordNo)
                .collect(Collectors.toSet());

        // All Approvals
        List<TbCategoryApprovals> allApprovals = allRequestRecordNos.isEmpty()
                ? Collections.emptyList()
                : tbCategoryApprovalsRepository.findByApprovalRecordIdIn(new ArrayList<>(allRequestRecordNos));

        // Group approvals by request recordNo
        Map<Long, List<TbCategoryApprovals>> approvalsByRequestRecordNo = allApprovals.stream()
                .collect(Collectors.groupingBy(TbCategoryApprovals::getApprovalRecordId));

        SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");

        // Process all records in parallel for THIS BATCH
        List<DccPOCombinedViewDTO> result = dccBatch.parallelStream()
                .flatMap(dcc -> {
                    List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap
                            .getOrDefault(dcc.getPoNumber(), Collections.emptyList());

                    if (purchaseOrderList.isEmpty()) {
                        logger.error("No Purchase Order found for poNumber: {} in DCC record: {}.",
                                dcc.getPoNumber(), dcc.getRecordNo());
                        return Stream.empty(); // Skip instead of throw for export
                    }

                    tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);

                    List<tb_PurchaseOrderUPL> uplList = uplMap
                            .getOrDefault(dcc.getPoNumber(), Collections.emptyList());

                    List<DCCLineItem> dccLnList = allDccLn.stream()
                            .filter(dln -> dln.getDccId().equals(String.valueOf(dcc.getRecordNo())))
                            .collect(Collectors.toList());

                    List<TbCategoryApprovalRequests> dccRequests = requestsByDccId
                            .getOrDefault(dcc.getRecordNo(), Collections.emptyList());

                    TbCategoryApprovalRequests latestApprovalRequest = dccRequests.stream()
                            .max(Comparator.comparing(TbCategoryApprovalRequests::getRecordDateTime))
                            .orElse(null);

                    List<TbCategoryApprovals> allRelatedApprovals = dccRequests.stream()
                            .flatMap(r -> approvalsByRequestRecordNo
                                    .getOrDefault(r.getRecordNo(), Collections.emptyList()).stream())
                            .collect(Collectors.toList());

                    if (dccLnList.isEmpty() || uplList.isEmpty()) {
                        logger.warn("No DCC_LN or UPL records found for DCC ID: {}. Skipping.",
                                dcc.getRecordNo());
                        return Stream.empty();
                    }

                    return buildDccPOCombinedViewDTOs(
                            dcc,
                            purchaseOrder,
                            uplList,
                            dccLnList,
                            latestApprovalRequest,
                            dccRequests,
                            allRelatedApprovals,
                            deliveredMap,
                            acceptanceByPoLine,
                            hasDccLnSet,
                            dateFormat
                    ).stream();
                })
                .collect(Collectors.toList());

        logger.debug("Batch processing complete: generated {} DTOs from {} DCC records",
                result.size(), dccBatch.size());

        return result;
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

            // Fallback condition if needed (from original code)
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
            calculateQuantitiesAndApprovalsV2(dto, dcc, purchaseOrder, upl, deliveredMap, acceptanceByPoLine, hasDccLnSet, latestApprovalRequest, allRelatedRequests, allRelatedApprovals);

            dtos.add(dto);
        }

        return dtos;
    }

    private void populateDccFields(DccPOCombinedViewDTO dto, DCC dcc, SimpleDateFormat dateFormat,
                                   TbCategoryApprovalRequests latestApprovalRequest) {
        dto.setDccRecordNo(dcc.getRecordNo());
        dto.setDccPoNumber(dcc.getPoNumber());
        dto.setDccVendorEmail(dcc.getVendorEmail());
        dto.setDccProjectName(dcc.getProjectName());
        dto.setDccAcceptanceType(dcc.getAcceptanceType());
        dto.setDccStatus(dcc.getStatus());
//        dto.setDccCreatedDate(dcc.getCreatedDate() != null ? dateFormat.format(dcc.getCreatedDate()) : null);
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


//        if (latestApprovalRequest != null && latestApprovalRequest.getApprovedDate() != null) {
//            Date approvedDate = Date.from(latestApprovalRequest.getApprovedDate().atZone(ZoneId.of("Africa/Nairobi")).toInstant());
//            dto.setDateApproved(dateFormat.format(approvedDate));
//        }

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

//        dto.setLnInserviceDate(dccLn.getDateInService() != null ? dateFormat.format(dccLn.getDateInService()) : null);
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

    private void calculateQuantitiesAndApprovalsV2(DccPOCombinedViewDTO dto, DCC dcc, tbPurchaseOrder purchaseOrder,
                                                   tb_PurchaseOrderUPL upl, Map<String, Double> deliveredMap,
                                                   Map<String, Double> acceptanceByPoLine, Set<String> hasDccLnSet,
                                                   TbCategoryApprovalRequests latestApprovalRequest, List<TbCategoryApprovalRequests> allRelatedRequests, List<TbCategoryApprovals> allRelatedApprovals) {
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
            calculateApprovalFieldsV2(dto, latestApprovalRequest, allRelatedRequests, allRelatedApprovals);
        } else {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setApproverComment(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging("0 days 0 hrs 0 mins");
        }
    }

    private void calculateApprovalFieldsV2(DccPOCombinedViewDTO dto, TbCategoryApprovalRequests latestApprovalRequest, List<TbCategoryApprovalRequests> allRelatedRequests, List<TbCategoryApprovals> allRelatedApprovals) {
        List<String> validRequestStatuses = Arrays.asList("pending", "returned");
        List<String> validApprovalStatuses = Arrays.asList("pending", "readyForApproval", "request-info");
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime nowLocal = now.toLocalDateTime();

        // Shared calculations
        String totalAging = calculateTotalAging(allRelatedApprovals, latestApprovalRequest, nowLocal);
        String approverComment = getLatestComment(allRelatedApprovals);

        // Handle terminal statuses: approved, rejected
        if ("approved".equalsIgnoreCase(latestApprovalRequest.getStatus()) || "rejected".equalsIgnoreCase(latestApprovalRequest.getStatus())) {
            logger.debug("Approval request recordNo={} has status '{}'; setting approvalCount=0, pendingApprovers=null, userAging=0",
                    latestApprovalRequest.getRecordNo(), latestApprovalRequest.getStatus());
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
                .mapToLong(a -> {
                    long pausedMinutes = Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes();
                    logger.debug("Historical paused period for approvalId={} by {}: {} minutes (from {} to {})",
                            a.getApprovalId(), a.getApproverName(), pausedMinutes, a.getRecordDateTime(), a.getApprovedDate());
                    return Math.max(pausedMinutes, 0);
                })
                .sum();

        //Handle request-info case
        if ("request-info".equals(latestApprovalRequest.getStatus())) {
            logger.debug("Approval request recordNo={} has status 'request-info'", latestApprovalRequest.getRecordNo());

//            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
//                    .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
//                    .filter(a -> allRelatedRequests.stream().anyMatch(r -> "request-info".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
//                    .collect(Collectors.toList());

            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                    .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
                    .filter(a -> allRelatedRequests.stream().anyMatch(r -> "request-info".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
                    .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                    .collect(Collectors.toList());

            dto.setApprovalCount((long) filteredApprovals.size());
            dto.setPendingApprovers(currentApproverName);

            // Calculate userAging1: Current period for request-info
            long userAging1Minutes = allRelatedApprovals.stream()
                    .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                    .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
                    .filter(a -> a.getRecordDateTime() != null && a.getApprovedDate() != null)
                    .mapToLong(a -> {
                        long currentMinutes = Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes();
                        logger.debug("Current period for approvalId={} by {}: {} minutes (from {} to {})",
                                a.getApprovalId(), a.getApproverName(), currentMinutes, a.getRecordDateTime(), a.getApprovedDate());
                        return Math.max(currentMinutes, 0);
                    })
                    .sum();

            // Total userAging = userAging1 + userAging2
            long totalUserAgingMinutes = userAging1Minutes + userAging2Minutes;
            dto.setUserAging(String.format("%d days %d hrs %d mins",
                    totalUserAgingMinutes / 1440, (totalUserAgingMinutes / 60) % 24, totalUserAgingMinutes % 60));
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }


        // Handle returned case
        if ("returned".equals(latestApprovalRequest.getStatus())) {
            logger.debug("Approval request recordNo={} has status 'returned'; setting approvalCount=0, pendingApprovers=null, userAging=0",
                    latestApprovalRequest.getRecordNo());
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(approverComment);
            return;
        }

        // Logic for pending status
        // Filter approvals for pending/readyForApproval
//        List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
//                .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "readyForApproval").contains(a.getApprovalStatus()))
//                .filter(a -> allRelatedRequests.stream().anyMatch(r -> "pending".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
//                .collect(Collectors.toList());

        List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "readyForApproval").contains(a.getApprovalStatus()))
                .filter(a -> allRelatedRequests.stream().anyMatch(r -> "pending".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                .collect(Collectors.toList());

        logger.debug("Processing approval request: recordNo={}, status={}, recordDateTime={}",
                latestApprovalRequest.getRecordNo(), latestApprovalRequest.getStatus(), latestApprovalRequest.getRecordDateTime());
        logger.debug("Filtered approvals count: {}", filteredApprovals.size());

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
                .mapToLong(a -> {
                    long pausedMinutes = Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes();
                    logger.debug("Historical paused period for approvalId={} by {} : {} minutes (from {} to {})",
                            a.getApprovalId(), a.getApproverName(), pausedMinutes, a.getRecordDateTime(), a.getApprovedDate());
                    return Math.max(pausedMinutes, 0);
                })
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
            logger.debug("Current userAging for approver {}: {} minutes", pendingApproverName, currentUserAgingMinutes);
        } else {
            logger.warn("No pending approver found for recordNo={}; setting currentUserAgingMinutes to 0", latestApprovalRequest.getRecordNo());
        }

        // Step 4: Sum for userAging
        long totalUserAgingMinutes = totalPausedUserAgingMinutes + currentUserAgingMinutes;
        dto.setUserAging(String.format("%d days %d hrs %d mins",
                totalUserAgingMinutes / 1440, (totalUserAgingMinutes / 60) % 24, totalUserAgingMinutes % 60));
        dto.setTotalAging(totalAging);
        dto.setApproverComment(approverComment);
    }

    private String calculateTotalAging(List<TbCategoryApprovals> allRelatedApprovals, TbCategoryApprovalRequests latestApprovalRequest, LocalDateTime nowLocal) {
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
        long totalAgingMinutes = Duration.between(minRecordDateTime, endDate).toMinutes();
        totalAgingMinutes = Math.max(totalAgingMinutes, 0);
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