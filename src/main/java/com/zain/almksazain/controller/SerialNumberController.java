package com.zain.almksazain.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.almksazain.services.SerialNumberConfirmationException;
import com.zain.almksazain.services.SerialNumberService;
import com.zain.almksazain.services.SerialNumberService.SaveResult;
import com.zain.almksazain.services.SerialNumberService.SerialNumberItem;
import com.zain.almksazain.services.SerialNumberValidationException;
import com.zain.almksazain.specs.SerialNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Condition;

/**
 * Serial Number configuration (Configurations > Acceptance Workflow > Serial Numbers) and its
 * audit trail (Audit Trail > Serial Numbers). Writes go through SerialNumberService, which
 * validates everything first and saves nothing if any item fails. Validation failures come back
 * as 400 with responseCode "1" and an "errors" list; a save that would also change the Validation
 * Rule of other rows comes back as 409 with requiresConfirmation and is only made when sent again
 * with confirmValidationRuleChange = true. Filtering (and the matching exports in
 * ExportsController) goes through SerialNumberFilterBuilder.
 */
@RestController
public class SerialNumberController {

    private static final Logger logger = LoggerFactory.getLogger(SerialNumberController.class);

    private static final int COLUMN_VALUES_LIMIT = 200;

    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private SerialNumberService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================================================
    // Serial Numbers
    // ============================================================

