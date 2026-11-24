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

import com.zain.almksazain.services.RequestSerialNumberService;
import com.zain.almksazain.services.RequestSerialNumberService.Item;
import com.zain.almksazain.services.RequestSerialNumberService.SaveResult;
import com.zain.almksazain.services.SerialNumberConfirmationException;
import com.zain.almksazain.services.SerialNumberValidationException;
import com.zain.almksazain.specs.RequestSerialNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Column;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Condition;

/**
 * Received Serials > "Raised in Acceptance Requests": the serial numbers on acceptance request
 * lines, and their audit trail (Audit Trail > Received Serials). Lines are only created by
 * acceptance requests; here their Validation Rule, Status and Comments are changed, one at a
 * time or by bulk upload, through RequestSerialNumberService (all-or-nothing; validation failures
 * come back as 400 with responseCode "1" and an "errors" list). Validation Rule and Status are
 * shared by all lines of a serial number: a save that would also change other lines comes back as
 * 409 with requiresConfirmation and is only made when sent again with confirmValidationRuleChange = true. Filtering (and the matching
 * exports in ExportsController) goes through RequestSerialNumberFilterBuilder.
 */
@RestController
public class RequestSerialNumberController {

    private static final Logger logger = LoggerFactory.getLogger(RequestSerialNumberController.class);

