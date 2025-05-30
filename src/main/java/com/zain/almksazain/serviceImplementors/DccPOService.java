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

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    public CompletableFuture<DccPOFetchResult> getDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery) {
        try {
            logger.info("Starting retrieval of DCC PO Combined View with supplierId: {}, pendingApprovers: {}, page: {}, size: {}, columnName: {}, searchQuery: {}",
                    supplierId, pendingApprovers, page, size, columnName, searchQuery);
            return CompletableFuture.supplyAsync(() -> fetchDccPOCombinedView(supplierId, pendingApprovers, page, size, columnName, searchQuery));
        } catch (Exception ex) {
            logger.error("Error initiating retrieval of DCC PO Combined View", ex);
            throw new DccPOProcessingException("Failed to initiate DCC PO Combined View retrieval", ex);
        }
    }

    @Async("taskExecutor")
    @Cacheable(value = "dccPOCombinedViewCache",
            key = "{#supplierId, #pendingApprovers, #page, #size, #columnName, #searchQuery}",
            unless = "#result.data == null || #result.data.isEmpty()")
    private DccPOFetchResult fetchDccPOCombinedView(String supplierId, String pendingApprovers, int page, int size, String columnName, String searchQuery) {
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

            // Batch fetch all related data
            Set<String> poNumbersSet = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
            List<String> poNumbers = new ArrayList<>(poNumbersSet);
            List<Long> dccIds = dccList.stream().map(DCC::getRecordNo).collect(Collectors.toList());
            Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                    .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));
            Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository.findByPoNumberIn(poNumbers)
                    .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));
            Map<Long, List<DCCLineItem>> dccLnMap = tbDccLnRepository.findByDccIdIn(dccIds.stream().map(String::valueOf).collect(Collectors.toList()))
                    .stream().collect(Collectors.groupingBy(dccLn -> Long.parseLong(dccLn.getDccId())));
            Map<Long, List<TbCategoryApprovalRequests>> approvalRequestMap = tbCategoryApprovalRequestsRepository
                    .findByAcceptanceRequestRecordNoIn(dccIds)
                    .stream().collect(Collectors.groupingBy(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo));

            // Precompute DCC Line Items by UPL key
            Map<String, List<DCCLineItem>> dccLnByUplLineNumber = dccLnMap.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(dccLn -> dccLn.getUplLineNumber() + "-" + dccLn.getLineNumber() + "-" + dccLn.getPoId()));

            // Process records in parallel
            Set<Long> processedRecordNos = ConcurrentHashMap.newKeySet();
            Set<Long> loggedInvalidLinkIds = ConcurrentHashMap.newKeySet();
            SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");

            List<DccPOCombinedViewDTO> result = dccList.parallelStream()
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
                        List<TbCategoryApprovalRequests> approvalRequests = approvalRequestMap.getOrDefault(dcc.getRecordNo(), Collections.emptyList());
                        TbCategoryApprovalRequests latestApprovalRequest = approvalRequests.isEmpty() ? null : approvalRequests.get(0);

                        if (dccLnList.isEmpty() || uplList.isEmpty()) {
                            logger.warn("No DCC_LN or UPL records found for DCC ID: {}. Skipping.", dcc.getRecordNo());
                            processedRecordNos.add(dcc.getRecordNo());
                            return Stream.empty();
                        }

                        return buildDccPOCombinedViewDTOs(dcc, purchaseOrder, uplList, dccLnList, latestApprovalRequest,
                                dccLnByUplLineNumber, dateFormat, loggedInvalidLinkIds, processedRecordNos).stream();
                    })
                    .collect(Collectors.toList());

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
        dto.setNewProjectName(dcc.getNewProjectName());
        dto.setDccAcceptanceType(dcc.getAcceptanceType());
        dto.setDccStatus(dcc.getStatus());
        dto.setDccCreatedDate(dcc.getCreatedDate() != null ? dateFormat.format(dcc.getCreatedDate()) : null);
        dto.setVendorComment(dcc.getVendorComment());
        dto.setDccId(dcc.getDccId());
        dto.setDccCurrency(dcc.getCurrency());
        dto.setCreatedBy(dcc.getCreatedBy());
        dto.setCreatedByName(dcc.getCreatedBy());

        if (latestApprovalRequest != null && latestApprovalRequest.getApprovedDate() != null) {
            Date approvedDate = Date.from(latestApprovalRequest.getApprovedDate().atZone(ZoneId.of("UTC")).toInstant());
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
        dto.setLinkId(parseLinkId(dccLn.getLinkId(), dccLn.getRecordNo(), loggedInvalidLinkIds));
        dto.setTagNumber(dccLn.getTagNumber());
        dto.setLineNumber(dccLn.getLineNumber());
        dto.setActualItemCode(dccLn.getActualItemCode());
        dto.setUplLineNumber(dccLn.getUplLineNumber());
    }

    private void populatePurchaseOrderAndUplFields(DccPOCombinedViewDTO dto, DCCLineItem dccLn,
                                                   tbPurchaseOrder purchaseOrder, tb_PurchaseOrderUPL upl) {
        dto.setPoId(purchaseOrder.getPoNumber());
        dto.setProjectName(purchaseOrder.getProjectName());
        dto.setSupplierId(purchaseOrder.getVendorNumber());
        dto.setVendorNumber(purchaseOrder.getVendorNumber());
        dto.setVendorName(purchaseOrder.getVendorName());
        double poOrderQty = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                ? upl.getPoLineQuantity()
                : parsePoOrderQuantity(purchaseOrder);
        dto.setPoLineQuantity(poOrderQty);
        dto.setPoOrderQuantity(poOrderQty);
        dto.setPoLineDescription(purchaseOrder.getPoLineDescription());
//        dto.setPoLineDescription(dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty()
//                ? upl.getUplLineDescription() : purchaseOrder.getPoLineDescription());
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
        double totalExpected = tbPurchaseOrderUplRepository.findByPoNumberAndPoLineNumberAndUplLine(
                        upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine())
                .stream()
                .filter(u -> u.getPoLineQuantity() != null && u.getPoLineUnitPrice() != null)
                .mapToDouble(u -> u.getPoLineQuantity() * u.getPoLineUnitPrice())
                .sum();
        totalExpected = totalExpected != 0 ? totalExpected : 1.0;
        dto.setPOAcceptanceQty(totalExpected != 0 ? totalDelivered / totalExpected : 0.0);

        // FIXED: Calculate POLineAcceptanceQty - This should sum across ALL UPL records for the same PO and PO Line
        // Based on SQL: sum((uplLineQuantity * poLineQuantity) / nullif((poLineQuantity * poLineUnitPrice), 0))
        List<tb_PurchaseOrderUPL> allUplForPoLine = tbPurchaseOrderUplRepository.findByPoNumberAndPoLineNumber(
                upl.getPoNumber(), upl.getPoLineNumber());

        double poLineAcceptanceQty = allUplForPoLine.stream()
                .filter(u -> u.getUplLineQuantity() != null && u.getUplLineQuantity() > 0) // Only include records with uplLineQuantity > 0
                .filter(u -> u.getPoLineQuantity() != null && u.getPoLineUnitPrice() != null)
                .mapToDouble(u -> {
                    double denominator = u.getPoLineQuantity() * u.getPoLineUnitPrice();
                    if (denominator == 0) {
                        logger.debug("Skipping record due to zero denominator - uplLine: {}, poLineQuantity: {}, poLineUnitPrice: {}",
                                u.getUplLine(), u.getPoLineQuantity(), u.getPoLineUnitPrice());
                        return 0.0;
                    }
                    double numerator = u.getUplLineQuantity() * u.getPoLineQuantity(); // This was missing the multiplication
                    double result = numerator / denominator;
                    logger.debug("POLineAcceptanceQty calculation - uplLine: {}, uplLineQuantity: {}, poLineQuantity: {}, poLineUnitPrice: {}, result: {}",
                            u.getUplLine(), u.getUplLineQuantity(), u.getPoLineQuantity(), u.getPoLineUnitPrice(), result);
                    return result;
                })
                .sum();

        dto.setPOLineAcceptanceQty(poLineAcceptanceQty);

        // FIXED: Set poPendingQuantity - This should be different from POLineAcceptanceQty
        // Based on SQL: if no DCC_LN exists for this combination, use poLineQuantity, else use POLineAcceptanceQty
        boolean exists = tbDccLnRepository.existsByPoIdAndLineNumberAndUplLineNumber(
                upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine());

        // The logic should be: if no DCC records exist, pending = poLineQuantity, else pending = calculated acceptance qty
        double poPendingQuantity = exists ? poLineAcceptanceQty : (upl.getPoLineQuantity() != null ? upl.getPoLineQuantity() : 0.0);
        dto.setPoPendingQuantity(poPendingQuantity);

        // Set uplPendingQuantity
        double uplPending = upl.getUplLineQuantity() != null ? upl.getUplLineQuantity() - totalDelivered : 0.0;
        dto.setUplPendingQuantity(uplPending > 0 ? uplPending : 0.0);

        if (latestApprovalRequest != null) {
            calculateApprovalFields(dto, latestApprovalRequest);
        }
    }

    private void calculateApprovalFields(DccPOCombinedViewDTO dto, TbCategoryApprovalRequests latestApprovalRequest) {
        List<TbCategoryApprovals> filteredApprovals = tbCategoryApprovalsRepository
                .findByApprovalRecordIdAndStatusAndApprovalStatusIn(
                        latestApprovalRequest.getRecordNo(), "pending",
                        Arrays.asList("pending", "readyForApproval", "request-info"))
                .stream()
                .filter(a -> "pending".equals(latestApprovalRequest.getStatus()) || "request-info".equals(latestApprovalRequest.getStatus()))
                .collect(Collectors.toList());
        dto.setApprovalCount((long) filteredApprovals.size());

        Optional<TbCategoryApprovals> readyForApproval = filteredApprovals.stream()
                .filter(a -> "readyForApproval".equals(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .findFirst();
        List<TbCategoryApprovals> pendingApproversList = filteredApprovals.stream()
                .filter(a -> Arrays.asList("pending", "readyForApproval", "request-info").contains(a.getApprovalStatus()))
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .collect(Collectors.toList());
        String pendingApproverName = readyForApproval
                .map(TbCategoryApprovals::getApproverName)
                .orElseGet(() -> pendingApproversList.stream()
                        .findFirst()
                        .map(TbCategoryApprovals::getApproverName)
                        .orElse(null));
        dto.setPendingApprovers(pendingApproverName);

        List<TbCategoryApprovals> comments = tbCategoryApprovalsRepository
                .findByApprovalRecordIdAndApprovalStatusNotIn(
                        latestApprovalRequest.getRecordNo(),
                        Arrays.asList("pending", "readyForApproval"))
                .stream()
                .filter(a -> a.getComments() != null)
                .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                .collect(Collectors.toList());
        dto.setApproverComment(comments.isEmpty() ? null : comments.get(0).getComments());

        LocalDateTime now = LocalDateTime.now();
        List<TbCategoryApprovals> allApprovals = tbCategoryApprovalsRepository
                .findByApprovalRecordId(latestApprovalRequest.getRecordNo());
        LocalDateTime maxDate = allApprovals.stream()
                .map(a -> a.getApprovedDate() != null ? a.getApprovedDate() : a.getRecordDateTime())
                .max(LocalDateTime::compareTo)
                .orElse(now);
        long minutes = Duration.between(maxDate, now).toMinutes();
        dto.setUserAging(String.format("%d days %d hrs %d mins", minutes / 1440, (minutes / 60) % 24, minutes % 60));

        long totalMinutes;
        List<TbCategoryApprovals> pendingApprovals = tbCategoryApprovalsRepository
                .findByApprovalRecordIdAndStatusAndApprovalStatus(
                        latestApprovalRequest.getRecordNo(), "pending", "pending")
                .stream()
                .filter(a -> "pending".equals(latestApprovalRequest.getStatus()))
                .collect(Collectors.toList());
        if (pendingApprovals.isEmpty()) {
            LocalDateTime minRecordDateTime = allApprovals.stream()
                    .map(TbCategoryApprovals::getRecordDateTime)
                    .min(LocalDateTime::compareTo)
                    .orElse(now);
            LocalDateTime maxApprovedDate = allApprovals.stream()
                    .map(TbCategoryApprovals::getApprovedDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(now);
            totalMinutes = Duration.between(minRecordDateTime, maxApprovedDate).toMinutes();
        } else {
            LocalDateTime minRecordDateTime = pendingApprovals.stream()
                    .map(TbCategoryApprovals::getRecordDateTime)
                    .min(LocalDateTime::compareTo)
                    .orElse(now);
            totalMinutes = Duration.between(minRecordDateTime, now).toMinutes();
        }
        dto.setTotalAging(String.format("%d days %d hrs %d mins", totalMinutes / 1440, (totalMinutes / 60) % 24, totalMinutes % 60));
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