    /**
     * Body: {conditions: [{field, operator, value}]} - see SerialNumberFilterBuilder. tb_SerialNumber
     * rows come first, then the serial numbers of approved-received acceptance requests. In the
     * default order (by Record No) each source is paged on its own index, so a page costs two
     * counts and one or two LIMITed reads however many rows there are; any other sort orders the
     * union of both.
     */
    @PostMapping(value = "/serialNumbers/filter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterSerialNumbers(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            String dir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";

            SerialNumberFilterBuilder.Where where = SerialNumberFilterBuilder.where(body);
            int serialCount = count(SerialNumberFilterBuilder.SERIAL_FROM + where.serial, where.serialParams);
            int requestCount = count(SerialNumberFilterBuilder.REQUEST_FROM + where.request, where.requestParams);
            int total = serialCount + requestCount;
            long offset = (long) page * size;

            List<Map<String, Object>> rows = new ArrayList<>();
            if (SerialNumberFilterBuilder.isDefaultSort(sortBy)) {
                if (offset < serialCount) {
                    rows.addAll(page(SerialNumberFilterBuilder.SERIAL_SELECT + SerialNumberFilterBuilder.SERIAL_FROM
                            + where.serial + " ORDER BY s.recordNo " + dir, where.serialParams, size, offset));
                }
                int remaining = size - rows.size();
                if (remaining > 0 && requestCount > 0 && offset + rows.size() < total) {
                    long requestOffset = Math.max(0, offset - serialCount);
                    rows.addAll(page(SerialNumberFilterBuilder.REQUEST_SELECT + SerialNumberFilterBuilder.REQUEST_FROM
                            + where.request + " ORDER BY ln.recordNo " + dir, where.requestParams, remaining, requestOffset));
                }
            } else if (offset < total) {
                List<Object> params = new ArrayList<>(where.serialParams);
                params.addAll(where.requestParams);
                String sql = "SELECT * FROM (" + SerialNumberFilterBuilder.SERIAL_SELECT
                        + SerialNumberFilterBuilder.SERIAL_FROM + where.serial + " UNION ALL "
                        + SerialNumberFilterBuilder.REQUEST_SELECT + SerialNumberFilterBuilder.REQUEST_FROM
                        + where.request + ") u" + SerialNumberFilterBuilder.unionOrderBy(sortBy, sortDir);
                rows.addAll(page(sql, params, size, offset));
            }

            Map<String, Object> response = pageResponse(rows, page, size, total, sortBy, sortDir);
            response.put("serialNumberCount", serialCount);
            response.put("acceptanceRequestCount", requestCount);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error filtering Serial Numbers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering Serial Numbers: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/serialNumbers/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> serialNumberColumnValues(@RequestParam String column) {
        return columnValues(SerialNumberFilterBuilder.distinctValuesSql(column, COLUMN_VALUES_LIMIT), column);
    }

    /**
     * The one-by-one form. Body: {createdById, createdByName, confirmValidationRuleChange?,
     * items: [{serialNumber, organizationCode, itemCode?, invoice?, validationRule?, comment?, status?}]}:
     * organizationCode is the vendor number, and Organization Name is looked up from tb_Supplier.
     */
    @PostMapping(value = "/serialNumbers/create", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> createSerialNumbers(@RequestBody Map<String, Object> body) {
        List<SerialNumberItem> items = items(body.get("items"));
        items.forEach(item -> item.recordNo = null);
        return save(items, toInteger(body.get("createdById")), str(body.get("createdByName")), false,
                confirmed(body));
    }

    /**
     * Bulk upload. Body: {uploadedById, uploadedByName, confirmValidationRuleChange?, items:
     * [{recordNo?, serialNumber, ...same fields as create, rowNumber}]}: rows with a recordNo update
     * that row, rows without one create a new one. A row without an optional field's key (the file
     * has no such column) keeps the stored value. All rows are saved or none are.
     */
    @PostMapping(value = "/serialNumbers/upload", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> uploadSerialNumbers(@RequestBody Map<String, Object> body) {
        return save(items(body.get("items")), toInteger(body.get("uploadedById")), str(body.get("uploadedByName")),
                false, confirmed(body));
    }

    /** Body: {recordNo, serialNumber, ...same fields as create, updatedById, updatedByName, confirmValidationRuleChange?}. */
    @PostMapping(value = "/serialNumbers/update", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> updateSerialNumber(@RequestBody Map<String, Object> body) {
        SerialNumberItem item = item(body);
        if (item.recordNo == null || item.recordNo <= 0) {
            return ResponseEntity.badRequest().body(error("recordNo is required."));
        }
        return save(Collections.singletonList(item), toInteger(body.get("updatedById")),
                str(body.get("updatedByName")), true, confirmed(body));
    }

    /** Soft delete. Body: {recordNos: [..], deletedById, deletedByName}. */
    @PostMapping(value = "/serialNumbers/delete", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> deleteSerialNumbers(@RequestBody Map<String, Object> body) {
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
            int deleted = service.deleteSerialNumbers(recordNos, toInteger(body.get("deletedById")),
                    str(body.get("deletedByName")));
            return ResponseEntity.ok(success(deleted == 1
                    ? "Serial Number deleted successfully."
                    : deleted + " Serial Numbers deleted successfully."));
        } catch (SerialNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (Exception ex) {
            logger.error("Failed to delete Serial Number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    // ============================================================
    // Audit trail
    // ============================================================

    /** Body: {conditions: [{field, operator, value}]} - see SerialNumberFilterBuilder. */
    @PostMapping(value = "/serialNumberAuditTrailFilter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterSerialNumberAuditTrail(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            Map<String, TagNumberFilterBuilder.Column> columns = SerialNumberFilterBuilder.AUDIT_TRAIL_COLUMNS;
            List<Condition> conditions = TagNumberFilterBuilder.parseConditions(body, columns);
            List<Object> params = new ArrayList<>();
            String where = TagNumberFilterBuilder.buildWhere(columns, conditions, params);
            int total = count(SerialNumberFilterBuilder.AUDIT_TRAIL_FROM + where, params);
            String sql = SerialNumberFilterBuilder.AUDIT_TRAIL_SELECT + SerialNumberFilterBuilder.AUDIT_TRAIL_FROM + where
                    + TagNumberFilterBuilder.orderBy(columns, sortBy, sortDir, "a.recordId");
            return ResponseEntity.ok(pageResponse(page(sql, params, size, (long) page * size), page, size, total,
                    sortBy, sortDir));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error filtering Serial Number audit trail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering Serial Number audit trail: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/serialNumberAuditTrail/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> serialNumberAuditTrailColumnValues(@RequestParam String column) {
        return columnValues(TagNumberFilterBuilder.distinctValuesSql(SerialNumberFilterBuilder.AUDIT_TRAIL_COLUMNS,
                column, SerialNumberFilterBuilder.AUDIT_TRAIL_FROM, COLUMN_VALUES_LIMIT), column);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private int count(String fromAndWhere, List<Object> params) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromAndWhere, params.toArray(), Integer.class);
        return n == null ? 0 : n;
    }

    private List<Map<String, Object>> page(String sql, List<Object> params, int limit, long offset) {
        List<Object> sqlParams = new ArrayList<>(params);
        sqlParams.add(limit);
        sqlParams.add(offset);
        return jdbcTemplate.queryForList(sql + " LIMIT ? OFFSET ?", sqlParams.toArray());
    }

    private ResponseEntity<Map<String, Object>> columnValues(String sql, String column) {
        Map<String, Object> resp = new HashMap<>();
        List<String> values = new ArrayList<>();
        if (sql != null) {
            try {
                jdbcTemplate.query(sql, rs -> {
                    values.add(rs.getString("v"));
                });
            } catch (Exception e) {
                logger.error("Error loading values for Serial Number column {}", column, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(e.getMessage()));
            }
        }
        resp.put("uniqueValues", values);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> save(List<SerialNumberItem> items, Integer userId, String userName,
            boolean rejectUnchanged, boolean confirmRuleChange) {
        try {
            SaveResult result = service.saveSerialNumbers(items, userId, userName, rejectUnchanged, confirmRuleChange);
            Map<String, Object> resp = success(saveMessage(result));
            resp.put("createdCount", result.created);
            resp.put("updatedCount", result.updated);
            resp.put("unchangedCount", result.unchanged);
            resp.put("ruleUpdatedOthersCount", result.ruleUpdatedOthers);
            return ResponseEntity.ok(resp);
        } catch (SerialNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (SerialNumberConfirmationException ex) {
            Map<String, Object> body = new HashMap<>();
            body.put("responseCode", "2");
            body.put("requiresConfirmation", true);
            body.put("message", ex.getMessage());
            List<Map<String, Object>> changes = new ArrayList<>();
            for (SerialNumberConfirmationException.RuleChange c : ex.getChanges()) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("serialNumber", c.serialNumber);
                change.put("validationRule", c.validationRule);
                change.put("otherRecords", c.otherRecords);
                changes.add(change);
            }
            body.put("ruleChanges", changes);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (Exception ex) {
            logger.error("Failed to save Serial Number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    private static String saveMessage(SaveResult r) {
        String others = r.ruleUpdatedOthers == 0 ? ""
                : " The Validation Rule of " + r.ruleUpdatedOthers + " other record"
                        + (r.ruleUpdatedOthers == 1 ? "" : "s") + " with the same serial number was updated too.";
        if (r.created + r.updated + r.unchanged == 1) {
            return (r.created == 1 ? "Serial Number created successfully."
                    : r.updated == 1 ? "Serial Number updated successfully." : "No changes to save.") + others;
        }
        List<String> parts = new ArrayList<>();
        if (r.created > 0) {
            parts.add(r.created + " created");
        }
        if (r.updated > 0) {
            parts.add(r.updated + " updated");
        }
        if (r.unchanged > 0) {
            parts.add(r.unchanged + " unchanged");
        }
        return "Serial Numbers saved: " + String.join(", ", parts) + "." + others;
    }

    private static List<SerialNumberItem> items(Object rawItems) {
        List<SerialNumberItem> items = new ArrayList<>();
        if (rawItems instanceof List) {
            for (Object raw : (List<?>) rawItems) {
                if (raw instanceof Map) {
                    items.add(item((Map<?, ?>) raw));
                }
            }
        }
        return items;
    }

    private static SerialNumberItem item(Map<?, ?> m) {
        SerialNumberItem item = new SerialNumberItem();
        item.recordNo = toInteger(m.get("recordNo"));
        if (item.recordNo == null && str(m.get("recordNo")) != null && !str(m.get("recordNo")).trim().isEmpty()) {
            item.recordNo = -1; // not a number - SerialNumberService reports it against the row
        }
        item.serialNumber = str(m.get("serialNumber"));
        item.itemCode = optional(m, "itemCode");
        item.organizationCode = optional(m, "organizationCode");
        item.invoice = optional(m, "invoice");
        item.comment = optional(m, "comment");
        item.validationRule = str(m.get("validationRule"));
        item.status = str(m.get("status"));
        item.rowNumber = toInteger(m.get("rowNumber"));
        return item;
    }

    private static boolean confirmed(Map<String, Object> body) {
        Object value = body.get("confirmValidationRuleChange");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Map<String, Object> pageResponse(List<Map<String, Object>> result, int page, int size,
            int totalRecords, String sortBy, String sortDir) {
        Map<String, Object> response = new HashMap<>();
        response.put("reports", result);
        response.put("currentPage", page);
        response.put("totalItems", totalRecords);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
        response.put("first", page == 0);
        response.put("last", result.size() < size || (long) (page + 1) * size >= totalRecords);
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

    private static Map<String, Object> validationError(SerialNumberValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "1");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        return body;
    }

    /** Spreadsheet cells can arrive as numbers (e.g. serial 12345 -> 12345.0); keep whole numbers as "12345". */
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

    /** null when there's no such key (keep the stored value); "" when it's sent empty or null (clear it). */
    private static String optional(Map<?, ?> m, String key) {
        if (!m.containsKey(key)) {
            return null;
        }
        String value = str(m.get(key));
        return value == null ? "" : value;
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
