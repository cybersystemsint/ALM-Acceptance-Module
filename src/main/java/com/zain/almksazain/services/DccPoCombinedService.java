package com.zain.almksazain.services;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

@Service
public class DccPoCombinedService {

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

    public Map<String, Object> getAgingReport(String supplierId, String columnName, String searchQuery, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        boolean hasFilter = columnName != null && !columnName.trim().isEmpty() && searchQuery != null && !searchQuery.trim().isEmpty();

        Page<DCC> pagedDcc;
        List<DCC> dccList;

        final String onlyStatus = "inprocess";
        
            if (hasFilter) {
        Pageable unpaged = Pageable.unpaged();
        pagedDcc = (supplierId != null && !"0".equals(supplierId))
                ? dccRepository.findAllBySupplierIdAndStatus(supplierId, onlyStatus, unpaged)
                : dccRepository.findAllByStatus(onlyStatus, unpaged);
        dccList = pagedDcc.getContent();
    } else {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
        pagedDcc = (supplierId != null && !"0".equals(supplierId))
                ? dccRepository.findAllBySupplierIdAndStatus(supplierId, onlyStatus, pageable)
                : dccRepository.findAllByStatus(onlyStatus, pageable);
        dccList = pagedDcc.getContent();
    }

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
                .map(dcc -> buildGroupedRow(dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap, userMap, uplMap, poByNumberLine, depMap))
                .collect(Collectors.toList());

        if (hasFilter) {
            final String rawSearch = searchQuery.trim();
            final String searchPattern = rawSearch.toLowerCase();

            final ColumnInfo mappingLocal = COLUMN_MAPPINGS.get(columnName);
            final String targetKey = (mappingLocal != null && mappingLocal.getFieldName() != null && !mappingLocal.getFieldName().trim().isEmpty())
                    ? mappingLocal.getFieldName()
                    : columnName;

            // Debug logging (remove in production)
            System.out.println("Filtering: column='" + columnName + "', targetKey='" + targetKey + "', search='" + rawSearch + "'");

            List<Map<String, Object>> filtered = groupedResults.stream()
                    .filter(row -> {
                        Object value = row.get(targetKey);
                        if (value == null) {
                            return false;
                        }

                        // Exact match columns (dccId, dccStatus, dccAcceptanceType)
                        if (isExactMatchColumn(columnName)) {
                            String valueStr = value.toString().trim();
                            boolean matches = valueStr.equalsIgnoreCase(rawSearch);
                            // Debug for exact match
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
                                return false;
                            }
                        }

                        // Date columns - exact match
                        if (isDateColumn(columnName, mappingLocal)) {
                            String formattedValue = formatValueForDateComparison(value);
                            String formattedSearch = formatSearchForDateComparison(rawSearch);
                            boolean matches = formattedValue != null && formattedValue.equals(formattedSearch);
                            // Debug for dates
                            System.out.println("Date match - " + columnName + ": '" + formattedValue + "' vs '" + formattedSearch + "' = " + matches);
                            return matches;
                        }

                        // Default: case-insensitive contains for other strings (lnScopeOfWork, lnLocationName)
                        String valueStr = value.toString().toLowerCase().trim();
                        return valueStr.contains(searchPattern);
                    })
                    .collect(Collectors.toList());

            int totalRecords = filtered.size();
            int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / size);
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(fromIndex + size, totalRecords);
            List<Map<String, Object>> pageData = fromIndex < toIndex ? filtered.subList(fromIndex, toIndex) : Collections.emptyList();

