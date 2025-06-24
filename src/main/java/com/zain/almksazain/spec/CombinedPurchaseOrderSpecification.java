package com.zain.almksazain.spec;
import org.springframework.data.jpa.domain.Specification;

import com.zain.almksazain.model.CombinedPurchaseOrder;

import javax.persistence.criteria.Predicate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CombinedPurchaseOrderSpecification {

    public static Specification<CombinedPurchaseOrder> filter(
            String supplierId, String poId, String columnName, String searchQuery,
            String dateFrom, String dateTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null && !"0".equals(supplierId)) {
                predicates.add(cb.equal(root.get("poVendorNumber"), supplierId));
            }
            if (poId != null && !"0".equals(poId)) {
                predicates.add(cb.equal(root.get("poNumber"), poId));
            }
            if (dateFrom != null && !dateFrom.isEmpty() && dateTo != null && !dateTo.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date from = sdf.parse(dateFrom);
                    Date to = sdf.parse(dateTo);
                    predicates.add(cb.between(root.get("poApprovedDate"), from, to));
                } catch (Exception e) {
                    // ignore date parse errors, or handle as needed
                }
            }
            if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()) {
                predicates.add(cb.like(root.get(columnName), "%" + searchQuery + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
