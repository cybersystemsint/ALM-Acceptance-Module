package com.zain.almksazain.specs;

import java.util.LinkedHashMap;
import java.util.Map;

import com.zain.almksazain.specs.TagNumberFilterBuilder.Column;

/**
 * SELECT / FROM for the "Received Outside ALM" tab of the Received Serials page (tb_SerialNumber:
 * the Oracle load plus serial numbers added here) and the Serial Number Audit Trail page - used
 * by both the paged fetch endpoints (SerialNumberController) and their export jobs
 * (ExportsController), so a filter behaves the same on screen and in the exported file.
 * Conditions are parsed and turned into SQL by TagNumberFilterBuilder (same request shape,
 * operators and date handling as the TAG Number pages). The serial numbers raised on acceptance
 * requests are the other tab - RequestSerialNumberFilterBuilder.
 */
public final class SerialNumberFilterBuilder {

    private SerialNumberFilterBuilder() {
    }

    /** Grid field -> SQL. */
    public static final Map<String, Column> SERIAL_COLUMNS = new LinkedHashMap<>();
    static {
        SERIAL_COLUMNS.put("recordNo", Column.number("s.recordNo"));
        SERIAL_COLUMNS.put("serialNumber", Column.text("s.serialNumber"));
        SERIAL_COLUMNS.put("itemCode", Column.text("s.itemCode"));
        SERIAL_COLUMNS.put("organizationCode", Column.text("s.organizationCode"));
        SERIAL_COLUMNS.put("organizationName", Column.text("s.organizationName"));
        SERIAL_COLUMNS.put("invoice", Column.text("s.invoice"));
        SERIAL_COLUMNS.put("validationRule", Column.exact("s.validationRule"));
        SERIAL_COLUMNS.put("comment", Column.text("s.comment"));
        SERIAL_COLUMNS.put("status", Column.exact("s.status"));
        SERIAL_COLUMNS.put("createdBy", Column.text("s.createdBy"));
        SERIAL_COLUMNS.put("recordDatetime", Column.date("s.recordDatetime"));
        SERIAL_COLUMNS.put("updatedBy", Column.text("s.updatedBy"));
        SERIAL_COLUMNS.put("updatedDatetime", Column.date("s.updatedDatetime"));
    }

    // Deleted rows never reach the UI. Uses idx_serial_number_status for the count.
    public static final String SERIAL_FROM = "FROM tb_SerialNumber s WHERE s.status <> 'Deleted'";

    public static final String SERIAL_SELECT = "SELECT s.recordNo AS recordNo, s.serialNumber AS serialNumber, "
            + "s.itemCode AS itemCode, s.organizationCode AS organizationCode, "
            + "s.organizationName AS organizationName, s.invoice AS invoice, "
            + "s.validationRule AS validationRule, s.comment AS comment, s.status AS status, "
            + "s.createdBy AS createdBy, s.recordDatetime AS recordDatetime, s.updatedBy AS updatedBy, "
            + "s.updatedDatetime AS updatedDatetime ";

    /** Default order (and the export's): newest first. */
    public static final String DEFAULT_ORDER_SQL = "s.recordNo";

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
