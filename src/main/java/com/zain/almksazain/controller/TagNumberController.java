package com.zain.almksazain.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.almksazain.model.TagNumber;
import com.zain.almksazain.services.TagNumberService;
import com.zain.almksazain.services.TagNumberService.TagNumberItem;
import com.zain.almksazain.services.TagNumberValidationException;

/**
 * TAG Number configuration (Configurations > Acceptance Workflow > TAG Numbers) and its audit
 * trail (Approval Processes > TAG Number Audit Trail). Writes go through TagNumberService, which
 * validates everything first and saves nothing if any item fails. Validation failures come back
 * as 400 with responseCode "1" and an "errors" list.
 */
@RestController
public class TagNumberController {

    private static final Logger logger = LoggerFactory.getLogger(TagNumberController.class);

    @Autowired
    private TagNumberService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================================================
    // TAG Numbers
    // ============================================================

    // Filterable columns -> SQL. Text columns match on "contains" (LIKE); tagNumber's binary
    // collation keeps that match case-sensitive.
    private static final Map<String, String> tagNumberColumns = new HashMap<>();
    private static final List<String> tagNumberExactColumns = List.of("recordNo", "status");
    static {
        tagNumberColumns.put("recordNo", "t.recordNo");
        tagNumberColumns.put("tagNumber", "t.tagNumber");
        tagNumberColumns.put("vendorNumber", "t.vendorNumber");
        tagNumberColumns.put("vendorName", "t.vendorName");
        tagNumberColumns.put("status", "t.status");
        tagNumberColumns.put("createdBy", "t.createdBy");
        tagNumberColumns.put("createdDate", "t.createdDate");
        tagNumberColumns.put("lastUpdatedBy", "t.lastUpdatedBy");
        tagNumberColumns.put("lastUpdatedDate", "t.lastUpdatedDate");
    }

