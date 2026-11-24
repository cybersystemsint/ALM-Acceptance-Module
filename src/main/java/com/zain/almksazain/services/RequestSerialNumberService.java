package com.zain.almksazain.services;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.model.SerialNumber;
import com.zain.almksazain.specs.RequestSerialNumberFilterBuilder;
import com.zain.almksazain.specs.TagNumberFilterBuilder;

/**
 * The serial numbers on acceptance request lines (tb_DCC_LN): their Validation Rule, Status and
 * Comments (the "Raised in Acceptance Requests" tab of the Received Serials page), each change
 * logged to tb_DCC_LN_Serial_Audit_Log, and the check APIController#postdcc runs so a serial
 * number already raised on another request is not raised again.
 *
 * Lines are only created by acceptance requests; here only those three settings change, one line
 * at a time or by bulk upload. Every save is all-or-nothing: all items are validated first and,
 * if any fails, a SerialNumberValidationException listing every error is thrown before anything
 * is saved.
 *
 * Validation Rule and Status are shared by every line of a serial number (on any request): the
 * check below is blocked by any Active line of it, so setting them on one line alone would not
 * change what gets blocked. Setting either on a line sets it on the serial number's other lines
 * too - after the caller confirms (SerialNumberConfirmationException), like the Validation Rule
 * of tb_SerialNumber rows. Comments stay per line. A new line takes the Validation Rule its
 * serial number's lines already have (Active, as a new claim).
 *
 * Validation Rule (default SerialNumber for every line): with 'SerialNumber' an Active line blocks
 * its serial number on any other request; with 'SerialNumber + ItemCode' only with the same item
 * code - the line's actual item code when it has one, else its item code, else (non-UPL lines)
 * its PO line's part number. InActive lines, and lines of incomplete, rejected or returned
 * requests (which hold nothing, like their TAG Numbers), never block.
 */
@Service
public class RequestSerialNumberService {

    private static final Logger logger = LoggerFactory.getLogger(RequestSerialNumberService.class);

    public static final String RULE_SERIAL_NUMBER = SerialNumber.RULE_SERIAL_NUMBER;
    public static final String RULE_SERIAL_NUMBER_ITEM_CODE = SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE;
    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "InActive";

    /** tb_DCC_LN.serialComment is VARCHAR(500). */
    public static final int MAX_COMMENT_LENGTH = 500;
    /** tb_DCC_LN.updatedBy is VARCHAR(150). */
    private static final int MAX_USER_LENGTH = 150;

    /** One save (form or bulk upload file) - keeps a request inside one reasonable transaction. */
    public static final int MAX_ITEMS_PER_SAVE = 10_000;

    /** Keeps IN (...) lists short on large bulk uploads. */
    private static final int IN_CHUNK = 500;

    /** Rows per multi-row audit INSERT. */
    private static final int INSERT_CHUNK = 1000;

    // The line's item code for 'SerialNumber + ItemCode': actual item code, else item code, else
    // (non-UPL lines store neither) the PO line's part number.
    private static final String VALIDATION_ITEM_CODE_SQL = "COALESCE(NULLIF(TRIM(ln.actualItemCode), ''), "
            + "NULLIF(TRIM(ln.itemCode), ''), (SELECT po.itemPartNumber FROM tb_PurchaseOrder po "
            + "WHERE po.poNumber = ln.poId AND po.lineNumber = ln.lineNumber ORDER BY po.recordNo DESC LIMIT 1))";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One line to update, by recordNo; rowNumber is the spreadsheet row on a bulk upload (null for
     * the form). A null or blank validationRule / status keeps the stored value; a null comment
     * keeps it too (a file without that column) and "" clears it.
     */
    public static class Item {
        public Integer recordNo;
        public String validationRule;
        public String status;
        public String comment;
        public Integer rowNumber;
    }

    public static class SaveResult {
        public int updated;
        public int unchanged;
        /** Other lines of the saved serial numbers whose Validation Rule / Status changed with them. */
        public int othersUpdated;
    }

    // A line as loaded (and locked) for an update.
    private static class Line {
        int recordNo;
        String requestNo;
        String requestStatus;
        String serialNumber;
        String itemCode;
        String validationRule;
        String status;
        String comment;
    }

