package com.zain.almksazain.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.criteria.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.zain.almksazain.dto.AgingReportApproverDTO;
import com.zain.almksazain.dto.AgingReportDTO;
import com.zain.almksazain.dto.AgingReportGroupDTO;
import com.zain.almksazain.dto.AgingReportPagedResponseDTO;
import com.zain.almksazain.dto.AgingReportRequestDTO;
import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.departmentsdata;
import com.zain.almksazain.model.tbCategoryApprovalRequests;
import com.zain.almksazain.model.tbCategoryApprovals;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.repo.DCCRepository;
import com.zain.almksazain.repo.DccLineRepo;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.deptsrepo;
import com.zain.almksazain.repo.tbPurchaseOrderRepo;
import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;

@Service
public class AgingReportService {
    private static final Logger logger = LogManager.getLogger(AgingReportService.class);
    @Autowired
    private TbCategoryApprovalRequestsRepository approvalRequestsRepository;
    @Autowired
    private TbCategoryApprovalsRepository approvalsRepository;
    @Autowired
    private DCCRepository dccRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private deptsrepo departmentsDataRepository;
    @Autowired
    private DccLineRepo dccLineItemRepository;
    @Autowired
    private tbPurchaseOrderUPLRepo purchaseOrderUPLRepository;
    @Autowired
    private tbPurchaseOrderRepo tbPurchaseOrderRepository;

    private static final ZoneId KSA_ZONE = ZoneId.of("Asia/Riyadh");

    private static final java.time.format.DateTimeFormatter DT_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    // Bucket definitions
    private static final List<String> BUCKETS = Arrays.asList(
            "sameDay", "oneDay", "twoDay", "threeDay", "fourToSevenDays", "oneToTwoWeeks",
            "twoToFourWeeks", "oneToTwoMonths", "twoToThreeMonths", "threePlusMonths"
    );
    private static final List<long[]> BUCKET_RANGES = Arrays.asList(
            new long[]{0, 1440}, new long[]{1440, 2880}, new long[]{2880, 4320},
            new long[]{4320, 5760}, new long[]{5760, 10080}, new long[]{10080, 20160},
            new long[]{20160, 40320}, new long[]{40320, 86400}, new long[]{86400, 129600},
            new long[]{129600, Long.MAX_VALUE}
    );

