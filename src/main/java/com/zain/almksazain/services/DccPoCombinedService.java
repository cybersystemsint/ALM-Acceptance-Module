package com.zain.almksazain.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;

@Service
public class DccPoCombinedService {

    private static final String DATE_FORMAT = "d-MMM-yyyy";

    @Autowired private DCCRepository dccRepository;
    @Autowired private DccLineRepo dccLineRepo;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private tbPurchaseOrderUPLRepo tbPurchaseOrderUPLRepo;
    @Autowired private tbCategoryApprovalRequestsRepo tbCategoryApprovalRequestsRepo;
    @Autowired private tbCategoryApprovalsRepo tbCategoryApprovalsRepo;
    @Autowired private UserAccountRepo userAccountRepo;
    @Autowired private tbDepartmentRepo tbDepartmentRepo;

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
        Specification<DCC> spec = buildSpecification(supplierId, columnName, searchQuery);
        Page<DCC> pagedDcc = dccRepository.findAll(spec, pageable);
        List<DCC> dccList = pagedDcc.getContent();

        // Preload related data
        Map<String, Object> preloadedData = preloadRelatedData(dccList);
        Map<String, PurchaseOrderTb> poMap = (Map<String, PurchaseOrderTb>) preloadedData.get("poMap");
        Map<String, List<DCCLineItem>> lineItemsMap = (Map<String, List<DCCLineItem>>) preloadedData.get("lineItemsMap");
        Map<Integer, tbCategoryApprovalRequests> approvalRequestMap = (Map<Integer, tbCategoryApprovalRequests>) preloadedData.get("approvalRequestMap");
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = (Map<Integer, List<tbCategoryApprovals>>) preloadedData.get("approvalsMap");
        Map<String, UserAccount> userMap = (Map<String, UserAccount>) preloadedData.get("userMap");

        List<Map<String, Object>> groupedResults = dccList.stream()
                .map(dcc -> buildGroupedRow(dcc, poMap, lineItemsMap, approvalRequestMap, approvalsMap, userMap))
                .collect(Collectors.toList());