            return buildResponseFromList(pageData, page, size, totalRecords, totalPages);
        }

        // no filter: return DB pagination
        return buildResponse(pagedDcc, groupedResults, page, size);
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
        row.put("dateApproved", formatDate(approvalRequest != null ? approvalRequest.getApprovedDate() : null));
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
        Double requestAmount = calculateRequestAmount(ln, totalDeliveredQty, poByNumberLine);
        row.put("requestAmountSAR", requestAmount);
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
        if (approvals.isEmpty()) return info;

        info.approvalCount = (int) approvals.stream()
                .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                        && "pending".equalsIgnoreCase(al.getStatus()))
                .count();

        String pendingApproverName = approvals.stream()
                .filter(al -> "readyForApproval".equals(al.getApprovalStatus()) && "pending".equalsIgnoreCase(al.getStatus()))
                .map(tbCategoryApprovals::getApproverName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> approvals.stream()
                        .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                                && "pending".equalsIgnoreCase(al.getStatus()))
                        .map(tbCategoryApprovals::getApproverName)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));

        if (pendingApproverName != null) {
            User pendingUser = userMap.get(pendingApproverName);
            if (pendingUser != null) {
                info.pendingApproverFullName = pendingUser.getFullName();
                Optional.ofNullable(pendingUser.getDepartmentId())
                        .map(Integer::longValue)
                        .map(depMap::get)
                        .ifPresent(dep -> info.pendingApproverDepartmentName = dep.getDeptName());
            }
        }

        info.approverComment = approvals.stream()
                .filter(al -> !Arrays.asList("pending", "readyForApproval").contains(al.getApprovalStatus()))
                .map(tbCategoryApprovals::getComments)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);

        return info;
    }

    private Map<String, Object> buildResponse(Page<DCC> pagedDcc, List<Map<String, Object>> groupedResults, int page, int size) {
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

    private Double calculateRequestAmount(DCCLineItem lineItem, Double deliveredQty, Map<String, tbPurchaseOrder> poByNumberLine) {
        if (lineItem == null || deliveredQty == null || lineItem.getPoId() == null || lineItem.getLineNumber() == null) {
            return 0.0;
        }
        String key = lineItem.getPoId() + "-" + lineItem.getLineNumber();
        return Optional.ofNullable(poByNumberLine.get(key))
                .map(tbPurchaseOrder::getUnitPriceInSAR)
                .map(unitPrice -> unitPrice * deliveredQty)
                .orElse(0.0);
    }

    private String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_FORMAT).format(date);
    }

    private String calculateUserAgingCustom(DCC dcc, tbCategoryApprovalRequests approvalRequest, List<tbCategoryApprovals> approvals) {
        String status = Optional.ofNullable(dcc.getStatus()).map(String::toLowerCase).orElse("");
        LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
        LocalDateTime now = LocalDateTime.now();

        if (Arrays.asList("rejected", "returned", "approved", "approved-received").contains(status)) {
            return "0 days 0 hrs 0 mins";
        }
        if ("request-info".equals(status)) {
            return approvals.stream()
                    .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "request-info".equalsIgnoreCase(a.getApprovalStatus()))
                    .findFirst()
                    .filter(a -> a.getRecordDateTime() != null && a.getApprovedDate() != null)
                    .map(a -> Duration.between(ensureLocalDateTime(a.getRecordDateTime()), ensureLocalDateTime(a.getApprovedDate())).toMinutes())
                    .map(this::diffToAgingString)
                    .orElse("0 days 0 hrs 0 mins");
        }
        if ("inprocess".equals(status)) {
            return approvals.stream()
                    .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "readyforapproval".equalsIgnoreCase(a.getApprovalStatus()))
                    .findFirst()
                    .map(a -> Duration.between(ensureLocalDateTime(a.getRecordDateTime()), now).toMinutes())
                    .map(this::diffToAgingString)
                    .orElseGet(() -> diffToAgingString(Duration.between(createdDate, now).toMinutes()));
        }
        return "0 days 0 hrs 0 mins";
    }

    private String calculateTotalAgingCustom(DCC dcc, tbCategoryApprovalRequests approvalRequest, List<tbCategoryApprovals> approvals) {
        String status = Optional.ofNullable(dcc.getStatus()).map(String::toLowerCase).orElse("");
        LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
        LocalDateTime now = LocalDateTime.now();

        if (Arrays.asList("rejected", "returned").contains(status)) {
            return approvals.stream()
                    .filter(a -> "rejected".equalsIgnoreCase(a.getStatus()) && "rejected".equalsIgnoreCase(a.getApprovalStatus()))
                    .findFirst()
                    .filter(a -> a.getApprovedDate() != null)
                    .map(a -> Duration.between(createdDate, ensureLocalDateTime(a.getApprovedDate())).toMinutes())
                    .map(this::diffToAgingString)
                    .orElse("0 days 0 hrs 0 mins");
        }
        if ("request-info".equals(status)) {
            return approvals.stream()
                    .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "request-info".equalsIgnoreCase(a.getApprovalStatus()))
                    .findFirst()
                    .filter(a -> a.getApprovedDate() != null)
                    .map(a -> approvals.stream()
                            .map(b -> ensureLocalDateTime(b.getRecordDateTime()))
                            .filter(Objects::nonNull)
                            .min(LocalDateTime::compareTo)
                            .map(firstRecord -> Duration.between(firstRecord, ensureLocalDateTime(a.getApprovedDate())).toMinutes())
                            .orElse(0L))
                    .map(this::diffToAgingString)
                    .orElse("0 days 0 hrs 0 mins");
        }
        if ("inprocess".equals(status)) {
            return approvals.stream()
                    .map(a -> ensureLocalDateTime(a.getRecordDateTime()))
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .map(firstRecord -> Duration.between(firstRecord, now).toMinutes())
                    .map(this::diffToAgingString)
                    .orElseGet(() -> diffToAgingString(Duration.between(createdDate, now).toMinutes()));
        }
        if ("approved".equals(status)) {
            return approvals.stream()
                    .filter(a -> "approved".equalsIgnoreCase(a.getStatus()) && "approved".equalsIgnoreCase(a.getApprovalStatus()))
                    .max(Comparator.comparing(a -> ensureLocalDateTime(a.getApprovedDate())))
                    .filter(a -> a.getApprovedDate() != null)
                    .map(a -> Duration.between(createdDate, ensureLocalDateTime(a.getApprovedDate())).toMinutes())
                    .map(this::diffToAgingString)
                    .orElse("0 days 0 hrs 0 mins");
        }
        if ("approved-received".equals(status)) {
            return Optional.ofNullable(approvalRequest)
                    .filter(req -> req.getApprovedDate() != null)
                    .map(req -> Duration.between(createdDate, toLocalDateTime(req.getApprovedDate())).toMinutes())
                    .map(this::diffToAgingString)
                    .orElse("0 days 0 hrs 0 mins");
        }
        return "0 days 0 hrs 0 mins";
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
        pagedDcc = (supplierId != null && !"0".equals(supplierId))
                ? dccRepository.findAllBySupplierIdAndStatus(supplierId, onlyStatus, unpaged)
                : dccRepository.findAllByStatus(onlyStatus, unpaged);
        dccList = pagedDcc.getContent();
        System.out.println("Loaded " + dccList.size() + " DCC records for filtering");
    } else {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
        pagedDcc = (supplierId != null && !"0".equals(supplierId))
                ? dccRepository.findAllBySupplierIdAndStatus(supplierId, onlyStatus, pageable)
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

// Add this debug method for troubleshooting
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

// Make sure these helper methods are available
private boolean matchesFilter(Map<String, Object> row, String columnName, String searchQuery) {
    if (searchQuery == null || searchQuery.trim().isEmpty()) {
        return true;
    }
    
    ColumnInfo mapping = COLUMN_MAPPINGS.get(columnName);
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


}