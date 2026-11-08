package com.zain.almksazain.specs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zain.almksazain.specs.TagNumberFilterBuilder.Column;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Condition;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Kind;

/**
 * SELECT / FROM / ORDER BY for the Serial Numbers page and the Serial Number Audit Trail page -
 * used by both the paged fetch endpoints (SerialNumberController) and their export jobs
 * (ExportsController), so a filter behaves the same on screen and in the exported file.
 * Conditions are parsed and turned into SQL by TagNumberFilterBuilder (same request shape,
 * operators and date handling as the TAG Number pages).
 *
 * The Serial Numbers page reads two sources with the same columns:
 *   SERIAL  - tb_SerialNumber (s), the editable rows (non-deleted), newest recordNo first;
 *   REQUEST - serial numbers on lines (ln) of approved-received acceptance requests (d), read-only,
 *             with the request as Request No, its vendor number / name as Organization Code / Name,
 *             and the line's actual item code (else its UPL item code, else its item code - non-UPL
 *             lines carry their part number there) as Item Code.
 * A column one source doesn't have is NULL there, so filtering on it matches only the other
 * source (and "is empty" matches the source without it).
 */
public final class SerialNumberFilterBuilder {

    private SerialNumberFilterBuilder() {
    }

    public static final String SOURCE_SERIAL = "Serial Numbers";
    public static final String SOURCE_REQUEST = "Acceptance Request";

    private static final String REQUEST_ITEM_CODE_SQL = "COALESCE(NULLIF(TRIM(ln.actualItemCode), ''), "
            + "NULLIF(TRIM(ln.uplItemCode), ''), NULLIF(TRIM(ln.itemCode), ''))";

    /** Grid field -> SQL in each source. */
    public static final Map<String, Column> SERIAL_COLUMNS = new LinkedHashMap<>();
    public static final Map<String, Column> REQUEST_COLUMNS = new LinkedHashMap<>();
    static {
        add("source", Column.exact("'" + SOURCE_SERIAL + "'"), Column.exact("'" + SOURCE_REQUEST + "'"));
        add("recordNo", Column.number("s.recordNo"), Column.number("NULL"));
        add("serialNumber", Column.text("s.serialNumber"), Column.text("ln.serialNumber"));
        add("itemCode", Column.text("s.itemCode"), Column.text(REQUEST_ITEM_CODE_SQL));
        add("organizationCode", Column.text("s.organizationCode"), Column.text("d.vendorNumber"));
        add("organizationName", Column.text("s.organizationName"), Column.text("d.vendorName"));
        add("requestNo", Column.text("NULL"), Column.text("ln.dccId"));
        add("invoice", Column.text("s.invoice"), Column.text("NULL"));
        add("validationRule", Column.exact("s.validationRule"), Column.exact("NULL"));
        add("comment", Column.text("s.comment"), Column.text("NULL"));
        add("status", Column.exact("s.status"), Column.exact("NULL"));
        add("createdBy", Column.text("s.createdBy"), Column.text("d.createdBy"));
        add("recordDatetime", Column.date("s.recordDatetime"), Column.date("d.createdDate"));
        add("updatedBy", Column.text("s.updatedBy"), Column.text("NULL"));
        add("updatedDatetime", Column.date("s.updatedDatetime"), Column.date("NULL"));
    }

    private static void add(String field, Column serial, Column request) {
        SERIAL_COLUMNS.put(field, serial);
        REQUEST_COLUMNS.put(field, request);
    }

    // Deleted rows never reach the UI. Uses idx_serial_number_status for the count.
    public static final String SERIAL_FROM = "FROM tb_SerialNumber s WHERE s.status <> 'Deleted'";
    // Driven from tb_DCC's (status, poNumber) index; matching dccId as text lets tb_DCC_LN's
    // dccId index find each request's lines (an INT = VARCHAR comparison would scan them all).
    public static final String REQUEST_FROM = "FROM tb_DCC d JOIN tb_DCC_LN ln "
            + "ON ln.dccId = CAST(d.recordNo AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_0900_ai_ci "
            + "WHERE d.status = 'approved-received' AND ln.serialNumber IS NOT NULL AND TRIM(ln.serialNumber) <> ''";

    // Same column aliases in both, so they can be UNIONed. rowId is unique across the two;
    // sourceOrder/sortKey give the default order (tb_SerialNumber first, newest first).
    public static final String SERIAL_SELECT = "SELECT CONCAT('SN-', s.recordNo) AS rowId, 0 AS sourceOrder, "
            + "s.recordNo AS sortKey, '" + SOURCE_SERIAL + "' AS source, s.recordNo AS recordNo, "
            + "s.serialNumber AS serialNumber, s.itemCode AS itemCode, s.organizationCode AS organizationCode, "
            + "s.organizationName AS organizationName, NULL AS requestNo, s.invoice AS invoice, "
            + "s.validationRule AS validationRule, s.comment AS comment, s.status AS status, "
            + "s.createdBy AS createdBy, s.recordDatetime AS recordDatetime, s.updatedBy AS updatedBy, "
            + "s.updatedDatetime AS updatedDatetime ";
    public static final String REQUEST_SELECT = "SELECT CONCAT('AR-', ln.recordNo) AS rowId, 1 AS sourceOrder, "
            + "ln.recordNo AS sortKey, '" + SOURCE_REQUEST + "' AS source, NULL AS recordNo, "
            + "ln.serialNumber AS serialNumber, " + REQUEST_ITEM_CODE_SQL + " AS itemCode, "
            + "d.vendorNumber AS organizationCode, d.vendorName AS organizationName, ln.dccId AS requestNo, NULL AS invoice, "
            + "NULL AS validationRule, NULL AS comment, NULL AS status, d.createdBy AS createdBy, "
            + "d.createdDate AS recordDatetime, NULL AS updatedBy, NULL AS updatedDatetime ";

