package com.zain.almksazain.services;

import com.zain.almksazain.dto.AgingReportApproverDTO;
import com.zain.almksazain.dto.AgingReportDTO;
import com.zain.almksazain.dto.AgingReportGroupDTO;
import com.zain.almksazain.dto.AgingReportItemsDTO;
import com.zain.almksazain.dto.AgingReportPagedResponseDTO;
import com.zain.almksazain.dto.AgingReportRequestDTO;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Arrays.sort;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

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
     private tbPurchaseOrderRepo  tbPurchaseOrderRepository;




    
    public Page<AgingReportDTO> getAgingReport(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("recordNo").descending());
        Page<DCC> dccPage = dccRepository.findByStatusIgnoreCase("inprocess", pageable);
        List<DCC> dccList = dccPage.getContent();

        // Batch fetch all ApprovalRequests for these DCCs
        List<Integer> dccRecordNos = dccList.stream().map(d -> (int)d.getRecordNo()).collect(Collectors.toList());
        List<tbCategoryApprovalRequests> allApprovalRequests = approvalRequestsRepository.findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(dccRecordNos);

        // Map dccRecordNo -> latest ApprovalRequest
        Map<Integer, tbCategoryApprovalRequests> latestReqMap = new HashMap<>();
        for (tbCategoryApprovalRequests req : allApprovalRequests) {
            latestReqMap.putIfAbsent(req.getAcceptanceRequestRecordNo(), req); // first is latest due to orderBy desc
        }

        // Batch fetch all Approvals for these ApprovalRequests
        List<Integer> approvalRequestIds = allApprovalRequests.stream().map(tbCategoryApprovalRequests::getRecordNo).collect(Collectors.toList());
        List<tbCategoryApprovals> allApprovals = approvalsRepository.findByApprovalRecordIdIn(approvalRequestIds);

        // Map approvalRecordId -> list of approvals
        Map<Integer, List<tbCategoryApprovals>> approvalsByReqId = allApprovals.stream()
                .collect(Collectors.groupingBy(tbCategoryApprovals::getApprovalRecordId));

        // Batch fetch all users and departments for pending approvers
        Set<String> allApproverNames = allApprovals.stream().map(tbCategoryApprovals::getApproverName).collect(Collectors.toSet());
        List<User> allUsers = userRepository.findByUsernameIn(new ArrayList<>(allApproverNames));
        Map<String, User> userByUsername = allUsers.stream()
                .collect(Collectors.toMap(User::getUsername, u -> u));
        Set<Integer> allDeptIds = allUsers.stream().map(User::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<departmentsdata> allDepts = departmentsDataRepository.findAllById(allDeptIds.stream().map(Long::valueOf).collect(Collectors.toList()));
        Map<Integer, String> deptNameById = allDepts.stream()
                .collect(Collectors.toMap(d -> (int)d.getRecordNo(), departmentsdata::getDeptName));
        List<AgingReportDTO> report = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        for (DCC dcc : dccList) {
            
            tbCategoryApprovalRequests latestApprovalReq = latestReqMap.getOrDefault((int)dcc.getRecordNo(), null);

            String userAging = "0 days 0 hrs 0 mins";
            String totalAging = "0 days 0 hrs 0 mins";
            long approvalCount = 0;
            String pendingApprovers = "";
            String departmentName = "";

            if (latestApprovalReq != null) {
                List<tbCategoryApprovals> approvals = approvalsByReqId.getOrDefault(latestApprovalReq.getRecordNo(), Collections.emptyList());

                // totalAging: difference between createdDate and now
                System.out.println("Parsing createdDate: " + dcc.getCreatedDate());
                LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
                long totalAgingMinutes = 0;
                if (createdDate != null) {
                    totalAgingMinutes = Duration.between(createdDate, now).toMinutes();
                }
                totalAging = formatDuration(totalAgingMinutes);

                // approvalCount: number of rows with status 'pending'
                approvalCount = approvals.stream()
                        .filter(a -> "pending".equalsIgnoreCase(a.getStatus()))
                        .count();

                // Find pending approver(s) and their departments
               List<tbCategoryApprovals> pendingApproverList = approvals.stream()
            .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                "readyForApproval".equalsIgnoreCase(a.getApprovalStatus()))
            .collect(Collectors.toList());

                if (!pendingApproverList.isEmpty()) {
                    List<String> pendingInfo = new ArrayList<>();
                    Set<String> departmentNamesSet = new HashSet<>();
                    for (tbCategoryApprovals appr : pendingApproverList) {
                        User user = userByUsername.get(appr.getApproverName());
                        if (user != null && user.getDepartmentId() != null) {
                            String deptName = deptNameById.getOrDefault(user.getDepartmentId(), "");
                            if (!deptName.isEmpty()) {
                                departmentNamesSet.add(deptName);
                            }
                        }
                        pendingInfo.add(appr.getApproverName());
                    }
                    pendingApprovers = String.join(", ", pendingInfo);
                   departmentName = departmentNamesSet.isEmpty() ? "Unknown" : String.join(", ", departmentNamesSet);
                }

                // userAging: from most recent approvedDate (with status 'approved') to now
                LocalDateTime latestApprovedDate = approvals.stream()
                        .filter(a -> "approved".equalsIgnoreCase(a.getStatus()))
                        .map(a -> toLocalDateTime(a.getApprovedDate()))
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null);

                long userAgingMinutes = 0;
                if (latestApprovedDate != null) {
                    userAgingMinutes = Duration.between(latestApprovedDate, now).toMinutes();
                }
                userAging = formatDuration(userAgingMinutes);
            }
                    //  Delivered Qty, Unit Price, Currency
            //   List<DCCLineItem> lineItems = dccLineItemRepository.findByDccId(dcc.getDccId());
   List<DCCLineItem> lineItems = dccLineItemRepository.findByDccId(String.valueOf(dcc.getRecordNo()));