    /**
     * confirmed: the caller has seen which other lines of the same serial numbers get the new
     * Validation Rule / Status; without it, a save that would change any throws
     * SerialNumberConfirmationException before anything is written.
     */
    @Transactional
    public SaveResult update(List<Item> items, Integer userId, String userName, boolean confirmed) {
        if (items == null || items.isEmpty()) {
            throw new SerialNumberValidationException("No serial numbers to update.");
        }
        if (items.size() > MAX_ITEMS_PER_SAVE) {
            throw new SerialNumberValidationException("A file can update at most " + MAX_ITEMS_PER_SAVE
                    + " serial numbers (this one has " + items.size() + "). Split it and upload each part.");
        }

        List<String> errors = new ArrayList<>();
        Map<Integer, String> rowOfRecordNo = new HashMap<>();
        for (Item item : items) {
            String row = rowPrefix(item);
            if (item.recordNo == null) {
                errors.add(row + "Record Number is required.");
            } else if (item.recordNo <= 0) {
                errors.add(row + "Record Number must be a whole number.");
            } else if (rowOfRecordNo.containsKey(item.recordNo)) {
                errors.add(row + "Record Number " + item.recordNo + " is also on " + rowOfRecordNo.get(item.recordNo) + ".");
            } else {
                rowOfRecordNo.put(item.recordNo, item.rowNumber == null ? "this request" : "Row " + item.rowNumber);
            }
            if (!trim(item.validationRule).isEmpty() && SerialNumberService.normalizeRule(item.validationRule) == null) {
                errors.add(row + "Validation Rule '" + item.validationRule.trim() + "' must be " + RULE_SERIAL_NUMBER
                        + " or " + RULE_SERIAL_NUMBER_ITEM_CODE + ".");
            }
            if (!trim(item.status).isEmpty() && normalizeStatus(item.status) == null) {
                errors.add(row + "Status '" + item.status.trim() + "' must be " + STATUS_ACTIVE + " or " + STATUS_INACTIVE + ".");
            }
            if (item.comment != null && item.comment.trim().length() > MAX_COMMENT_LENGTH) {
                errors.add(row + "Comments cannot be longer than " + MAX_COMMENT_LENGTH + " characters.");
            }
        }

        // Every error at once: the lines of the rows that passed the checks above are checked too.
        Map<Integer, Line> lines = lockLines("ln.recordNo", new ArrayList<>(rowOfRecordNo.keySet()));
        for (Item item : items) {
            if (item.recordNo == null || item.recordNo <= 0) {
                continue;
            }
            Line line = lines.get(item.recordNo);
            String row = rowPrefix(item);
            if (line == null) {
                errors.add(row + "Record Number " + item.recordNo + " does not exist.");
            } else if (trim(line.serialNumber).isEmpty()) {
                errors.add(row + "Record Number " + item.recordNo + " has no serial number.");
            } else if (isExcludedRequest(line.requestStatus)) {
                errors.add(row + "Record Number " + item.recordNo + " is on acceptance request " + line.requestNo
                        + ", which is " + line.requestStatus.trim().toLowerCase(Locale.ROOT)
                        + " - its serial numbers cannot be changed here.");
            }
        }
        if (!errors.isEmpty()) {
            throw new SerialNumberValidationException(errors);
        }

        // The Validation Rule / Status each save sets per serial number; two rows giving one serial
        // number different values fail.
        Map<String, String> ruleBySerial = new LinkedHashMap<>();
        Map<String, String> statusBySerial = new LinkedHashMap<>();
        Map<String, String> ruleSetOn = new HashMap<>();
        Map<String, String> statusSetOn = new HashMap<>();
        Map<String, String> serialOfKey = new LinkedHashMap<>();
        Map<Integer, Item> itemByRecordNo = new HashMap<>();
        for (Item item : items) {
            Line line = lines.get(item.recordNo);
            itemByRecordNo.put(item.recordNo, item);
            String serialKey = key(line.serialNumber);
            serialOfKey.putIfAbsent(serialKey, trim(line.serialNumber));
            String label = item.rowNumber == null ? "this request" : "Row " + item.rowNumber;
            if (!trim(item.validationRule).isEmpty()) {
                String rule = SerialNumberService.normalizeRule(item.validationRule);
                String given = ruleBySerial.putIfAbsent(serialKey, rule);
                if (given != null && !given.equals(rule)) {
                    errors.add(rowPrefix(item) + "Serial Number '" + trim(line.serialNumber) + "' is given a different "
                            + "Validation Rule on " + ruleSetOn.get(serialKey) + " - all lines of a serial number share one.");
                }
                ruleSetOn.putIfAbsent(serialKey, label);
            }
            if (!trim(item.status).isEmpty()) {
                String status = normalizeStatus(item.status);
                String given = statusBySerial.putIfAbsent(serialKey, status);
                if (given != null && !given.equals(status)) {
                    errors.add(rowPrefix(item) + "Serial Number '" + trim(line.serialNumber) + "' is given a different "
                            + "Status on " + statusSetOn.get(serialKey) + " - all lines of a serial number share one.");
                }
                statusSetOn.putIfAbsent(serialKey, label);
            }
        }
        if (!errors.isEmpty()) {
            throw new SerialNumberValidationException(errors);
        }

        // Every line of the serial numbers whose Validation Rule or Status is set, locked too.
        Set<String> sharedKeys = new LinkedHashSet<>(ruleBySerial.keySet());
        sharedKeys.addAll(statusBySerial.keySet());
        Map<Integer, Line> allLines = new LinkedHashMap<>(lines);
        if (!sharedKeys.isEmpty()) {
            List<Object> serials = new ArrayList<>();
            sharedKeys.forEach(k -> serials.add(serialOfKey.get(k)));
            lockLines("ln.serialNumber", serials).forEach(allLines::putIfAbsent);
        }

        String user = userColumn(userName);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> updates = new ArrayList<>();
        List<Object[]> auditRows = new ArrayList<>();
        Map<String, Integer> othersBySerial = new LinkedHashMap<>();
        SaveResult result = new SaveResult();
        for (Line line : allLines.values()) {
            Item item = itemByRecordNo.get(line.recordNo);
            String serialKey = key(line.serialNumber);
            String rule = ruleBySerial.getOrDefault(serialKey, line.validationRule);
            String status = statusBySerial.getOrDefault(serialKey, line.status);
            String comment = item != null && item.comment != null ? emptyToNull(item.comment.trim()) : line.comment;

            Map<String, Object[]> changes = new LinkedHashMap<>();
            diff(changes, "validationRule", line.validationRule, rule);
            diff(changes, "status", line.status, status);
            diff(changes, "comment", line.comment, comment);
            if (changes.isEmpty()) {
                if (item != null) {
                    result.unchanged++;
                }
                continue;
            }
            if (item != null) {
                result.updated++;
            } else {
                result.othersUpdated++;
                othersBySerial.merge(serialKey, 1, Integer::sum);
            }
            updates.add(new Object[] { rule, status, comment, user, now, line.recordNo });
            auditRows.add(new Object[] { null, line.recordNo, line.requestNo, "UPDATE", line.serialNumber,
                    line.itemCode, fieldChangesJson(changes), userId, user });
        }
        if (!othersBySerial.isEmpty() && !confirmed) {
            List<SerialNumberConfirmationException.RuleChange> changes = new ArrayList<>();
            othersBySerial.forEach((serialKey, others) -> changes.add(new SerialNumberConfirmationException.RuleChange(
                    serialOfKey.get(serialKey), ruleBySerial.get(serialKey), statusBySerial.get(serialKey), others)));
            throw new SerialNumberConfirmationException(confirmationMessage(result.othersUpdated, changes.size()), changes);
        }
        if (!updates.isEmpty()) {
            String batchId = updates.size() > 1 ? UUID.randomUUID().toString() : null;
            auditRows.forEach(row -> row[0] = batchId);
            jdbcTemplate.batchUpdate("UPDATE tb_DCC_LN SET validationRule = ?, serialStatus = ?, serialComment = ?, "
                    + "updatedBy = ?, updatedDatetime = ? WHERE recordNo = ?", updates);
            insertAuditRows(auditRows);
        }
        return result;
    }

