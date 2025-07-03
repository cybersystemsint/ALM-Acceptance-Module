package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.TbCategoryApprovalRequests;
import com.zain.almksazain.model.TbCategoryApprovals;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.TbDccLnRepository;
import com.zain.almksazain.repo.TbDccRepository;
import com.zain.almksazain.repo.TbPurchaseOrderRepository;
import com.zain.almksazain.repo.TbPurchaseOrderUplRepository;
import com.zain.almksazain.serviceImplementors.DccSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DccPOService {

    private static final Logger logger = LogManager.getLogger(DccPOService.class);

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

//    public CompletableFuture<DccPOFetchResult> getDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery) {
//        try {
//            logger.info("Starting retrieval of DCC PO Combined View with supplierId: {}, pendingApprovers: {}, page: {}, size: {}, columnName: {}, searchQuery: {}",
//                    supplierId, pendingApprovers, page, size, columnName, searchQuery);
//            return CompletableFuture.supplyAsync(() -> fetchDccPOCombinedView(supplierId, pendingApprovers, page, size, columnName, searchQuery));
//        } catch (Exception ex) {
//            logger.error("Error initiating retrieval of DCC PO Combined View", ex);
//            throw new DccPOProcessingException("Failed to initiate DCC PO Combined View retrieval", ex);
//        }
//    }
    public CompletableFuture<DccPOFetchResult> getDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery, boolean exporting) {
        try {
            logger.info("Starting retrieval of DCC PO Combined View with supplierId: {}, pendingApprovers: {}, page: {}, size: {}, columnName: {}, searchQuery: {}, exporting: {}",
                    supplierId, pendingApprovers, page, size, columnName, searchQuery, exporting);
            return CompletableFuture.supplyAsync(() -> fetchDccPOCombinedView(supplierId, pendingApprovers, page, size, columnName, searchQuery, exporting));
        } catch (Exception ex) {
            logger.error("Error initiating retrieval of DCC PO Combined View", ex);
            throw new DccPOProcessingException("Failed to initiate DCC PO Combined View retrieval", ex);
        }
    }

//    @Async("taskExecutor")
//    @Cacheable(value = "dccPOCombinedViewCache",
//            key = "{#supplierId, #pendingApprovers, #page, #size, #columnName, #searchQuery}",
//            unless = "#result.data == null || #result.data.isEmpty()")
    @Async("taskExecutor")
    @Cacheable(value = "dccPOCombinedViewCache",
            key = "{#supplierId, #pendingApprovers, #page, #size, #columnName, #searchQuery, #exporting}",
            unless = "#result.data == null || #result.data.isEmpty()")

    private DccPOFetchResult fetchDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery, boolean exporting) {

//    private DccPOFetchResult fetchDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery) {
        try {
            if (page < 1 || size < 1) {
                logger.error("Invalid pagination parameters: page={}, size={}", page, size);
                throw new IllegalArgumentException("Page and size must be positive");
            }

            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));

            long totalUnfilteredRecords = tbDccRepository.countByPoNumberIsNotNull();
            logger.info("Total unfiltered distinct dccRecordNo: {}", totalUnfilteredRecords);

            DccSpecification spec = new DccSpecification(supplierId, pendingApprovers, columnName, searchQuery);
            Page<DCC> dccPage = tbDccRepository.findAll(spec, pageable);
            List<DCC> dccList = dccPage.getContent();
            long totalFilteredRecords = dccPage.getTotalElements();
            logger.info("Total filtered valid dccRecordNo: {}", totalFilteredRecords);

            if (dccList.isEmpty()) {
                logger.info("No DCC records found for page {}", page);
                return new DccPOFetchResult(new ArrayList<>(), totalFilteredRecords, totalUnfilteredRecords);
            }

            List<DCC> invalidDccRecords = dccList.stream()
                    .filter(dcc -> dcc.getPoNumber() == null || dcc.getPoNumber().isEmpty())
                    .collect(Collectors.toList());
            if (!invalidDccRecords.isEmpty()) {
                logger.error("Found {} DCC records with missing or invalid poNumber: {}",
                        invalidDccRecords.size(),
                        invalidDccRecords.stream().map(DCC::getRecordNo).collect(Collectors.toList()));
                throw new DccPOProcessingException("Invalid DCC records detected with missing poNumber");
            }

            List<DccPOCombinedViewDTO> result = new ArrayList<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");

            // Check if we should fetch only parent records
            boolean fetchParentOnly = !exporting && !(columnName != null && columnName.equalsIgnoreCase("recordNo") && searchQuery != null && !searchQuery.isEmpty());
