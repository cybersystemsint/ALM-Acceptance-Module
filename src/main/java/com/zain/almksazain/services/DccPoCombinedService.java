package com.zain.almksazain.services;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.zain.almksazain.controller.AgingReportController;
import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.departmentsdata;
import com.zain.almksazain.model.tbCategoryApprovalRequests;
import com.zain.almksazain.model.tbCategoryApprovals;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.repo.DCCRepository;
import com.zain.almksazain.repo.DccLineRepo;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.deptsrepo;
import com.zain.almksazain.repo.tbPurchaseOrderRepo;
import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
@Service
public class DccPoCombinedService {
   private static final Logger logger = LogManager.getLogger(DccPoCombinedService.class);


    private static final String DATE_FORMAT = "d-MMM-yyyy";

    @Autowired private DCCRepository dccRepository;
    @Autowired private DccLineRepo dccLineRepo;
    @Autowired private tbPurchaseOrderRepo purchaseOrderRepository;
    @Autowired private tbPurchaseOrderUPLRepo tbPurchaseOrderUPLRepo;
    @Autowired private TbCategoryApprovalRequestsRepository tbCategoryApprovalRequestsRepo;
    @Autowired private TbCategoryApprovalsRepository tbCategoryApprovalsRepo;
    @Autowired private UserRepository userAccountRepo;
    @Autowired private deptsrepo tbDepartmentRepo;

   private static final Map<String, ColumnInfo> COLUMN_MAPPINGS = new HashMap<>();
    private static final List<String> CALCULATED_COLUMNS = Arrays.asList(
            "uplacptRequestValue", "userAging", "totalAging", "userAgingInDays",
            "totalAgingInDays", "requestAmountSAR", "approvalCount", "approverComment", "pendingApprovers"
    );