    /**
     * Called after an acceptance request adds a line: it takes the Validation Rule the serial
     * number's other lines already have (newest first), so the serial number's lines keep one rule.
     * Runs in the request's own transaction.
     */
    public void inheritValidationRule(long lineRecordNo, String serialNumber) {
        String serial = trim(serialNumber);
        if (serial.isEmpty()) {
            return;
        }
        List<String> rules = jdbcTemplate.queryForList("SELECT validationRule FROM tb_DCC_LN "
                + "WHERE serialNumber IN (?, ?) AND recordNo <> ? ORDER BY recordNo DESC LIMIT 1",
                String.class, serial, serialNumber, lineRecordNo);
        if (!rules.isEmpty() && rules.get(0) != null) {
            jdbcTemplate.update("UPDATE tb_DCC_LN SET validationRule = ? WHERE recordNo = ? AND validationRule <> ?",
                    rules.get(0), lineRecordNo, rules.get(0));
        }
    }

    /**
     * Called when resubmitting an acceptance request saves one of its existing lines: the
     * resubmitter becomes the line's Updated By, and a change to its serial number or item codes
     * is logged. Runs in the request's own transaction.
     */
    public void recordResubmission(long lineRecordNo, String requestNo, String oldSerial, String oldItemCode,
            String oldActualItemCode, String newSerial, String newItemCode, String newActualItemCode,
            Integer userId, String userName) {
        String user = userColumn(userName);
        jdbcTemplate.update("UPDATE tb_DCC_LN SET updatedBy = ?, updatedDatetime = ? WHERE recordNo = ?",
                user, Timestamp.valueOf(LocalDateTime.now()), lineRecordNo);
        Map<String, Object[]> changes = new LinkedHashMap<>();
        diff(changes, "serialNumber", emptyToNull(trim(oldSerial)), emptyToNull(trim(newSerial)));
        diff(changes, "itemCode", emptyToNull(trim(oldItemCode)), emptyToNull(trim(newItemCode)));
        diff(changes, "actualItemCode", emptyToNull(trim(oldActualItemCode)), emptyToNull(trim(newActualItemCode)));
        if (changes.isEmpty()) {
            return;
        }
        if (changes.containsKey("serialNumber")) {
            inheritValidationRule(lineRecordNo, newSerial);
        }
        String itemCode = !trim(newItemCode).isEmpty() ? trim(newItemCode) : emptyToNull(trim(newActualItemCode));
        insertAuditRows(Collections.singletonList(new Object[] { null, lineRecordNo, requestNo, "RESUBMIT",
                emptyToNull(trim(newSerial)), itemCode, fieldChangesJson(changes), userId, user }));
    }

