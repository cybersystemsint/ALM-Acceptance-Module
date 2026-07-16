package com.zain.almksazain.repo;

import com.zain.almksazain.model.dto.PoUplCombinedPair;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

@Repository
public class PoUplCombinedQueryRepository {

    private static final String BASE_FROM =
            "FROM tbPurchaseOrder po LEFT JOIN tb_PurchaseOrderUPL upl "
            + "ON po.poNumber = upl.poNumber AND upl.poLineNumber = CAST(po.lineNumber AS string) ";

    private static final Map<String, String> SEARCHABLE_COLUMNS = new HashMap<>();

    static {
        SEARCHABLE_COLUMNS.put("poNumber", "po.poNumber");
        SEARCHABLE_COLUMNS.put("poVendorNumber", "po.vendorNumber");
        SEARCHABLE_COLUMNS.put("poVendorName", "po.vendorName");
        SEARCHABLE_COLUMNS.put("poLineDescription", "po.poLineDescription");
        SEARCHABLE_COLUMNS.put("authorisationStatus", "po.authorisationStatus");
        SEARCHABLE_COLUMNS.put("poClosureStatus", "po.poClosureStatus");
        SEARCHABLE_COLUMNS.put("newProjectName", "po.newProjectName");
        SEARCHABLE_COLUMNS.put("poProjectName", "po.newProjectName");
        SEARCHABLE_COLUMNS.put("itemPartNumber", "po.itemPartNumber");
        SEARCHABLE_COLUMNS.put("departmentName", "po.departmentName");
        SEARCHABLE_COLUMNS.put("uplLine", "upl.uplLine");
        SEARCHABLE_COLUMNS.put("uplLineDescription", "upl.uplLineDescription");
        SEARCHABLE_COLUMNS.put("uplLineItemCode", "upl.uplLineItemCode");
        SEARCHABLE_COLUMNS.put("zainItemCategoryCode", "upl.zainItemCategoryCode");
        SEARCHABLE_COLUMNS.put("activeOrPassive", "upl.activeOrPassive");
    }

    @PersistenceContext
    private EntityManager entityManager;

    public long countCombinedRows(
            String supplierId,
            String poId,
            String dateFrom,
            String dateTo,
            String columnName,
            String searchQuery) {

        StringBuilder jpql = new StringBuilder("SELECT COUNT(po) ").append(BASE_FROM).append("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        appendFilters(jpql, params, supplierId, poId, dateFrom, dateTo, columnName, searchQuery);

        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    public List<PoUplCombinedPair> findCombinedRows(
            String supplierId,
            String poId,
            String dateFrom,
            String dateTo,
            String columnName,
            String searchQuery,
            int offset,
            int limit) {

        StringBuilder jpql = new StringBuilder("SELECT po, upl ").append(BASE_FROM).append("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        appendFilters(jpql, params, supplierId, poId, dateFrom, dateTo, columnName, searchQuery);
        jpql.append("ORDER BY po.recordNo ASC, upl.recordNo ASC");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        params.forEach(query::setParameter);
        if (limit > 0) {
            query.setFirstResult(offset);
            query.setMaxResults(limit);
        }

        List<Object[]> rows = query.getResultList();
        List<PoUplCombinedPair> pairs = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            pairs.add(new PoUplCombinedPair((tbPurchaseOrder) row[0], (tb_PurchaseOrderUPL) row[1]));
        }
        return pairs;
    }

    private void appendFilters(
            StringBuilder jpql,
            Map<String, Object> params,
            String supplierId,
            String poId,
            String dateFrom,
            String dateTo,
            String columnName,
            String searchQuery) {

        if (supplierId != null && !supplierId.equalsIgnoreCase("0")) {
            jpql.append("AND po.vendorNumber = :supplierId ");
            params.put("supplierId", supplierId);
        }
        if (poId != null && !poId.equalsIgnoreCase("0")) {
            jpql.append("AND po.poNumber = :poId ");
            params.put("poId", poId);
        }
        if (dateFrom != null && !dateFrom.isEmpty() && dateTo != null && !dateTo.isEmpty()) {
            jpql.append("AND po.createdDate BETWEEN :dateFrom AND :dateTo ");
            params.put("dateFrom", java.sql.Date.valueOf(dateFrom.length() >= 10 ? dateFrom.substring(0, 10) : dateFrom));
            params.put("dateTo", java.sql.Date.valueOf(dateTo.length() >= 10 ? dateTo.substring(0, 10) : dateTo));
        }
        if (columnName != null && !columnName.isEmpty()
                && searchQuery != null && !searchQuery.isEmpty()
                && SEARCHABLE_COLUMNS.containsKey(columnName)) {
            jpql.append("AND ").append(SEARCHABLE_COLUMNS.get(columnName)).append(" LIKE :searchQuery ");
            params.put("searchQuery", "%" + searchQuery + "%");
        }
    }

    public boolean isEntityBackedSearchColumn(String columnName) {
        return columnName == null || columnName.isEmpty() || SEARCHABLE_COLUMNS.containsKey(columnName);
    }
}