    static {
        // DCC Columns - FIXED: Use actual keys from buildGroupedRow(), not database field names
        COLUMN_MAPPINGS.put("recordNo", new ColumnInfo("recordNo", "numeric", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorComment", new ColumnInfo("vendorComment", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorName", new ColumnInfo("vendorName", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorEmail", new ColumnInfo("vendorEmail", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccId", new ColumnInfo("dccId", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccCreatedDate", new ColumnInfo("dccCreatedDate", "date", EntityType.DCC)); // FIXED
        COLUMN_MAPPINGS.put("poNumber", new ColumnInfo("poNumber", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccAcceptanceType", new ColumnInfo("dccAcceptanceType", "string", EntityType.DCC)); // FIXED
        COLUMN_MAPPINGS.put("dccStatus", new ColumnInfo("dccStatus", "string", EntityType.DCC)); // FIXED
        COLUMN_MAPPINGS.put("createdBy", new ColumnInfo("createdBy", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("createdByName", new ColumnInfo("createdByName", "string", EntityType.DCC)); // FIXED

        // PO Columns
        COLUMN_MAPPINGS.put("projectName", new ColumnInfo("projectName", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("newProjectName", new ColumnInfo("newProjectName", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("supplierId", new ColumnInfo("supplierId", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("poId", new ColumnInfo("poId", "string", EntityType.PURCHASE_ORDER));

        // Line Item Columns - FIXED: Use actual keys from buildGroupedRow()
        COLUMN_MAPPINGS.put("lnLocationName", new ColumnInfo("lnLocationName", "string", EntityType.LINE_ITEM));
        COLUMN_MAPPINGS.put("lnScopeOfWork", new ColumnInfo("lnScopeOfWork", "string", EntityType.LINE_ITEM));
        COLUMN_MAPPINGS.put("lnInserviceDate", new ColumnInfo("lnInserviceDate", "date", EntityType.LINE_ITEM)); // FIXED

        // Approval Request Columns - FIXED
        COLUMN_MAPPINGS.put("dateApproved", new ColumnInfo("dateApproved", "date", EntityType.APPROVAL_REQUEST)); // FIXED

        // Calculated numeric columns
        COLUMN_MAPPINGS.put("requestAmountSAR", new ColumnInfo("requestAmountSAR", "numeric", EntityType.DCC));
    }

    public Map<String, Object> getAgingReport(
            String supplierId, String columnName, String searchQuery, int page, int size) {

            logger.debug("Fetching aging report for supplierId={} page={} size={}", supplierId, page, size);
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            boolean hasFilter = columnName != null && !columnName.trim().isEmpty() &&
                                searchQuery != null && !searchQuery.trim().isEmpty();

            final String onlyStatus = "inprocess";
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));

            // Fast! Only get single page from DB, filtering by supplier in DB if set
            Page<DCC> dccPage = (supplierId != null && !"0".equals(supplierId.trim()))
                ? dccRepository.findAllBySupplierVendorAndStatus(supplierId, onlyStatus, pageable)
                : dccRepository.findAllByStatus(onlyStatus, pageable);

            List<DCC> dccList = dccPage.getContent();

            if (dccList.isEmpty()) {
                    logger.info("No DCC records found for supplierId={} page={} size={}", supplierId, page, size);
                return buildResponseFromList(Collections.emptyList(), page, size, 0, 0);
            }

            // Efficient batch preload for only records on this page
            Map<String, Object> preloaded = preloadRelatedData(dccList);

            @SuppressWarnings("unchecked")
            Map<String, tbPurchaseOrder> poMap = (Map<String, tbPurchaseOrder>) preloaded.get("poMapByPoNumber");
            @SuppressWarnings("unchecked")
            Map<String, List<DCCLineItem>> lineItemsMap = (Map<String, List<DCCLineItem>>) preloaded.get("lineItemsMap");
            @SuppressWarnings("unchecked")
            Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = (Map<Integer, tbCategoryApprovalRequests>) preloaded.get("approvalRequestMap");
            @SuppressWarnings("unchecked")
            Map<Integer, List<tbCategoryApprovals>> approvalsMap = (Map<Integer, List<tbCategoryApprovals>>) preloaded.get("approvalsMap");
            @SuppressWarnings("unchecked")
            Map<String, User> userMap = (Map<String, User>) preloaded.get("userMap");
            @SuppressWarnings("unchecked")
            Map<String, List<tb_PurchaseOrderUPL>> uplMap = (Map<String, List<tb_PurchaseOrderUPL>>) preloaded.get("uplMap");
            @SuppressWarnings("unchecked")
            Map<String, tbPurchaseOrder> poByNumberLine = (Map<String, tbPurchaseOrder>) preloaded.get("poByNumberLine");
            @SuppressWarnings("unchecked")
            Map<Long, departmentsdata> depMap = (Map<Long, departmentsdata>) preloaded.get("departmentsMap");

            List<Map<String, Object>> groupedResults = dccList.stream()
                .map(dcc -> buildGroupedRow(
                    dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap, userMap,
                    uplMap, poByNumberLine, depMap))
                .collect(Collectors.toList());

            // In-memory filter for searchQuery, if provided
            if (hasFilter) {
                final String rawSearch = searchQuery.trim();
                final ColumnInfo mappingLocal = COLUMN_MAPPINGS.get(columnName);
                final String targetKey = (mappingLocal != null && mappingLocal.getFieldName() != null && !mappingLocal.getFieldName().trim().isEmpty())
                        ? mappingLocal.getFieldName()
                        : columnName;

                groupedResults = groupedResults.stream().filter(row -> {
                    Object value = row.get(targetKey);
                    if (value == null) return false;
                    if (isExactMatchColumn(columnName)) {
                        return value.toString().trim().equalsIgnoreCase(rawSearch);
                    }
                    if (isNumericColumn(columnName)) {
                        try {
                            double searchNum = Double.parseDouble(rawSearch);
                            if (value instanceof Number)
                                return Math.abs(((Number) value).doubleValue() - searchNum) < 0.001;
                            else
                                return Math.abs(Double.parseDouble(value.toString().trim()) - searchNum) < 0.001;
                        } catch (NumberFormatException nfe) {
                            return false;
                        }
                    }
                    if (isDateColumn(columnName, mappingLocal)) {
                        String formattedValue = formatValueForDateComparison(value);
                        String formattedSearch = formatSearchForDateComparison(rawSearch);
                        return formattedValue != null && formattedValue.equals(formattedSearch);
                    }
                    return value.toString().toLowerCase().contains(rawSearch.toLowerCase());
                }).collect(Collectors.toList());

                // After filter, forced in-memory paging
                int totalRecords = groupedResults.size();
                int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / size);
                int fromIndex = Math.max(0, (page - 1) * size);
                int toIndex = Math.min(fromIndex + size, totalRecords);
                List<Map<String, Object>> pageData = (fromIndex < toIndex) ? groupedResults.subList(fromIndex, toIndex) : Collections.emptyList();

                return buildResponseFromList(pageData, page, size, totalRecords, totalPages);
            }

            // If no in-memory filter needed, respect DB page and DB totals
            return buildResponse(dccPage, groupedResults, page, size);
    }
  
    // Helper methods for filtering
    private boolean isExactMatchColumn(String columnName) {
        if (columnName == null) return false;
        String lowerColumn = columnName.toLowerCase().trim();
        return Arrays.asList("dccid", "dccstatus", "dccacceptancetype").contains(lowerColumn);
    }

    private boolean isNumericColumn(String columnName) {
        if (columnName == null) return false;
        String lowerColumn = columnName.toLowerCase().trim();
        return Arrays.asList(
                "recordno", "approvalcount", "useragingindays", 
                "totalagingindays", "requestamountsar"
        ).contains(lowerColumn);
    }

    private boolean isDateColumn(String columnName, ColumnInfo mapping) {
        if (columnName == null) return false;
        String lowerColumn = columnName.toLowerCase().trim();
        return Arrays.asList("dcccreateddate", "dateapproved", "lninservicedate").contains(lowerColumn) ||
                (mapping != null && "date".equalsIgnoreCase(mapping.getType()));
    }

    private String formatValueForDateComparison(Object value) {
        if (value == null) return null;
        String strValue = value.toString().trim();
        
        // Since dates are already formatted strings like "13-Oct-2025", return as-is
        if (value instanceof String || isValidDateFormat(strValue)) {
            return strValue;
        }
        
        if (value instanceof Date) {
            return formatDate((Date) value);
        }
        
        return strValue;
    }
    

    private String formatSearchForDateComparison(String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) return null;
        
        String trimmedQuery = searchQuery.trim();
        
        // If already in correct format, return as-is
        if (isValidDateFormat(trimmedQuery)) {
            return trimmedQuery;
        }
        
        // Try to parse and reformat
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            sdf.setLenient(false);
            Date parsedDate = sdf.parse(trimmedQuery);
            return sdf.format(parsedDate);
        } catch (Exception e) {
            // Return exact string for direct matching
            return trimmedQuery;
        }
    }

    private boolean isValidDateFormat(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            sdf.setLenient(false);
            Date parsed = sdf.parse(dateStr.trim());
            return sdf.format(parsed).equals(dateStr.trim());
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> buildResponseFromList(List<Map<String, Object>> pageData, int page, int size, int totalRecords, int totalPages) {
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", totalRecords);
        response.put("totalPages", totalPages);
        response.put("data", pageData);
        return response;
    }// helper used when filtering in-memory

    private Map<String, Object> preloadRelatedData(List<DCC> dccList) {
        Map<String, Object> result = new HashMap<>();
        Set<String> poNumbers = dccList.stream().map(DCC::getPoNumber).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> dccIds = dccList.stream().map(dcc -> String.valueOf(dcc.getRecordNo())).collect(Collectors.toSet());

        // Preload Purchase Orders
        List<tbPurchaseOrder> poList = purchaseOrderRepository.findByPoNumberIn(poNumbers);
        Map<String, tbPurchaseOrder> poMapByPoNumber = new HashMap<>();
        Map<String, tbPurchaseOrder> poByNumberLine = new HashMap<>();
        for (tbPurchaseOrder po : poList) {
            if (po.getPoNumber() != null) {
                poMapByPoNumber.putIfAbsent(po.getPoNumber(), po);
                if (po.getLineNumber() != null) {
                    poByNumberLine.put(po.getPoNumber() + "-" + po.getLineNumber(), po);
                }
            }
        }
        result.put("poMapByPoNumber", poMapByPoNumber);
        result.put("poByNumberLine", poByNumberLine);

        // Preload Line Items
        List<DCCLineItem> allLineItems = dccLineRepo.findByDccIdIn(dccIds);
        Map<String, List<DCCLineItem>> lineItemsMap = allLineItems.stream()
                .collect(Collectors.groupingBy(DCCLineItem::getDccId));
        result.put("lineItemsMap", lineItemsMap);

        // Preload UPLs
        Set<String> poIds = allLineItems.stream().map(DCCLineItem::getPoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> lineNumbers = allLineItems.stream().map(DCCLineItem::getLineNumber).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> uplLineNumbers = allLineItems.stream().map(DCCLineItem::getUplLineNumber).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUPLRepo.findByPoNumberInAndPoLineNumberInAndUplLineIn(poIds, lineNumbers, uplLineNumbers)
                .stream().collect(Collectors.groupingBy(upl -> upl.getPoNumber() + "-" + upl.getPoLineNumber() + "-" + upl.getUplLine()));
        result.put("uplMap", uplMap);

        // Preload Approval Requests
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = tbCategoryApprovalRequestsRepo
                .findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(dccIds.stream().map(Integer::parseInt).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(tbCategoryApprovalRequests::getAcceptanceRequestRecordNo, req -> req, (r1, r2) -> r1));

        // Preload Approvals
        Set<Integer> approvalRecordNos = approvalRequestMap.values().stream().map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = tbCategoryApprovalsRepo.findByApprovalRecordIdIn(approvalRecordNos)
                .stream().collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId));
        result.put("approvalsMap", approvalsMap);
        result.put("approvalRequestMap", approvalRequestMap);

        // Preload Users
        Set<String> usernames = new HashSet<>();
        dccList.forEach(dcc -> Optional.ofNullable(dcc.getCreatedBy()).ifPresent(usernames::add));
        approvalsMap.values().forEach(approvals -> approvals.forEach(a -> Optional.ofNullable(a.getApproverName()).ifPresent(usernames::add)));
        Map<String, User> userMap = userAccountRepo.findByUsernameIn(usernames)
                .stream().collect(Collectors.toMap(User::getUsername, u -> u, (u1, u2) -> u1));
        result.put("userMap", userMap);

        // Preload Departments
        Set<Long> depIds = userMap.values().stream().map(User::getDepartmentId).filter(Objects::nonNull).map(Integer::longValue).collect(Collectors.toSet());
        Map<Long, departmentsdata> depMap = tbDepartmentRepo.findAllById(depIds)
                .stream().collect(Collectors.toMap(departmentsdata::getRecordNo, d -> d));
        result.put("departmentsMap", depMap);

        return result;
    }

    private Map<String, Object> buildGroupedRow(
            DCC dcc, Map<String, tbPurchaseOrder> poMap, Map<String, List<DCCLineItem>> lineItemsMap,
            Map<Integer, tbCategoryApprovalRequests> approvalRequestMap, Map<Integer, List<tbCategoryApprovals>> approvalsMap,
            Map<String, User> userMap, Map<String, List<tb_PurchaseOrderUPL>> uplMap, Map<String, tbPurchaseOrder> poByNumberLine,
            Map<Long, departmentsdata> depMap) {
        Map<String, Object> row = new LinkedHashMap<>();
        tbPurchaseOrder po = poMap.get(dcc.getPoNumber());
        List<DCCLineItem> lineItems = lineItemsMap.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
        tbCategoryApprovalRequests approvalRequest = approvalRequestMap.get((int) dcc.getRecordNo());
        DCCLineItem ln = lineItems.isEmpty() ? null : lineItems.get(0);

        // DCC fields
        row.put("dccId", Optional.ofNullable(dcc.getDccId()).orElse(String.valueOf(dcc.getRecordNo())));
        row.put("recordNo", dcc.getRecordNo());
        row.put("projectName", determineProjectName(dcc, po));
        row.put("newProjectName", po != null ? po.getNewProjectName() : null);
        row.put("vendorComment", dcc.getVendorComment());
        row.put("vendorName", dcc.getVendorName());
        row.put("vendorEmail", dcc.getVendorEmail());
        row.put("supplierId", po != null ? po.getVendorNumber() : null);
        row.put("dccCreatedDate", formatDate(dcc.getCreatedDate()));
        row.put("dateApproved", formatLocalDateTime(approvalRequest != null ? approvalRequest.getApprovedDate() : null));
        row.put("poNumber", dcc.getPoNumber());
        row.put("dccAcceptanceType", dcc.getAcceptanceType());
        row.put("dccStatus", dcc.getStatus());
        row.put("vendorNumber", po != null ? po.getVendorNumber() : dcc.getVendorNumber());

        // Currency determination
        String currency = lineItems.stream()
                .filter(lnItem -> lnItem.getUplLineNumber() != null && lnItem.getLineNumber() != null && lnItem.getPoId() != null)
                .map(lnItem -> uplMap.getOrDefault(lnItem.getPoId() + "-" + lnItem.getLineNumber() + "-" + lnItem.getUplLineNumber(), Collections.emptyList()))
                .filter(upls -> !upls.isEmpty())
                .map(upls -> upls.get(0).getCurrency())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(dcc.getCurrency());
        row.put("currency", currency);

        // Total delivered quantity
        double totalDeliveredQty = lineItems.stream()
                .filter(li -> !Arrays.asList("incomplete", "rejected").contains(dcc.getStatus()))
                .mapToDouble(DCCLineItem::getDeliveredQty)
                .sum();
        row.put("totalDeliveredQty", totalDeliveredQty);
        row.put("uplacptRequestValue", totalDeliveredQty);

        // Total unit price
        BigDecimal totalUnitPrice = lineItems.stream()
                .filter(lnItem -> lnItem.getUplLineNumber() != null && lnItem.getLineNumber() != null && lnItem.getPoId() != null)
                .flatMap(lnItem -> uplMap.getOrDefault(lnItem.getPoId() + "-" + lnItem.getLineNumber() + "-" + lnItem.getUplLineNumber(), Collections.emptyList()).stream()
                        .map(upl -> BigDecimal.valueOf(lnItem.getDeliveredQty()).multiply(BigDecimal.valueOf(upl.getUplLineUnitPrice()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        row.put("totalUnitPrice", totalUnitPrice);

        // Line item details
        row.put("lnLocationName", ln != null ? ln.getLocationName() : null);
        row.put("lnScopeOfWork", ln != null ? ln.getScopeOfWork() : null);
        row.put("lnInserviceDate", formatDate(ln != null ? ln.getDateInService() : null));

        // Aging calculations
        List<tbCategoryApprovals> approvals = approvalRequest != null ? approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList()) : Collections.emptyList();
        String userAging = calculateUserAgingCustom(dcc, approvalRequest, approvals);
        String totalAging = calculateTotalAgingCustom(dcc, approvalRequest, approvals);
        row.put("userAging", userAging);
        row.put("totalAging", totalAging);
        row.put("userAgingInDays", extractDaysFromAging(userAging));
        row.put("totalAgingInDays", extractDaysFromAging(totalAging));

        // Request Amount SAR
        Double requestAmountSAR = calculateRequestAmountSAR(lineItems, uplMap);
        row.put("requestAmountSAR", requestAmountSAR);
        row.put("poId", po != null ? po.getPoNumber() : dcc.getPoNumber());

        // Created By details
        String createdByFullName = Optional.ofNullable(userMap.get(dcc.getCreatedBy())).map(User::getFullName).orElse(null);
        row.put("createdBy", createdByFullName);
        row.put("requestedBy", createdByFullName);
        row.put("createdByName", createdByFullName);

        // Approval Info
        ApprovalInfo approvalInfo = calculateApprovalInfo(approvals, userMap, depMap);
        row.put("approvalCount", approvalInfo.approvalCount);
        row.put("approverComment", approvalInfo.approverComment);
        row.put("pendingApprovers", approvalInfo.pendingApproverFullName);
        row.put("departmentName", approvalInfo.pendingApproverDepartmentName);

        return row;
    }

    private String determineProjectName(DCC dcc, tbPurchaseOrder po) {
        return Optional.ofNullable(po)
                .map(p -> Optional.ofNullable(p.getNewProjectName()).filter(name -> !name.trim().isEmpty())
                        .orElse(Optional.ofNullable(p.getProjectName()).filter(name -> !name.trim().isEmpty()).orElse(null)))
                .orElse(dcc.getProjectName());
    }

private ApprovalInfo calculateApprovalInfo(List<tbCategoryApprovals> approvals, Map<String, User> userMap, Map<Long, departmentsdata> depMap) {
    ApprovalInfo info = new ApprovalInfo();

    // If there are no approvals at all, return with explicit fallback and zero count
    if (approvals == null || approvals.isEmpty()) {
        info.pendingApproverFullName = "No approver";
        info.approvalCount = 0;
        return info;
    }

    // Sort by recordDateTime ASC using existing helper ensureLocalDateTime
    List<tbCategoryApprovals> sorted = approvals.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(a -> {
                try {
                    LocalDateTime dt = ensureLocalDateTime(a.getRecordDateTime());
                    return dt == null ? LocalDateTime.MAX : dt;
                } catch (Exception e) {
                    return LocalDateTime.MAX;
                }
            }))
            .collect(Collectors.toList());

    // Find the pending approver only when:
    // status == "pending" AND approvalStatus == "readyforapproval"
    tbCategoryApprovals pendingApprover = sorted.stream()
            .filter(a -> "pending".equalsIgnoreCase(Optional.ofNullable(a.getStatus()).orElse("").trim()))
            .filter(a -> "readyforapproval".equalsIgnoreCase(Optional.ofNullable(a.getApprovalStatus()).orElse("").trim()))
            .findFirst()
            .orElse(null);

    if (pendingApprover != null) {
        info.approvalCount = (int) sorted.stream()
                .filter(a -> "pending".equalsIgnoreCase(Optional.ofNullable(a.getStatus()).orElse("").trim()))
                .count();

        String approverName = Optional.ofNullable(pendingApprover.getApproverName()).orElse("").trim();
        if (!approverName.isEmpty()) {
            User pendingUser = userMap.get(approverName);
            if (pendingUser != null) {
                info.pendingApproverFullName = Optional.ofNullable(pendingUser.getFullName()).filter(s -> !s.trim().isEmpty()).orElse(approverName);
                Optional.ofNullable(pendingUser.getDepartmentId())
                        .map(Integer::longValue)
                        .map(depMap::get)
                        .ifPresent(dep -> info.pendingApproverDepartmentName = dep.getDeptName());
            } else {
                info.pendingApproverFullName = approverName;
            }
        } else {
            info.pendingApproverFullName = "No approver";
            info.approvalCount = 0;
        }
    } else {
        info.pendingApproverFullName = "No approver";
        info.approvalCount = 0;
    }

    // approverComment: keep previous behavior — latest comment from non-pending/active approvals
    info.approverComment = sorted.stream()
            .filter(al -> {
                String apprStatus = Optional.ofNullable(al.getApprovalStatus()).orElse("").trim().toLowerCase();
                // exclude approvals that are still pending/active
                return !("pending".equalsIgnoreCase(Optional.ofNullable(al.getStatus()).orElse("").trim())
                        && "readyforapproval".equals(apprStatus));
            })
            .map(tbCategoryApprovals::getComments)
            .filter(Objects::nonNull)
            .reduce((first, second) -> second)
            .orElse(null);

    return info;
} private Map<String, Object> buildResponse(Page<DCC> pagedDcc, List<Map<String, Object>> groupedResults, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", pagedDcc.getTotalElements());
        response.put("totalPages", pagedDcc.getTotalPages());
        response.put("data", groupedResults);
        return response;
    }

    private int extractDaysFromAging(String agingString) {
        if (agingString == null || agingString.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(agingString.trim().split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

private Double calculateRequestAmountSAR(List<DCCLineItem> lineItems, Map<String, List<tb_PurchaseOrderUPL>> uplMap) {
    if (lineItems == null || lineItems.isEmpty()) return 0.0;
    return lineItems.stream()
        .filter(li -> li.getPoId() != null && li.getLineNumber() != null && li.getUplLineNumber() != null)
        .mapToDouble(li -> {
            String key = li.getPoId() + "-" + li.getLineNumber() + "-" + li.getUplLineNumber();
            List<tb_PurchaseOrderUPL> upls = uplMap.getOrDefault(key, Collections.emptyList());
            if (upls.isEmpty()) return 0.0;
            // uplLineUnitPrice * deliveredQty — same formula as acceptanceReceivingRequestReport
            return upls.get(0).getUplLineUnitPrice() * li.getDeliveredQty();
        })
        .sum();
}

    private String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_FORMAT).format(date);
    }

    private String formatLocalDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
    }


private String calculateUserAgingCustom(DCC dcc, tbCategoryApprovalRequests latestApprovalReq, List<tbCategoryApprovals> approvals) {
    String defaultAging = "0 days 0 hrs 0 mins";
    if (latestApprovalReq == null) return defaultAging;

    final ZoneId KSA = ZoneId.of("Asia/Riyadh");
    LocalDateTime now = LocalDateTime.now(KSA);

    // helper to convert various temporal types into a KSA LocalDateTime
    java.util.function.Function<Object, LocalDateTime> toKsaLocalDateTime = obj -> {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof java.time.LocalDate) return ((java.time.LocalDate) obj).atStartOfDay();
        if (obj instanceof java.util.Date) {
            return ((java.util.Date) obj).toInstant().atZone(KSA).toLocalDateTime();
        }
        if (obj instanceof String) {
            try {
                return LocalDateTime.parse((String) obj);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    };

    List<tbCategoryApprovals> sortedApprovals = new ArrayList<>(approvals == null ? Collections.emptyList() : approvals);
    sortedApprovals.sort(Comparator.comparing(a -> {
        LocalDateTime dt = toKsaLocalDateTime.apply(a.getRecordDateTime());
        return dt == null ? LocalDateTime.MAX : dt;
    }));

    // Find the pending approver: status == "pending" && approvalStatus == "readyForApproval" (case-insensitive)
    tbCategoryApprovals pendingApprover = sortedApprovals.stream()
            .filter(a -> "pending".equalsIgnoreCase(Optional.ofNullable(a.getStatus()).orElse("").trim()))
            .filter(a -> "readyforapproval".equalsIgnoreCase(Optional.ofNullable(a.getApprovalStatus()).orElse("").trim()))
            .findFirst()
            .orElse(null);

    long userAgingMinutes = 0L;
    if (pendingApprover != null) {
        LocalDateTime pendingRecordDateTime = toKsaLocalDateTime.apply(pendingApprover.getRecordDateTime());
        if (pendingRecordDateTime != null) {
            userAgingMinutes = Duration.between(pendingRecordDateTime, now).toMinutes();
        }
    } else {
        // No pending approver found: keep as 0 (consistent with prior behavior)
        userAgingMinutes = 0L;
    }

    return diffToAgingString(userAgingMinutes);
}

private String calculateTotalAgingCustom(DCC dcc, tbCategoryApprovalRequests latestApprovalReq, List<tbCategoryApprovals> approvals) {
    String defaultAging = "0 days 0 hrs 0 mins";
    if (latestApprovalReq == null) return defaultAging;

    final ZoneId KSA = ZoneId.of("Asia/Riyadh");
    LocalDateTime now = LocalDateTime.now(KSA);

    java.util.function.Function<Object, LocalDateTime> toKsaLocalDateTime = obj -> {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof java.time.LocalDate) return ((java.time.LocalDate) obj).atStartOfDay();
        if (obj instanceof java.util.Date) {
            return ((java.util.Date) obj).toInstant().atZone(KSA).toLocalDateTime();
        }
        if (obj instanceof String) {
            try {
                return LocalDateTime.parse((String) obj);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    };

    List<tbCategoryApprovals> sortedApprovals = new ArrayList<>(approvals == null ? Collections.emptyList() : approvals);
    sortedApprovals.sort(Comparator.comparing(a -> {
        LocalDateTime dt = toKsaLocalDateTime.apply(a.getRecordDateTime());
        return dt == null ? LocalDateTime.MAX : dt;
    }));

    if (!sortedApprovals.isEmpty()) {
        LocalDateTime firstRecordDateTime = toKsaLocalDateTime.apply(sortedApprovals.get(0).getRecordDateTime());
        if (firstRecordDateTime != null) {
            long totalMinutes = Duration.between(firstRecordDateTime, now).toMinutes();
            return diffToAgingString(totalMinutes);
        }
    }

    return defaultAging;
}
    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate().atStartOfDay();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private LocalDateTime ensureLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        if (obj instanceof Date) return toLocalDateTime((Date) obj);
        throw new IllegalArgumentException("Unsupported temporal type: " + obj.getClass());
    }

    private String diffToAgingString(long totalMinutes) {
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long mins = totalMinutes % 60;
        return String.format("%d days %d hrs %d mins", days, hours, mins);
    }

    private static class ColumnInfo {
        private final String fieldName;
        private final String type;
        private final EntityType entityType;

        ColumnInfo(String fieldName, String type, EntityType entityType) {
            this.fieldName = fieldName;
            this.type = type;
            this.entityType = entityType;
        }

        String getFieldName() { return fieldName; }
        String getType() { return type; }
        EntityType getEntityType() { return entityType; }
    }

    private enum EntityType {
        DCC, PURCHASE_ORDER, LINE_ITEM, APPROVAL_REQUEST
    }

    private static class ApprovalInfo {
        int approvalCount = 0;
        String approverComment = null;
        String pendingApproverFullName = null;
        String pendingApproverDepartmentName = null;
    }

    public Map<String, Object> getAgingReportWithMultipleFilters(
        String supplierId,
        Map<String, String> filters,
        int page,
        int size) {

        page = Math.max(page, 1);
        size = Math.max(size, 1);

        boolean hasFilters = filters != null && !filters.isEmpty();

        System.out.println("Service - hasFilters: " + hasFilters + ", filters: " + filters);
        System.out.println("SupplierId: " + supplierId);

        Page<DCC> pagedDcc;
        List<DCC> dccList;

        final String onlyStatus = "inprocess";

        if (hasFilters) {
            Pageable unpaged = Pageable.unpaged();
            pagedDcc = (supplierId != null && !"0".equals(supplierId.trim()))
                    ? dccRepository.findAllBySupplierVendorAndStatus(supplierId, onlyStatus, unpaged)
                    : dccRepository.findAllByStatus(onlyStatus, unpaged);
            dccList = pagedDcc.getContent();
            System.out.println("Loaded " + dccList.size() + " DCC records for filtering");
        } else {
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
            pagedDcc = (supplierId != null && !"0".equals(supplierId.trim()))
                    ? dccRepository.findAllBySupplierVendorAndStatus(supplierId, onlyStatus, pageable)
                    : dccRepository.findAllByStatus(onlyStatus, pageable);
            dccList = pagedDcc.getContent();
            System.out.println("Loaded " + dccList.size() + " DCC records with pagination");
        }

        if (dccList.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("totalRecords", 0);
            emptyResponse.put("data", Collections.emptyList());
            emptyResponse.put("totalPages", 0);
            emptyResponse.put("pageSize", size);
            emptyResponse.put("currentPage", page);
            return emptyResponse;
        }

        // Preload related data
        Map<String, Object> preloaded = preloadRelatedData(dccList);

        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poMap = (Map<String, tbPurchaseOrder>) preloaded.get("poMapByPoNumber");
        @SuppressWarnings("unchecked")
        Map<String, List<DCCLineItem>> lineItemsMap = (Map<String, List<DCCLineItem>>) preloaded.get("lineItemsMap");
        @SuppressWarnings("unchecked")
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = (Map<Integer, tbCategoryApprovalRequests>) preloaded.get("approvalRequestMap");
        @SuppressWarnings("unchecked")
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = (Map<Integer, List<tbCategoryApprovals>>) preloaded.get("approvalsMap");
        @SuppressWarnings("unchecked")
        Map<String, User> userMap = (Map<String, User>) preloaded.get("userMap");
        @SuppressWarnings("unchecked")
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = (Map<String, List<tb_PurchaseOrderUPL>>) preloaded.get("uplMap");
        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poByNumberLine = (Map<String, tbPurchaseOrder>) preloaded.get("poByNumberLine");
        @SuppressWarnings("unchecked")
        Map<Long, departmentsdata> depMap = (Map<Long, departmentsdata>) preloaded.get("departmentsMap");

        System.out.println("Preloaded data - PO Map size: " + poMap.size() + ", Line Items: " + lineItemsMap.size());

        // Build grouped results
        List<Map<String, Object>> groupedResults = dccList.stream()
                .map(dcc -> buildGroupedRow(dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap,
                        userMap, uplMap, poByNumberLine, depMap))
                .collect(Collectors.toList());

        System.out.println("Generated " + groupedResults.size() + " grouped rows");

        if (hasFilters) {
            // Debug: Check first row to see available data
            if (!groupedResults.isEmpty()) {
                System.out.println("Sample row keys: " + groupedResults.get(0).keySet());

            }

            List<Map<String, Object>> filtered = groupedResults.stream()
                    .filter(row -> {
                        boolean matchesAll = filters.entrySet().stream().allMatch(filterEntry -> {
                            String columnName = filterEntry.getKey();
                            String searchQuery = filterEntry.getValue();

                            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                                return true;
                            }

                            // System.out.println("Checking filter: " + columnName + " = '" + searchQuery + "'");

                            boolean matches = matchesFilter(row, columnName, searchQuery);
                            // System.out.println("Row matches filter " + columnName + ": " + matches);
                            return matches;
                        });
                        return matchesAll;
                    })
                    .collect(Collectors.toList());

            // System.out.println("After filtering: " + filtered.size() + " results");

            int totalRecords = filtered.size();
            int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / size);
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(fromIndex + size, totalRecords);
            List<Map<String, Object>> pageData = fromIndex < toIndex ? filtered.subList(fromIndex, toIndex) : Collections.emptyList();

            return buildResponseFromList(pageData, page, size, totalRecords, totalPages);
        }

        // No filters: return DB pagination
        return buildResponse(pagedDcc, groupedResults, page, size);
    }

    public Map<String, Object> getFullAgingReport(
            String supplierId, String columnName, String searchQuery, int page, int size) {

        logger.debug("Fetching aging report for supplierId={} page={} size={}", supplierId, page, size);
        page = Math.max(page, 1);
        size = Math.max(size, 1);
        boolean hasFilter = columnName != null && !columnName.trim().isEmpty() &&
                searchQuery != null && !searchQuery.trim().isEmpty();

        final String excludedStatus = "incomplete";
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));

        // Fast! Only get single page from DB, filtering by supplier in DB if set
        Page<DCC> dccPage = (supplierId != null && !"0".equals(supplierId.trim()))
                ? dccRepository.findAllBySupplierVendorAndStatusNot(supplierId, excludedStatus, pageable)
                : dccRepository.findAllByStatusNot(excludedStatus, pageable);

        List<DCC> dccList = dccPage.getContent();

        if (dccList.isEmpty()) {
            logger.info("No DCC records found for supplierId={} page={} size={}", supplierId, page, size);
            return buildResponseFromList(Collections.emptyList(), page, size, 0, 0);
        }

        // Efficient batch preload for only records on this page
        Map<String, Object> preloaded = preloadRelatedData(dccList);

        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poMap = (Map<String, tbPurchaseOrder>) preloaded.get("poMapByPoNumber");
        @SuppressWarnings("unchecked")
        Map<String, List<DCCLineItem>> lineItemsMap = (Map<String, List<DCCLineItem>>) preloaded.get("lineItemsMap");
        @SuppressWarnings("unchecked")
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = (Map<Integer, tbCategoryApprovalRequests>) preloaded.get("approvalRequestMap");
        @SuppressWarnings("unchecked")
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = (Map<Integer, List<tbCategoryApprovals>>) preloaded.get("approvalsMap");
        @SuppressWarnings("unchecked")
        Map<String, User> userMap = (Map<String, User>) preloaded.get("userMap");
        @SuppressWarnings("unchecked")
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = (Map<String, List<tb_PurchaseOrderUPL>>) preloaded.get("uplMap");
        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poByNumberLine = (Map<String, tbPurchaseOrder>) preloaded.get("poByNumberLine");
        @SuppressWarnings("unchecked")
        Map<Long, departmentsdata> depMap = (Map<Long, departmentsdata>) preloaded.get("departmentsMap");

        List<Map<String, Object>> groupedResults = dccList.stream()
                .map(dcc -> buildGroupedRow(
                        dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap, userMap,
                        uplMap, poByNumberLine, depMap))
                .collect(Collectors.toList());

        // In-memory filter for searchQuery, if provided
        if (hasFilter) {
            final String rawSearch = searchQuery.trim();
            final ColumnInfo mappingLocal = COLUMN_MAPPINGS.get(columnName);
            final String targetKey = (mappingLocal != null && mappingLocal.getFieldName() != null && !mappingLocal.getFieldName().trim().isEmpty())
                    ? mappingLocal.getFieldName()
                    : columnName;

            groupedResults = groupedResults.stream().filter(row -> {
                Object value = row.get(targetKey);
                if (value == null) return false;
                if (isExactMatchColumn(columnName)) {
                    return value.toString().trim().equalsIgnoreCase(rawSearch);
                }
                if (isNumericColumn(columnName)) {
                    try {
                        double searchNum = Double.parseDouble(rawSearch);
                        if (value instanceof Number)
                            return Math.abs(((Number) value).doubleValue() - searchNum) < 0.001;
                        else
                            return Math.abs(Double.parseDouble(value.toString().trim()) - searchNum) < 0.001;
                    } catch (NumberFormatException nfe) {
                        return false;
                    }
                }
                if (isDateColumn(columnName, mappingLocal)) {
                    String formattedValue = formatValueForDateComparison(value);
                    String formattedSearch = formatSearchForDateComparison(rawSearch);
                    return formattedValue != null && formattedValue.equals(formattedSearch);
                }
                return value.toString().toLowerCase().contains(rawSearch.toLowerCase());
            }).collect(Collectors.toList());

            // After filter, forced in-memory paging
            int totalRecords = groupedResults.size();
            int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / size);
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(fromIndex + size, totalRecords);
            List<Map<String, Object>> pageData = (fromIndex < toIndex) ? groupedResults.subList(fromIndex, toIndex) : Collections.emptyList();

            return buildResponseFromList(pageData, page, size, totalRecords, totalPages);
        }

        // If no in-memory filter needed, respect DB page and DB totals
        return buildResponse(dccPage, groupedResults, page, size);
    }

    public Map<String, Object> getFullAgingReportWithMultipleFilters(
            String supplierId,
            Map<String, String> filters,
            int page,
            int size) {

        page = Math.max(page, 1);
        size = Math.max(size, 1);

        boolean hasFilters = filters != null && !filters.isEmpty();

        System.out.println("Service - hasFilters: " + hasFilters + ", filters: " + filters);
        System.out.println("SupplierId: " + supplierId);

        Page<DCC> pagedDcc;
        List<DCC> dccList;

        final String excludedStatus = "incomplete";

        if (hasFilters) {
            Pageable unpaged = Pageable.unpaged();
            pagedDcc = (supplierId != null && !"0".equals(supplierId.trim()))
                    ? dccRepository.findAllBySupplierVendorAndStatusNot(supplierId, excludedStatus, unpaged)
                    : dccRepository.findAllByStatusNot(excludedStatus, unpaged);
            dccList = pagedDcc.getContent();
            System.out.println("Loaded " + dccList.size() + " DCC records for filtering");
        } else {
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
            pagedDcc = (supplierId != null && !"0".equals(supplierId.trim()))
                    ? dccRepository.findAllBySupplierVendorAndStatusNot(supplierId, excludedStatus, pageable)
                    : dccRepository.findAllByStatusNot(excludedStatus, pageable);
            dccList = pagedDcc.getContent();
            System.out.println("Loaded " + dccList.size() + " DCC records with pagination");
        }

        if (dccList.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("totalRecords", 0);
            emptyResponse.put("data", Collections.emptyList());
            emptyResponse.put("totalPages", 0);
            emptyResponse.put("pageSize", size);
            emptyResponse.put("currentPage", page);
            return emptyResponse;
        }

        // Preload related data
        Map<String, Object> preloaded = preloadRelatedData(dccList);

        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poMap = (Map<String, tbPurchaseOrder>) preloaded.get("poMapByPoNumber");
        @SuppressWarnings("unchecked")
        Map<String, List<DCCLineItem>> lineItemsMap = (Map<String, List<DCCLineItem>>) preloaded.get("lineItemsMap");
        @SuppressWarnings("unchecked")
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = (Map<Integer, tbCategoryApprovalRequests>) preloaded.get("approvalRequestMap");
        @SuppressWarnings("unchecked")
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = (Map<Integer, List<tbCategoryApprovals>>) preloaded.get("approvalsMap");
        @SuppressWarnings("unchecked")
        Map<String, User> userMap = (Map<String, User>) preloaded.get("userMap");
        @SuppressWarnings("unchecked")
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = (Map<String, List<tb_PurchaseOrderUPL>>) preloaded.get("uplMap");
        @SuppressWarnings("unchecked")
        Map<String, tbPurchaseOrder> poByNumberLine = (Map<String, tbPurchaseOrder>) preloaded.get("poByNumberLine");
        @SuppressWarnings("unchecked")
        Map<Long, departmentsdata> depMap = (Map<Long, departmentsdata>) preloaded.get("departmentsMap");

        System.out.println("Preloaded data - PO Map size: " + poMap.size() + ", Line Items: " + lineItemsMap.size());

        // Build grouped results
        List<Map<String, Object>> groupedResults = dccList.stream()
                .map(dcc -> buildGroupedRow(dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap,
                        userMap, uplMap, poByNumberLine, depMap))
                .collect(Collectors.toList());

        System.out.println("Generated " + groupedResults.size() + " grouped rows");

        if (hasFilters) {
            // Debug: Check first row to see available data
            if (!groupedResults.isEmpty()) {
                System.out.println("Sample row keys: " + groupedResults.get(0).keySet());

            }

            List<Map<String, Object>> filtered = groupedResults.stream()
                    .filter(row -> {
                        boolean matchesAll = filters.entrySet().stream().allMatch(filterEntry -> {
                            String columnName = filterEntry.getKey();
                            String searchQuery = filterEntry.getValue();

                            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                                return true;
                            }

                            // System.out.println("Checking filter: " + columnName + " = '" + searchQuery + "'");

                            boolean matches = matchesFilter(row, columnName, searchQuery);
                            // System.out.println("Row matches filter " + columnName + ": " + matches);
                            return matches;
                        });
                        return matchesAll;
                    })
                    .collect(Collectors.toList());

            // System.out.println("After filtering: " + filtered.size() + " results");

            int totalRecords = filtered.size();
            int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / size);
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(fromIndex + size, totalRecords);
            List<Map<String, Object>> pageData = fromIndex < toIndex ? filtered.subList(fromIndex, toIndex) : Collections.emptyList();

            return buildResponseFromList(pageData, page, size, totalRecords, totalPages);
        }

        // No filters: return DB pagination
        return buildResponse(pagedDcc, groupedResults, page, size);
    }

    //  debug method for troubleshooting
    private void debugRow(Map<String, Object> row) {
        System.out.println("=== Row Debug ===");
        row.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                System.out.println(entry.getKey() + ": " + entry.getValue() +
                    " (type: " + (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
            });
        System.out.println("=================");
    }

    // helper methods
    private boolean matchesFilter(Map<String, Object> row, String columnName, String searchQuery) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return true;
        }

        ColumnInfo mapping = getColumnInfoInsensitive(columnName);
        String targetKey = (mapping != null && mapping.getFieldName() != null && !mapping.getFieldName().trim().isEmpty())
                ? mapping.getFieldName()
                : columnName;

        Object value = row.get(targetKey);
        // System.out.println("Target key: '" + targetKey + "', found: " + (value != null));

        if (value == null) {
            System.out.println("Value is null for key: " + targetKey);
            return false;
        }

        String rawSearch = searchQuery.trim();
        String searchPattern = rawSearch.toLowerCase();

        // Exact match columns - CASE INSENSITIVE
        if (isExactMatchColumn(columnName)) {
            String valueStr = value.toString().trim();
            boolean matches = valueStr.equalsIgnoreCase(rawSearch);
            System.out.println("Exact match - " + columnName + ": '" + valueStr + "' vs '" + rawSearch + "' = " + matches);
            return matches;
        }

        // Numeric columns
        if (isNumericColumn(columnName)) {
            try {
                double searchNum = Double.parseDouble(rawSearch);
                if (value instanceof Number) {
                    return Math.abs(((Number) value).doubleValue() - searchNum) < 0.001;
                } else {
                    double valueNum = Double.parseDouble(value.toString().trim());
                    return Math.abs(valueNum - searchNum) < 0.001;
                }
            } catch (NumberFormatException nfe) {
                System.out.println("Numeric parse error for: " + rawSearch);
                return false;
            }
        }

        // Date columns - exact match
        if (isDateColumn(columnName, mapping)) {
            String formattedValue = formatValueForDateComparison(value);
            String formattedSearch = formatSearchForDateComparison(rawSearch);
            boolean matches = formattedValue != null && formattedValue.equals(formattedSearch);
            System.out.println("Date match - " + columnName + ": '" + formattedValue + "' vs '" + formattedSearch + "' = " + matches);
            return matches;
        }

        // Default: case-insensitive contains for other strings
        String valueStr = value.toString().toLowerCase().trim();
        boolean containsMatch = valueStr.contains(searchPattern);
        System.out.println("Contains match - " + columnName + ": '" + valueStr + "' contains '" + searchPattern + "' = " + containsMatch);
        return containsMatch;
    }

    // case-insensitive lookup helper
    private ColumnInfo getColumnInfoInsensitive(String key) {
        if (key == null) return null;
        ColumnInfo info = COLUMN_MAPPINGS.get(key);
        if (info != null) return info;
        for (Map.Entry<String, ColumnInfo> e : COLUMN_MAPPINGS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }
}