    /** Default order inside each source (and the export's order). */
    public static final String SERIAL_ORDER = " ORDER BY s.recordNo DESC";
    public static final String REQUEST_ORDER = " ORDER BY ln.recordNo DESC";

    /** Both sources' WHERE fragments for one request, with their own parameter lists. */
    public static final class Where {
        public final String serial;
        public final List<Object> serialParams = new ArrayList<>();
        public final String request;
        public final List<Object> requestParams = new ArrayList<>();
        public final boolean filtered;

        Where(List<Condition> conditions) {
            serial = TagNumberFilterBuilder.buildWhere(SERIAL_COLUMNS, conditions, serialParams);
            request = TagNumberFilterBuilder.buildWhere(REQUEST_COLUMNS, conditions, requestParams);
            filtered = TagNumberFilterBuilder.hasActiveConditions(conditions);
        }
    }

    public static Where where(Map<String, Object> body) {
        return new Where(TagNumberFilterBuilder.parseConditions(body, SERIAL_COLUMNS));
    }

    /** True for the default order (none, or by Record No), which pages each source on its own index. */
    public static boolean isDefaultSort(String sortBy) {
        return sortBy == null || sortBy.isEmpty() || sortBy.equals("recordNo") || !SERIAL_COLUMNS.containsKey(sortBy);
    }

    /** ORDER BY over the UNION of both sources for any other whitelisted column. */
    public static String unionOrderBy(String sortBy, String sortDir) {
        String dir = "asc".equalsIgnoreCase(sortDir) ? " ASC" : " DESC";
        return " ORDER BY u." + sortBy + dir + ", u.sourceOrder ASC, u.sortKey DESC";
    }

    /**
     * Up to `limit` distinct values of one column across both sources (not just the page on
     * screen) for the "Filter By" dialog's suggestions; null for date or unknown columns.
     */
    public static String distinctValuesSql(String field, int limit) {
        Column serial = field == null ? null : SERIAL_COLUMNS.get(field);
        Column request = field == null ? null : REQUEST_COLUMNS.get(field);
        if (serial == null || serial.kind == Kind.DATE || field.equals("source")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (!"NULL".equals(serial.sql)) {
            parts.add(distinctPart(serial, SERIAL_FROM, limit));
        }
        if (!"NULL".equals(request.sql)) {
            parts.add(distinctPart(request, REQUEST_FROM, limit));
        }
        String desc = serial.kind == Kind.NUMBER ? " DESC" : "";
        return "SELECT v FROM (" + String.join(" UNION ", parts) + ") x ORDER BY v" + desc + " LIMIT " + limit;
    }

    private static String distinctPart(Column column, String from, int limit) {
        String notBlank = column.kind == Kind.NUMBER
                ? column.sql + " IS NOT NULL"
                : column.sql + " IS NOT NULL AND " + column.sql + " <> ''";
        return "(SELECT DISTINCT " + column.sql + " AS v " + from + " AND " + notBlank
                + " ORDER BY v" + (column.kind == Kind.NUMBER ? " DESC" : "") + " LIMIT " + limit + ")";
    }

    // ============================================================
    // Serial Number Audit Trail page
    // ============================================================

    public static final Map<String, Column> AUDIT_TRAIL_COLUMNS = new LinkedHashMap<>();
    static {
        AUDIT_TRAIL_COLUMNS.put("recordId", Column.number("a.recordId"));
        AUDIT_TRAIL_COLUMNS.put("serialRecordNo", Column.number("a.serialRecordNo"));
        AUDIT_TRAIL_COLUMNS.put("changeType", Column.exact("a.changeType"));
        AUDIT_TRAIL_COLUMNS.put("serialNumber", Column.text("a.serialNumber"));
        AUDIT_TRAIL_COLUMNS.put("itemCode", Column.text("a.itemCode"));
        AUDIT_TRAIL_COLUMNS.put("changedByName", Column.text("a.changedByName"));
        AUDIT_TRAIL_COLUMNS.put("changedAt", Column.date("a.changedAt"));
        AUDIT_TRAIL_COLUMNS.put("batchId", Column.text("a.batchId"));
    }

    public static final String AUDIT_TRAIL_SELECT =
            "SELECT a.recordId AS recordId, a.batchId AS batchId, a.serialRecordNo AS serialRecordNo, "
                    + "a.changeType AS changeType, a.serialNumber AS serialNumber, a.itemCode AS itemCode, "
                    + "a.fieldChanges AS fieldChanges, a.changedBy AS changedBy, a.changedByName AS changedByName, "
                    + "a.changedAt AS changedAt ";
    public static final String AUDIT_TRAIL_FROM = "FROM tb_SerialNumber_Audit_Log a WHERE 1=1";
}