//            boolean fetchParentOnly = !(columnName != null && columnName.equalsIgnoreCase("recordNo") && searchQuery != null && !searchQuery.isEmpty());

            if (fetchParentOnly) {
                // Fetch parent-level data (DCC and tbPurchaseOrder)
                Set<String> poNumbersSet = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
                List<String> poNumbers = new ArrayList<>(poNumbersSet);
                Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                        .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));

                for (DCC dcc : dccList) {
                    List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                    if (purchaseOrderList.isEmpty()) {
                        logger.error("No Purchase Order found for poNumber: {} in DCC record: {}.",
                                dcc.getPoNumber(), dcc.getRecordNo());
                        throw new DccPOProcessingException("Missing Purchase Order for DCC record: " + dcc.getRecordNo());
                    }
                    tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);

                    // Fetch latest approval request for this DCC
                    List<TbCategoryApprovalRequests> requests = tbCategoryApprovalRequestsRepository
                            .findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(dcc.getRecordNo());
                    TbCategoryApprovalRequests latestApprovalRequest = requests.isEmpty() ? null : requests.get(0);

                    DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();
                    // Populate parent-level fields from DCC
                    populateDccFields(dto, dcc, dateFormat, latestApprovalRequest);
                    // Populate fields from tbPurchaseOrder
                    dto.setPoId(purchaseOrder.getPoNumber());
                    dto.setProjectName(
                            purchaseOrder.getNewProjectName() != null && !purchaseOrder.getNewProjectName().isEmpty()
                                    ? purchaseOrder.getNewProjectName()
                                    : purchaseOrder.getProjectName()
                    );
                    dto.setSupplierId(purchaseOrder.getVendorNumber());
                    dto.setVendorNumber(purchaseOrder.getVendorNumber());
                    dto.setVendorName(purchaseOrder.getVendorName());

                    // Set approval fields
                    if (latestApprovalRequest != null) {
                        calculateApprovalFields(dto, latestApprovalRequest);
                    } else {
                        dto.setApprovalCount(0L);
                        dto.setPendingApprovers(null);
                        dto.setApproverComment(null);
                        dto.setUserAging("0 days 0 hrs 0 mins");
                        dto.setTotalAging("0 days 0 hrs 0 mins");
                    }

                    result.add(dto);
                }
            } else {
                // Fetch full data with child records
                Set<String> poNumbersSet = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
                List<String> poNumbers = new ArrayList<>(poNumbersSet);
                List<Long> dccIds = dccList.stream().map(DCC::getRecordNo).collect(Collectors.toList());

                // Batch fetch all related data
                Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                        .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));
                Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository.findByPoNumberIn(poNumbers)
                        .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));
                Map<Long, List<DCCLineItem>> dccLnMap = tbDccLnRepository.findByDccIdIn(dccIds.stream().map(String::valueOf).collect(Collectors.toList()))
                        .stream().collect(Collectors.groupingBy(dccLn -> Long.parseLong(dccLn.getDccId())));
                // Fetch latest approval request per DCC
                Map<Long, TbCategoryApprovalRequests> approvalRequestMap = new HashMap<>();
                for (Long dccId : dccIds) {
                    List<TbCategoryApprovalRequests> requests = tbCategoryApprovalRequestsRepository
                            .findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(dccId);
                    if (!requests.isEmpty()) {
                        approvalRequestMap.put(dccId, requests.get(0));
                        logger.debug("Selected latest approval request for DCC ID {}: recordNo={}, status={}, recordDateTime={}",
                                dccId, requests.get(0).getRecordNo(), requests.get(0).getStatus(), requests.get(0).getRecordDateTime());
                    } else {
                        logger.debug("No approval requests found for DCC ID {}", dccId);
                    }
                }

                // Precompute DCC Line Items by UPL key
                Map<String, List<DCCLineItem>> dccLnByUplLineNumber = dccLnMap.values().stream()
                        .flatMap(List::stream)
                        .collect(Collectors.groupingBy(dccLn -> dccLn.getUplLineNumber() + "-" + dccLn.getLineNumber() + "-" + dccLn.getPoId()));

                // Process records in parallel
                Set<Long> processedRecordNos = ConcurrentHashMap.newKeySet();
                Set<Long> loggedInvalidLinkIds = ConcurrentHashMap.newKeySet();

                result = dccList.parallelStream()
                        .filter(dcc -> !processedRecordNos.contains(dcc.getRecordNo()))
                        .flatMap(dcc -> {
                            List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                            if (purchaseOrderList.isEmpty()) {
                                logger.error("No Purchase Order found for poNumber: {} in DCC record: {}.",
                                        dcc.getPoNumber(), dcc.getRecordNo());
                                throw new DccPOProcessingException("Missing Purchase Order for DCC record: " + dcc.getRecordNo());
                            }
                            tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);

                            List<tb_PurchaseOrderUPL> uplList = uplMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                            List<DCCLineItem> dccLnList = dccLnMap.getOrDefault(dcc.getRecordNo(), Collections.emptyList());
                            TbCategoryApprovalRequests latestApprovalRequest = approvalRequestMap.getOrDefault(dcc.getRecordNo(), null);

                            if (dccLnList.isEmpty() || uplList.isEmpty()) {
                                logger.warn("No DCC_LN or UPL records found for DCC ID: {}. Skipping.", dcc.getRecordNo());
                                processedRecordNos.add(dcc.getRecordNo());
                                return Stream.empty();
                            }

                            return buildDccPOCombinedViewDTOs(dcc, purchaseOrder, uplList, dccLnList, latestApprovalRequest,
                                    dccLnByUplLineNumber, dateFormat, loggedInvalidLinkIds, processedRecordNos).stream();
                        })
                        .collect(Collectors.toList());
            }

            logger.info("Retrieved {} records for page {}, size {}", result.size(), page, size);
            return new DccPOFetchResult(result, totalFilteredRecords, totalUnfilteredRecords);
        } catch (Exception ex) {
            logger.error("Error in fetchDccPOCombinedView for supplierId: {}, pendingApprovers: {}, page: {}, size: {}, columnName: {}, searchQuery: {}",
                    supplierId, pendingApprovers, page, size, columnName, searchQuery, ex);
            throw new DccPOProcessingException("Failed to fetch DCC PO Combined View", ex);
        }
    }

    private List<DccPOCombinedViewDTO> buildDccPOCombinedViewDTOs(
            DCC dcc, tbPurchaseOrder purchaseOrder, List<tb_PurchaseOrderUPL> uplList,
            List<DCCLineItem> dccLnList, TbCategoryApprovalRequests latestApprovalRequest,
            Map<String, List<DCCLineItem>> dccLnByUplLineNumber, SimpleDateFormat dateFormat,
            Set<Long> loggedInvalidLinkIds, Set<Long> processedRecordNos) {
        List<DccPOCombinedViewDTO> dtos = new ArrayList<>();

        for (DCCLineItem dccLn : dccLnList) {
            for (tb_PurchaseOrderUPL upl : uplList) {
                boolean condition = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                        ? (dccLn.getUplLineNumber().equals(upl.getUplLine()) &&
                        upl.getPoLineNumber().equals(dccLn.getLineNumber()) &&
                        upl.getPoNumber().equals(dcc.getPoNumber()))
                        : (purchaseOrder.getLineNumber().equals(dccLn.getLineNumber()) &&
                        purchaseOrder.getPoNumber().equals(dcc.getPoNumber()));
                if (!condition) {
                    logger.debug("No matching UPL record for DCC recordNo: {}, poNumber: {}, poLineNumber: {}, uplLine: {}",
                            dcc.getRecordNo(), dcc.getPoNumber(), dccLn.getLineNumber(), dccLn.getUplLineNumber());
                    continue;
                }

                DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();
                populateDccFields(dto, dcc, dateFormat, latestApprovalRequest);
                populateLineItemFields(dto, dccLn, dateFormat, loggedInvalidLinkIds);
                populatePurchaseOrderAndUplFields(dto, dccLn, purchaseOrder, upl);
                calculateQuantitiesAndApprovals(dto, dcc, purchaseOrder, upl, dccLnByUplLineNumber, latestApprovalRequest);

                dtos.add(dto);
            }
        }

        processedRecordNos.add(dcc.getRecordNo());
        return dtos;
    }

    private void populateDccFields(DccPOCombinedViewDTO dto, DCC dcc, SimpleDateFormat dateFormat,
                                   TbCategoryApprovalRequests latestApprovalRequest) {
        dto.setDccRecordNo(dcc.getRecordNo());
        dto.setDccPoNumber(dcc.getPoNumber());
        dto.setDccVendorName(dcc.getVendorName());
        dto.setDccVendorEmail(dcc.getVendorEmail());
        dto.setDccProjectName(dcc.getProjectName());
//        dto.setNewProjectName(dcc.getNewProjectName());
        dto.setDccAcceptanceType(dcc.getAcceptanceType());
        dto.setDccStatus(dcc.getStatus());
        dto.setDccCreatedDate(dcc.getCreatedDate() != null ? dateFormat.format(dcc.getCreatedDate()) : null);
        dto.setVendorComment(dcc.getVendorComment());
        dto.setDccId(dcc.getDccId());
        dto.setDccCurrency(dcc.getCurrency());
        dto.setCreatedBy(dcc.getCreatedBy());
        dto.setCreatedByName(dcc.getCreatedBy());

        if (latestApprovalRequest != null && latestApprovalRequest.getApprovedDate() != null) {
            Date approvedDate = Date.from(latestApprovalRequest.getApprovedDate().atZone(ZoneId.of("Africa/Nairobi")).toInstant());
            dto.setDateApproved(dateFormat.format(approvedDate));
        }
    }

    private void populateLineItemFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn, SimpleDateFormat dateFormat,
                                        Set<Long> loggedInvalidLinkIds) {
        dto.setLnRecordNo(dccLn.getRecordNo());
        dto.setLnProductName(dccLn.getProductName());
        dto.setLnProductSerialNo(dccLn.getSerialNumber());
        dto.setLnDeliveredQty(dccLn.getDeliveredQty());
        dto.setLnLocationName(dccLn.getLocationName());
        dto.setLnInserviceDate(dccLn.getDateInService() != null ? dateFormat.format(dccLn.getDateInService()) : null);
        dto.setLnUnitPrice(dccLn.getUnitPrice() != null ? dccLn.getUnitPrice() : 0.0);
        dto.setLnScopeOfWork(dccLn.getScopeOfWork());
        dto.setLnRemarks(dccLn.getRemarks());
        dto.setLinkId(dccLn.getLinkId());
//        dto.setLinkId(parseLinkId(dccLn.getLinkId(), dccLn.getRecordNo(), loggedInvalidLinkIds));
        dto.setTagNumber(dccLn.getTagNumber());
        dto.setLineNumber(dccLn.getLineNumber());
        dto.setActualItemCode(dccLn.getActualItemCode());
        dto.setUplLineNumber(dccLn.getUplLineNumber());
        dto.setpoAcceptanceQty(dccLn.getpoAcceptanceQty());
    }

    private void populatePurchaseOrderAndUplFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn,
                                                   tbPurchaseOrder purchaseOrder, tb_PurchaseOrderUPL upl) {
        dto.setPoId(purchaseOrder.getPoNumber());
//        dto.setProjectName(purchaseOrder.getProjectName());
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
        dto.setPoLineDescription(purchaseOrder.getPoLineDescription());
        dto.setUplLineQuantity(upl.getUplLineQuantity());
        dto.setUplLineItemCode(upl.getUplLineItemCode());
        dto.setUplLineDescription(upl.getUplLineDescription());
        dto.setUnitOfMeasure(upl.getUom());
        dto.setActiveOrPassive(upl.getActiveOrPassive());
        dto.setItemCode(upl.getUplLineItemCode());
        dto.setItemPartNumber(upl.getPoLineItemCode());
    }

    private void calculateQuantitiesAndApprovals(DccPOCombinedViewDTO dto, DCC dcc, tbPurchaseOrder purchaseOrder,
                                                 tb_PurchaseOrderUPL upl, Map<String, List<DCCLineItem>> dccLnByUplLineNumber,
                                                 TbCategoryApprovalRequests latestApprovalRequest) {
        // Calculate totalDelivered
        double totalDelivered = upl.getUplLine() != null && !upl.getUplLine().isEmpty()
                ? tbDccLnRepository.findByPoIdAndLineNumberAndUplLineNumber(
                        upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine()).stream()
                .filter(d -> d.getDeliveredQty() != null)
                .filter(d -> {
                    DCC dccForLine = tbDccRepository.findByRecordNo(Long.parseLong(d.getDccId())).orElse(null);
                    return dccForLine != null && !Arrays.asList("incomplete", "rejected").contains(dccForLine.getStatus());
                })
                .mapToDouble(DCCLineItem::getDeliveredQty)
                .sum()
                : 0.0;
        dto.setUPLACPTRequestValue(totalDelivered);

        // Calculate POAcceptanceQty
//        double totalExpected = tbPurchaseOrderUplRepository.findByPoNumberAndPoLineNumberAndUplLine(
//                        upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine())
//                .stream()
//                .filter(u -> u.getPoLineQuantity() != null && u.getPoLineUnitPrice() != null)
//                .mapToDouble(u -> u.getPoLineQuantity() * u.getPoLineUnitPrice())
//                .sum();
//        totalExpected = totalExpected != 0 ? totalExpected : 1.0;
//        dto.setPOAcceptanceQty(totalExpected != 0 ? totalDelivered / totalExpected : 0.0);

        // Calculate POLineAcceptanceQty
        List<tb_PurchaseOrderUPL> allUplForPoLine = tbPurchaseOrderUplRepository.findByPoNumberAndPoLineNumber(
                upl.getPoNumber(), upl.getPoLineNumber());
        double poLineAcceptanceQty = allUplForPoLine.stream()
                .filter(u -> u.getUplLineQuantity() != null && u.getUplLineQuantity() > 0)
                .filter(u -> u.getPoLineQuantity() != null && u.getPoLineUnitPrice() != null)
                .mapToDouble(u -> {
                    double denominator = u.getPoLineQuantity() * u.getPoLineUnitPrice();
                    if (denominator == 0) {
                        logger.debug("Skipping record due to zero denominator - uplLine: {}, poLineQuantity: {}, poLineUnitPrice: {}",
                                u.getUplLine(), u.getPoLineQuantity(), u.getPoLineUnitPrice());
                        return 0.0;
                    }
                    double numerator = u.getUplLineQuantity() * u.getPoLineQuantity();
                    double result = numerator / denominator;
                    logger.debug("POLineAcceptanceQty calculation - uplLine: {}, uplLineQuantity: {}, poLineQuantity: {}, poLineUnitPrice: {}, result: {}",
                            u.getUplLine(), u.getUplLineQuantity(), u.getPoLineQuantity(), u.getPoLineUnitPrice(), result);
                    return result;
                })
                .sum();
        dto.setPOLineAcceptanceQty(poLineAcceptanceQty);

        // Calculate poPendingQuantity
        boolean exists = tbDccLnRepository.existsByPoIdAndLineNumberAndUplLineNumber(
                upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine());
        double poPendingQuantity = exists ? poLineAcceptanceQty : (upl.getPoLineQuantity() != null ? upl.getPoLineQuantity() : 0.0);
        dto.setPoPendingQuantity(poPendingQuantity);

        // Set uplPendingQuantity
        double uplPending = upl.getUplLineQuantity() != null ? upl.getUplLineQuantity() - totalDelivered : 0.0;
        dto.setUplPendingQuantity(uplPending > 0 ? uplPending : 0.0);

        if (latestApprovalRequest != null) {
            calculateApprovalFields(dto, latestApprovalRequest);
        } else {
            logger.debug("No approval request found for DCC ID: {}. Setting default approval fields.", dcc.getRecordNo());
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setApproverComment(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging("0 days 0 hrs 0 mins");
        }
    }




    private void calculateApprovalFields(DccPOCombinedViewDTO dto, TbCategoryApprovalRequests latestApprovalRequest) {
        List<String> validRequestStatuses = Arrays.asList("pending", "returned");
        List<String> validApprovalStatuses = Arrays.asList("pending", "readyForApproval", "request-info");
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime nowLocal = now.toLocalDateTime();

        // Step 1: Fetch all related requests and approvals without initial filtering
        List<TbCategoryApprovalRequests> allRelatedRequests = tbCategoryApprovalRequestsRepository
                .findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(latestApprovalRequest.getAcceptanceRequestRecordNo());
        List<TbCategoryApprovals> allRelatedApprovals = allRelatedRequests.stream()
                .flatMap(request -> tbCategoryApprovalsRepository.findByApprovalRecordId(request.getRecordNo()).stream())
                .collect(Collectors.toList());

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
                .filter(a -> Arrays.asList("pending", "request-info").contains(a.getStatus()) && validApprovalStatuses.contains(a.getApprovalStatus()))
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

        // Handle request-info case
        if ("request-info".equals(latestApprovalRequest.getStatus())) {
            logger.debug("Approval request recordNo={} has status 'request-info'", latestApprovalRequest.getRecordNo());

            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                    .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
                    .filter(a -> allRelatedRequests.stream().anyMatch(r -> "request-info".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
                    .collect(Collectors.toList());

            dto.setApprovalCount((long) filteredApprovals.size());
            dto.setPendingApprovers(currentApproverName);

            // Calculate userAging1: Current period for request-info
            long userAging1Minutes = allRelatedApprovals.stream()
                    .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                    .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
                    .filter(a -> a.getRecordDateTime() != null)
                    .mapToLong(a -> {
                        long currentMinutes = Duration.between(a.getRecordDateTime(), nowLocal).toMinutes();
                        logger.debug("Current period for approvalId={} by {}: {} minutes (from {} to now)",
                                a.getApprovalId(), a.getApproverName(), currentMinutes, a.getRecordDateTime());
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

//        // Handle request-info casee
//        if ("request-info".equals(latestApprovalRequest.getStatus())) {
////            logger.debug("Approval request recordNo={} has status 'request-info'; setting approvalCount=0 and pendingApprovers=null",
////                    latestApprovalRequest.getRecordNo());
//            logger.debug("Approval request recordNo={} has status 'request-info'", latestApprovalRequest.getRecordNo());
//
////            dto.setApprovalCount(0L);
////            dto.setPendingApprovers(null);
//
//            List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
//                    .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "request-info").contains(a.getApprovalStatus()))
//                    .filter(a -> allRelatedRequests.stream().anyMatch(r -> "request-info".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
//                    .collect(Collectors.toList());
//
//
//            dto.setApprovalCount((long) filteredApprovals.size());
//
//
//
//            String currentApproverNamed = allRelatedApprovals.stream()
//                    .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
////                    .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
//                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
//                    .findFirst()
//                    .map(TbCategoryApprovals::getApproverName)
//                    .orElse(null);
//
//            dto.setPendingApprovers(currentApproverNamed);
//
//            String currentApproverName = allRelatedApprovals.stream()
//                    .filter(a -> a.getApprovalRecordId().equals(latestApprovalRequest.getRecordNo()))
//                    .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
////                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
//                    .findFirst()
//                    .map(TbCategoryApprovals::getApproverName)
//                    .orElse(null);
//
//            // Step 2: Calculate totalPausedUserAgingMinutes for request-info
//            long totalPausedUserAgingMinutes = allRelatedApprovals.stream()
//                    .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
//                    .filter(a -> currentApproverName != null && currentApproverName.equals(a.getApproverName()))
//                    .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
//                    .mapToLong(a -> {
//                        long pausedMinutes = Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes();
//                        logger.debug("Paused period for approvalId={} by {} : {} minutes (from {} to {})",
//                                a.getApprovalId(), a.getApproverName(), pausedMinutes, a.getRecordDateTime(), a.getApprovedDate());
//                        return Math.max(pausedMinutes, 0);
//                    })
//                    .sum();
//
//            //  Set userAging (only paused for request-info)
//            dto.setUserAging(String.format("%d days %d hrs %d mins",
//                    totalPausedUserAgingMinutes / 1440, (totalPausedUserAgingMinutes / 60) % 24, totalPausedUserAgingMinutes % 60));
//
//            dto.setTotalAging(totalAging);
//            dto.setApproverComment(approverComment);
//            return;
//        }

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
        List<TbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus()) && Arrays.asList("pending", "readyForApproval").contains(a.getApprovalStatus()))
                .filter(a -> allRelatedRequests.stream().anyMatch(r -> "pending".equals(r.getStatus()) && r.getRecordNo().equals(a.getApprovalRecordId())))
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
    public static class DccPOFetchResult {
        private final List<DccPOCombinedViewDTO> data;
        private final Long totalRecordsInDb;
        private final Long totalFilteredRecords;

        public DccPOFetchResult(List<DccPOCombinedViewDTO> data, Long totalFilteredRecords, Long totalRecordsInDb) {
            this.data = data;
            this.totalRecordsInDb = totalRecordsInDb;
            this.totalFilteredRecords = totalFilteredRecords;
        }

        public List<DccPOCombinedViewDTO> getData() { return data; }
        public Long getTotalRecordsInDb() { return totalRecordsInDb; }
        public Long getTotalFilteredRecords() { return totalFilteredRecords; }
    }

    private String mapColumnToDbField(String columnName) {
        if (columnName == null) return null;
        switch (columnName.toLowerCase()) {
            case "recordno": return "recordNo";
            case "dccponumber": return "poNumber";
            case "newprojectname": return "newProjectName";
            case "dccacceptancetype": return "acceptanceType";
            case "dccstatus": return "status";
            case "dcccreateddate": return "createdDate";
            case "vendorcomment": return "vendorComment";
            case "dccid": return "dccId";
            case "poid": return "poNumber";
            case "projectname": return "projectName";
            case "supplierid":
            case "vendornumber": return "vendorNumber";
            case "vendorname": return "vendorName";
            case "createdby": return "createdBy";
            case "createdbyname": return "createdBy";
            case "vendoremail": return "vendorEmail";
            default: return null;
        }
    }

    private Long parseLinkId(String linkIdStr, Long recordNo, Set<Long> loggedInvalidLinkIds) {
        try {
            if (linkIdStr != null && !linkIdStr.trim().isEmpty()) {
                return Long.valueOf(linkIdStr);
            } else if (!loggedInvalidLinkIds.contains(recordNo)) {
                loggedInvalidLinkIds.add(recordNo);
            }
        } catch (NumberFormatException ex) {
            if (!loggedInvalidLinkIds.contains(recordNo)) {
                loggedInvalidLinkIds.add(recordNo);
            }
        }
        return null;
    }

    private double parsePoOrderQuantity(tbPurchaseOrder purchaseOrder) {
        String poQtyNew = String.valueOf(purchaseOrder.getPoQtyNew());
        return (poQtyNew != null && !poQtyNew.isEmpty()) ? Double.parseDouble(poQtyNew) : purchaseOrder.getAmountDueLine();
    }
}
