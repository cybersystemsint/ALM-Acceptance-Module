package com.zain.almksazain.specs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared filter-building logic for tb_PurchaseOrder, used by the fetch/filter endpoints
 * (ReportsController.filterNestedPurchaseOrders, the v2 grid endpoint) and the export worker
 * (ExportsController.runPurchaseOrdersNestedExportJob), so a fix or added column applies everywhere
 * at once instead of drifting between fetch and export.
 *
 * Extracted from filterNestedPurchaseOrders's original inline logic - no behavior change there,
 * just made reusable. The aggregate ("total*") columns are Java-computed sums over a PO's line
 * items, not real tb_PurchaseOrder columns, so they can only be filtered via a HAVING clause on a
 * GROUP BY poNumber subquery - buildAggregateRestriction wraps that up as a single self-contained
 * "AND PO.poNumber IN (...)" fragment for callers (like export) that don't already build their own
 * grouped/sorted subquery; buildHavingFragment exposes the bare HAVING condition for callers (like
 * filterNestedPurchaseOrders) that need to fold it into their own subquery/sort-by-aggregate logic.
 */
public final class PoFilterBuilder {

    private PoFilterBuilder() {
    }

    // filter key -> real tb_PurchaseOrder column summed to produce that aggregate
    private static final Map<String, String> AGGREGATE_SQL_COLUMN = new LinkedHashMap<>();
    static {
        AGGREGATE_SQL_COLUMN.put("totalPoQtyNew", "poQtyNew");
        AGGREGATE_SQL_COLUMN.put("totalpoOrderQuantity", "poOrderQuantity");
        AGGREGATE_SQL_COLUMN.put("totalQuantityReceived", "quantityReceived");
        AGGREGATE_SQL_COLUMN.put("totalQuantityDueOld", "quantityDueOld");
        AGGREGATE_SQL_COLUMN.put("totalQuantityDueNew", "quantityDueNew");
        AGGREGATE_SQL_COLUMN.put("totalQuantityBilled", "quantityBilled");
        AGGREGATE_SQL_COLUMN.put("totallinePriceInSAR", "linePriceInSAR");
    }

    /** The 7 Java-computed aggregate filter keys (not real tb_PurchaseOrder columns). */
    public static final Set<String> AGGREGATE_COLUMNS = Collections.unmodifiableSet(AGGREGATE_SQL_COLUMN.keySet());

    private static boolean hasValue(Map<String, String> filters, String key) {
        return filters.containsKey(key) && filters.get(key) != null && !filters.get(key).isEmpty();
    }

    /** Plain per-field predicates against alias "PO" (poNumber, projectName, ... , supplierId), plus date ranges. */
    public static String buildWhereClause(Map<String, String> filters, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (hasValue(filters, "poNumber")) {
            where.append(" AND PO.poNumber = ?");
            params.add(filters.get("poNumber"));
        }
        if (hasValue(filters, "projectName")) {
            where.append(" AND PO.projectName = ?");
            params.add(filters.get("projectName"));
        }
        if (hasValue(filters, "prNum")) {
            where.append(" AND PO.prNum = ?");
            params.add(filters.get("prNum"));
        }
        if (hasValue(filters, "typeLookUpCode")) {
            where.append(" AND PO.typeLookUpCode = ?");
            params.add(filters.get("typeLookUpCode"));
        }
        if (hasValue(filters, "vendorName")) {
            where.append(" AND PO.vendorName = ?");
            params.add(filters.get("vendorName"));
        }
        if (hasValue(filters, "currencyCode")) {
            where.append(" AND PO.currencyCode = ?");
            params.add(filters.get("currencyCode"));
        }
        // "0" is this codebase's established sentinel for "no vendor restriction" (see
        // getNestedPurchaseOrders) - AMU callers omit supplierId entirely or may pass "0"; a
        // vendor session always supplies its own real vendor number.
        if (hasValue(filters, "supplierId") && !"0".equals(filters.get("supplierId"))) {
            where.append(" AND PO.vendorNumber = ?");
            params.add(filters.get("supplierId"));
        }
        where.append(buildDateRangeClause(filters, params));
        return where.toString();
    }

    /** Just the createdDate/approvedDate range predicates - reusable on their own by callers (like
     *  export) that already have their own plain-field filter matching and only need the date-range
     *  piece added, without duplicating the plain-field conditions above. */
    public static String buildDateRangeClause(Map<String, String> filters, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (hasValue(filters, "createdDateStart")) {
            where.append(" AND PO.createdDate >= ?");
            params.add(filters.get("createdDateStart"));
        }
        if (hasValue(filters, "createdDateEnd")) {
            where.append(" AND PO.createdDate <= ?");
            params.add(filters.get("createdDateEnd"));
        }
        if (hasValue(filters, "approvedDateStart")) {
            where.append(" AND PO.approvedDate >= ?");
            params.add(filters.get("approvedDateStart"));
        }
        if (hasValue(filters, "approvedDateEnd")) {
            where.append(" AND PO.approvedDate <= ?");
            params.add(filters.get("approvedDateEnd"));
        }
        return where.toString();
    }

    /** Bare "SUM(PO.x) = ? AND SUM(PO.y) = ?" fragment (no " HAVING " prefix, no wrapping) - for
     *  callers building their own GROUP BY/HAVING subquery (e.g. one that also sorts by an aggregate). */
    public static String buildHavingFragment(Map<String, String> filters, List<Object> havingParams) {
        StringBuilder having = new StringBuilder();
        for (Map.Entry<String, String> entry : AGGREGATE_SQL_COLUMN.entrySet()) {
            if (hasValue(filters, entry.getKey())) {
                try {
                    double value = Double.parseDouble(filters.get(entry.getKey()));
                    if (having.length() > 0) {
                        having.append(" AND ");
                    }
                    having.append("SUM(PO.").append(entry.getValue()).append(") = ?");
                    havingParams.add(value);
                } catch (NumberFormatException ignored) {
                    // Skip invalid numeric value, same defensive style as the rest of this controller
                }
            }
        }
        return having.toString();
    }

    /** Self-contained "AND PO.poNumber IN (...)" restriction to the POs whose aggregate(s) match,
     *  for callers (like export) that don't already build their own grouped subquery. Returns ""
     *  when no aggregate filter is present. */
    public static String buildAggregateRestriction(Map<String, String> filters, List<Object> params) {
        List<Object> havingParams = new ArrayList<>();
        String having = buildHavingFragment(filters, havingParams);
        if (having.isEmpty()) {
            return "";
        }
        List<Object> whereParams = new ArrayList<>();
        String where = buildWhereClause(filters, whereParams);
        params.addAll(whereParams);
        params.addAll(havingParams);
        return " AND PO.poNumber IN (SELECT PO.poNumber FROM tb_PurchaseOrder PO" + where
                + " GROUP BY PO.poNumber HAVING " + having + ")";
    }
}
