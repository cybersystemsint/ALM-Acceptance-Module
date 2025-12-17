package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.model.*;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JPA Specification for filtering DCC records with enhanced export support.
 * Now includes support for approved date range filtering.
 */
public class DccSpecification implements Specification<DCC> {

    private final String supplierId;
    private final String pendingApprovers;
    private final String columnName;
    private final String searchQuery;
    private final String operator;
    private final Map<String, String> fieldFilters;
    private final String createdDateStart;
    private final String createdDateEnd;
    private final String approvedDateStart;
    private final String approvedDateEnd;

    // Constructor 1: Full constructor with ALL filters (10 parameters)
    public DccSpecification(String supplierId, String pendingApprovers, String columnName, String searchQuery,
                            String operator, Map<String, String> fieldFilters,
                            String createdDateStart, String createdDateEnd,
                            String approvedDateStart, String approvedDateEnd) {
        this.supplierId = supplierId;
        this.pendingApprovers = pendingApprovers;
        this.columnName = columnName;
        this.searchQuery = searchQuery;
        this.operator = operator;
        this.fieldFilters = fieldFilters;
        this.createdDateStart = createdDateStart;
        this.createdDateEnd = createdDateEnd;
        this.approvedDateStart = approvedDateStart;
        this.approvedDateEnd = approvedDateEnd;
    }

    // Constructor 2: With field filters but no approved dates (8 parameters) - FOR SERVICE COMPATIBILITY
    public DccSpecification(String supplierId, String pendingApprovers, String columnName,
                            String searchQuery, String operator, Map<String, String> fieldFilters,
                            String createdDateStart, String createdDateEnd) {
        this(supplierId, pendingApprovers, columnName, searchQuery, operator,
                fieldFilters, createdDateStart, createdDateEnd, null, null);
    }

    // Constructor 3: Old/legacy constructor for backward compatibility (5 parameters)
    public DccSpecification(String supplierId, String pendingApprovers, String columnName,
                            String searchQuery, String operator) {
        this(supplierId, pendingApprovers, columnName, searchQuery, operator,
                null, null, null, null, null);
    }

    @Override
    public Predicate toPredicate(Root<DCC> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        // Ensure poNumber is not null
        predicates.add(cb.isNotNull(root.get("poNumber")));

        // Filter by supplierId if not "0"
        if (!"0".equals(supplierId)) {
            predicates.add(cb.equal(root.get("vendorNumber"), supplierId));
        }

        // Filter by pendingApprovers if provided
        if (pendingApprovers != null && !pendingApprovers.isEmpty()) {
            // Subquery to get the latest TbCategoryApprovalRequests.recordNo for each DCC
            Subquery<Long> approvalRequestSubquery = query.subquery(Long.class);
            Root<TbCategoryApprovalRequests> approvalRequestRoot = approvalRequestSubquery.from(TbCategoryApprovalRequests.class);

            // Subquery to find the maximum recordDateTime for the matching acceptanceRequestRecordNo
            Subquery<LocalDateTime> maxDateSubquery = query.subquery(LocalDateTime.class);
            Root<TbCategoryApprovalRequests> maxDateRoot = maxDateSubquery.from(TbCategoryApprovalRequests.class);
            maxDateSubquery.select(cb.greatest(maxDateRoot.<LocalDateTime>get("recordDateTime")))
                    .where(
                            cb.and(
                                    cb.equal(maxDateRoot.get("acceptanceRequestRecordNo"), root.get("recordNo")),
                                    cb.equal(maxDateRoot.get("status"), "pending")
                            )
                    );

            // Select the recordNo where recordDateTime matches the maximum
            approvalRequestSubquery.select(approvalRequestRoot.get("recordNo"))
                    .where(
                            cb.and(
                                    cb.equal(approvalRequestRoot.get("acceptanceRequestRecordNo"), root.get("recordNo")),
                                    cb.equal(approvalRequestRoot.get("status"), "pending"),
                                    cb.equal(approvalRequestRoot.get("recordDateTime"), maxDateSubquery)
                            )
                    );

            // Subquery to get TbCategoryApprovals with matching approverName and approvalStatus = 'readyForApproval'
            Subquery<TbCategoryApprovals> approvalsSubquery = query.subquery(TbCategoryApprovals.class);
            Root<TbCategoryApprovals> approvalsRoot = approvalsSubquery.from(TbCategoryApprovals.class);
            approvalsSubquery.select(approvalsRoot)
                    .where(
                            cb.and(
                                    cb.equal(approvalsRoot.get("approvalRecordId"), approvalRequestSubquery),
                                    cb.equal(cb.lower(approvalsRoot.get("approverName")), pendingApprovers.toLowerCase()),
                                    cb.equal(approvalsRoot.get("approvalStatus"), "readyForApproval")
                            )
                    );

            predicates.add(cb.exists(approvalsSubquery));
        }

        // Apply legacy search filter (columnName/searchQuery)
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()) {
            String dbColumnName = mapColumnToDbField(columnName);
            if (dbColumnName != null) {
                if (columnName.toLowerCase().equals("recordno")) {
                    try {
                        predicates.add(cb.equal(root.get(dbColumnName), Long.parseLong(searchQuery)));
                    } catch (NumberFormatException e) {
                        // Invalid number format, return no results
                        predicates.add(cb.isFalse(cb.literal(true)));
                    }
                } else {
                    String op = operator != null ? operator.toLowerCase() : "contains";
                    Predicate searchPredicate;
                    switch (op) {
                        case "equals":
                            searchPredicate = cb.equal(cb.lower(root.get(dbColumnName)), searchQuery.toLowerCase());
                            break;
                        case "startswith":
                            searchPredicate = cb.like(cb.lower(root.get(dbColumnName)), searchQuery.toLowerCase() + "%");
                            break;
                        case "endswith":
                            searchPredicate = cb.like(cb.lower(root.get(dbColumnName)), "%" + searchQuery.toLowerCase());
                            break;
                        case "contains":
                        default:
                            searchPredicate = cb.like(cb.lower(root.get(dbColumnName)), "%" + searchQuery.toLowerCase() + "%");
                            break;
                    }
                    predicates.add(searchPredicate);
                }
            }
        }

