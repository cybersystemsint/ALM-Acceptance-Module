package com.zain.almksazain.specs;

import java.util.LinkedHashMap;
import java.util.Map;

import com.zain.almksazain.specs.TagNumberFilterBuilder.Column;

/**
 * SELECT / FROM for the "Raised in Acceptance Requests" tab of the Received Serials page and its
 * audit trail - used by the paged fetch endpoints (RequestSerialNumberController) and their
 * export jobs (ExportsController), so a filter behaves the same on screen and in the file.
 * Conditions are parsed and turned into SQL by TagNumberFilterBuilder (same request shape,
 * operators and date handling as the other Received Serials and TAG Number pages).
 *
 * One row per acceptance request line (ln) that carries a serial number, on a request (d) that
 * is not rejected or returned. Request No is the request, Request Status its tb_DCC.status, Organization Code its vendor number and
 * Organization Name that vendor's tb_Supplier name (the request's own vendorName when the vendor
 * is gone). Item Code is the line's item code, else its actual item code, else - non-UPL lines
 * store neither - its PO line's part number.
 */
public final class RequestSerialNumberFilterBuilder {

    private RequestSerialNumberFilterBuilder() {
    }

    /** Requests whose lines are listed: every status but these. */
    public static final String EXCLUDED_REQUEST_STATUSES = "'rejected', 'returned'";

    public static final String PO_PART_NUMBER_SQL = "(SELECT po.itemPartNumber FROM tb_PurchaseOrder po "
            + "WHERE po.poNumber = ln.poId AND po.lineNumber = ln.lineNumber ORDER BY po.recordNo DESC LIMIT 1)";
    public static final String ITEM_CODE_SQL = "COALESCE(NULLIF(TRIM(ln.itemCode), ''), NULLIF(TRIM(ln.actualItemCode), ''), "
            + PO_PART_NUMBER_SQL + ")";
    public static final String ORGANIZATION_NAME_SQL = "COALESCE((SELECT sp.supplierName FROM tb_Supplier sp "
            + "WHERE sp.supplierId = d.vendorNumber ORDER BY sp.recordNo DESC LIMIT 1), d.vendorName)";

    /** Grid field -> SQL. Same field names as the "Received Outside ALM" tab where they mean the same. */
    public static final Map<String, Column> COLUMNS = new LinkedHashMap<>();
    static {
        COLUMNS.put("recordNo", Column.number("ln.recordNo"));
        COLUMNS.put("serialNumber", Column.text("ln.serialNumber"));
        COLUMNS.put("itemCode", Column.text(ITEM_CODE_SQL));
        COLUMNS.put("actualItemCode", Column.text("ln.actualItemCode"));
        COLUMNS.put("organizationCode", Column.text("d.vendorNumber"));
        COLUMNS.put("organizationName", Column.text(ORGANIZATION_NAME_SQL));
        COLUMNS.put("requestNo", Column.number("CAST(ln.dccId AS UNSIGNED)"));
        COLUMNS.put("requestStatus", Column.text("d.status"));
        COLUMNS.put("validationRule", Column.exact("ln.validationRule"));
        COLUMNS.put("status", Column.exact("ln.serialStatus"));
        COLUMNS.put("comment", Column.text("ln.serialComment"));
        COLUMNS.put("createdBy", Column.text("d.createdBy"));
        COLUMNS.put("updatedBy", Column.text("ln.updatedBy"));
        COLUMNS.put("recordDatetime", Column.date("d.createdDate"));
        COLUMNS.put("updatedDatetime", Column.date("ln.updatedDatetime"));
    }

    // Driven from tb_DCC_LN's primary key (newest line first), each line finding its request by
    // tb_DCC's primary key; dccId always holds a whole number.
    public static final String FROM = "FROM tb_DCC_LN ln JOIN tb_DCC d ON d.recordNo = CAST(ln.dccId AS UNSIGNED) "
            + "WHERE d.status NOT IN (" + EXCLUDED_REQUEST_STATUSES + ") "
            + "AND ln.serialNumber IS NOT NULL AND ln.serialNumber <> ''";

    public static final String SELECT = "SELECT ln.recordNo AS recordNo, ln.serialNumber AS serialNumber, "
            + ITEM_CODE_SQL + " AS itemCode, ln.actualItemCode AS actualItemCode, d.vendorNumber AS organizationCode, "
            + ORGANIZATION_NAME_SQL + " AS organizationName, ln.dccId AS requestNo, d.status AS requestStatus, "
            + "ln.validationRule AS validationRule, ln.serialStatus AS status, ln.serialComment AS comment, "
            + "d.createdBy AS createdBy, ln.updatedBy AS updatedBy, d.createdDate AS recordDatetime, "
            + "ln.updatedDatetime AS updatedDatetime ";

    /** Default order (and the export's): newest line first. */
    public static final String DEFAULT_ORDER_SQL = "ln.recordNo";

    // ============================================================
    // Audit trail (Audit Trail > Received Serials > Raised in Acceptance Requests)
    // ============================================================

    public static final Map<String, Column> AUDIT_TRAIL_COLUMNS = new LinkedHashMap<>();
    static {
        AUDIT_TRAIL_COLUMNS.put("recordId", Column.number("a.recordId"));
        AUDIT_TRAIL_COLUMNS.put("lineRecordNo", Column.number("a.lineRecordNo"));
        AUDIT_TRAIL_COLUMNS.put("requestNo", Column.text("a.requestNo"));
        AUDIT_TRAIL_COLUMNS.put("changeType", Column.exact("a.changeType"));
        AUDIT_TRAIL_COLUMNS.put("serialNumber", Column.text("a.serialNumber"));
        AUDIT_TRAIL_COLUMNS.put("itemCode", Column.text("a.itemCode"));
        AUDIT_TRAIL_COLUMNS.put("changedByName", Column.text("a.changedByName"));
        AUDIT_TRAIL_COLUMNS.put("changedAt", Column.date("a.changedAt"));
        AUDIT_TRAIL_COLUMNS.put("batchId", Column.text("a.batchId"));
    }

    public static final String AUDIT_TRAIL_SELECT =
            "SELECT a.recordId AS recordId, a.batchId AS batchId, a.lineRecordNo AS lineRecordNo, "
                    + "a.requestNo AS requestNo, a.changeType AS changeType, a.serialNumber AS serialNumber, "
                    + "a.itemCode AS itemCode, a.fieldChanges AS fieldChanges, a.changedBy AS changedBy, "
                    + "a.changedByName AS changedByName, a.changedAt AS changedAt ";
    public static final String AUDIT_TRAIL_FROM = "FROM tb_DCC_LN_Serial_Audit_Log a WHERE 1=1";
}