    /**
     * The other acceptance requests on which this serial number is already raised, by the
     * Validation Rule of each of their Active lines (see the class comment); empty when none.
     * The item code compared is the submitted actual item code, else its item code, else (non-UPL
     * lines send it blank) its part number. excludeRequestNo is the request being resubmitted.
     */
    public List<String> findRaisedRequests(String serialNumber, String itemCode, String actualItemCode,
            String itemPartNumber, String excludeRequestNo) {
        String serial = trim(serialNumber);
        if (serial.isEmpty()) {
            return Collections.emptyList();
        }
        String submittedItem = !trim(actualItemCode).isEmpty() ? trim(actualItemCode)
                : !trim(itemCode).isEmpty() ? trim(itemCode) : trim(itemPartNumber);
        Set<String> requests = new LinkedHashSet<>();
        jdbcTemplate.query("SELECT ln.dccId AS dccId, ln.validationRule AS validationRule, "
                + VALIDATION_ITEM_CODE_SQL + " AS itemCode "
                + "FROM tb_DCC_LN ln JOIN tb_DCC d ON d.recordNo = CAST(ln.dccId AS UNSIGNED) "
                + "WHERE ln.serialNumber IN (?, ?) AND ln.serialStatus = ? "
                + "AND d.status NOT IN (" + TagNumberFilterBuilder.INACTIVE_DCC_STATUSES + ") AND ln.dccId <> ? "
                + "ORDER BY CAST(ln.dccId AS UNSIGNED)",
                rs -> {
                    String rule = SerialNumberService.normalizeRule(rs.getString("validationRule"));
                    boolean blocks = !RULE_SERIAL_NUMBER_ITEM_CODE.equals(rule)
                            || (!submittedItem.isEmpty() && key(submittedItem).equals(key(rs.getString("itemCode"))));
                    if (blocks) {
                        requests.add(rs.getString("dccId"));
                    }
                },
                serial, serialNumber, STATUS_ACTIVE, trim(excludeRequestNo));
        return new ArrayList<>(requests);
    }