BigDecimal totalDeliveredQty = BigDecimal.ZERO;
BigDecimal totalUnitPrice = BigDecimal.ZERO;
String currency = null;

for (DCCLineItem line : lineItems) {
    System.out.println("Line deliveredQty: " + line.getDeliveredQty());
    BigDecimal deliveredQty = BigDecimal.valueOf(line.getDeliveredQty());

    totalDeliveredQty = totalDeliveredQty.add(deliveredQty);

    // Find matching Purchase Order UPL row
    tb_PurchaseOrderUPL upl = purchaseOrderUPLRepository.findFirstByPoNumberAndPoLineNumberAndUplLine(
        dcc.getPoNumber(), line.getLineNumber(), line.getUplLineNumber()
    );

    if (upl != null) {
        BigDecimal unitPrice = BigDecimal.valueOf(upl.getUplLineUnitPrice()); // <-- USE THIS
        totalUnitPrice = totalUnitPrice.add(unitPrice.multiply(deliveredQty));
        if (currency == null && upl.getCurrency() != null) {
            currency = upl.getCurrency();
        }
    }
}

            AgingReportDTO dto = new AgingReportDTO(
                    (int)dcc.getRecordNo(),
                    dcc.getPoNumber(),
                    dcc.getVendorName(),
                    dcc.getStatus(),
                    dcc.getCreatedDate() != null ? dcc.getCreatedDate().toString() : null,
                    dcc.getCreatedBy(),
                    dcc.getSupplierId() != null ? dcc.getSupplierId() : dcc.getVendorNumber(),
                    dcc.getVendorNumber(),
                    userAging,
                    totalAging,
                    approvalCount,
                    pendingApprovers,
                    departmentName,
                    totalDeliveredQty,
                    totalUnitPrice,
                    currency
            );
            report.add(dto);
        }
        return new PageImpl<>(report, pageable, dccPage.getTotalElements());
    }


private static final DateTimeFormatter DT_FORMATTER = new DateTimeFormatterBuilder()
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
        // Filtering and pagination