    public AgingReportPagedResponseDTO getGroupedAgingReport(AgingReportRequestDTO req) {
        Specification<DCC> spec = getDccSpecification(req);

        Integer reqPage = req.getPage();
        Integer reqSize = req.getSize();

        List<DCC> dccList;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate");
        long totalRecords;
        int page, size;

        // If pagination params are not provided or invalid, fetch all
        if (reqSize == null || reqSize <= 0 || reqPage == null || reqPage < 0) {
            dccList = dccRepository.findAll(spec, sort);
            totalRecords = dccList.size();
            size = dccList.size();
            page = 0;
        } else {
            PageRequest pageReq = PageRequest.of(reqPage, reqSize, sort);
            Page<DCC> dccPage = dccRepository.findAll(spec, pageReq);
            dccList = dccPage.getContent();
            totalRecords = dccPage.getTotalElements();
            size = reqSize;
            page = reqPage;
        }

        // --- Batch fetch related entities ---
        List<Integer> dccRecordNos = dccList.stream().map(d -> (int) d.getRecordNo()).collect(Collectors.toList());
        List<tbCategoryApprovalRequests> allApprovalRequests = approvalRequestsRepository
                .findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(dccRecordNos);
        Map<Integer, tbCategoryApprovalRequests> latestReqMap = new HashMap<>();
        for (tbCategoryApprovalRequests reqObj : allApprovalRequests) {
            latestReqMap.putIfAbsent(reqObj.getAcceptanceRequestRecordNo(), reqObj);
        }
        List<Integer> approvalRequestIds = allApprovalRequests.stream()
                .map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toList());
        List<tbCategoryApprovals> allApprovals = approvalsRepository.findByApprovalRecordIdIn(approvalRequestIds);
        Map<Integer, List<tbCategoryApprovals>> approvalsByReqId = allApprovals.stream()
                .collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId));

        Set<String> allApproverNames = allApprovals.stream()
                .map(tbCategoryApprovals::getApproverName).collect(Collectors.toSet());
        List<User> allUsers = userRepository.findByUsernameIn(new ArrayList<>(allApproverNames));
        Map<String, User> userByUsername = allUsers.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u));
        Set<Integer> allDeptIds = allUsers.stream().map(User::getDepartmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<departmentsdata> allDepts = departmentsDataRepository
                .findAllById(allDeptIds.stream().map(Long::valueOf).collect(Collectors.toList()));
        Map<Integer, String> deptNameById = allDepts.stream()
                .collect(Collectors.toMap(d -> (int) d.getRecordNo(), departmentsdata::getDeptName));

        LocalDateTime now = LocalDateTime.now(KSA_ZONE);

        List<String> dccIds = dccList.stream().map(d -> String.valueOf(d.getRecordNo())).collect(Collectors.toList());
        List<DCCLineItem> allLineItems = dccLineItemRepository.findByDccIdIn(dccIds);
        Map<String, List<DCCLineItem>> lineItemsByDccId = allLineItems.stream()
                .collect(Collectors.groupingBy(DCCLineItem::getDccId));

        Set<String> poNumbers = new HashSet<>(), lineNumbers = new HashSet<>(), uplLineNumbers = new HashSet<>();
        for (DCCLineItem item : allLineItems) {
            if (item.getPoId() != null) poNumbers.add(item.getPoId());
            if (item.getLineNumber() != null) lineNumbers.add(item.getLineNumber());
            if (item.getUplLineNumber() != null) uplLineNumbers.add(item.getUplLineNumber());
        }
        List<tb_PurchaseOrderUPL> uplList = purchaseOrderUPLRepository
                .findByPoNumberInAndPoLineNumberInAndUplLineIn(
                        new ArrayList<>(poNumbers), new ArrayList<>(lineNumbers), new ArrayList<>(uplLineNumbers));
        Map<String, tb_PurchaseOrderUPL> uplMap = new HashMap<>();
        for (tb_PurchaseOrderUPL upl : uplList) {
            String key = upl.getPoNumber() + "|" + upl.getPoLineNumber() + "|" + upl.getUplLine();
            uplMap.put(key, upl);
        }

        // --- BATCH FETCH Purchase Orders for projectName filtering (new) ---
        Map<String, tbPurchaseOrder> purchaseOrderMap = new HashMap<>();
        if (!poNumbers.isEmpty()) {
            List<tbPurchaseOrder> poList = tbPurchaseOrderRepository.findByPoNumberIn(new ArrayList<>(poNumbers));
            for (tbPurchaseOrder po : poList) {
                if (po != null && po.poNumber != null) {
                    purchaseOrderMap.put(po.poNumber, po);
                }
            }
        }

        // --- Build flat DTO list ---
        List<AgingReportDTO> flatReport = new ArrayList<>();
        for (DCC dcc : dccList) {
            tbCategoryApprovalRequests latestApprovalReq = latestReqMap.getOrDefault((int) dcc.getRecordNo(), null);
            AgingDurations agingDurations = calculateAgingDurations(
                    dcc, latestApprovalReq, approvalsByReqId, userByUsername, deptNameById, now);
            List<DCCLineItem> lineItems = lineItemsByDccId.getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());
            BigDecimal totalDeliveredQty = BigDecimal.ZERO, totalUnitPrice = BigDecimal.ZERO;
            String currency = null;
            for (DCCLineItem line : lineItems) {
                BigDecimal deliveredQty = BigDecimal.valueOf(line.getDeliveredQty());
                totalDeliveredQty = totalDeliveredQty.add(deliveredQty);
                String uplKey = dcc.getPoNumber() + "|" + line.getLineNumber() + "|" + line.getUplLineNumber();
                tb_PurchaseOrderUPL upl = uplMap.get(uplKey);
                if (upl != null) {
                    BigDecimal unitPrice = BigDecimal.valueOf(upl.getUplLineUnitPrice());
                    totalUnitPrice = totalUnitPrice.add(unitPrice.multiply(deliveredQty));
                    if (currency == null && upl.getCurrency() != null) currency = upl.getCurrency();
                }
            }
            totalUnitPrice = totalUnitPrice.setScale(5, java.math.RoundingMode.HALF_UP);
            String createdDateStr = null;
            if (dcc.getCreatedDate() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                createdDateStr = sdf.format(dcc.getCreatedDate());
            }
String projectName = null;
if (dcc.getPoNumber() != null && purchaseOrderMap != null) {
    tbPurchaseOrder po = purchaseOrderMap.get(dcc.getPoNumber());
    if (po != null) {
        projectName = po.getProjectName(); // tbPurchaseOrder.getProjectName() should already return newProjectName if present
    }
}

// aggregate in-scope-of-work values from the DCC's line items
List<DCCLineItem> lineItemsForDcc = lineItems; // already retrieved above for this DCC
String inScopeOfWork = lineItemsForDcc.stream()
        .map(li -> {
            if (li.getScopeOfWork() != null && !li.getScopeOfWork().trim().isEmpty()) {
                return li.getScopeOfWork().trim();
            }
            return li.getScopeOfWork() != null ? li.getScopeOfWork().trim() : null;
        })
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .collect(Collectors.joining(", "));
            AgingReportDTO dto = new AgingReportDTO(
                    (int) dcc.getRecordNo(), dcc.getPoNumber(), dcc.getVendorName(), dcc.getStatus(),
                    createdDateStr, dcc.getCreatedBy(),
                    dcc.getSupplierId() != null ? dcc.getSupplierId() : dcc.getVendorNumber(), dcc.getVendorNumber(),
                    agingDurations.userAging, agingDurations.totalAging, agingDurations.approvalCount, agingDurations.pendingApprovers,
                    agingDurations.departmentName, totalDeliveredQty, totalUnitPrice, currency, projectName, inScopeOfWork
            );
            flatReport.add(dto);
        }

        // --- Apply in-memory filter BEFORE grouping ---
        List<AgingReportDTO> filteredFlatReport = filterFlatReport(flatReport, req, lineItemsByDccId, purchaseOrderMap);

        List<AgingReportDTO> noApproverRequests = flatReport.stream()
                .filter(dto -> dto.getPendingApprovers() == null || dto.getPendingApprovers().trim().isEmpty())
                .collect(Collectors.toList());
        for (AgingReportDTO dto : noApproverRequests) {
            System.out.println("No approver for RecordNo: " + dto.getRecordNo() + ", PO: " + dto.getPoNumber());
        }

        // --- Grouping ---
        Map<String, List<AgingReportDTO>> byDept = filteredFlatReport.stream()
                .collect(Collectors.groupingBy(AgingReportDTO::getDepartmentName));
        List<AgingReportGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<AgingReportDTO>> deptEntry : byDept.entrySet()) {
            String department = deptEntry.getKey();
            List<AgingReportDTO> deptList = deptEntry.getValue();
            Map<String, List<AgingReportDTO>> byApprover = deptList.stream()
                    .flatMap(dto -> Arrays.stream((dto.getPendingApprovers() == null ? "" : dto.getPendingApprovers()).split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .map(appr -> new AbstractMap.SimpleEntry<>(appr, dto)))
                    .collect(Collectors.groupingBy(Map.Entry::getKey,
                            Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
            List<AgingReportApproverDTO> approverDTOs = new ArrayList<>();
            for (Map.Entry<String, List<AgingReportDTO>> apprEntry : byApprover.entrySet()) {
                String approver = apprEntry.getKey();
                List<AgingReportDTO> apprList = apprEntry.getValue();
                User user = userByUsername.get(approver);
                String title = user != null ? user.getUserPosition() : "";
                String status = !apprList.isEmpty() ? apprList.get(0).getStatus() : "";
                Map<String, Integer> apprBuckets = getBuckets(apprList);
                int total = apprList.size();
                BigDecimal value = apprList.stream().map(AgingReportDTO::getTotalUnitPrice)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                AgingReportApproverDTO apprDTO = new AgingReportApproverDTO();
                apprDTO.setName(approver);
                apprDTO.setTitle(title);
                apprDTO.setStatus(status);
                apprDTO.setBuckets(apprBuckets);
                apprDTO.setTotal(total);
                apprDTO.setValue(value);
                apprDTO.setDtos(apprList);
                approverDTOs.add(apprDTO);
            }
            Map<String, Integer> deptBuckets = getBuckets(deptList);
            int deptTotal = deptList.size();
            BigDecimal deptValue = deptList.stream().map(AgingReportDTO::getTotalUnitPrice)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            AgingReportGroupDTO groupDTO = new AgingReportGroupDTO();
            groupDTO.setDepartment(department);
            groupDTO.setDepartmentStatus("InProcess");
            groupDTO.setTotalApprovers(approverDTOs.size());
            groupDTO.setTotalRequests(deptTotal);
            groupDTO.setValue(deptValue);
            groupDTO.setBuckets(deptBuckets);
            groupDTO.setApprovers(approverDTOs);
            result.add(groupDTO);
        }

        // --- Optionally: Further filter on department or approver name at group level (if you want double filtering)
        if (req.getColumnName() != null && req.getSearchQuery() != null && !req.getSearchQuery().isEmpty()) {
            String column = req.getColumnName().trim().toLowerCase();
            String searchVal = req.getSearchQuery().trim().toLowerCase();
            switch (column) {
                case "department":
                    result = result.stream()
                            .filter(group -> {
                                String deptVal = group.getDepartment();
                                if (deptVal == null) return false;
                                String[] depts = deptVal.split(",");
                                for (String d : depts) {
                                    if (d.trim().equalsIgnoreCase(searchVal) || d.trim().toLowerCase().contains(searchVal))
                                        return true;
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                    break;
  case "name": // Approver name
    result = result.stream()
        .map(group -> {
            // keep only approvers whose name matches, then recompute each approver's aggregates
            List<AgingReportApproverDTO> filteredApprovers = group.getApprovers().stream()
                    .filter(appr -> appr.getName() != null && appr.getName().toLowerCase().contains(searchVal))
                    .map(appr -> {
                        // recompute per-approver totals/values/buckets (DTO list unchanged here)
                        List<AgingReportDTO> dtos = appr.getDtos() == null ? Collections.emptyList() : appr.getDtos();
                        appr.setDtos(dtos);
                        appr.setTotal(dtos.size());
                        appr.setValue(dtos.stream().map(AgingReportDTO::getTotalUnitPrice)
                                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                        appr.setBuckets(getBuckets(dtos));
                        return appr;
                    })
                    .filter(appr -> appr.getDtos() != null && !appr.getDtos().isEmpty())
                    .collect(Collectors.toList());

            group.setApprovers(filteredApprovers);
            group.setTotalApprovers(filteredApprovers.size());

            // recompute group-level aggregates from remaining approvers
            List<AgingReportDTO> groupDtos = filteredApprovers.stream()
                    .flatMap(a -> a.getDtos().stream())
                    .collect(Collectors.toList());

            group.setTotalRequests(groupDtos.size());
            group.setValue(groupDtos.stream().map(AgingReportDTO::getTotalUnitPrice)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            group.setBuckets(getBuckets(groupDtos));

            return group;
        })
        // remove groups that now have no approvers (after name filter)
        .filter(group -> !group.getApprovers().isEmpty())
        .collect(Collectors.toList());
    break;
                case "ponumber":
                case "vendorname":
                case "createdby":
                case "supplierid":
                case "recordno":
                case "vendornumber":
                case "createddate":
                case "pendingapprovers":
                case "projectname":          // <-- new supported group-level column
                case "inscopeofwork": // <-- new supported group-level column
                    // For these fields, filter at the DTO level inside each group, then update group totals
                    result = result.stream()
                            .map(group -> {
                                // Filter each DTO inside each approver inside the group
                                List<AgingReportApproverDTO> filteredApprovers = group.getApprovers().stream()
                                        .map(appr -> {
                                            List<AgingReportDTO> filteredDtos = appr.getDtos().stream().filter(dto -> {
                                                switch (column) {
                                                    case "ponumber":
                                                        return dto.getPoNumber() != null && dto.getPoNumber().toLowerCase().contains(searchVal);
                                                    case "recordno":
                                                        try {
                                                            return dto.getRecordNo() == Integer.parseInt(searchVal);
                                                        } catch (Exception e) {
                                                            return false;
                                                        }
                                                    case "vendorname":
                                                        return dto.getVendorName() != null && dto.getVendorName().toLowerCase().contains(searchVal);
                                                    case "createdby":
                                                        return dto.getCreatedBy() != null && dto.getCreatedBy().toLowerCase().contains(searchVal);
                                                    case "supplierid":
                                                        return dto.getSupplierId() != null && !dto.getSupplierId().isEmpty()
                                                                && dto.getSupplierId().equalsIgnoreCase(searchVal);
                                                    case "vendornumber":
                                                        return dto.getVendorNumber() != null && dto.getVendorNumber().toLowerCase().contains(searchVal);
                                                    case "createddate":
                                                        try {
                                                            String[] dates = req.getSearchQuery().split(",");
                                                            if (dates.length == 2 && dto.getCreatedDate() != null) {
                                                                LocalDate actualDate = LocalDate.parse(dto.getCreatedDate());
                                                                LocalDate start = LocalDate.parse(dates[0].trim());
                                                                LocalDate end = LocalDate.parse(dates[1].trim());
                                                                return (!actualDate.isBefore(start)) && (!actualDate.isAfter(end));
                                                            }
                                                        } catch (Exception ignore) {
                                                        }
                                                        return false;
                                                    case "pendingapprovers":
                                                        return dto.getPendingApprovers() != null &&
                                                              Arrays.stream(dto.getPendingApprovers().split(","))
                                                                        .anyMatch(name -> name.trim().toLowerCase().contains(searchVal));
                                                    case "projectname":
                                                        // lookup purchase order map for effective project name
                                                        if (dto.getPoNumber() == null) return false;
                                                        tbPurchaseOrder p = purchaseOrderMap.get(dto.getPoNumber());
                                                        if (p == null) return false;
                                                        String effectiveProj = p.getProjectName();
                                                        return effectiveProj != null && effectiveProj.toLowerCase().contains(searchVal);
                                                    case "inscopeofwork":
                                                        // lookup line items for this DCC; true if any match
                                                        List<DCCLineItem> lines = lineItemsByDccId.getOrDefault(String.valueOf(dto.getRecordNo()), Collections.emptyList());
                                                        return lines.stream().anyMatch(li -> li.getScopeOfWork() != null
                                                                && li.getScopeOfWork().toLowerCase().contains(searchVal));
                                                    default:
                                                        return true;
                                                }
                                            }).collect(Collectors.toList());
                                            // appr.setDtos(filteredDtos);
                                            // appr.setTotal(filteredDtos.size());
                                            // appr.setValue(filteredDtos.stream().map(AgingReportDTO::getTotalUnitPrice)
                                            //         .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                                            // return appr;
                                                            appr.setDtos(filteredDtos);
                appr.setTotal(filteredDtos.size());
                appr.setValue(filteredDtos.stream()
                        .map(AgingReportDTO::getTotalUnitPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
                // recompute buckets for this approver so counts reflect filteredDtos
                appr.setBuckets(getBuckets(filteredDtos));
                return appr;
                                        })
                                        .filter(appr -> !appr.getDtos().isEmpty())
                                        .collect(Collectors.toList());
                                group.setApprovers(filteredApprovers);
                                group.setTotalApprovers(filteredApprovers.size());
                                group.setTotalRequests(filteredApprovers.stream().mapToInt(AgingReportApproverDTO::getTotal).sum());
                                group.setValue(filteredApprovers.stream().map(AgingReportApproverDTO::getValue)
                                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                                      // recompute department/group buckets from remaining DTOs so group buckets match totals
        List<AgingReportDTO> groupDtos = filteredApprovers.stream()
                .flatMap(a -> a.getDtos().stream())
                .collect(Collectors.toList());
        group.setBuckets(getBuckets(groupDtos));

        return group;
                            })
                            .filter(group -> !group.getApprovers().isEmpty())
                            .collect(Collectors.toList());
                    break;
                default:
                    // No filter
            }
        }

        // --- Aggregates (filtered/grouped) ---
        List<AgingReportDTO> finalFlatReport = result.stream()
                .flatMap(group -> group.getApprovers().stream())
                .flatMap(appr -> appr.getDtos().stream())
                .collect(Collectors.toList());
        
                // --- Sync pagination / totals with the filtered result (finalFlatReport) ---
        if (req.getColumnName() != null && req.getSearchQuery() != null && !req.getSearchQuery().isEmpty()) {
            // Use number of remaining DTOs as totalRecords (requests after all filtering)
            totalRecords = finalFlatReport.size();
        
            // If the original request did not supply valid pagination, keep "unpaged" semantics:
            if (reqSize == null || reqSize <= 0 || reqPage == null || reqPage < 0) {
                page = 0;
                size = totalRecords > 0 ? (int) totalRecords : 0;
            } else {
                // preserve the requested page/size
                page = reqPage;
                size = reqSize;
            }
        }
        BigDecimal totalValue = finalFlatReport.stream()
                .map(AgingReportDTO::getTotalUnitPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalPendingApprovers = finalFlatReport.stream()
                .mapToLong(dto -> dto.getPendingApprovers() == null ? 0 : dto.getPendingApprovers().split(",").length)
                .sum();

        long totalPendingRequests = finalFlatReport.stream()
                .filter(dto -> dto.getApprovalCount() > 0)
                .count();

        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (String bucket : BUCKETS) dailyCounts.put(bucket, 0L);
        for (AgingReportDTO dto : finalFlatReport) {
            long mins = parseMinutes(dto.getUserAging());
            for (int i = 0; i < BUCKETS.size(); i++) {
                long[] range = BUCKET_RANGES.get(i);
                if (mins >= range[0] && mins < range[1]) {
                    dailyCounts.put(BUCKETS.get(i), dailyCounts.get(BUCKETS.get(i)) + 1);
                    break;
                }
            }
        }

        AgingReportPagedResponseDTO resp = new AgingReportPagedResponseDTO();
        resp.setData(result);
        resp.setTotalValue(totalValue != null ? totalValue.longValue() : 0);
        resp.setTotalPendingApprovers(totalPendingApprovers);
        resp.setTotalPendingRequests(totalPendingRequests);
        resp.setDailyCounts(dailyCounts);
        resp.setTotalRecords(totalRecords);
        resp.setPage(page);
        resp.setSize(size);
        return resp;
    }

    // Bucket helpers
    private Map<String, Integer> getBuckets(List<AgingReportDTO> list) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String b : BUCKETS) map.put(b, 0);
        for (AgingReportDTO dto : list) {
            long mins = parseMinutes(dto.getUserAging());
            for (int i = 0; i < BUCKETS.size(); i++) {
                long[] range = BUCKET_RANGES.get(i);
                if (mins >= range[0] && mins < range[1]) {
                    map.put(BUCKETS.get(i), map.get(BUCKETS.get(i)) + 1);
                    break;
                }
            }
        }
        return map;
    }

    private long parseMinutes(String duration) {
        if (duration == null) return 0;
        String[] parts = duration.split(" ");
        long days = parts.length > 0 ? Long.parseLong(parts[0]) : 0;
        long hrs = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
        long mins = parts.length > 4 ? Long.parseLong(parts[4]) : 0;
        return days * 1440 + hrs * 60 + mins;
    }

    private String formatDuration(long minutes) {
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        return String.format("%d days %d hrs %d mins", days, hours, mins);
    }

    private LocalDateTime toLocalDateTime(Object date) {
        if (date instanceof LocalDateTime) {
            return (LocalDateTime) date;
        }
        if (date instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) date).atStartOfDay();
        }
        if (date instanceof java.util.Date) {
            return ((java.util.Date) date).toInstant()
                    .atZone(KSA_ZONE)   // ✅ Explicitly use KSA timezone
                    .toLocalDateTime();
        }
        if (date instanceof String) {
            try {
                return LocalDateTime.parse((String) date);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private AgingDurations calculateAgingDurations(
            DCC dcc,
            tbCategoryApprovalRequests latestApprovalReq,
            Map<Integer, List<tbCategoryApprovals>> approvalsByReqId,
            Map<String, User> userByUsername,
            Map<Integer, String> deptNameById,
            LocalDateTime now) {

        String userAging = "0 days 0 hrs 0 mins", totalAging = "0 days 0 hrs 0 mins";
        long approvalCount = 0;
        String pendingApprovers = "", departmentName = "";

        if (latestApprovalReq != null) {
            List<tbCategoryApprovals> approvals = approvalsByReqId
                    .getOrDefault(latestApprovalReq.getRecordNo(), Collections.emptyList());

            // SORT approvals by recordDateTime ASC
            List<tbCategoryApprovals> sortedApprovals = new ArrayList<>(approvals);
            sortedApprovals.sort(Comparator.comparing(tbCategoryApprovals::getRecordDateTime));

            // --- totalAging: from earliest approval recordDateTime to now
            long totalAgingMinutes = 0;
            if (!sortedApprovals.isEmpty()) {
                LocalDateTime firstRecordDateTime = toLocalDateTime(sortedApprovals.get(0).getRecordDateTime());
                if (firstRecordDateTime != null) {
                    totalAgingMinutes = Duration.between(firstRecordDateTime, now).toMinutes();
                }
            }
            totalAging = formatDuration(totalAgingMinutes);

            // --- userAging: find the ONLY pending approver and use their recordDateTime
            tbCategoryApprovals pendingApprover = approvals.stream()
                    .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                            "readyForApproval".equalsIgnoreCase(a.getApprovalStatus()))
                    .findFirst().orElse(null);

            long userAgingMinutes = 0;
            if (pendingApprover != null) {
                LocalDateTime pendingRecordDateTime = toLocalDateTime(pendingApprover.getRecordDateTime());
                if (pendingRecordDateTime != null) {
                    userAgingMinutes = Duration.between(pendingRecordDateTime, now).toMinutes();
                }
                pendingApprovers = pendingApprover.getApproverName();
                User user = userByUsername.get(pendingApprover.getApproverName());
                if (user != null && user.getDepartmentId() != null) {
                    String deptName = deptNameById.getOrDefault(user.getDepartmentId(), "");
                    if (!deptName.isEmpty()) departmentName = deptName;
                }
            }
            userAging = formatDuration(userAgingMinutes);

            // --- approvalCount (as before)
            approvalCount = approvals.stream().filter(a -> "pending".equalsIgnoreCase(a.getStatus())).count();

            // If no pending approver, set departmentName = "Unknown"
            if (pendingApprover == null) {
                departmentName = "Unknown";
            }
        }
        return new AgingDurations(userAging, totalAging, approvalCount, pendingApprovers, departmentName);
    }

    // Helper class to hold aging calculation results
    private static class AgingDurations {
        final String userAging;
        final String totalAging;
        final long approvalCount;
        final String pendingApprovers;
        final String departmentName;

        AgingDurations(String userAging, String totalAging, long approvalCount, String pendingApprovers, String departmentName) {
            this.userAging = userAging;
            this.totalAging = totalAging;
            this.approvalCount = approvalCount;
            this.pendingApprovers = pendingApprovers;
            this.departmentName = departmentName;
        }
    }

    // (Helper method for DB-level filtering)
    private Specification<DCC> getDccSpecification(AgingReportRequestDTO req) {
        return (root, query, cb) -> {
            Predicate statusPred = cb.equal(cb.lower(root.get("status")), "inprocess");
            Predicate filterPred = cb.conjunction();

            // DB-level filtering for DCC columns
            if (req.getColumnName() != null && req.getSearchQuery() != null
                    && !req.getColumnName().isEmpty() && !req.getSearchQuery().isEmpty()) {
                String column = req.getColumnName().trim().toLowerCase();
                String searchVal = req.getSearchQuery();

                try {
                    if ("ponumber".equals(column)) {
                        filterPred = cb.like(cb.lower(root.get("poNumber")), "%" + searchVal.toLowerCase() + "%");
                    } else if ("vendorname".equals(column)) {
                        filterPred = cb.like(cb.lower(root.get("vendorName")), "%" + searchVal.toLowerCase() + "%");
                    } else if ("createdby".equals(column)) {
                        filterPred = cb.like(cb.lower(root.get("createdBy")), "%" + searchVal.toLowerCase() + "%");
                    } else if ("vendornumber".equals(column)) {
                        filterPred = cb.like(cb.lower(root.get("vendorNumber")), "%" + searchVal.toLowerCase() + "%");
                    }
                } catch (Exception e) {
                    filterPred = cb.disjunction();
                }
            }
            return cb.and(statusPred, filterPred);
        };
    }

    /**
     * Filters the flat report list in-memory for criteria not handled at DB-level.
     * Supports: pendingApprovers, department, and also poNumber, vendorName, createdBy, supplierId, vendorNumber, createdDate,
     * and newly added: projectname (uses purchaseOrderMap), inscopeofworkpackage (uses lineItemsByDccId)
     */
    private List<AgingReportDTO> filterFlatReport(List<AgingReportDTO> list, AgingReportRequestDTO req,
                                                 Map<String, List<DCCLineItem>> lineItemsByDccId,
                                                 Map<String, tbPurchaseOrder> purchaseOrderMap) {
        if (req.getColumnName() == null || req.getSearchQuery() == null || req.getSearchQuery().isEmpty())
            return list;
        String column = req.getColumnName().trim().toLowerCase();
        String searchVal = req.getSearchQuery().trim().toLowerCase();

        return list.stream().filter(dto -> {
            switch (column) {
                case "pendingapprovers":
                    return dto.getPendingApprovers() != null && Arrays.stream(dto.getPendingApprovers().split(","))
                            .anyMatch(name -> name.trim().toLowerCase().contains(searchVal));
                case "department":
                    return dto.getDepartmentName() != null && Arrays.stream(dto.getDepartmentName().split(","))
                            .anyMatch(name -> name.trim().toLowerCase().contains(searchVal));
                case "ponumber":
                    return dto.getPoNumber() != null && dto.getPoNumber().toLowerCase().contains(searchVal);
                case "vendorname":
                    return dto.getVendorName() != null && dto.getVendorName().toLowerCase().contains(searchVal);
                case "createdby":
                    return dto.getCreatedBy() != null && dto.getCreatedBy().toLowerCase().contains(searchVal);
                case "vendornumber":
                    return dto.getVendorNumber() != null && dto.getVendorNumber().toLowerCase().contains(searchVal);
                case "projectname":
                    if (dto.getPoNumber() == null) return false;
                    tbPurchaseOrder po = purchaseOrderMap.get(dto.getPoNumber());
                    if (po == null) return false;
                    String effective = po.getProjectName();
                    return effective != null && effective.toLowerCase().contains(searchVal);
                case "inscopeofwork":
                    List<DCCLineItem> lines = lineItemsByDccId.getOrDefault(String.valueOf(dto.getRecordNo()), Collections.emptyList());
                    return lines.stream().anyMatch(li -> li.getScopeOfWork() != null &&
                            li.getScopeOfWork().toLowerCase().contains(searchVal));
                default:
                    return true;
            }
        }).collect(Collectors.toList());
    }

}