        // Filter calculated columns if needed
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && CALCULATED_COLUMNS.contains(columnName)) {
            String searchPattern = searchQuery.toLowerCase();
            groupedResults = groupedResults.stream()
                    .filter(row -> {
                        Object value = row.get(columnName);
                        return value != null && String.valueOf(value).toLowerCase().contains(searchPattern);
                    })
                    .collect(Collectors.toList());
        }

        return buildResponse(pagedDcc, groupedResults, page, size);
    }

    private Specification<DCC> buildSpecification(String supplierId, String columnName, String searchQuery) {
        Specification<DCC> spec = Specification.where(null);

        if (supplierId != null && !"0".equals(supplierId)) {
            spec = spec.and((root, query, cb) -> {
                Subquery<String> poSub = query.subquery(String.class);
                Root<PurchaseOrderTb> poRoot = poSub.from(PurchaseOrderTb.class);
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
        Root<PurchaseOrderTb> poRoot = poSub.from(PurchaseOrderTb.class);
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
                .stream().collect(Collectors.toMap(PurchaseOrderTb::getPoNumber, po -> po, (existing, replacement) -> existing)));

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
                .stream().collect(Collectors.toMap(UserAccount::getUsername, u -> u)));

        return result;
    }

    private Map<String, Object> buildGroupedRow(DCC dcc, Map<String, PurchaseOrderTb> poMap,
            Map<String, List<DCCLineItem>> lineItemsMap, Map<Integer, tbCategoryApprovalRequests> approvalRequestMap,
            Map<Integer, List<tbCategoryApprovals>> approvalsMap, Map<String, UserAccount> userMap) {
        Map<String, Object> row = new LinkedHashMap<>();
        PurchaseOrderTb po = poMap.get(dcc.getPoNumber());
        List<DCCLineItem> lineItems = lineItemsMap.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
        tbCategoryApprovalRequests approvalRequest = approvalRequestMap.get((int) dcc.getRecordNo());
        DCCLineItem ln = lineItems.isEmpty() ? null : lineItems.get(0);

        row.put("recordNo", dcc.getRecordNo());
        row.put("projectName", determineProjectName(dcc, po));
        row.put("newProjectName", po != null ? po.getNewProjectName() : null);
        row.put("vendorComment", dcc.getVendorComment());
        row.put("vendorName", dcc.getVendorName());
        row.put("vendorEmail", dcc.getVendorEmail());
        row.put("dccId", dcc.getDccId());
        row.put("supplierId", po != null ? po.getVendorNumber() : null);
        row.put("dccCreatedDate", formatDate(dcc.getCreatedDate()));
        row.put("dateApproved", formatDate(approvalRequest != null ? approvalRequest.getApprovedDate() : null));

        Double uplAcptRequestValue = calculateTotalDeliveredQtyForDcc(dcc.getRecordNo());
        row.put("uplacptRequestValue", uplAcptRequestValue);
        row.put("lnLocationName", ln != null ? ln.getLocationName() : null);
        row.put("lnScopeOfWork", ln != null ? ln.getScopeOfWork() : null);
        row.put("lnInserviceDate", formatDate(ln != null ? ln.getDateInService() : null));

        String userAging = calculateUserAging(approvalRequest, dcc.getRecordNo(), approvalsMap);
        String totalAging = calculateTotalAging(approvalRequest, dcc.getRecordNo(), approvalsMap);
        row.put("userAging", userAging);
        row.put("totalAging", totalAging);
        row.put("userAgingInDays", extractDaysFromAging(userAging));
        row.put("totalAgingInDays", extractDaysFromAging(totalAging));

        row.put("requestAmountSAR", calculateRequestAmount(ln, uplAcptRequestValue));
        row.put("poId", po != null ? po.getPoNumber() : dcc.getPoNumber());

        // Created By
        String createdByFullName = userMap.getOrDefault(dcc.getCreatedBy(), new UserAccount()).getFullName();
        row.put("createdBy", createdByFullName);
        row.put("requestedBy", createdByFullName);
        row.put("createdByName", createdByFullName);

        // Approval Info
        List<tbCategoryApprovals> approvals = approvalRequest != null
                ? approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList())
                : Collections.emptyList();
        ApprovalInfo approvalInfo = calculateApprovalInfo(approvals, userMap);
        row.put("approvalCount", approvalInfo.approvalCount);
        row.put("approverComment", approvalInfo.approverComment);
        row.put("pendingApprovers", approvalInfo.pendingApproverFullName);
        row.put("departmentName", approvalInfo.pendingApproverDepartmentName);
        row.put("poNumber", dcc.getPoNumber());
        row.put("dccAcceptanceType", dcc.getAcceptanceType());
        row.put("dccStatus", dcc.getStatus());

        return row;
    }

    private String determineProjectName(DCC dcc, PurchaseOrderTb po) {
        if (po != null) {
            if (po.getNewProjectName() != null && !po.getNewProjectName().trim().isEmpty()) {
                return po.getNewProjectName();
            } else if (po.getProjectName() != null && !po.getProjectName().trim().isEmpty()) {
                return po.getProjectName();
            }
        }
        return dcc.getProjectName();
    }

    private ApprovalInfo calculateApprovalInfo(List<tbCategoryApprovals> approvals, Map<String, UserAccount> userMap) {
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
            UserAccount pendingUser = userMap.get(pendingApproverName);
            if (pendingUser != null) {
                info.pendingApproverFullName = pendingUser.getFullName();
                tb_department dep = pendingUser.getDepartment();
                info.pendingApproverDepartmentName = dep != null ? dep.getDepartmentName() : null;
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

    private String calculateUserAging(tbCategoryApprovalRequests approvalRequest, Long dccRecordNo, Map<Integer, List<tbCategoryApprovals>> approvalsMap) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovals> approvals = approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList());
        Optional<LocalDateTime> lastPendingOrReady = approvals.stream()
                .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                        && "pending".equalsIgnoreCase(al.getStatus()))
                .map(tbCategoryApprovals::getRecordDateTime)
                .filter(Objects::nonNull)
                .map(LocalDate::atStartOfDay)
                .max(LocalDateTime::compareTo);
        long diffMinutes = lastPendingOrReady
                .map(ld -> Duration.between(ld, LocalDateTime.now()).toMinutes())
                .orElse(0L);
        return diffToAgingString(diffMinutes);
    }

    private String calculateTotalAging(tbCategoryApprovalRequests approvalRequest, Long dccRecordNo, Map<Integer, List<tbCategoryApprovals>> approvalsMap) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovals> approvals = approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList());
        Optional<LocalDateTime> minPending = approvals.stream()
                .filter(al -> "pending".equalsIgnoreCase(al.getApprovalStatus()) && "pending".equalsIgnoreCase(al.getStatus()))
                .map(tbCategoryApprovals::getRecordDateTime)
                .filter(Objects::nonNull)
                .map(LocalDate::atStartOfDay)
                .min(LocalDateTime::compareTo);
        Optional<LocalDateTime> maxApproved = approvals.stream()
                .map(tbCategoryApprovals::getApprovedDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);
        long diffMinutes = minPending.map(pending -> maxApproved
                .map(approved -> Duration.between(pending, approved).toMinutes())
                .orElse(Duration.between(pending, LocalDateTime.now()).toMinutes()))
                .orElse(0L);
        return diffToAgingString(diffMinutes);
    }

    private String diffToAgingString(long totalMinutes) {
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long mins = totalMinutes % 60;
        return String.format("%d days %d hrs %d mins", days, hours, mins);
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
            PurchaseOrderTb po = purchaseOrderRepository.findByPoNumberAndLineNumber(lineItem.getPoId(), lineNumber);
            double unitPrice = po != null && po.getUnitPriceInSAR() != null ? po.getUnitPriceInSAR() : 0.0;
            return unitPrice * deliveredQty;
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
}