// Improved Specification for DCC filtering, supports skipping DB-level filter for approver names
Specification<DCC> spec = (root, query, cb) -> {
    Predicate statusPred = cb.equal(cb.lower(root.get("status")), "inprocess");
    Predicate filterPred = cb.conjunction();

    // Only apply DB-level filtering for DCC columns, not for approver name
    if (req.getColumnName() != null && req.getSearchQuery() != null
        && !req.getColumnName().isEmpty() && !req.getSearchQuery().isEmpty()) {

        String column = req.getColumnName().trim().toLowerCase();

        // Only filter at DB level for non-"name" columns
        if (!"name".equals(column)) {
            try {
                Path<?> colPath;
                if (req.getColumnName().contains(".")) {
                    String[] parts = req.getColumnName().split("\\.");
                    colPath = root.get(parts[0]);
                    for (int i = 1; i < parts.length; i++) {
                        colPath = colPath.get(parts[i]);
                    }
                } else {
                    colPath = root.get(req.getColumnName());
                }

                Class<?> colType = colPath.getJavaType();
                String searchVal = req.getSearchQuery();

                if (String.class.isAssignableFrom(colType)) {
                    filterPred = cb.like(cb.lower(colPath.as(String.class)), "%" + searchVal.toLowerCase() + "%");
                } else if (Number.class.isAssignableFrom(colType)) {
                    try {
                        filterPred = cb.equal(colPath, new BigDecimal(searchVal));
                    } catch (NumberFormatException ex) {
                        filterPred = cb.disjunction();
                    }
                } else if (Enum.class.isAssignableFrom(colType)) {
                    filterPred = cb.equal(colPath, Enum.valueOf((Class<Enum>) colType, searchVal));
                } else {
                    // Fallback to string matching
                    filterPred = cb.like(cb.lower(colPath.as(String.class)), "%" + searchVal.toLowerCase() + "%");
                }
            } catch (Exception e) {
                filterPred = cb.disjunction();
            }
        }
    }
    return cb.and(statusPred, filterPred);
};


        List<DCC> dccList;
