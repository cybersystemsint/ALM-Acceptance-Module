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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service class to fetch and combine DCC, Purchase Order, UPL, Line Item, and Approval data
 * into DccPOCombinedViewDTO objects, matching the dccPOCombinedView MySQL view.
 * Applies pagination on distinct dccRecordNo values to avoid truncating related data.
 * Enforces that every Tb_DCC record has a valid poNumber in tb_PurchaseOrder.
 */
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Public method to asynchronously retrieve paginated DccPOCombinedViewDTO objects.
     * @param supplierId Supplier ID to filter DCC records (use "0" for all suppliers).
     * @param page Page number (1-based).
     * @param size Number of parent DCC records per page.
     * @param columnName Column to filter on (mapped to database field).
     * @param searchQuery Search term for filtering.
     * @return CompletableFuture containing the list of DTOs.
     */
    public CompletableFuture<List<DccPOCombinedViewDTO>> getDccPOCombinedView(String supplierId, int page, int size, String columnName, String searchQuery) {
        try {
            logger.info("Starting retrieval of DCC PO Combined View with supplierId: {}, page: {}, size: {}, columnName: {}, searchQuery: {}",
                    supplierId, page, size, columnName, searchQuery);
            return CompletableFuture.supplyAsync(() -> fetchDccPOCombinedView(supplierId, page, size, columnName, searchQuery));
        } catch (Exception ex) {
            logger.error("Error initiating retrieval of DCC PO Combined View", ex);
            throw new DccPOProcessingException("Failed to initiate DCC PO Combined View retrieval", ex);
        }
    }

    /**
     * Fetches paginated DCC records and their related data, building DccPOCombinedViewDTO objects.
     * Paginates only distinct dccRecordNo values to match the view's grouping.
     * Enforces that every Tb_DCC record has a valid poNumber in tb_PurchaseOrder.
     * Aligns calculations with the dccPOCombinedView MySQL view.
     * @param supplierId Supplier ID filter.
     * @param page Page number (1-based).
     * @param size Number of parent DCC records per page.
     * @param columnName Column to filter on.
     * @param searchQuery Search term for filtering.
     * @return List of DccPOCombinedViewDTO objects.
     */
    @Async("taskExecutor")
    @Cacheable(value = "dccPOCombinedViewCache", unless = "#result == null || #result.isEmpty()")
    private List<DccPOCombinedViewDTO> fetchDccPOCombinedView(String supplierId, int page, int size, String columnName, String searchQuery) {
        try {
            // Initialize result list and set to track invalid linkId logs
            List<DccPOCombinedViewDTO> result = new ArrayList<>();
            Set<Long> loggedInvalidLinkIds = new HashSet<>();

            // Validate input parameters
            if (page < 1 || size < 1) {
                logger.error("Invalid pagination parameters: page={}, size={}", page, size);
                throw new IllegalArgumentException("Page and size must be positive");
            }

            // Build dynamic WHERE clause for filtering
            StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (!"0".equals(supplierId)) {
                whereClause.append(" AND p.vendorNumber = ?");
                params.add(supplierId);
            }
            if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()) {
                String dbColumnName = mapColumnToDbField(columnName);
                if (dbColumnName != null) {
                    whereClause.append(" AND LOWER(").append(dbColumnName).append(") LIKE ?");
                    params.add("%" + searchQuery.toLowerCase() + "%");
                }
            }

            // SCount total distinct dccRecordNo values
            String countSql = "SELECT COUNT(DISTINCT d.recordNo) FROM tb_DCC d JOIN tb_PurchaseOrder p ON d.poNumber = p.poNumber" +
                    whereClause.toString();
            Long totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
            if (totalRecords == null || totalRecords == 0) {
                logger.info("No DCC records found after filtering with supplierId: {}, columnName: {}, searchQuery: {}",
                        supplierId, columnName, searchQuery);
                return result;
            }

            // Step 2: Fetch paginated distinct dccRecordNo values
            params.add(size);
            params.add((page - 1) * size);
            String dccSql = "SELECT DISTINCT d.recordNo FROM tb_DCC d JOIN tb_PurchaseOrder p ON d.poNumber = p.poNumber" +
                    whereClause.toString() + " ORDER BY d.recordNo DESC LIMIT ? OFFSET ?";
            long startTime = System.nanoTime();
            List<Long> paginatedRecordNos = jdbcTemplate.query(dccSql, params.toArray(), (rs, rowNum) -> rs.getLong("recordNo"));
            logger.debug("Paginated dccRecordNo query took {} ms", (System.nanoTime() - startTime) / 1_000_000);
            if (paginatedRecordNos.isEmpty()) {
                logger.info("No DCC records found for page {} with size {}", page, size);
                return result;
            }

            // Step 3: Fetch full DCC details for paginated recordNos
            List<DCC> dccList = tbDccRepository.findByRecordNoIn(paginatedRecordNos);
            if (dccList.isEmpty()) {
                logger.warn("No DCC records found for recordNos: {}. Possible data inconsistency.", paginatedRecordNos);
                return result;
            }

            // Validate that all DCC records have a valid poNumber
            List<DCC> invalidDccRecords = dccList.stream()
                    .filter(dcc -> dcc.getPoNumber() == null || dcc.getPoNumber().isEmpty())
                    .collect(Collectors.toList());
            if (!invalidDccRecords.isEmpty()) {
                logger.error("Found {} DCC records with missing or invalid poNumber: {}",
                        invalidDccRecords.size(),
                        invalidDccRecords.stream().map(DCC::getRecordNo).collect(Collectors.toList()));
                throw new DccPOProcessingException("Invalid DCC records detected with missing poNumber");
            }

            // Batch fetch related data
            Set<String> poNumbers = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
            Map<String, List<tbPurchaseOrder>> purchaseOrderMap = tbPurchaseOrderRepository.findByPoNumberIn(poNumbers)
                    .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));
            Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUplRepository.findByPoNumberIn(poNumbers)
                    .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));
            Map<String, List<DCCLineItem>> dccLnMap = tbDccLnRepository.findByDccIdIn(
                            paginatedRecordNos.stream().map(String::valueOf).collect(Collectors.toList()))
                    .stream().collect(Collectors.groupingBy(DCCLineItem::getDccId));

            // Step 5: Build DccPOCombinedViewDTO objects
            SimpleDateFormat dateFormat = new SimpleDateFormat("d-MMM-yyyy");
            for (DCC dcc : dccList) {
                // Verify Purchase Order exists (enforced by JOIN, but added for robustness)
                List<tbPurchaseOrder> purchaseOrderList = purchaseOrderMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                if (purchaseOrderList.isEmpty()) {
                    logger.error("No Purchase Order found for poNumber: {} in DCC record with recordNo: {}. Data inconsistency detected.",
                            dcc.getPoNumber(), dcc.getRecordNo());
                    throw new DccPOProcessingException("Missing Purchase Order for DCC record: " + dcc.getRecordNo());
                }
                tbPurchaseOrder purchaseOrder = purchaseOrderList.get(0);

                // Fetch approval requests (mimics LatestApprovalRequests CTE)
                List<TbCategoryApprovalRequests> approvalRequests = tbCategoryApprovalRequestsRepository
                        .findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(dcc.getRecordNo());
                TbCategoryApprovalRequests latestApprovalRequest = approvalRequests.isEmpty() ? null : approvalRequests.get(0);

                // Fetch line items
                List<DCCLineItem> dccLnList = dccLnMap.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
                if (dccLnList.isEmpty()) {
                    logger.warn("No DCC_LN records found for DCC ID: {}. Skipping DTO creation.", dcc.getRecordNo());
                    continue; // Optionally create partial DTO if required
                }

                // Fetch UPLs
                List<tb_PurchaseOrderUPL> uplList = uplMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
                if (uplList.isEmpty()) {
                    logger.warn("No PurchaseOrderUPL records found for PO Number: {}. Skipping DTO creation.", dcc.getPoNumber());
                    continue; // Optionally create partial DTO if required
                }

                // Precompute DCC Line Items by UPL key for efficiency
                Map<String, List<DCCLineItem>> dccLnByUplLineNumber = new HashMap<>();
                for (tb_PurchaseOrderUPL upl : uplList) {
                    String key = upl.getPoLineNumber() + "-" + upl.getPoLineNumber() + "-" + upl.getPoNumber();
                    dccLnByUplLineNumber.computeIfAbsent(key, k -> tbDccLnRepository.findByUplLineNumberAndLineNumberAndPoId(
                            upl.getPoLineNumber(), upl.getPoLineNumber(), upl.getPoNumber()));
                }

                // Iterate over line items and UPLs to build DTOs
                for (DCCLineItem dccLn : dccLnList) {
                    for (tb_PurchaseOrderUPL upl : uplList) {
                        // Match line item with UPL or Purchase Order (mimics view's CASE condition)
                        boolean condition = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                                ? (dccLn.getUplLineNumber().equals(upl.getUplLine()) &&
                                upl.getPoLineNumber().equals(dccLn.getLineNumber()) &&
                                upl.getPoNumber().equals(dcc.getPoNumber()))
                                : (purchaseOrder.getLineNumber().equals(dccLn.getLineNumber()) &&
                                purchaseOrder.getPoNumber().equals(dcc.getPoNumber()));
                        if (!condition) continue;

                        DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();

                        // Set DCC fields
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

                        // Set approval date
                        if (latestApprovalRequest != null && latestApprovalRequest.getApprovedDate() != null) {
                            Date approvedDate = Date.from(latestApprovalRequest.getApprovedDate().atZone(ZoneId.of("UTC")).toInstant());
                            dto.setDateApproved(dateFormat.format(approvedDate));
                        }

                        // Set Line Item fields
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
                        dto.setPoId(purchaseOrder.getPoNumber());

                        // Set Purchase Order and UPL fields
                        dto.setProjectName(purchaseOrder.getProjectName());
                        dto.setSupplierId(purchaseOrder.getVendorNumber());
                        dto.setVendorNumber(purchaseOrder.getVendorNumber());
                        dto.setVendorName(purchaseOrder.getVendorName());
                        dto.setCreatedBy(dcc.getCreatedBy());
                        dto.setCreatedByName(dcc.getCreatedBy());
                        dto.setItemPartNumber(dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty()
                                ? upl.getUplLineItemCode() : purchaseOrder.getItemPartNumber());
                        double poOrderQty = (dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty())
                                ? upl.getPoLineQuantity()
                                : parsePoOrderQuantity(purchaseOrder);
                        dto.setPoOrderQuantity(poOrderQty);
                        dto.setPoLineDescription(dccLn.getUplLineNumber() != null && !dccLn.getUplLineNumber().isEmpty()
                                ? upl.getUplLineDescription() : purchaseOrder.getPoLineDescription());
                        dto.setUplLineQuantity(upl.getUplLineQuantity());
                        dto.setUplLineItemCode(upl.getUplLineItemCode());
                        dto.setUplLineDescription(upl.getUplLineDescription());
                        dto.setUnitOfMeasure(upl.getUom());
                        dto.setActiveOrPassive(upl.getActiveOrPassive());

                        // Calculate quantities (aligned with view)
                        String key = upl.getPoLineNumber() + "-" + upl.getPoLineNumber() + "-" + upl.getPoNumber();
                        double totalDelivered = upl.getUplLineQuantity() > 0
                                ? dccLnByUplLineNumber.getOrDefault(key, Collections.emptyList()).stream()
                                .filter(d -> !Arrays.asList("incomplete", "rejected").contains(dcc.getStatus()))
                                .mapToDouble(DCCLineItem::getDeliveredQty)
                                .sum()
                                : 0.0;
                        dto.setUPLACPTRequestValue(totalDelivered);

                        // POAcceptanceQty: Use specific UPL denominator
                        double specificDenominator = upl.getUplLineQuantity() > 0
                                ? dccLnByUplLineNumber.getOrDefault(key, Collections.emptyList()).stream()
                                .mapToDouble(d -> upl.getPoLineQuantity() * upl.getUnitPrice())
                                .sum()
                                : 0.0;
                        dto.setPOAcceptanceQty(specificDenominator != 0 ? totalDelivered / specificDenominator : 0.0);

                        // POLineAcceptanceQty
                        double poLineAcceptanceQty = uplList.stream()
                                .filter(u -> u.getPoNumber().equals(upl.getPoNumber()) && u.getPoLineNumber().equals(upl.getPoLineNumber()))
                                .mapToDouble(u -> {
                                    double denominator = u.getPoLineQuantity() * u.getUnitPrice();
                                    return denominator != 0 ? (u.getUplLineQuantity() * u.getPoLineQuantity()) / denominator : 0.0;
                                })
                                .sum();
                        dto.setPOLineAcceptanceQty(poLineAcceptanceQty);

                        // poPendingQuantity: Corrected uplLineNumber
                        boolean exists = tbDccLnRepository.existsByPoIdAndLineNumberAndUplLineNumber(
                                upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine());
                        dto.setPoPendingQuantity(exists ? poLineAcceptanceQty : upl.getPoLineQuantity());

                        // uplPendingQuantity
                        dto.setUplPendingQuantity(upl.getUplLineQuantity() - totalDelivered);

                        // Approval and aging calculations
                        if (latestApprovalRequest != null) {
                            // Fetch approvals with view's criteria
                            List<TbCategoryApprovals> filteredApprovals = tbCategoryApprovalsRepository
                                    .findByApprovalRecordIdAndStatusAndApprovalStatusIn(
                                            latestApprovalRequest.getRecordNo(), "pending",
                                            Arrays.asList("pending", "readyForApproval", "request-info"))
                                    .stream()
                                    .filter(a -> "pending".equals(latestApprovalRequest.getStatus()) || "request-info".equals(latestApprovalRequest.getStatus()))
                                    .collect(Collectors.toList());
                            dto.setApprovalCount((long) filteredApprovals.size());

                            // Pending approvers
                            Optional<TbCategoryApprovals> readyForApproval = filteredApprovals.stream()
                                    .filter(a -> "readyForApproval".equals(a.getApprovalStatus()))
                                    .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId)) // Fixed: Use comparing for Long
                                    .findFirst();
                            List<TbCategoryApprovals> pendingApproversList = filteredApprovals.stream()
                                    .filter(a -> Arrays.asList("pending", "readyForApproval", "request-info").contains(a.getApprovalStatus()))
                                    .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId)) // Fixed: Use comparing for Long
                                    .collect(Collectors.toList());
                            String pendingApproverName = readyForApproval
                                    .map(TbCategoryApprovals::getApproverName)
                                    .orElseGet(() -> pendingApproversList.stream()
                                            .findFirst()
                                            .map(TbCategoryApprovals::getApproverName)
                                            .orElse(null));
                            dto.setPendingApprovers(pendingApproverName);

                            // Approver comments
                            List<TbCategoryApprovals> comments = tbCategoryApprovalsRepository
                                    .findByApprovalRecordIdAndApprovalStatusNotIn(
                                            latestApprovalRequest.getRecordNo(),
                                            Arrays.asList("pending", "readyForApproval"))
                                    .stream()
                                    .filter(a -> a.getComments() != null)
                                    .sorted(Comparator.comparing(TbCategoryApprovals::getApprovalId).reversed())
                                    .collect(Collectors.toList());
                            dto.setApproverComment(comments.isEmpty() ? null : comments.get(0).getComments());

                            // User aging: Use all approvals
                            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
                            List<TbCategoryApprovals> allApprovals = tbCategoryApprovalsRepository
                                    .findByApprovalRecordId(latestApprovalRequest.getRecordNo());
                            LocalDateTime maxDate = allApprovals.stream()
                                    .map(a -> a.getApprovedDate() != null ? a.getApprovedDate() : a.getRecordDateTime())
                                    .max(LocalDateTime::compareTo)
                                    .orElse(now);
                            long minutes = Duration.between(maxDate, now).toMinutes();
                            dto.setUserAging(String.format("%d days %d hrs %d mins", minutes / 1440, (minutes / 60) % 24, minutes % 60));

                            // Total aging
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

                        result.add(dto);
                    }
                }
            }

            logger.info("Completed retrieval of DCC PO Combined View with {} DTOs for page {} with size {}", result.size(), page, size);
            return result;
        } catch (DataAccessException ex) {
            logger.error("Database error in fetchDccPOCombinedView for supplierId: {}, page: {}, size: {}", supplierId, page, size, ex);
            throw new DccPOProcessingException("Database error fetching DCC PO Combined View", ex);
        } catch (DccPOProcessingException ex) {
            throw ex; // Re-throw custom exceptions for data inconsistencies
        } catch (Exception ex) {
            logger.error("Unexpected error in fetchDccPOCombinedView for supplierId: {}, page: {}, size: {}", supplierId, page, size, ex);
            throw new DccPOProcessingException("Failed to fetch DCC PO Combined View", ex);
        }
    }

    /**
     * Maps DTO column names to database fields for filtering.
     * @param columnName DTO column name.
     * @return Corresponding database field name or null if invalid.
     */
    private String mapColumnToDbField(String columnName) {
        if (columnName == null) return null;
        switch (columnName.toLowerCase()) {
            case "dccponumber": return "d.poNumber";
            case "newprojectname": return "p.newProjectName";
            case "dccacceptancetype": return "d.acceptanceType";
            case "dccstatus": return "d.status";
            case "dcccreateddate": return "d.createdDate";
            case "vendorcomment": return "d.vendorComment";
            case "dccid": return "d.dccId";
            case "poid": return "p.poNumber";
            case "projectname": return "p.projectName";
            case "supplierid":
            case "vendornumber": return "p.vendorNumber";
            case "vendorname": return "p.vendorName";
            case "createdby": return "d.createdBy";
            case "createdbyname": return "d.createdBy";
            case "vendoremail": return "d.vendorEmail";
            default: return null;
        }
    }

    /**
     * Parses linkId from string to Long, handling invalid cases.
     * @param linkIdStr Link ID string from DCCLineItem.
     * @param recordNo Record number for logging.
     * @param loggedInvalidLinkIds Set to track logged invalid linkIds.
     * @return Parsed Long value or null if invalid.
     */
    private Long parseLinkId(String linkIdStr, Long recordNo, Set<Long> loggedInvalidLinkIds) {
        try {
            if (linkIdStr != null && !linkIdStr.trim().isEmpty()) {
                return Long.valueOf(linkIdStr);
            } else if (!loggedInvalidLinkIds.contains(recordNo)) {
                logger.warn("Empty or invalid linkId for DCCLineItem with recordNo: {}", recordNo);
                loggedInvalidLinkIds.add(recordNo);
            }
        } catch (NumberFormatException ex) {
            if (!loggedInvalidLinkIds.contains(recordNo)) {
                logger.warn("Invalid linkId format for DCCLineItem with recordNo: {}. Value: {}", recordNo, linkIdStr);
                loggedInvalidLinkIds.add(recordNo);
            }
        }
        return null;
    }

    /**
     * Parses PO order quantity from tbPurchaseOrder, handling poQtyNew and fallback.
     * @param purchaseOrder Purchase Order entity.
     * @return Parsed quantity as double.
     */
    private double parsePoOrderQuantity(tbPurchaseOrder purchaseOrder) {
        String poQtyNew = String.valueOf(purchaseOrder.getPoQtyNew());
        return (poQtyNew != null && !poQtyNew.isEmpty()) ? Double.parseDouble(poQtyNew) : purchaseOrder.getPoOrderQuantity();
    }
}