        // Apply field filters (NEW: enhanced filtering for exports)
        if (fieldFilters != null && !fieldFilters.isEmpty()) {
            for (Map.Entry<String, String> entry : fieldFilters.entrySet()) {
                String field = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.trim().isEmpty()) continue;

                String dbField = mapFieldToDbColumn(field);
                if (dbField == null) continue;

                // EXACT match for recordNo (ID field)
                if (field.equals("dccRecordNo") || field.equals("recordNo")) {
                    try {
                        Long recordNo = Long.parseLong(value);
                        predicates.add(cb.equal(root.get(dbField), recordNo));
                    } catch (NumberFormatException e) {
                        // Skip invalid number
                    }
                }
                // Skip fields not in DCC table (filtered in-memory after DTO construction)
                else if (field.equals("approvalCount")) {
                    // approvalCount is calculated, not in DCC table - skip
                }
                // EXACT match for string fields (case-insensitive)
                else {
                    try {
                        predicates.add(cb.equal(
                                cb.lower(root.get(dbField).as(String.class)),
                                value.toLowerCase()
                        ));
                    } catch (Exception e) {
                        // Skip if field doesn't support string operations
                    }
                }
            }
        }

        // Date range filters - Created Date
        if (createdDateStart != null && !createdDateStart.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);
                Date startDate = sdf.parse(createdDateStart);
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), startDate));
            } catch (Exception e) {
                // Skip invalid date
            }
        }

        if (createdDateEnd != null && !createdDateEnd.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);
                Date endDate = sdf.parse(createdDateEnd);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), endDate));
            } catch (Exception e) {
                // Skip invalid date
            }
        }

        // NEW: Date range filters - Approved Date
        if (approvedDateStart != null && !approvedDateStart.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);
                Date startDate = sdf.parse(approvedDateStart);
                predicates.add(cb.greaterThanOrEqualTo(root.get("approvedDate"), startDate));
            } catch (Exception e) {
                // Skip invalid date
            }
        }

        if (approvedDateEnd != null && !approvedDateEnd.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);
                Date endDate = sdf.parse(approvedDateEnd);
                predicates.add(cb.lessThanOrEqualTo(root.get("approvedDate"), endDate));
            } catch (Exception e) {
                // Skip invalid date
            }
        }

        // Subquery for DCCLineItem (ensure records have line items)
        Subquery<DCCLineItem> lineItemSubquery = query.subquery(DCCLineItem.class);
        Root<DCCLineItem> lineItemRoot = lineItemSubquery.from(DCCLineItem.class);
        lineItemSubquery.select(lineItemRoot)
                .where(cb.equal(lineItemRoot.get("dccId"), root.get("recordNo").as(String.class)));
        predicates.add(cb.exists(lineItemSubquery));

        // Subquery for tb_PurchaseOrderUPL (ensure records have UPL data)
        Subquery<tb_PurchaseOrderUPL> uplSubquery = query.subquery(tb_PurchaseOrderUPL.class);
        Root<tb_PurchaseOrderUPL> uplRoot = uplSubquery.from(tb_PurchaseOrderUPL.class);
        uplSubquery.select(uplRoot)
                .where(cb.equal(uplRoot.get("poNumber"), root.get("poNumber")));
        predicates.add(cb.exists(uplSubquery));

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private String mapColumnToDbField(String columnName) {
        if (columnName == null) return null;
        switch (columnName.toLowerCase()) {
            case "recordno": return "recordNo";
            case "dccponumber":
            case "ponumber": return "poNumber";
            case "newprojectname": return "newProjectName";
            case "dccacceptancetype":
            case "acceptancetype": return "acceptanceType";
            case "dccstatus":
            case "status": return "status";
            case "dcccreateddate":
            case "createddate": return "createdDate";
            case "vendorcomment": return "vendorComment";
            case "dccid": return "dccId";
            case "poid": return "poNumber";
            case "projectname": return "projectName";
            case "supplierid": return "supplierId";
            case "vendornumber": return "vendorNumber";
            case "vendorname": return "vendorName";
            case "createdby":
            case "createdbyname": return "createdBy";
            case "vendoremail": return "vendorEmail";
            case "currency":
            case "dcccurrency": return "currency";
            case "approveddate": return "approvedDate";
            default: return null;
        }
    }

    // Map field filters to database columns
    private String mapFieldToDbColumn(String field) {
        if (field == null) return null;
        switch (field.toLowerCase()) {
            case "dccrecordno":
            case "recordno": return "recordNo";
            case "dccponumber":
            case "ponumber": return "poNumber";
            case "newprojectname": return "newProjectName";
            case "projectname": return "projectName";
            case "dccstatus":
            case "status": return "status";
            case "dccacceptancetype":
            case "acceptancetype": return "acceptanceType";
            case "vendorname": return "vendorName";
            case "vendornumber": return "vendorNumber";
            case "createdbyname":
            case "createdby": return "createdBy";
            case "vendorcomment": return "vendorComment";
            case "dccid": return "dccId";
            case "dcccurrency":
            case "currency": return "currency";
            case "vendoremail": return "vendorEmail";
            case "supplierid": return "vendorNumber";  // ← FIX: Map to vendorNumber, not supplierId
            default: return null;
        }
    }
}