Sort sort = Sort.by(Sort.Direction.DESC, "createdDate");
long totalRecords;
int page = 0;
int size;
    if (req.getSize() <= 0 || req.getPage() < 0) {
    // Fetch all records
    dccList = dccRepository.findAll(spec, sort);
    totalRecords = dccList.size();
    size = dccList.size();
    page = 0;
} else {
    // Fetch paginated records
    PageRequest pageReq = PageRequest.of(req.getPage(), req.getSize(), sort);
    Page<DCC> dccPage = dccRepository.findAll(spec, pageReq);
    dccList = dccPage.getContent();
    totalRecords = dccPage.getTotalElements();
    size = req.getSize();
    page = req.getPage();
}
        // Sort sort = Sort.by(Sort.Direction.DESC, "createdDate");
        //  List<DCC> dccList = dccRepository.findAll(spec, sort);

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

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
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

        // --- Build flat DTO list ---
        List<AgingReportDTO> flatReport = new ArrayList<>();
        for (DCC dcc : dccList) {
            tbCategoryApprovalRequests latestApprovalReq = latestReqMap.getOrDefault((int) dcc.getRecordNo(), null);
            String userAging = "0 days 0 hrs 0 mins", totalAging = "0 days 0 hrs 0 mins";
            long approvalCount = 0; String pendingApprovers = "", departmentName = "";

            if (latestApprovalReq != null) {
                List<tbCategoryApprovals> approvals = approvalsByReqId
                    .getOrDefault(latestApprovalReq.getRecordNo(), Collections.emptyList());
                LocalDateTime createdDate = toLocalDateTime(dcc.getCreatedDate());
                long totalAgingMinutes = createdDate != null ? Duration.between(createdDate, now).toMinutes() : 0;
                totalAging = formatDuration(totalAgingMinutes);
                approvalCount = approvals.stream().filter(a -> "pending".equalsIgnoreCase(a.getStatus())).count();

              List<tbCategoryApprovals> pendingApproverList = approvals.stream()
                  .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                      "readyForApproval".equalsIgnoreCase(a.getApprovalStatus()))
                  .collect(Collectors.toList());
                if (!pendingApproverList.isEmpty()) {
                    List<String> pendingInfo = new ArrayList<>();
                    Set<String> departmentNamesSet = new HashSet<>();
                    for (tbCategoryApprovals appr : pendingApproverList) {
                        User user = userByUsername.get(appr.getApproverName());
                        if (user != null && user.getDepartmentId() != null) {
                            String deptName = deptNameById.getOrDefault(user.getDepartmentId(), "");
                            if (!deptName.isEmpty()) departmentNamesSet.add(deptName);
                            System.out.println("Approver: " + appr.getApproverName() + ", Dept: " + deptName);
                        }
                        pendingInfo.add(appr.getApproverName());
                    }
                    pendingApprovers = String.join(", ", pendingInfo);
                    
                   departmentName = departmentNamesSet.isEmpty() ? "Unknown" : String.join(", ", departmentNamesSet);
                }
             // Sort approvals by approvalId ascending (lowest first)
List<tbCategoryApprovals> sortedApprovals = new ArrayList<>(approvals);
sortedApprovals.sort(Comparator.comparingInt(tbCategoryApprovals::getApprovalId));

// Find first approval row with status 'pending' and approvalStatus 'pending approval'
tbCategoryApprovals pendingApproval = sortedApprovals.stream()
    .filter(a -> "pending".equalsIgnoreCase(a.getStatus()) &&
                 "pending approval".equalsIgnoreCase(a.getApprovalStatus()))
    .findFirst().orElse(null);

long userAgingMinutes = 0;
if (pendingApproval != null) {
    // userAging: from recordDateTime of this pending approval row to now
    LocalDateTime recordDateTime = toLocalDateTime(pendingApproval.getRecordDateTime());
    if (recordDateTime != null) {
        userAgingMinutes = Duration.between(recordDateTime, now).toMinutes();
    }

    // totalAging: from first row's recordDateTime to now
    LocalDateTime firstRowDateTime = sortedApprovals.isEmpty() ? null
        : toLocalDateTime(sortedApprovals.get(0).getRecordDateTime());
    if (firstRowDateTime != null) {
        totalAgingMinutes = Duration.between(firstRowDateTime, now).toMinutes();
    }
} else {
    // Fallback to your previous logic if no such pending approval row is found
    tbCategoryApprovals lastApproved = sortedApprovals.stream()
        .filter(a -> "approved".equalsIgnoreCase(a.getStatus()))
        .max(Comparator.comparingInt(tbCategoryApprovals::getApprovalId))
        .orElse(null);
    LocalDateTime approvedDateTime = lastApproved != null ? toLocalDateTime(lastApproved.getApprovedDate()) : null;
    if (approvedDateTime != null) {
        userAgingMinutes = Duration.between(approvedDateTime, now).toMinutes();
    }
    if (createdDate != null) {
        totalAgingMinutes = Duration.between(createdDate, now).toMinutes();
    }
}

userAging = formatDuration(userAgingMinutes);
totalAging = formatDuration(totalAgingMinutes);
            }
            List<DCCLineItem> lineItems = lineItemsByDccId
                .getOrDefault(String.valueOf(dcc.getRecordNo()), Collections.emptyList());

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
            AgingReportDTO dto = new AgingReportDTO(
                (int) dcc.getRecordNo(), dcc.getPoNumber(), dcc.getVendorName(), dcc.getStatus(),
                dcc.getCreatedDate() != null ? dcc.getCreatedDate().toString() : null, dcc.getCreatedBy(),
                dcc.getSupplierId() != null ? dcc.getSupplierId() : dcc.getVendorNumber(), dcc.getVendorNumber(),
                userAging, totalAging, approvalCount, pendingApprovers, departmentName,
                totalDeliveredQty, totalUnitPrice, currency
            );
            flatReport.add(dto);
        }

        // --- Grouping ---
        for (AgingReportDTO dto : flatReport) {
    System.out.println("AgingReportDTO departmentName: [" + dto.getDepartmentName() + "]");
}
        Map<String, List<AgingReportDTO>> byDept = flatReport.stream()
            .collect(Collectors.groupingBy(AgingReportDTO::getDepartmentName));
        List<AgingReportGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<AgingReportDTO>> deptEntry : byDept.entrySet()) {
            String department = deptEntry.getKey();
            List<AgingReportDTO> deptList = deptEntry.getValue();
            Map<String, List<AgingReportDTO>> byApprover = deptList.stream()
                .flatMap(dto -> Arrays.stream(dto.getPendingApprovers().split(","))
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
                apprDTO.setName(approver); apprDTO.setTitle(title); apprDTO.setStatus(status);
                apprDTO.setBuckets(apprBuckets); apprDTO.setTotal(total); apprDTO.setValue(value);apprDTO.setDtos(apprList); 
                approverDTOs.add(apprDTO);
            }
            Map<String, Integer> deptBuckets = getBuckets(deptList);
            int deptTotal = deptList.size();
            BigDecimal deptValue = deptList.stream().map(AgingReportDTO::getTotalUnitPrice)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            AgingReportGroupDTO groupDTO = new AgingReportGroupDTO();
            groupDTO.setDepartment(department);
            groupDTO.setDepartmentStatus("Active Department");
            groupDTO.setTotalApprovers(approverDTOs.size());
            groupDTO.setTotalRequests(deptTotal);
            groupDTO.setValue(deptValue);
            groupDTO.setBuckets(deptBuckets);
            groupDTO.setApprovers(approverDTOs);
            result.add(groupDTO);
        }
      // --- Filter on department or approver name ---