    @PostMapping(value = "/tagNumbers/filter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterTagNumbers(
            @RequestBody(required = false) Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            // Deleted TAGs never reach the UI.
            String whereClause = " WHERE t.status <> 'Deleted'";
            List<Object> params = new ArrayList<>();
            if (filters != null) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    String column = tagNumberColumns.get(entry.getKey());
                    if (column == null || entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                        continue;
                    }
                    if (tagNumberExactColumns.contains(entry.getKey())) {
                        whereClause += " AND " + column + " = ?";
                        params.add(entry.getValue().trim());
                    } else {
                        whereClause += " AND " + column + " LIKE ?";
                        params.add("%" + entry.getValue().trim() + "%");
                    }
                }
            }

            String from = " FROM tb_TagNumber t";
            int totalRecords = jdbcTemplate.queryForObject("SELECT COUNT(*)" + from + whereClause,
                    params.toArray(), Integer.class);

            String orderColumn = tagNumberColumns.getOrDefault(sortBy, "t.recordNo");
            String sql = "SELECT t.recordNo, t.tagNumber, t.vendorNumber, t.vendorName, t.status, t.createdBy, "
                    + "t.createdDate, t.lastUpdatedBy, t.lastUpdatedDate" + from + whereClause
                    + " ORDER BY " + orderColumn + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC")
                    + " LIMIT ? OFFSET ?";
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(size);
            sqlParams.add(page * size);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, sqlParams.toArray());

            return ResponseEntity.ok(pageResponse(result, page, size, totalRecords, sortBy, sortDir));
        } catch (Exception e) {
            logger.error("Error filtering TAG Numbers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering TAG Numbers: " + e.getMessage()));
        }
    }

    /**
     * Body: {createdById, createdByName, items: [{tagNumber, vendorNumber, status?, rowNumber?}]}.
     * One item from the form, many from a bulk upload; vendorName is looked up from tb_Supplier.
     */
    @PostMapping(value = "/tagNumbers/create", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> createTagNumbers(@RequestBody Map<String, Object> body) {
        try {
            List<TagNumberItem> items = new ArrayList<>();
            Object rawItems = body.get("items");
            if (rawItems instanceof List) {
                for (Object raw : (List<?>) rawItems) {
                    if (!(raw instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> m = (Map<?, ?>) raw;
                    TagNumberItem item = new TagNumberItem();
                    item.tagNumber = str(m.get("tagNumber"));
                    item.vendorNumber = str(m.get("vendorNumber"));
                    item.status = str(m.get("status"));
                    item.rowNumber = toInteger(m.get("rowNumber"));
                    items.add(item);
                }
            }
            List<TagNumber> created = service.createTags(items, toInteger(body.get("createdById")),
                    str(body.get("createdByName")));
            Map<String, Object> resp = success(created.size() == 1
                    ? "TAG Number created successfully."
                    : created.size() + " TAG Numbers created successfully.");
            resp.put("createdCount", created.size());
            return ResponseEntity.ok(resp);
        } catch (TagNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (Exception ex) {
            logger.error("Failed to create TAG Number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    /** Body: {recordNo, tagNumber, vendorNumber, status, updatedById, updatedByName}. */
    @PostMapping(value = "/tagNumbers/update", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> updateTagNumber(@RequestBody Map<String, Object> body) {
        try {
            service.updateTag(toInteger(body.get("recordNo")), str(body.get("tagNumber")),
                    str(body.get("vendorNumber")), str(body.get("status")),
                    toInteger(body.get("updatedById")), str(body.get("updatedByName")));
            return ResponseEntity.ok(success("TAG Number updated successfully."));
        } catch (TagNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (Exception ex) {
            logger.error("Failed to update TAG Number", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    /** Soft delete. Body: {recordNos: [..], deletedById, deletedByName}. */
    @PostMapping(value = "/tagNumbers/delete", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> deleteTagNumbers(@RequestBody Map<String, Object> body) {
        try {
            List<Integer> recordNos = new ArrayList<>();
            Object raw = body.get("recordNos");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    Integer recordNo = toInteger(o);
                    if (recordNo != null) {
                        recordNos.add(recordNo);
                    }
                }
            }
            int deleted = service.deleteTags(recordNos, toInteger(body.get("deletedById")),
                    str(body.get("deletedByName")));
            return ResponseEntity.ok(success(deleted == 1
                    ? "TAG Number deleted successfully."
                    : deleted + " TAG Numbers deleted successfully."));
        } catch (TagNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (Exception ex) {
            logger.error("Failed to delete TAG Number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    // ============================================================
    // Audit trail
    // ============================================================

    // Package-private so ExportsController's audit-trail export job filters/selects exactly
    // what this page shows (same pattern as ReportsController.uplAuditTrailColumns).
    static final Map<String, String> tagNumberAuditTrailColumns = new HashMap<>();
    static {
        tagNumberAuditTrailColumns.put("recordId", "a.recordId");
        tagNumberAuditTrailColumns.put("batchId", "a.batchId");
        tagNumberAuditTrailColumns.put("tagRecordNo", "a.tagRecordNo");
        tagNumberAuditTrailColumns.put("changeType", "a.changeType");
        tagNumberAuditTrailColumns.put("tagNumber", "a.tagNumber");
        tagNumberAuditTrailColumns.put("vendorNumber", "a.vendorNumber");
        tagNumberAuditTrailColumns.put("vendorName", "a.vendorName");
        tagNumberAuditTrailColumns.put("changedByName", "a.changedByName");
        tagNumberAuditTrailColumns.put("changedAt", "a.changedAt");
    }

    static final String TAG_NUMBER_AUDIT_TRAIL_SELECT =
            "SELECT a.recordId AS recordId, a.batchId AS batchId, a.tagRecordNo AS tagRecordNo, "
                    + "a.changeType AS changeType, a.tagNumber AS tagNumber, a.vendorNumber AS vendorNumber, "
                    + "a.vendorName AS vendorName, a.fieldChanges AS fieldChanges, a.changedBy AS changedBy, "
                    + "a.changedByName AS changedByName, a.changedAt AS changedAt ";
    static final String TAG_NUMBER_AUDIT_TRAIL_FROM = "FROM tb_TagNumber_Audit_Log a ";

    /** Builds " WHERE ..." from the filter map (exact match, whitelisted columns only) into params. */
    static String tagNumberAuditTrailWhere(Map<String, String> filters, List<Object> params) {
        String whereClause = " WHERE 1=1";
        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                if (tagNumberAuditTrailColumns.containsKey(entry.getKey()) && entry.getValue() != null
                        && !entry.getValue().trim().isEmpty()) {
                    whereClause += " AND " + tagNumberAuditTrailColumns.get(entry.getKey()) + " = ?";
                    params.add(entry.getValue().trim());
                }
            }
        }
        return whereClause;
    }

    @PostMapping(value = "/tagNumberAuditTrailFilter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterTagNumberAuditTrail(
            @RequestBody(required = false) Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            List<Object> params = new ArrayList<>();
            String whereClause = tagNumberAuditTrailWhere(filters, params);
            int totalRecords = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) " + TAG_NUMBER_AUDIT_TRAIL_FROM + whereClause, params.toArray(), Integer.class);

            String orderColumn = tagNumberAuditTrailColumns.getOrDefault(sortBy, "a.recordId");
            String sql = TAG_NUMBER_AUDIT_TRAIL_SELECT + TAG_NUMBER_AUDIT_TRAIL_FROM + whereClause
                    + " ORDER BY " + orderColumn + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC")
                    + " LIMIT ? OFFSET ?";
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(size);
            sqlParams.add(page * size);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, sqlParams.toArray());

            return ResponseEntity.ok(pageResponse(result, page, size, totalRecords, sortBy, sortDir));
        } catch (Exception e) {
            logger.error("Error filtering TAG Number audit trail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering TAG Number audit trail: " + e.getMessage()));
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static Map<String, Object> pageResponse(List<Map<String, Object>> result, int page, int size,
            int totalRecords, String sortBy, String sortDir) {
        Map<String, Object> response = new HashMap<>();
        response.put("reports", result);
        response.put("currentPage", page);
        response.put("totalItems", totalRecords);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
        response.put("first", page == 0);
        response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
        response.put("size", size);
        response.put("sort", sortBy + "," + sortDir);
        return response;
    }

    private static Map<String, Object> success(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "0");
        body.put("message", message);
        return body;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "1");
        body.put("message", message);
        body.put("errors", Collections.singletonList(message));
        return body;
    }

    private static Map<String, Object> validationError(TagNumberValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "1");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        return body;
    }

    /** Spreadsheet cells can arrive as numbers (e.g. vendor 138 -> 138.0); keep whole numbers as "138". */
    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
        }
        return String.valueOf(value);
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