    private static final int COLUMN_VALUES_LIMIT = 200;

    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private RequestSerialNumberService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Body: {conditions: [{field, operator, value}]} - see RequestSerialNumberFilterBuilder. */
    @PostMapping(value = "/requestSerialNumbers/filter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterRequestSerialNumbers(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return filter(body, page, size, sortBy, sortDir, RequestSerialNumberFilterBuilder.COLUMNS,
                RequestSerialNumberFilterBuilder.SELECT, RequestSerialNumberFilterBuilder.FROM,
                RequestSerialNumberFilterBuilder.DEFAULT_ORDER_SQL, "acceptance request serial numbers");
    }

    @GetMapping(value = "/requestSerialNumbers/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> requestSerialNumberColumnValues(@RequestParam String column) {
        return columnValues(TagNumberFilterBuilder.distinctValuesSql(RequestSerialNumberFilterBuilder.COLUMNS, column,
                RequestSerialNumberFilterBuilder.FROM, COLUMN_VALUES_LIMIT), column);
    }

    /** The one-by-one form. Body: {recordNo, validationRule, status, comment, updatedById, updatedByName}. */
    @PostMapping(value = "/requestSerialNumbers/update", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> updateRequestSerialNumber(@RequestBody Map<String, Object> body) {
        return save(Collections.singletonList(item(body)), toInteger(body.get("updatedById")),
                str(body.get("updatedByName")), confirmed(body));
    }

    /**
     * Bulk upload. Body: {uploadedById, uploadedByName, items: [{recordNo, validationRule?,
     * status?, comment?, rowNumber}]}: an empty Validation Rule or Status keeps the stored value,
     * and a row without the comment key (the file has no such column) keeps the stored comment.
     * All rows are saved or none are.
     */
    @PostMapping(value = "/requestSerialNumbers/upload", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> uploadRequestSerialNumbers(@RequestBody Map<String, Object> body) {
        List<Item> items = new ArrayList<>();
        Object raw = body.get("items");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) {
                    items.add(item((Map<?, ?>) o));
                }
            }
        }
        return save(items, toInteger(body.get("uploadedById")), str(body.get("uploadedByName")), confirmed(body));
    }

    /** Body: {conditions: [{field, operator, value}]} - see RequestSerialNumberFilterBuilder. */
    @PostMapping(value = "/requestSerialNumberAuditTrailFilter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterRequestSerialNumberAuditTrail(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return filter(body, page, size, sortBy, sortDir, RequestSerialNumberFilterBuilder.AUDIT_TRAIL_COLUMNS,
                RequestSerialNumberFilterBuilder.AUDIT_TRAIL_SELECT, RequestSerialNumberFilterBuilder.AUDIT_TRAIL_FROM,
                "a.recordId", "acceptance request serial number audit trail");
    }

    @GetMapping(value = "/requestSerialNumberAuditTrail/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> requestSerialNumberAuditTrailColumnValues(@RequestParam String column) {
        return columnValues(TagNumberFilterBuilder.distinctValuesSql(RequestSerialNumberFilterBuilder.AUDIT_TRAIL_COLUMNS,
                column, RequestSerialNumberFilterBuilder.AUDIT_TRAIL_FROM, COLUMN_VALUES_LIMIT), column);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ResponseEntity<Map<String, Object>> filter(Map<String, Object> body, int page, int size, String sortBy,
            String sortDir, Map<String, Column> columns, String select, String from, String defaultOrderSql, String what) {
        try {
            page = Math.max(page, 0);
            size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            List<Condition> conditions = TagNumberFilterBuilder.parseConditions(body, columns);
            List<Object> params = new ArrayList<>();
            String where = TagNumberFilterBuilder.buildWhere(columns, conditions, params);
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from + where, params.toArray(), Integer.class);
            int totalRecords = total == null ? 0 : total;
            List<Map<String, Object>> rows = new ArrayList<>();
            long offset = (long) page * size;
            if (offset < totalRecords) {
                List<Object> pageParams = new ArrayList<>(params);
                pageParams.add(size);
                pageParams.add(offset);
                rows = jdbcTemplate.queryForList(select + from + where
                        + TagNumberFilterBuilder.orderBy(columns, sortBy, sortDir, defaultOrderSql)
                        + " LIMIT ? OFFSET ?", pageParams.toArray());
            }
            Map<String, Object> response = new HashMap<>();
            response.put("reports", rows);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", (long) (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error filtering {}", what, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering " + what + ": " + e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> columnValues(String sql, String column) {
        List<String> values = new ArrayList<>();
        if (sql != null) {
            try {
                jdbcTemplate.query(sql, rs -> {
                    values.add(rs.getString("v"));
                });
            } catch (Exception e) {
                logger.error("Error loading values for acceptance request serial number column {}", column, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(e.getMessage()));
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("uniqueValues", values);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> save(List<Item> items, Integer userId, String userName,
            boolean confirmed) {
        try {
            SaveResult result = service.update(items, userId, userName, confirmed);
            Map<String, Object> resp = new HashMap<>();
            resp.put("responseCode", "0");
            resp.put("message", saveMessage(result));
            resp.put("updatedCount", result.updated);
            resp.put("unchangedCount", result.unchanged);
            resp.put("othersUpdatedCount", result.othersUpdated);
            return ResponseEntity.ok(resp);
        } catch (SerialNumberConfirmationException ex) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("responseCode", "2");
            resp.put("requiresConfirmation", true);
            resp.put("message", ex.getMessage());
            List<Map<String, Object>> changes = new ArrayList<>();
            for (SerialNumberConfirmationException.RuleChange c : ex.getChanges()) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("serialNumber", c.serialNumber);
                change.put("validationRule", c.validationRule);
                change.put("status", c.status);
                change.put("otherRecords", c.otherRecords);
                changes.add(change);
            }
            resp.put("ruleChanges", changes);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(resp);
        } catch (SerialNumberValidationException ex) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("responseCode", "1");
            resp.put("message", ex.getMessage());
            resp.put("errors", ex.getErrors());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception ex) {
            logger.error("Failed to update acceptance request serial number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    private static String saveMessage(SaveResult r) {
        String others = r.othersUpdated == 0 ? ""
                : " The Validation Rule / Status of " + r.othersUpdated + " other line"
                        + (r.othersUpdated == 1 ? "" : "s") + " with the same serial number was updated too.";
        if (r.updated + r.unchanged == 1) {
            return (r.updated == 1 ? "Serial Number updated successfully." : "No changes to save.") + others;
        }
        List<String> parts = new ArrayList<>();
        if (r.updated > 0) {
            parts.add(r.updated + " updated");
        }
        if (r.unchanged > 0) {
            parts.add(r.unchanged + " unchanged");
        }
        return "Serial Numbers saved: " + String.join(", ", parts) + "." + others;
    }

    private static boolean confirmed(Map<String, Object> body) {
        Object value = body.get("confirmValidationRuleChange");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Item item(Map<?, ?> m) {
        Item item = new Item();
        item.recordNo = toInteger(m.get("recordNo"));
        if (item.recordNo == null && str(m.get("recordNo")) != null && !str(m.get("recordNo")).trim().isEmpty()) {
            item.recordNo = -1; // not a number - RequestSerialNumberService reports it against the row
        }
        item.validationRule = str(m.get("validationRule"));
        item.status = str(m.get("status"));
        if (m.containsKey("comment")) {
            String comment = str(m.get("comment"));
            item.comment = comment == null ? "" : comment;
        }
        item.rowNumber = toInteger(m.get("rowNumber"));
        return item;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "1");
        body.put("message", message);
        body.put("errors", Collections.singletonList(message));
        return body;
    }

    /** Spreadsheet cells can arrive as numbers (e.g. Record Number 123 -> 123.0); keep whole numbers as "123". */
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
            double d = ((Number) value).doubleValue();
            return d == Math.rint(d) ? (int) d : null;
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
