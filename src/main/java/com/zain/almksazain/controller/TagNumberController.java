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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zain.almksazain.services.TagNumberService;
import com.zain.almksazain.services.TagNumberService.SaveResult;
import com.zain.almksazain.services.TagNumberService.TagNumberItem;
import com.zain.almksazain.services.TagNumberValidationException;
import com.zain.almksazain.specs.TagNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Column;
import com.zain.almksazain.specs.TagNumberFilterBuilder.Condition;

/**
 * TAG Number configuration (Configurations > Acceptance Workflow > TAG Numbers) and its audit
 * trail (Approval Processes > TAG Number Audit Trail). Writes go through TagNumberService, which
 * validates everything first and saves nothing if any item fails. Validation failures come back
 * as 400 with responseCode "1" and an "errors" list. Filtering (and the matching exports in
 * ExportsController) goes through TagNumberFilterBuilder.
 */
@RestController
public class TagNumberController {

    private static final Logger logger = LoggerFactory.getLogger(TagNumberController.class);

    private static final int COLUMN_VALUES_LIMIT = 200;

    @Autowired
    private TagNumberService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================================================
    // TAG Numbers
    // ============================================================

    /** Body: {conditions: [{field, operator, value}]} - see TagNumberFilterBuilder. */
    @PostMapping(value = "/tagNumbers/filter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterTagNumbers(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return filter(TagNumberFilterBuilder.TAG_NUMBER_COLUMNS, TagNumberFilterBuilder.TAG_NUMBER_SELECT,
                TagNumberFilterBuilder.TAG_NUMBER_FROM, "t.recordNo", body, page, size, sortBy, sortDir,
                "TAG Numbers");
    }

    @GetMapping(value = "/tagNumbers/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> tagNumberColumnValues(@RequestParam String column) {
        return columnValues(TagNumberFilterBuilder.TAG_NUMBER_COLUMNS, TagNumberFilterBuilder.TAG_NUMBER_FROM, column);
    }

    /**
     * The one-by-one form. Body: {createdById, createdByName, items: [{tagNumber, vendorNumber, status?}]};
     * vendorName is looked up from tb_Supplier.
     */
    @PostMapping(value = "/tagNumbers/create", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> createTagNumbers(@RequestBody Map<String, Object> body) {
        List<TagNumberItem> items = items(body.get("items"));
        items.forEach(item -> item.recordNo = null);
        return save(items, toInteger(body.get("createdById")), str(body.get("createdByName")), false);
    }

    /**
     * Bulk upload. Body: {uploadedById, uploadedByName, items: [{recordNo?, tagNumber, vendorNumber,
     * status?, rowNumber}]}: rows with a recordNo update that TAG, rows without one create a new TAG.
     * All rows are saved or none are.
     */
    @PostMapping(value = "/tagNumbers/upload", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> uploadTagNumbers(@RequestBody Map<String, Object> body) {
        return save(items(body.get("items")), toInteger(body.get("uploadedById")), str(body.get("uploadedByName")), false);
    }

    /** Body: {recordNo, tagNumber, vendorNumber, status, updatedById, updatedByName}. */
    @PostMapping(value = "/tagNumbers/update", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> updateTagNumber(@RequestBody Map<String, Object> body) {
        TagNumberItem item = new TagNumberItem();
        item.recordNo = toInteger(body.get("recordNo"));
        if (item.recordNo == null || item.recordNo <= 0) {
            return ResponseEntity.badRequest().body(error("recordNo is required."));
        }
        item.tagNumber = str(body.get("tagNumber"));
        item.vendorNumber = str(body.get("vendorNumber"));
        item.status = str(body.get("status"));
        return save(Collections.singletonList(item), toInteger(body.get("updatedById")),
                str(body.get("updatedByName")), true);
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

    /** Body: {conditions: [{field, operator, value}]} - see TagNumberFilterBuilder. */
    @PostMapping(value = "/tagNumberAuditTrailFilter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterTagNumberAuditTrail(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return filter(TagNumberFilterBuilder.AUDIT_TRAIL_COLUMNS, TagNumberFilterBuilder.AUDIT_TRAIL_SELECT,
                TagNumberFilterBuilder.AUDIT_TRAIL_FROM, "a.recordId", body, page, size, sortBy, sortDir,
                "TAG Number audit trail");
    }

    @GetMapping(value = "/tagNumberAuditTrail/columnValues", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> tagNumberAuditTrailColumnValues(@RequestParam String column) {
        return columnValues(TagNumberFilterBuilder.AUDIT_TRAIL_COLUMNS, TagNumberFilterBuilder.AUDIT_TRAIL_FROM, column);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ResponseEntity<Map<String, Object>> filter(Map<String, Column> columns, String select, String from,
            String keySql, Map<String, Object> body, int page, int size, String sortBy, String sortDir, String what) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            List<Condition> conditions = TagNumberFilterBuilder.parseConditions(body, columns);
            List<Object> params = new ArrayList<>();
            String where = TagNumberFilterBuilder.buildWhere(columns, conditions, params);
            int totalRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from + where,
                    params.toArray(), Integer.class);

            String sql = select + from + where + TagNumberFilterBuilder.orderBy(columns, sortBy, sortDir, keySql)
                    + " LIMIT ? OFFSET ?";
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(size);
            sqlParams.add(page * size);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, sqlParams.toArray());

            return ResponseEntity.ok(pageResponse(result, page, size, totalRecords, sortBy, sortDir));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error filtering {}", what, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error filtering " + what + ": " + e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> columnValues(Map<String, Column> columns, String from, String column) {
        Map<String, Object> resp = new HashMap<>();
        String sql = TagNumberFilterBuilder.distinctValuesSql(columns, column, from, COLUMN_VALUES_LIMIT);
        List<String> values = new ArrayList<>();
        if (sql != null) {
            try {
                jdbcTemplate.query(sql, rs -> {
                    values.add(rs.getString("v"));
                });
            } catch (Exception e) {
                logger.error("Error loading values for TAG Number column {}", column, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(e.getMessage()));
            }
        }
        resp.put("uniqueValues", values);
        return ResponseEntity.ok(resp);
    }

    private ResponseEntity<Map<String, Object>> save(List<TagNumberItem> items, Integer userId, String userName,
            boolean rejectUnchanged) {
        try {
            SaveResult result = service.saveTags(items, userId, userName, rejectUnchanged);
            Map<String, Object> resp = success(saveMessage(result));
            resp.put("createdCount", result.created);
            resp.put("updatedCount", result.updated);
            resp.put("unchangedCount", result.unchanged);
            return ResponseEntity.ok(resp);
        } catch (TagNumberValidationException ex) {
            return ResponseEntity.badRequest().body(validationError(ex));
        } catch (Exception ex) {
            logger.error("Failed to save TAG Number(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    private static String saveMessage(SaveResult r) {
        if (r.created + r.updated + r.unchanged == 1) {
            return r.created == 1 ? "TAG Number created successfully."
                    : r.updated == 1 ? "TAG Number updated successfully." : "No changes to save.";
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
        return "TAG Numbers saved: " + String.join(", ", parts) + ".";
    }

    private static List<TagNumberItem> items(Object rawItems) {
        List<TagNumberItem> items = new ArrayList<>();
        if (rawItems instanceof List) {
            for (Object raw : (List<?>) rawItems) {
                if (!(raw instanceof Map)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) raw;
                TagNumberItem item = new TagNumberItem();
                item.recordNo = toInteger(m.get("recordNo"));
                if (item.recordNo == null && str(m.get("recordNo")) != null && !str(m.get("recordNo")).trim().isEmpty()) {
                    item.recordNo = -1; // not a number - TagNumberService reports it against the row
                }
                item.tagNumber = str(m.get("tagNumber"));
                item.vendorNumber = str(m.get("vendorNumber"));
                item.status = str(m.get("status"));
                item.rowNumber = toInteger(m.get("rowNumber"));
                items.add(item);
            }
        }
        return items;
    }

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
