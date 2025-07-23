package com.zain.almksazain.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;

import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;

@Service
public class DccPoCombinedService {

    @Autowired
    private DCCRepository dccRepository;
    @Autowired
    private DccLineRepo dccLineRepo;
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private tbPurchaseOrderUPLRepo tbPurchaseOrderUPLRepo;
    @Autowired
    private tbCategoryApprovalRequestsRepo tbCategoryApprovalRequestsRepo;
    @Autowired
    private tbCategoryApprovalsRepo tbCategoryApprovalsRepo;
public Map<String, Object> getAgingReport(String supplierId, String columnName, String searchQuery, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        // Define column mappings: result map column name -> (entity field name, type)
        final Map<String, Map.Entry<String, String>> dccColumns = new HashMap<>();
        dccColumns.put("recordNo", Map.entry("recordNo", "numeric"));
        dccColumns.put("vendorComment", Map.entry("vendorComment", "string"));
        dccColumns.put("vendorEmail", Map.entry("vendorEmail", "string"));
        dccColumns.put("dccId", Map.entry("dccId", "string"));
        dccColumns.put("dccCreatedDate", Map.entry("createdDate", "date"));
        dccColumns.put("poNumber", Map.entry("poNumber", "string"));
        dccColumns.put("dccAcceptanceType", Map.entry("acceptanceType", "string"));
        dccColumns.put("dccStatus", Map.entry("status", "string"));
        dccColumns.put("createdBy", Map.entry("createdBy", "string"));
        dccColumns.put("createdByName", Map.entry("createdBy", "string")); // Assuming createdByName uses createdBy

        final Map<String, Map.Entry<String, String>> poColumns = new HashMap<>();
        poColumns.put("projectName", Map.entry("newProjectName", "string")); // or projectName based on logic
        poColumns.put("newProjectName", Map.entry("newProjectName", "string"));
        poColumns.put("supplierId", Map.entry("vendorNumber", "string"));
        poColumns.put("departmentName", Map.entry("departmentName", "string"));
        poColumns.put("poId", Map.entry("poNumber", "string"));

        final Map<String, Map.Entry<String, String>> lineItemColumns = new HashMap<>();
        lineItemColumns.put("lnLocationName", Map.entry("locationName", "string"));
        lineItemColumns.put("lnScopeOfWork", Map.entry("scopeOfWork", "string"));
        lineItemColumns.put("lnInserviceDate", Map.entry("dateInService", "date"));

        final Map<String, Map.Entry<String, String>> approvalRequestColumns = new HashMap<>();
        approvalRequestColumns.put("dateApproved", Map.entry("approvedDate", "date"));

        final List<String> calculatedColumns = Arrays.asList(
            "uplacptRequestValue", "userAging", "totalAging", "userAgingInDays",
            "totalAgingInDays", "Request Amount (SAR)", "approvalCount", "approverComment", "pendingApprovers"
        );

        // Build Specification for database filtering
        Specification<DCC> spec = Specification.where(null);

        if (supplierId != null && !"0".equals(supplierId)) {
            spec = spec.and((root, query, cb) -> {
                Subquery<String> poSub = query.subquery(String.class);
                Root<PurchaseOrderTb> poRoot = poSub.from(PurchaseOrderTb.class);
                poSub.select(poRoot.get("poNumber"));
                poSub.where(cb.equal(poRoot.get("vendorNumber"), supplierId));
                return root.get("poNumber").in(poSub);
            });
        }

        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && !calculatedColumns.contains(columnName)) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true); // Avoid duplicates from joins
                try {
                    if (dccColumns.containsKey(columnName)) {
                        Map.Entry<String, String> entry = dccColumns.get(columnName);
                        String fieldName = entry.getKey();
                        String type = entry.getValue();
                        if ("string".equals(type)) {
                            return cb.like(cb.lower(root.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
                        } else if ("numeric".equals(type)) {
                            return cb.equal(root.get(fieldName), Long.parseLong(searchQuery));
                        } else if ("date".equals(type)) {
                            SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy");
                            Date parsedDate = sdf.parse(searchQuery);
                            return cb.equal(root.get(fieldName), parsedDate);
                        }
                    } else if (poColumns.containsKey(columnName)) {
                        Subquery<String> poSub = query.subquery(String.class);
                        Root<PurchaseOrderTb> poRoot = poSub.from(PurchaseOrderTb.class);
                        poSub.select(poRoot.get("poNumber"));
                        Map.Entry<String, String> entry = poColumns.get(columnName);
                        String fieldName = entry.getKey();
                        String type = entry.getValue();
                        if ("string".equals(type)) {
                            poSub.where(cb.like(cb.lower(poRoot.get(fieldName)), "%" + searchQuery.toLowerCase() + "%"));
                        } else if ("numeric".equals(type)) {
                            poSub.where(cb.equal(poRoot.get(fieldName), Long.parseLong(searchQuery)));
                        }
                        return root.get("poNumber").in(poSub);
                    } else if (lineItemColumns.containsKey(columnName)) {
                        Join<DCC, DCCLineItem> lineJoin = root.join("dccLineItems", JoinType.LEFT);
                        Map.Entry<String, String> entry = lineItemColumns.get(columnName);
                        String fieldName = entry.getKey();
                        String type = entry.getValue();
                        if ("string".equals(type)) {
                            return cb.like(cb.lower(lineJoin.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
                        } else if ("date".equals(type)) {
                            SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy");
                            Date parsedDate = sdf.parse(searchQuery);
                            return cb.equal(lineJoin.get(fieldName), parsedDate);
                        }
                    } else if (approvalRequestColumns.containsKey(columnName)) {
                        Subquery<Integer> approvalSub = query.subquery(Integer.class);
                        Root<tbCategoryApprovalRequests> approvalRoot = approvalSub.from(tbCategoryApprovalRequests.class);
                        approvalSub.select(approvalRoot.get("acceptanceRequestRecordNo"));
                        Map.Entry<String, String> entry = approvalRequestColumns.get(columnName);
                        String fieldName = entry.getKey();
                        SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy");
                        Date parsedDate = sdf.parse(searchQuery);
                        approvalSub.where(cb.equal(approvalRoot.get(fieldName), parsedDate));
                        return cb.equal(root.get("recordNo"), approvalSub);
                    }
                } catch (NumberFormatException | ParseException e) {
                    // Invalid number or date format; skip filtering
                    return null;
                }
                return null; // No filtering if columnName is invalid
            });
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
        Page<DCC> pagedDcc = dccRepository.findAll(spec, pageable);
        List<DCC> dccList = pagedDcc.getContent();

        // Preload related data for performance
        Set<String> poNumbers = dccList.stream().map(DCC::getPoNumber).collect(Collectors.toSet());
        Map<String, PurchaseOrderTb> poMap = purchaseOrderRepository.findByPoNumberIn(poNumbers)
            .stream().collect(Collectors.toMap(
                PurchaseOrderTb::getPoNumber,
                po -> po,
                (existing, replacement) -> existing
            ));

        // Collect dccIds as Set<String>
        Set<String> dccIds = dccList.stream()
            .map(dcc -> String.valueOf(dcc.getRecordNo()))
            .collect(Collectors.toSet());

        // Fetch line items
        Map<String, List<DCCLineItem>> lineItemsMap = dccLineRepo.findByDccIdIn(dccIds)
            .stream().collect(Collectors.groupingBy(DCCLineItem::getDccId));

        // Fetch approval requests
        Map<Integer, tbCategoryApprovalRequests> latestApprovalRequestMap = new HashMap<>();
        List<tbCategoryApprovalRequests> allApprovalRequests = tbCategoryApprovalRequestsRepo
            .findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(
                dccIds.stream().map(Integer::parseInt).collect(Collectors.toList())
            );
        for (tbCategoryApprovalRequests req : allApprovalRequests) {
            latestApprovalRequestMap.putIfAbsent(req.getAcceptanceRequestRecordNo(), req);
        }

        Set<Integer> approvalRecordNos = latestApprovalRequestMap.values().stream()
            .map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toSet());
        Map<Integer, List<tbCategoryApprovals>> approvalsMap = tbCategoryApprovalsRepo.findByApprovalRecordIdIn(approvalRecordNos)
            .stream().collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId));

        // Map and group result
        List<Map<String, Object>> groupedResults = new ArrayList<>();
        for (DCC dcc : dccList) {
            Map<String, Object> groupedRow = new LinkedHashMap<>();

            PurchaseOrderTb po = poMap.get(dcc.getPoNumber());
            List<DCCLineItem> lineItems = lineItemsMap.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
            tbCategoryApprovalRequests latestApprovalRequest = latestApprovalRequestMap.get((int) dcc.getRecordNo());
            DCCLineItem ln = lineItems.isEmpty() ? null : lineItems.get(0);

            groupedRow.put("recordNo", dcc.getRecordNo());
            String projectName = null;
            String newProjectName = null;
            if (po != null) {
                newProjectName = po.getNewProjectName();
                if (newProjectName != null && !newProjectName.trim().isEmpty()) {
                    projectName = newProjectName;
                } else if (po.getProjectName() != null && !po.getProjectName().trim().isEmpty()) {
                    projectName = po.getProjectName();
                }
            }
            if (projectName == null || projectName.trim().isEmpty()) {
                projectName = dcc.getProjectName();
            }
            groupedRow.put("projectName", projectName);
            groupedRow.put("newProjectName", newProjectName);
            groupedRow.put("vendorComment", dcc.getVendorComment());
            groupedRow.put("vendorEmail", dcc.getVendorEmail());
            groupedRow.put("dccId", dcc.getDccId());
            groupedRow.put("supplierId", po != null ? po.getVendorNumber() : null);
            groupedRow.put("dccCreatedDate", formatDate(dcc.getCreatedDate()));
            groupedRow.put("dateApproved", formatDate(latestApprovalRequest != null ? latestApprovalRequest.getApprovedDate() : null));

            Double uplAcptRequestValue = calculateUPLACPTRequestValue(dcc, ln);
            groupedRow.put("uplacptRequestValue", uplAcptRequestValue);

            groupedRow.put("lnLocationName", ln != null ? ln.getLocationName() : null);
            groupedRow.put("lnScopeOfWork", ln != null ? ln.getScopeOfWork() : null);
            groupedRow.put("lnInserviceDate", formatDate(ln != null ? ln.getDateInService() : null));
            groupedRow.put("departmentName", po != null ? po.getDepartmentName() : null);

            String userAging = calculateUserAging(latestApprovalRequest, dcc.getRecordNo(), approvalsMap);
            String totalAging = calculateTotalAging(latestApprovalRequest, dcc.getRecordNo(), approvalsMap);
            groupedRow.put("userAging", userAging);
            groupedRow.put("totalAging", totalAging);
            groupedRow.put("userAgingInDays", extractDaysFromAging(userAging));
            groupedRow.put("totalAgingInDays", extractDaysFromAging(totalAging));

            Double unitPriceInSAR = ln != null && ln.getUnitPrice() != null ? ln.getUnitPrice().doubleValue() : null;
            Double requestAmountSAR = calculateRequestAmount(unitPriceInSAR, uplAcptRequestValue);
            groupedRow.put("Request Amount (SAR)", requestAmountSAR);
            groupedRow.put("poId", po != null ? po.getPoNumber() : dcc.getPoNumber());
            groupedRow.put("createdBy", dcc.getCreatedBy());
            groupedRow.put("createdByName", dcc.getCreatedBy());

            // Approval info
            int approvalCount = 0;
            String approverComment = null;
            String pendingApprovers = null;
            List<tbCategoryApprovals> approvals = latestApprovalRequest != null
                ? approvalsMap.getOrDefault(latestApprovalRequest.getRecordNo(), Collections.emptyList())
                : Collections.emptyList();
            if (!approvals.isEmpty()) {
                approvalCount = (int) approvals.stream()
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
                pendingApprovers = readyApprover.orElse(pendingApprover.orElse(null));
                approverComment = approvals.stream()
                    .filter(al -> !Arrays.asList("pending", "readyForApproval").contains(al.getApprovalStatus()))
                    .map(tbCategoryApprovals::getComments)
                    .filter(Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse(null);
            }
            groupedRow.put("approvalCount", approvalCount);
            groupedRow.put("approverComment", approverComment);
            groupedRow.put("pendingApprovers", pendingApprovers);
            groupedRow.put("poNumber", dcc.getPoNumber());
            groupedRow.put("dccAcceptanceType", dcc.getAcceptanceType());
            groupedRow.put("dccStatus", dcc.getStatus());

            groupedResults.add(groupedRow);
        }

        // Post-processing for calculated columns
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && calculatedColumns.contains(columnName)) {
            String searchPattern = searchQuery.toLowerCase();
            groupedResults = groupedResults.stream()
                .filter(row -> {
                    Object value = row.get(columnName);
                    if (value == null) return false;
                    String stringValue = String.valueOf(value).toLowerCase();
                    return stringValue.contains(searchPattern);
                })
                .collect(Collectors.toList());
        }

        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", pagedDcc.getTotalElements());
        response.put("totalPages", pagedDcc.getTotalPages());
        response.put("data", groupedResults);

        return response;
    }

    // Helper for Approval/Aging logic
    private String calculateUserAging(tbCategoryApprovalRequests approvalRequest, Long dccRecordNo, Map<Integer, List<tbCategoryApprovals>> approvalsMap) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovals> approvals = approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList());
        Optional<LocalDate> lastPendingOrReady = approvals.stream()
            .filter(al -> Arrays.asList("pending", "readyForApproval", "request-info").contains(al.getApprovalStatus())
                && "pending".equalsIgnoreCase(al.getStatus()))
            .map(tbCategoryApprovals::getRecordDateTime)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo);
        LocalDateTime now = LocalDateTime.now();
        long diffMinutes = lastPendingOrReady
            .map(ld -> Duration.between(ld.atStartOfDay(), now).toMinutes())
            .orElse(0L);
        return diffToAgingString(diffMinutes);
    }

    private String calculateTotalAging(tbCategoryApprovalRequests approvalRequest, Long dccRecordNo, Map<Integer, List<tbCategoryApprovals>> approvalsMap) {
        if (approvalRequest == null) return "0 days 0 hrs 0 mins";
        List<tbCategoryApprovals> approvals = approvalsMap.getOrDefault(approvalRequest.getRecordNo(), Collections.emptyList());
        Optional<LocalDate> minPending = approvals.stream()
            .filter(al -> "pending".equalsIgnoreCase(al.getApprovalStatus()) && "pending".equalsIgnoreCase(al.getStatus()))
            .map(tbCategoryApprovals::getRecordDateTime)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo);
        Optional<LocalDateTime> maxApproved = approvals.stream()
            .map(tbCategoryApprovals::getApprovedDate)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo);
        LocalDateTime now = LocalDateTime.now();
        long diffMinutes = 0;
        if (minPending.isPresent() && maxApproved.isPresent()) {
            diffMinutes = java.time.Duration.between(
                minPending.get().atStartOfDay(),
                maxApproved.get()
            ).toMinutes();
        } else if (minPending.isPresent()) {
            diffMinutes = java.time.Duration.between(
                minPending.get().atStartOfDay(),
                now
            ).toMinutes();
        }
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
            String[] parts = agingString.trim().split("\\s+");
            if (parts.length > 0) return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
        return 0;
    }

    private Double calculateRequestAmount(Double unitPriceInSAR, Double uplRequestValue) {
        double unitPrice = (unitPriceInSAR != null) ? unitPriceInSAR : 0.0;
        double requestValue = (uplRequestValue != null) ? uplRequestValue : 0.0;
        return unitPrice * requestValue;
    }

    private String formatDate(Date date) {
        if (date == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy");
        return sdf.format(date);
    }

    private Double calculateUPLACPTRequestValue(DCC dcc, DCCLineItem ln) {
        if (ln == null || ln.getUplLineNumber() == null) return 0.0;
        List<tb_PurchaseOrderUPL> uplList = tbPurchaseOrderUPLRepo.findByPoNumber(dcc.getPoNumber());
        for (tb_PurchaseOrderUPL upl : uplList) {
            if (upl.getUplLine() != null && upl.getUplLine().equals(ln.getUplLineNumber()) &&
                upl.getPoLineNumber() != null && upl.getPoLineNumber().equals(ln.getLineNumber()) &&
                upl.getUplLineQuantity() > 0) {
                List<DCCLineItem> dccLnMatches = dccLineRepo.findByPoIdAndLineNumberAndUplLineNumberAndDccStatusNotIn(
                    dcc.getPoNumber(), ln.getLineNumber(), ln.getUplLineNumber(), Arrays.asList("incomplete", "rejected")
                );
                double totalDelivered = dccLnMatches.stream()
                    .mapToDouble(item -> item.getDeliveredQty())
                    .sum();
                return totalDelivered;
            }
        }
        return 0.0;
    }
}