package com.zain.almksazain.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;

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
            "totalAgingInDays", "Request Amount (SAR)", "approvalCount", "approverComment", "pendingApprovers"
    );

    static {
        // DCC Columns
        COLUMN_MAPPINGS.put("recordNo", new ColumnInfo("recordNo", "numeric", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorComment", new ColumnInfo("vendorComment", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorName", new ColumnInfo("vendorName", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("vendorEmail", new ColumnInfo("vendorEmail", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccId", new ColumnInfo("dccId", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccCreatedDate", new ColumnInfo("createdDate", "date", EntityType.DCC));
        COLUMN_MAPPINGS.put("poNumber", new ColumnInfo("poNumber", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccAcceptanceType", new ColumnInfo("acceptanceType", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("dccStatus", new ColumnInfo("status", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("createdBy", new ColumnInfo("createdBy", "string", EntityType.DCC));
        COLUMN_MAPPINGS.put("createdByName", new ColumnInfo("createdBy", "string", EntityType.DCC));

        // PO Columns
        COLUMN_MAPPINGS.put("projectName", new ColumnInfo("newProjectName", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("newProjectName", new ColumnInfo("newProjectName", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("supplierId", new ColumnInfo("vendorNumber", "string", EntityType.PURCHASE_ORDER));
        COLUMN_MAPPINGS.put("poId", new ColumnInfo("poNumber", "string", EntityType.PURCHASE_ORDER));

        // Line Item Columns
        COLUMN_MAPPINGS.put("lnLocationName", new ColumnInfo("locationName", "string", EntityType.LINE_ITEM));
        COLUMN_MAPPINGS.put("lnScopeOfWork", new ColumnInfo("scopeOfWork", "string", EntityType.LINE_ITEM));
        COLUMN_MAPPINGS.put("lnInserviceDate", new ColumnInfo("dateInService", "date", EntityType.LINE_ITEM));

        // Approval Request Columns
        COLUMN_MAPPINGS.put("dateApproved", new ColumnInfo("approvedDate", "date", EntityType.APPROVAL_REQUEST));
    }

  public Map<String, Object> getAgingReport(String supplierId, String columnName, String searchQuery, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
        Page<DCC> pagedDcc;

        // If supplierId is specified, use repo method directly
        if (supplierId != null && !"0".equals(supplierId)) {
            // You must implement this method in your DCCRepository
            pagedDcc = dccRepository.findAllBySupplierId(supplierId, pageable);
        } else {
            pagedDcc = dccRepository.findAll(pageable);
        }

        List<DCC> dccList = pagedDcc.getContent();
        Set<String> poNumbers = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
        Set<String> dccIds = dccList.stream().map(dcc -> String.valueOf(dcc.getRecordNo())).collect(Collectors.toSet());

        // Fetch related POs in one call
       Map<String, tbPurchaseOrder> poMap = purchaseOrderRepository.findByPoNumberIn(poNumbers)
    .stream()
    .collect(Collectors.toMap(
        tbPurchaseOrder::getPoNumber,
        po -> po,
        (existing, replacement) -> existing 
    ));
        // Fetch related line items in one call
    // Fetch related line items in one call
Map<String, List<DCCLineItem>> lineItemsMap = dccLineRepo.findByDccIdIn(dccIds)
    .stream().collect(Collectors.groupingBy(DCCLineItem::getDccId));

// Batch-fetch all tb_PurchaseOrderUPL records
Set<String> poIds = lineItemsMap.values().stream()
    .flatMap(List::stream)
    .map(DCCLineItem::getPoId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Set<String> lineNumbers = lineItemsMap.values().stream()
    .flatMap(List::stream)
    .map(DCCLineItem::getLineNumber)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Set<String> uplLineNumbers = lineItemsMap.values().stream()
    .flatMap(List::stream)
    .map(DCCLineItem::getUplLineNumber)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<String, List<tb_PurchaseOrderUPL>> uplMap = tbPurchaseOrderUPLRepo
    .findByPoNumberInAndPoLineNumberInAndUplLineIn(poIds, lineNumbers, uplLineNumbers)
    .stream()
    .collect(Collectors.groupingBy(upl -> upl.getPoNumber() + "-" + upl.getPoLineNumber() + "-" + upl.getUplLine()));
        // Fetch approval requests
        List<Integer> dccIdInts = dccIds.stream().map(Integer::parseInt).collect(Collectors.toList());
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = tbCategoryApprovalRequestsRepo
            .findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(dccIdInts)
            .stream().collect(Collectors.toMap(tbCategoryApprovalRequests::getAcceptanceRequestRecordNo, req -> req, (a, b) -> a));

        // Fetch approvals
        Set<Integer> approvalRecordNos = approvalRequestMap.values().stream()
            .map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = tbCategoryApprovalsRepo.findByApprovalRecordIdIn(approvalRecordNos)
            .stream().collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId));

        // Fetch user info
        Set<String> usernames = new HashSet<>();
        dccList.forEach(dcc -> usernames.add(dcc.getCreatedBy()));
        approvalsMap.values().forEach(approvals -> approvals.forEach(a -> {
            if (a.getApproverName() != null) usernames.add(a.getApproverName());
        }));
        Map<String, User> userMap = userAccountRepo.findByUsernameIn(usernames)
            .stream().collect(Collectors.toMap(User::getUsername, u -> u));

        // Build grouped results, filtering if necessary
        List<Map<String, Object>> groupedResults = dccList.stream()
            .map(dcc -> buildGroupedRow(dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap, userMap))
            .collect(Collectors.toList());

        // If filtering calculated columns
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
            && Arrays.asList("uplacptRequestValue", "userAging", "totalAging", "userAgingInDays", "totalAgingInDays", "Request Amount (SAR)", "approvalCount", "approverComment", "pendingApprovers").contains(columnName)) {
            String searchPattern = searchQuery.toLowerCase();
            groupedResults = groupedResults.stream()
                .filter(row -> {
                    Object value = row.get(columnName);
                    return value != null && String.valueOf(value).toLowerCase().contains(searchPattern);
                })
                .collect(Collectors.toList());
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", pagedDcc.getTotalElements());
        response.put("totalPages", pagedDcc.getTotalPages());
        response.put("data", groupedResults);
        return response;
    }
    private Specification<DCC> buildSpecification(String supplierId, String columnName, String searchQuery) {
        Specification<DCC> spec = Specification.where(null);

        if (supplierId != null && !"0".equals(supplierId)) {
            spec = spec.and((root, query, cb) -> {
                Subquery<String> poSub = query.subquery(String.class);
                Root<tbPurchaseOrder > poRoot = poSub.from(tbPurchaseOrder .class);
                poSub.select(poRoot.get("poNumber")).where(cb.equal(poRoot.get("vendorNumber"), supplierId));
                return root.get("poNumber").in(poSub);
            });
        }

        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && !CALCULATED_COLUMNS.contains(columnName) && COLUMN_MAPPINGS.containsKey(columnName)) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                ColumnInfo columnInfo = COLUMN_MAPPINGS.get(columnName);
                try {
                    switch (columnInfo.getEntityType()) {
                        case DCC:
                            return buildDccPredicate(root, cb, columnInfo, searchQuery);
                        case PURCHASE_ORDER:
                            return buildPoPredicate(root, query, cb, columnInfo, searchQuery);
                        case LINE_ITEM:
                            return buildLineItemPredicate(root, cb, columnInfo, searchQuery);
                        case APPROVAL_REQUEST:
                            return buildApprovalRequestPredicate(root, query, cb, columnInfo, searchQuery);
                        default:
                            return null;
                    }
                } catch (ParseException | NumberFormatException e) {
                    return null;
                }
            });
        }
        return spec;
    }

    private Predicate buildDccPredicate(Root<DCC> root, CriteriaBuilder cb, ColumnInfo columnInfo, String searchQuery) throws ParseException {
        String fieldName = columnInfo.getFieldName();
        switch (columnInfo.getType()) {
            case "string":
                return cb.like(cb.lower(root.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
            case "numeric":
                return cb.equal(root.get(fieldName), Long.parseLong(searchQuery));
            case "date":
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
                return cb.equal(root.get(fieldName), sdf.parse(searchQuery));
            default:
                return null;
        }
    }

    private Predicate buildPoPredicate(Root<DCC> root, CriteriaQuery<?> query, CriteriaBuilder cb, ColumnInfo columnInfo, String searchQuery) throws NumberFormatException {
        Subquery<String> poSub = query.subquery(String.class);
        Root<tbPurchaseOrder> poRoot = poSub.from(tbPurchaseOrder .class);
        poSub.select(poRoot.get("poNumber"));
        String fieldName = columnInfo.getFieldName();
        if ("string".equals(columnInfo.getType())) {
            poSub.where(cb.like(cb.lower(poRoot.get(fieldName)), "%" + searchQuery.toLowerCase() + "%"));
        } else if ("numeric".equals(columnInfo.getType())) {
            poSub.where(cb.equal(poRoot.get(fieldName), Long.parseLong(searchQuery)));
        }
        return root.get("poNumber").in(poSub);
    }

    private Predicate buildLineItemPredicate(Root<DCC> root, CriteriaBuilder cb, ColumnInfo columnInfo, String searchQuery) throws ParseException {
        Join<DCC, DCCLineItem> lineJoin = root.join("dccLineItems", JoinType.LEFT);
        String fieldName = columnInfo.getFieldName();
        if ("string".equals(columnInfo.getType())) {
            return cb.like(cb.lower(lineJoin.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
        } else if ("date".equals(columnInfo.getType())) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            return cb.equal(lineJoin.get(fieldName), sdf.parse(searchQuery));
        }
        return null;
    }

    private Predicate buildApprovalRequestPredicate(Root<DCC> root, CriteriaQuery<?> query, CriteriaBuilder cb, ColumnInfo columnInfo, String searchQuery) throws ParseException {
        Subquery<Integer> approvalSub = query.subquery(Integer.class);
        Root<tbCategoryApprovalRequests> approvalRoot = approvalSub.from(tbCategoryApprovalRequests.class);
        approvalSub.select(approvalRoot.get("acceptanceRequestRecordNo"));
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        approvalSub.where(cb.equal(approvalRoot.get(columnInfo.getFieldName()), sdf.parse(searchQuery)));
        return cb.equal(root.get("recordNo"), approvalSub);
    }

    private Map<String, Object> preloadRelatedData(List<DCC> dccList) {
        Map<String, Object> result = new HashMap<>();
        Set<String> poNumbers = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
        Set<String> dccIds = dccList.stream().map(dcc -> String.valueOf(dcc.getRecordNo())).collect(Collectors.toSet());

        // Preload Purchase Orders
        result.put("poMap", purchaseOrderRepository.findByPoNumberIn(poNumbers)
                .stream().collect(Collectors.toMap(tbPurchaseOrder::getPoNumber, po -> po, (existing, replacement) -> existing)));

        // Preload Line Items
        result.put("lineItemsMap", dccLineRepo.findByDccIdIn(dccIds)
                .stream().collect(Collectors.groupingBy(DCCLineItem::getDccId)));

        // Preload Approval Requests
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = new HashMap<>();
        tbCategoryApprovalRequestsRepo.findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(
                dccIds.stream().map(Integer::parseInt).collect(Collectors.toList())
        ).forEach(req -> approvalRequestMap.putIfAbsent(req.getAcceptanceRequestRecordNo(), req));
        result.put("approvalRequestMap", approvalRequestMap);

        // Preload Approvals
        Set<Integer> approvalRecordNos = approvalRequestMap.values().stream()
                .map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());
        result.put("approvalsMap", tbCategoryApprovalsRepo.findByApprovalRecordIdIn(approvalRecordNos)
                .stream().collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId)));

        // Preload Users
        Set<String> usernames = new HashSet<>();
        dccList.forEach(dcc -> {
            usernames.add(dcc.getCreatedBy());
            List<tbCategoryApprovals> approvals = ((Map<Integer, List<tbCategoryApprovals>>) result.get("approvalsMap"))
                    .getOrDefault(approvalRequestMap.getOrDefault((int) dcc.getRecordNo(), new tbCategoryApprovalRequests()).getRecordNo(), Collections.emptyList());
            approvals.forEach(approval -> {
                if (approval.getApproverName() != null) usernames.add(approval.getApproverName());
            });
        });
        result.put("userMap", userAccountRepo.findByUsernameIn(usernames)
                .stream().collect(Collectors.toMap(User::getUsername, u -> u)));

        return result;
    }

   private Map<String, Object> buildGroupedRow(
    DCC dcc,
    Map<String, tbPurchaseOrder> poMap,
    Map<String, List<DCCLineItem>> lineItemsMap,
    Map<Integer, tbCategoryApprovalRequests> approvalRequestMap,
    Map<Integer, List<tbCategoryApprovals>> approvalsMap,
    Map<String, User> userMap
) {
    Map<String, Object> row = new LinkedHashMap<>();
    tbPurchaseOrder po = poMap.get(dcc.getPoNumber());
    List<DCCLineItem> lineItems = lineItemsMap.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
    tbCategoryApprovalRequests approvalRequest = approvalRequestMap.get((int) dcc.getRecordNo());
    DCCLineItem ln = lineItems.isEmpty() ? null : lineItems.get(0);

    // DCCId from tb_DCC_LN, currency from tb_PurchaseOrderUPL
    row.put("dccId", dcc.getDccId() != null ? dcc.getDccId() : String.valueOf(dcc.getRecordNo()));
    String currency = null;
for (DCCLineItem dccLn : lineItems) {
    if (dccLn.getUplLineNumber() != null && dccLn.getLineNumber() != null && dccLn.getPoId() != null) {
        tb_PurchaseOrderUPL upl = tbPurchaseOrderUPLRepo.findFirstByPoNumberAndPoLineNumberAndUplLine(
            dccLn.getPoId(), dccLn.getLineNumber(), dccLn.getUplLineNumber()
        );
        if (upl != null && upl.getCurrency() != null) {
            currency = upl.getCurrency();
            break; // take the first available
        }
    }
}
if (currency == null) {
    currency = dcc.getCurrency();
}

    row.put("recordNo", dcc.getRecordNo());
    row.put("projectName", determineProjectName(dcc, po));
    row.put("newProjectName", po != null ? po.getNewProjectName() : null);
    row.put("vendorComment", dcc.getVendorComment());
    row.put("vendorName", dcc.getVendorName());
    row.put("vendorEmail", dcc.getVendorEmail());
    row.put("supplierId", po != null ? po.getVendorNumber() : null);
    row.put("dccCreatedDate", formatDate(dcc.getCreatedDate()));
    row.put("dateApproved", formatDate(approvalRequest != null ? approvalRequest.getApprovedDate() : null));

    // Calculate total delivered quantity for this DCC
    double totalDeliveredQty = lineItems.stream()
        .filter(li -> !Arrays.asList("incomplete", "rejected").contains(dcc.getStatus()))
        .mapToDouble(DCCLineItem::getDeliveredQty)
        .sum();
    row.put("totalDeliveredQty", totalDeliveredQty);

    // Calculate total unit price: sum deliveredQty * uplLineUnitPrice for matching UPL rows
    BigDecimal totalUnitPrice = BigDecimal.ZERO;
    for (DCCLineItem dccLn : lineItems) {
        if (dccLn.getUplLineNumber() != null && dccLn.getLineNumber() != null && dccLn.getPoId() != null) {
            List<tb_PurchaseOrderUPL> uplMatches = tbPurchaseOrderUPLRepo.findByPoNumberAndPoLineNumberAndUplLine(
                dccLn.getPoId(), dccLn.getLineNumber(), dccLn.getUplLineNumber()
            );
            for (tb_PurchaseOrderUPL upl : uplMatches) {
                Double deliveredQtyObj = dccLn.getDeliveredQty();
                double deliveredQty = deliveredQtyObj != null ? deliveredQtyObj : 0.0;
                Double unitPriceObj = upl.getUplLineUnitPrice();
                double unitPrice = unitPriceObj != null ? unitPriceObj : 0.0;
                totalUnitPrice = totalUnitPrice.add(BigDecimal.valueOf(deliveredQty).multiply(BigDecimal.valueOf(unitPrice)));
            }
        }
    }
    row.put("totalUnitPrice", totalUnitPrice);

    // Original calculation for uplacptRequestValue (legacy)
    Double uplAcptRequestValue = calculateTotalDeliveredQtyForDcc(dcc.getRecordNo());
    row.put("uplacptRequestValue", uplAcptRequestValue);

    // Line item details
    row.put("lnLocationName", ln != null ? ln.getLocationName() : null);
    row.put("lnScopeOfWork", ln != null ? ln.getScopeOfWork() : null);
    row.put("lnInserviceDate", formatDate(ln != null ? ln.getDateInService() : null));

    // Aging calculations
List<tbCategoryApprovals> approvals = approvalRequest != null
    ? approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList())
    : Collections.emptyList();

String userAging = calculateUserAgingCustom(dcc, approvalRequest, approvals);
String totalAging = calculateTotalAgingCustom(dcc, approvalRequest, approvals);
row.put("userAging", userAging);
row.put("totalAging", totalAging);
row.put("userAgingInDays", extractDaysFromAging(userAging));
row.put("totalAgingInDays", extractDaysFromAging(totalAging));

    // Request Amount SAR
    row.put("requestAmountSAR", calculateRequestAmount(ln, uplAcptRequestValue));
    row.put("poId", po != null ? po.getPoNumber() : dcc.getPoNumber());

    // Created By details
    String createdByFullName = userMap.getOrDefault(dcc.getCreatedBy(), new User()).getFullName();
    row.put("createdBy", createdByFullName);
    row.put("requestedBy", createdByFullName);
    row.put("createdByName", createdByFullName);

    // Approval Info

    ApprovalInfo approvalInfo = calculateApprovalInfo(approvals, userMap);
    row.put("approvalCount", approvalInfo.approvalCount);
    row.put("approverComment", approvalInfo.approverComment);
    row.put("pendingApprovers", approvalInfo.pendingApproverFullName);
    row.put("departmentName", approvalInfo.pendingApproverDepartmentName);

    row.put("poNumber", dcc.getPoNumber());
    row.put("dccAcceptanceType", dcc.getAcceptanceType());
    row.put("dccStatus", dcc.getStatus());
    row.put("vendorNumber", po != null ? po.getVendorNumber() : dcc.getVendorNumber());

    return row;
}
   

private String determineProjectName(DCC dcc, tbPurchaseOrder po) {
        if (po != null) {
            if (po.getNewProjectName() != null && !po.getNewProjectName().trim().isEmpty()) {
                return po.getNewProjectName();
            } else if (po.getProjectName() != null && !po.getProjectName().trim().isEmpty()) {
                return po.getProjectName();
            }
        }
        return dcc.getProjectName();
    }

    private ApprovalInfo calculateApprovalInfo(List<tbCategoryApprovals> approvals, Map<String, User> userMap) {
        ApprovalInfo info = new ApprovalInfo();
        if (approvals.isEmpty()) return info;

        info.approvalCount = (int) approvals.stream()
                .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                        && "pending".equalsIgnoreCase(al.getStatus()))
                .count();

        Optional<String> readyApprover = approvals.stream()
                .filter(al -> "readyForApproval".equals(al.getApprovalStatus()) && "pending".equalsIgnoreCase(al.getStatus()))
                .map(tbCategoryApprovals::getApproverName)
                .filter(Objects::nonNull)
                .findFirst();
        Optional<String> pendingApprover = approvals.stream()
                .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                        && "pending".equalsIgnoreCase(al.getStatus()))
                .map(tbCategoryApprovals::getApproverName)
                .filter(Objects::nonNull)
                .findFirst();
        String pendingApproverName = readyApprover.orElse(pendingApprover.orElse(null));

     if (pendingApproverName != null) {
    User pendingUser = userMap.get(pendingApproverName);
    if (pendingUser != null) {
        info.pendingApproverFullName = pendingUser.getFullName();
        Integer depId = pendingUser.getDepartmentId();
        departmentsdata dep = null;
       if (depId != null) {
   dep = tbDepartmentRepo.findById(depId.longValue()).orElse(null); // Convert Integer to Long
}
        info.pendingApproverDepartmentName = dep != null ? dep.getDeptName() : null;
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

    private Double calculateRequestAmount(DCCLineItem lineItem, Double deliveredQty) {
        if (lineItem == null || deliveredQty == null || lineItem.getPoId() == null || lineItem.getLineNumber() == null) {
            return 0.0;
        }
        try {
            Integer lineNumber = Integer.valueOf(lineItem.getLineNumber());
            tbPurchaseOrder po = purchaseOrderRepository.findByPoNumberAndLineNumber(lineItem.getPoId(), lineNumber);
            Double unitPrice = po != null ? po.getUnitPriceInSAR() : null;
            return (unitPrice != null ? unitPrice : 0.0) * deliveredQty;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String formatDate(Date date) {
        if (date == null) return null;
        return new SimpleDateFormat(DATE_FORMAT).format(date);
    }

    public double calculateTotalDeliveredQtyForDcc(long dccRecordNo) {
        return dccLineRepo.findByDccIdAndDccStatusNotIn(
                String.valueOf(dccRecordNo),
                Arrays.asList("incomplete", "rejected")
        ).stream().mapToDouble(DCCLineItem::getDeliveredQty).sum();
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

// Helper: Convert your date fields to LocalDateTime

private LocalDateTime toLocalDateTime(LocalDate date) {
    return date == null ? null : date.atStartOfDay();
}
private LocalDateTime toLocalDateTime(Date date) {
    if (date == null) return null;
    if (date instanceof java.sql.Date) {
        // sql.Date represents a date without time info
        return ((java.sql.Date) date).toLocalDate().atStartOfDay();
    } else {
        // util.Date and others
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
// Fetch all requests/approvals for a DCC
  private List<tbCategoryApprovalRequests> getAllRelatedRequests(int acceptanceRequestRecordNo) {
        return tbCategoryApprovalRequestsRepo.findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(acceptanceRequestRecordNo);
    }
    private List<tbCategoryApprovals> getAllRelatedApprovals(List<tbCategoryApprovalRequests> requests) {
        return requests.stream()
            .flatMap(req -> tbCategoryApprovalsRepo.findByApprovalRecordId(req.getRecordNo()).stream())
            .collect(Collectors.toList());
    }

    // Accurate userAging calculation (legacy, fallback)
    private String calculateUserAgingAccurate(tbCategoryApprovalRequests approvalRequest) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovalRequests> allRelatedRequests = getAllRelatedRequests(approvalRequest.getAcceptanceRequestRecordNo());
        List<tbCategoryApprovals> allRelatedApprovals = getAllRelatedApprovals(allRelatedRequests);
        LocalDateTime nowLocal = LocalDateTime.now();

        List<tbCategoryApprovals> filteredApprovals = allRelatedApprovals.stream()
            .filter(a -> a.getApprovalRecordId() == approvalRequest.getRecordNo())
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                Arrays.asList("pending", "readyForApproval", "request-info")
                    .contains(a.getApprovalStatus().toLowerCase()))
            .collect(Collectors.toList());

        String pendingApproverName = filteredApprovals.stream()
            .filter(a -> "readyForApproval".equalsIgnoreCase(a.getApprovalStatus()))
            .findFirst()
            .map(tbCategoryApprovals::getApproverName)
            .orElseGet(() -> filteredApprovals.stream()
                .findFirst()
                .map(tbCategoryApprovals::getApproverName)
                .orElse(null));

        long totalPausedUserAgingMinutes = allRelatedApprovals.stream()
            .filter(a -> "request-info".equalsIgnoreCase(a.getStatus()) &&
                "request-info".equalsIgnoreCase(a.getApprovalStatus()))
            .filter(a -> pendingApproverName != null && pendingApproverName.equals(a.getApproverName()))
            .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
            .mapToLong(a -> Duration.between(
                toLocalDateTime(a.getRecordDateTime()), a.getApprovedDate()
            ).toMinutes())
            .sum();

        long currentUserAgingMinutes = 0;
        if (pendingApproverName != null) {
            Optional<LocalDateTime> latestReadyForApprovalDate = filteredApprovals.stream()
                .filter(a -> "readyForApproval".equalsIgnoreCase(a.getApprovalStatus()) &&
                    pendingApproverName.equals(a.getApproverName()))
                .map(a -> toLocalDateTime(a.getRecordDateTime()))
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);

            currentUserAgingMinutes = latestReadyForApprovalDate
                .map(date -> Duration.between(date, nowLocal).toMinutes())
                .orElseGet(() -> toLocalDateTime(approvalRequest.getRecordDateTime()) != null
                    ? Duration.between(toLocalDateTime(approvalRequest.getRecordDateTime()), nowLocal).toMinutes()
                    : 0L);
        }

        long totalUserAgingMinutes = totalPausedUserAgingMinutes + currentUserAgingMinutes;
        return diffToAgingString(totalUserAgingMinutes);
    }

    // Accurate totalAging calculation (legacy, fallback)
    private String calculateTotalAgingAccurate(tbCategoryApprovalRequests approvalRequest) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovalRequests> allRelatedRequests = getAllRelatedRequests(approvalRequest.getAcceptanceRequestRecordNo());
        List<tbCategoryApprovals> allRelatedApprovals = getAllRelatedApprovals(allRelatedRequests);
        LocalDateTime nowLocal = LocalDateTime.now();

        LocalDateTime minRecordDateTime = allRelatedApprovals.stream()
            .map(a -> toLocalDateTime(a.getRecordDateTime()))
            .filter(Objects::nonNull)
            .min(LocalDateTime::compareTo)
            .orElseGet(() -> toLocalDateTime(approvalRequest.getRecordDateTime()) != null
                ? toLocalDateTime(approvalRequest.getRecordDateTime())
                : nowLocal);

        LocalDateTime endDate = allRelatedApprovals.stream()
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                "pending".equalsIgnoreCase(a.getApprovalStatus()))
            .findAny()
            .map(a -> nowLocal)
            .orElseGet(() -> allRelatedApprovals.stream()
                .map(tbCategoryApprovals::getApprovedDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(nowLocal));

        long totalAgingMinutes = Duration.between(minRecordDateTime, endDate).toMinutes();
        return diffToAgingString(totalAgingMinutes);
    }


private String calculateUserAgingCustom(DCC dcc, tbCategoryApprovalRequests approvalRequest, List<tbCategoryApprovals> approvals) {
    String status = dcc.getStatus() != null ? dcc.getStatus().toLowerCase() : "";
    LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
    LocalDateTime now = LocalDateTime.now();

    if (Arrays.asList("rejected", "returned", "approved", "approved-received").contains(status)) {
        return "0 days 0 hrs 0 mins";
    }
    if ("request-info".equals(status)) {
        Optional<tbCategoryApprovals> reqInfo = approvals.stream()
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "request-info".equalsIgnoreCase(a.getApprovalStatus()))
            .findFirst();
        if (reqInfo.isPresent() && reqInfo.get().getRecordDateTime() != null && reqInfo.get().getApprovedDate() != null) {
            LocalDateTime recordDateTime = ensureLocalDateTime(reqInfo.get().getRecordDateTime());
            LocalDateTime approvedDate = ensureLocalDateTime(reqInfo.get().getApprovedDate());
            long mins = Duration.between(recordDateTime, approvedDate).toMinutes();
            return diffToAgingString(mins);
        }
        return "0 days 0 hrs 0 mins";
    }
    if ("inprocess".equals(status)) {
        // Find the approval row with status=pending and approvalStatus=readyForApproval
        Optional<tbCategoryApprovals> readyRow = approvals.stream()
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "readyforapproval".equalsIgnoreCase(a.getApprovalStatus()))
            .findFirst();
        if (readyRow.isPresent() && readyRow.get().getRecordDateTime() != null) {
            LocalDateTime readyRecordDateTime = ensureLocalDateTime(readyRow.get().getRecordDateTime());
            long mins = Duration.between(readyRecordDateTime, now).toMinutes();
            return diffToAgingString(mins);
        } else {
            // Fallback: use createdDate if no readyForApproval row
            long mins = Duration.between(createdDate, now).toMinutes();
            return diffToAgingString(mins);
        }
    }
    return calculateUserAgingAccurate(approvalRequest);
}

private String calculateTotalAgingCustom(DCC dcc, tbCategoryApprovalRequests approvalRequest, List<tbCategoryApprovals> approvals) {
    String status = dcc.getStatus() != null ? dcc.getStatus().toLowerCase() : "";
    LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
    LocalDateTime now = LocalDateTime.now();

    if (Arrays.asList("rejected", "returned").contains(status)) {
        Optional<tbCategoryApprovals> rejectedApproval = approvals.stream()
            .filter(a -> "rejected".equalsIgnoreCase(a.getStatus()) && "rejected".equalsIgnoreCase(a.getApprovalStatus()))
            .findFirst();
        if (rejectedApproval.isPresent() && rejectedApproval.get().getApprovedDate() != null) {
            LocalDateTime approvedDate = ensureLocalDateTime(rejectedApproval.get().getApprovedDate());
            long mins = Duration.between(createdDate, approvedDate).toMinutes();
            return diffToAgingString(mins);
        }
        return "0 days 0 hrs 0 mins";
    }
    if ("request-info".equals(status)) {
        Optional<tbCategoryApprovals> reqInfo = approvals.stream()
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) && "request-info".equalsIgnoreCase(a.getApprovalStatus()))
            .findFirst();
        if (reqInfo.isPresent() && reqInfo.get().getApprovedDate() != null) {
            LocalDateTime approvedDate = ensureLocalDateTime(reqInfo.get().getApprovedDate());
            // Find the first recordDateTime among all approvals
            Optional<LocalDateTime> firstRecordDateTime = approvals.stream()
                .map(a -> ensureLocalDateTime(a.getRecordDateTime()))
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo);
            if (firstRecordDateTime.isPresent()) {
                long mins = Duration.between(firstRecordDateTime.get(), approvedDate).toMinutes();
                return diffToAgingString(mins);
            }
        }
        return "0 days 0 hrs 0 mins";
    }
    if ("inprocess".equals(status)) {
        // Total aging: first recordDateTime difference with now
        Optional<LocalDateTime> firstRecordDateTime = approvals.stream()
            .map(a -> ensureLocalDateTime(a.getRecordDateTime()))
            .filter(Objects::nonNull)
            .min(LocalDateTime::compareTo);
        if (firstRecordDateTime.isPresent()) {
            long mins = Duration.between(firstRecordDateTime.get(), now).toMinutes();
            return diffToAgingString(mins);
        } else {
            long mins = Duration.between(createdDate, now).toMinutes();
            return diffToAgingString(mins);
        }
    }
    if ("approved".equals(status)) {
        Optional<tbCategoryApprovals> lastApproved = approvals.stream()
            .filter(a -> "approved".equalsIgnoreCase(a.getStatus()) && "approved".equalsIgnoreCase(a.getApprovalStatus()))
            .max(Comparator.comparing(a -> ensureLocalDateTime(a.getApprovedDate())));
        if (lastApproved.isPresent() && lastApproved.get().getApprovedDate() != null) {
            LocalDateTime approvedDate = ensureLocalDateTime(lastApproved.get().getApprovedDate());
            long mins = Duration.between(createdDate, approvedDate).toMinutes();
            return diffToAgingString(mins);
        }
        return "0 days 0 hrs 0 mins";
    }
    if ("approved-received".equals(status)) {
        if (approvalRequest != null && approvalRequest.getApprovedDate() != null) {
            LocalDateTime approvedDate = toLocalDateTime(approvalRequest.getApprovedDate());
            long mins = Duration.between(createdDate, approvedDate).toMinutes();
            return diffToAgingString(mins);
        }
        return "0 days 0 hrs 0 mins";
    }
    return calculateTotalAgingAccurate(approvalRequest);
}
    private LocalDateTime ensureLocalDateTime(Object obj) {
    if (obj == null) return null;
    if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
    if (obj instanceof LocalDate) return ((LocalDate) obj).atStartOfDay();
    if (obj instanceof Date) return toLocalDateTime((Date) obj);
    throw new IllegalArgumentException("Unsupported temporal type: " + obj.getClass());
}

    // Format aging output
    private String diffToAgingString(long totalMinutes) {
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long mins = totalMinutes % 60;
        return String.format("%d days %d hrs %d mins", days, hours, mins);
    }
}
