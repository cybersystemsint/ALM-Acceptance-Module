package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.model.*;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification for filtering DCC records.
 */
public class DccSpecification implements Specification<DCC> {

    private final String supplierId;
    private final String pendingApprovers;
    private final String columnName;
    private final String searchQuery;
    private final String operator;

    public DccSpecification(String supplierId, String pendingApprovers, String columnName, String searchQuery, String operator) {
        this.supplierId = supplierId;
        this.pendingApprovers = pendingApprovers;
        this.columnName = columnName;
        this.searchQuery = searchQuery;
        this.operator = operator;
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

        // Apply search filter
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

        // Subquery for DCCLineItem
        Subquery<DCCLineItem> lineItemSubquery = query.subquery(DCCLineItem.class);
        Root<DCCLineItem> lineItemRoot = lineItemSubquery.from(DCCLineItem.class);
        lineItemSubquery.select(lineItemRoot)
                .where(cb.equal(lineItemRoot.get("dccId"), root.get("recordNo")));
        predicates.add(cb.exists(lineItemSubquery));

        // Subquery for tb_PurchaseOrderUPL
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
}