    /** "Active"/"InActive" in any case (also "Inactive"); null when it's anything else. */
    static String normalizeStatus(String status) {
        String s = trim(status).replace(" ", "");
        if (s.equalsIgnoreCase(STATUS_ACTIVE)) {
            return STATUS_ACTIVE;
        }
        if (s.equalsIgnoreCase(STATUS_INACTIVE)) {
            return STATUS_INACTIVE;
        }
        return null;
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** The lines whose `column` (ln.recordNo or ln.serialNumber) is one of `values`, locked until the transaction ends. */
    private Map<Integer, Line> lockLines(String column, List<Object> values) {
        Map<Integer, Line> lines = new LinkedHashMap<>();
        try {
            for (int i = 0; i < values.size(); i += IN_CHUNK) {
                List<Object> chunk = values.subList(i, Math.min(i + IN_CHUNK, values.size()));
                jdbcTemplate.query("SELECT ln.recordNo AS recordNo, ln.dccId AS requestNo, d.status AS requestStatus, "
                        + "ln.serialNumber AS serialNumber, " + RequestSerialNumberFilterBuilder.ITEM_CODE_SQL
                        + " AS itemCode, ln.validationRule AS validationRule, "
                        + "ln.serialStatus AS status, ln.serialComment AS comment "
                        + "FROM tb_DCC_LN ln LEFT JOIN tb_DCC d ON d.recordNo = CAST(ln.dccId AS UNSIGNED) "
                        + "WHERE " + column + " IN (" + String.join(",", Collections.nCopies(chunk.size(), "?")) + ") "
                        + "ORDER BY ln.recordNo FOR UPDATE OF ln",
                        rs -> {
                            Line line = new Line();
                            line.recordNo = rs.getInt("recordNo");
                            line.requestNo = rs.getString("requestNo");
                            line.requestStatus = rs.getString("requestStatus");
                            line.serialNumber = rs.getString("serialNumber");
                            line.itemCode = rs.getString("itemCode");
                            line.validationRule = rs.getString("validationRule");
                            line.status = rs.getString("status");
                            line.comment = rs.getString("comment");
                            lines.put(line.recordNo, line);
                        },
                        chunk.toArray());
            }
        } catch (PessimisticLockingFailureException ex) {
            logger.warn("Acceptance request serial number update could not lock its lines", ex);
            throw new SerialNumberValidationException(
                    "These serial numbers are being changed by someone else right now. Try again in a moment.");
        }
        return lines;
    }

    private static String confirmationMessage(int otherLines, int serials) {
        return "Saving this also changes the Validation Rule / Status of " + otherLines + " other line"
                + (otherLines == 1 ? "" : "s") + " of " + (serials == 1 ? "the same serial number" : serials + " serial numbers")
                + ": all lines of a serial number share them. Confirm to save.";
    }

    private static boolean isExcludedRequest(String requestStatus) {
        String status = trim(requestStatus).toLowerCase(Locale.ROOT);
        return status.isEmpty() || status.equals("rejected") || status.equals("returned");
    }

    private void insertAuditRows(List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += INSERT_CHUNK) {
            List<Object[]> chunk = rows.subList(i, Math.min(i + INSERT_CHUNK, rows.size()));
            String sql = "INSERT INTO tb_DCC_LN_Serial_Audit_Log (batchId, lineRecordNo, requestNo, changeType, "
                    + "serialNumber, itemCode, fieldChanges, changedBy, changedByName) VALUES "
                    + String.join(",", Collections.nCopies(chunk.size(), "(?,?,?,?,?,?,?,?,?)"));
            List<Object> args = new ArrayList<>();
            chunk.forEach(row -> Collections.addAll(args, row));
            jdbcTemplate.update(sql, args.toArray());
        }
    }

    /** {"field": {"old": .., "new": ..}} as stored in tb_DCC_LN_Serial_Audit_Log.fieldChanges. */
    private String fieldChangesJson(Map<String, Object[]> changes) {
        Map<String, Map<String, Object>> json = new LinkedHashMap<>();
        changes.forEach((field, values) -> {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("old", values[0]);
            pair.put("new", values[1]);
            json.put(field, pair);
        });
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise serial number changes", e);
        }
    }

    private static void diff(Map<String, Object[]> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(field, new Object[] { oldValue, newValue });
        }
    }

    private static String rowPrefix(Item item) {
        return item.rowNumber == null ? "" : "Row " + item.rowNumber + ": ";
    }

    private static String userColumn(String userName) {
        String user = trim(userName);
        return user.length() > MAX_USER_LENGTH ? user.substring(0, MAX_USER_LENGTH) : emptyToNull(user);
    }

    private static String key(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