if (req.getColumnName() != null && req.getSearchQuery() != null && !req.getSearchQuery().isEmpty()) {
    String column = req.getColumnName().trim().toLowerCase();
    String searchVal = req.getSearchQuery().trim().toLowerCase();
System.out.println("Grouped departments before filtering:");
for (AgingReportGroupDTO group : result) {
    System.out.println("[" + group.getDepartment() + "]");
}
 if ("department".equals(column)) {
    result = result.stream()
        .filter(group -> {
            String deptVal = group.getDepartment();
            if (deptVal == null) return false;
            // Split by comma, trim whitespace, match case-insensitive
            String[] depts = deptVal.split(",");
            for (String d : depts) {
                if (d.trim().equalsIgnoreCase(searchVal) || d.trim().toLowerCase().contains(searchVal)) return true;
            }
            return false;
        })
        .collect(Collectors.toList());
} else if ("name".equals(column)) {
        // Filter by approver name
        result = result.stream()
            .map(group -> {
                List<AgingReportApproverDTO> filteredApprovers = group.getApprovers().stream()
                    .filter(appr -> appr.getName() != null && appr.getName().toLowerCase().contains(searchVal))
                    .collect(Collectors.toList());
                group.setApprovers(filteredApprovers);
                group.setTotalApprovers(filteredApprovers.size());
                return group;
            })
            .filter(group -> !group.getApprovers().isEmpty())
            .collect(Collectors.toList());
    }
}
// --- Aggregates (filtered/grouped) ---
List<AgingReportDTO> filteredFlatReport = result.stream()
    .flatMap(group -> group.getApprovers().stream())
    .flatMap(appr -> appr.getDtos().stream()) 
    .collect(Collectors.toList());

BigDecimal totalValue = filteredFlatReport.stream()
    .map(AgingReportDTO::getTotalUnitPrice)
    .filter(Objects::nonNull)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

long totalPendingApprovers = filteredFlatReport.stream()
    .mapToLong(dto -> dto.getPendingApprovers() == null ? 0 : dto.getPendingApprovers().split(",").length)
    .sum();

long totalPendingRequests = filteredFlatReport.stream()
    .filter(dto -> dto.getApprovalCount() > 0)
    .count();

Map<String, Long> dailyCounts = new LinkedHashMap<>();
for (String bucket : BUCKETS) dailyCounts.put(bucket, 0L);
for (AgingReportDTO dto : filteredFlatReport) {
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
        if (date instanceof LocalDateTime) return (LocalDateTime) date;
        if (date instanceof java.time.LocalDate) return ((java.time.LocalDate) date).atStartOfDay();
        if (date instanceof java.util.Date) {
            return ((java.util.Date) date).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        }
        if (date instanceof String) {
            try { return LocalDateTime.parse((String) date); } catch (Exception e) { return null; }
        }
        return null;
    }


    
}