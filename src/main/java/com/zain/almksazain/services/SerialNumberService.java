package com.zain.almksazain.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.model.SerialNumber;
import com.zain.almksazain.model.SerialNumberAuditLog;
import com.zain.almksazain.model.supplierdata;
import com.zain.almksazain.repo.SerialNumberAuditLogRepo;
import com.zain.almksazain.repo.SerialNumberRepo;
import com.zain.almksazain.repo.supplierrepo;

/**
 * Create / update / soft-delete of tb_SerialNumber rows, each one logged to
 * tb_SerialNumber_Audit_Log, and the "already received" check acceptance requests go through.
 *
 * Every write is all-or-nothing: all items are validated first and, if any fails, a
 * SerialNumberValidationException listing every error is thrown before anything is saved (same
 * "no partial uploads" rule as the TAG Number bulk upload). Serial numbers and item codes are
 * trimmed and compared case-insensitively.
 *
 * Uniqueness depends on the serial number's Validation Rule: with 'SerialNumber' it can exist
 * only once (among non-deleted rows); with 'SerialNumber + ItemCode' once per item code. All rows
 * of a serial number share one rule, so giving one row a new rule changes the others too - the
 * caller has to confirm that first (SerialNumberConfirmationException).
 *
 * Organization Code is a vendor number (tb_Supplier.supplierId), like a TAG Number's vendor: it is
 * required on a create, must exist when set or changed, and Organization Name is always that
 * vendor's name, looked up here (never taken from the request). Rows from the Oracle load carry
 * their own organization (e.g. 'ZKC'), which is kept while their Organization Code is left as is.
 *
 * The Oracle load already holds serial numbers under several item codes with the default rule,
 * so uniqueness is only checked for the serial numbers a save actually creates, renames, re-codes
 * or re-rules - editing, say, the Comment of such a row is still allowed.
 */
@Service
public class SerialNumberService {

    private static final Logger logger = LoggerFactory.getLogger(SerialNumberService.class);

    // Column sizes in tb_SerialNumber.
    public static final int MAX_SERIAL_LENGTH = 100;
    public static final int MAX_ITEM_CODE_LENGTH = 100;
    public static final int MAX_ORGANIZATION_CODE_LENGTH = 50;
    public static final int MAX_ORGANIZATION_NAME_LENGTH = 255;
    public static final int MAX_INVOICE_LENGTH = 120;
    public static final int MAX_COMMENT_LENGTH = 120;
    /** createdBy / updatedBy are VARCHAR(50). */
    private static final int MAX_USER_LENGTH = 50;

    /** One save (form or bulk upload file) - keeps a request inside one reasonable transaction. */
    public static final int MAX_ITEMS_PER_SAVE = 10_000;

    /** Keeps IN (...) lists short on large bulk uploads. */
    private static final int IN_CHUNK = 500;

    /** Rows per multi-row INSERT. */
    private static final int INSERT_CHUNK = 1000;

    /** At most this many conflicting Record Nos are listed in one error. */
    private static final int MAX_LISTED_CONFLICTS = 5;

    @Autowired
    private SerialNumberRepo serialNumberRepo;

    @Autowired
    private SerialNumberAuditLogRepo auditLogRepo;

    @Autowired
    private supplierrepo supplierRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One serial number to save: recordNo set means update that row, null means create. rowNumber
     * is the spreadsheet row on a bulk upload (null for the form). For the optional fields, null
     * means "not given" (an update keeps the stored value - e.g. a file without that column) and
     * "" clears it. A blank validationRule or status means: keep it on an update; on a create, the
     * serial number's existing rule (SerialNumber if it is new) and Active. organizationCode is the
     * vendor number; there is no organizationName - it is looked up from tb_Supplier.
     */
    public static class SerialNumberItem {
        public Integer recordNo;
        public String serialNumber;
        public String itemCode;
        public String organizationCode;
        public String invoice;
        public String validationRule;
        public String comment;
        public String status;
        public Integer rowNumber;
    }

    public static class SaveResult {
        public int created;
        public int updated;
        public int unchanged;
        /** Other rows of the saved serial numbers whose Validation Rule changed with them. */
        public int ruleUpdatedOthers;
    }

    // One validated item waiting to be written.
    private static class Planned {
        String row;      // "Row 5: " prefix for errors ("" for the form)
        String label;    // "Row 5" / "this request", for "also in ..." references
        SerialNumber existing; // null for a create
        String serial;
        String itemCode;
        String organizationCode;
        String organizationName;
        String invoice;
        String rule;     // null until resolved against the serial number's other rows
        boolean ruleGiven;
        String comment;
        String status;
        Map<String, Object[]> changes;
    }

    // A row as it will be after the save: a stored row the save doesn't touch, or a planned one.
    private static class FinalRow {
        final SerialNumber stored; // null for a planned create
        final Planned planned;     // null for an untouched stored row
        String rule;

        FinalRow(SerialNumber stored, Planned planned, String rule) {
            this.stored = stored;
            this.planned = planned;
            this.rule = rule;
        }

        String itemCode() {
            return planned != null ? planned.itemCode : trim(stored.getItemCode());
        }

        String describe() {
            return planned != null
                    ? (planned.existing != null ? "Record No " + planned.existing.getRecordNo() + ", " : "") + planned.label
                    : "Record No " + stored.getRecordNo();
        }
    }

    // ============================================================
    // Create / update (form and bulk upload share this)
    // ============================================================

    /**
     * Creates the items without a recordNo and updates the ones with one. When rejectUnchanged is
     * set (the edit form), an update that changes nothing is an error; on a bulk upload such rows
     * are just counted as unchanged, so a re-uploaded export only touches the rows that differ.
     * When the save would change the Validation Rule of rows it doesn't list itself and
     * confirmRuleChange is false, nothing is saved and SerialNumberConfirmationException says which.
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult saveSerialNumbers(List<SerialNumberItem> items, Integer userId, String userName,
            boolean rejectUnchanged, boolean confirmRuleChange) {
        if (items == null || items.isEmpty()) {
            throw new SerialNumberValidationException("No Serial Numbers provided.");
        }
        if (items.size() > MAX_ITEMS_PER_SAVE) {
            throw new SerialNumberValidationException("A single upload can hold at most " + MAX_ITEMS_PER_SAVE
                    + " Serial Numbers (this one has " + items.size() + "). Split the file and upload each part.");
        }
        boolean single = items.size() == 1;
        List<String> errors = new ArrayList<>();
        List<Planned> planned = new ArrayList<>();
        Map<Integer, String> firstRowByRecordNo = new HashMap<>();
        Map<String, String> vendorNames = new HashMap<>(); // vendor number -> name, "" when not a supplier

        Set<Integer> recordNos = new HashSet<>();
        for (SerialNumberItem item : items) {
            if (item.recordNo != null && item.recordNo > 0) {
                recordNos.add(item.recordNo);
            }
        }
        Map<Integer, SerialNumber> existingById = new HashMap<>();
        for (SerialNumber s : serialNumberRepo.findAllById(recordNos)) {
            existingById.put(s.getRecordNo(), s);
        }

        // 1. Each item on its own.
        for (int i = 0; i < items.size(); i++) {
            SerialNumberItem item = items.get(i);
            String row = single ? "" : "Row " + (item.rowNumber != null ? item.rowNumber : i + 1) + ": ";
            String label = single ? "this request" : row.substring(0, row.length() - 2);
            boolean isUpdate = item.recordNo != null;
            SerialNumber existing = isUpdate ? existingById.get(item.recordNo) : null;

            List<String> rowErrors = new ArrayList<>();
            if (isUpdate) {
                if (item.recordNo <= 0) {
                    rowErrors.add("Record No must be a whole number (leave it empty to add a new Serial Number).");
                } else if (existing == null || SerialNumber.STATUS_DELETED.equals(existing.getStatus())) {
                    rowErrors.add("Serial Number record " + item.recordNo + " does not exist.");
                } else if (firstRowByRecordNo.containsKey(item.recordNo)) {
                    rowErrors.add("Record No " + item.recordNo + " is duplicated (also in "
                            + firstRowByRecordNo.get(item.recordNo) + ").");
                } else {
                    firstRowByRecordNo.put(item.recordNo, label);
                }
            }

            Planned p = new Planned();
            p.row = row;
            p.label = label;
            p.existing = existing;
            p.serial = trim(item.serialNumber);
            p.itemCode = value(item.itemCode, existing != null ? existing.getItemCode() : null);
            String storedCode = existing != null ? emptyToNull(trim(existing.getOrganizationCode())) : null;
            p.organizationCode = value(item.organizationCode, storedCode);
            p.organizationName = existing != null ? emptyToNull(trim(existing.getOrganizationName())) : null;
            p.invoice = value(item.invoice, existing != null ? existing.getInvoice() : null);
            p.comment = value(item.comment, existing != null ? existing.getComment() : null);

            if (p.serial.isEmpty()) {
                rowErrors.add("Serial Number is required.");
            } else if (p.serial.length() > MAX_SERIAL_LENGTH) {
                rowErrors.add("Serial Number '" + p.serial + "' is longer than " + MAX_SERIAL_LENGTH + " characters.");
            }
            checkLength(rowErrors, "Item Code", p.itemCode, MAX_ITEM_CODE_LENGTH);
            if (p.organizationCode == null) {
                // Required on a create, and can't be cleared; an Oracle row without one can still be edited.
                if (existing == null || item.organizationCode != null) {
                    rowErrors.add("Organization Code (Vendor Number) is required.");
                }
            } else {
                String vendorName = vendorName(p.organizationCode, vendorNames);
                if (vendorName != null) {
                    p.organizationName = vendorName;
                } else if (!p.organizationCode.equals(storedCode)) {
                    rowErrors.add("Organization Code (Vendor Number) " + p.organizationCode + " does not exist.");
                }
                // else: an unchanged organization from the Oracle load keeps its stored name.
            }
            checkLength(rowErrors, "Organization Name", p.organizationName, MAX_ORGANIZATION_NAME_LENGTH);
            checkLength(rowErrors, "Invoice", p.invoice, MAX_INVOICE_LENGTH);
            checkLength(rowErrors, "Comment", p.comment, MAX_COMMENT_LENGTH);

            if (!trim(item.validationRule).isEmpty()) {
                p.rule = normalizeRule(item.validationRule);
                p.ruleGiven = true;
                if (p.rule == null) {
                    rowErrors.add("Validation Rule must be " + SerialNumber.RULE_SERIAL_NUMBER + " or "
                            + SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE + ".");
                } else if (SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE.equals(p.rule) && p.itemCode == null) {
                    rowErrors.add("Item Code is required when the Validation Rule is "
                            + SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE + ".");
                }
            }
            p.status = normalizeStatus(item.status, existing != null ? existing.getStatus() : SerialNumber.STATUS_ACTIVE);
            if (p.status == null) {
                rowErrors.add("Status must be " + SerialNumber.STATUS_ACTIVE + " or " + SerialNumber.STATUS_INACTIVE + ".");
            }

            if (rowErrors.isEmpty()) {
                planned.add(p);
            } else {
                rowErrors.forEach(e -> errors.add(row + e));
            }
        }

        // 2. Against the stored rows of every serial number involved (new values, and the old
        //    serial number of a row being renamed), locked until this transaction ends.
        Set<String> serials = new LinkedHashSet<>();
        for (Planned p : planned) {
            serials.add(p.serial);
            if (p.existing != null) {
                serials.add(trim(p.existing.getSerialNumber()));
            }
        }
        Set<Integer> plannedRecordNos = new HashSet<>();
        planned.forEach(p -> {
            if (p.existing != null) {
                plannedRecordNos.add(p.existing.getRecordNo());
            }
        });
        Map<String, List<FinalRow>> groups = new LinkedHashMap<>();
        for (SerialNumber s : lockNonDeleted(serials)) {
            if (!plannedRecordNos.contains(s.getRecordNo())) {
                groups.computeIfAbsent(key(s.getSerialNumber()), k -> new ArrayList<>())
                        .add(new FinalRow(s, null, normalizeRuleOrDefault(s.getValidationRule())));
            }
        }
        for (Planned p : planned) {
            groups.computeIfAbsent(key(p.serial), k -> new ArrayList<>()).add(new FinalRow(p.existing, p, null));
        }

        // 3. One Validation Rule per serial number, then uniqueness under it.
        List<SerialNumberConfirmationException.RuleChange> ruleChanges = new ArrayList<>();
        List<FinalRow> cascaded = new ArrayList<>();
        for (List<FinalRow> group : groups.values()) {
            List<FinalRow> batchRows = group.stream().filter(r -> r.planned != null).collect(Collectors.toList());
            if (batchRows.isEmpty()) {
                continue; // only the old serial number of a renamed row - nothing in it changes
            }
            List<FinalRow> storedRows = group.stream().filter(r -> r.planned == null).collect(Collectors.toList());

            Set<String> givenRules = new LinkedHashSet<>();
            batchRows.forEach(r -> {
                if (r.planned.ruleGiven) {
                    givenRules.add(r.planned.rule);
                }
            });
            if (givenRules.size() > 1) {
                errors.add("Serial Number '" + batchRows.get(0).planned.serial + "' is given different Validation Rules ("
                        + batchRows.stream().filter(r -> r.planned.ruleGiven).map(r -> r.planned.label + ": " + r.planned.rule)
                                .collect(Collectors.joining(", "))
                        + "). All records of a serial number share one Validation Rule.");
                continue;
            }
            String storedRule = storedRows.isEmpty() ? null : storedRows.get(0).rule;
            String groupRule = givenRules.isEmpty() ? null : givenRules.iterator().next();
            for (FinalRow r : batchRows) {
                Planned p = r.planned;
                if (groupRule != null) {
                    p.rule = groupRule;
                } else if (p.existing != null && key(p.existing.getSerialNumber()).equals(key(p.serial))) {
                    p.rule = normalizeRuleOrDefault(p.existing.getValidationRule());
                } else {
                    // A new row, or one renamed into this serial number, joins the rule it already has.
                    p.rule = storedRule != null ? storedRule : SerialNumber.RULE_SERIAL_NUMBER;
                }
                r.rule = p.rule;
            }
            if (groupRule != null) {
                int others = 0;
                for (FinalRow r : storedRows) {
                    if (!groupRule.equals(r.rule)) {
                        r.rule = groupRule;
                        cascaded.add(r);
                        others++;
                    }
                }
                if (others > 0) {
                    ruleChanges.add(new SerialNumberConfirmationException.RuleChange(
                            batchRows.get(0).planned.serial, groupRule, others));
                }
            }

            boolean keyChanged = false;
            for (FinalRow r : batchRows) {
                Planned p = r.planned;
                if (p.existing == null
                        || !key(p.existing.getSerialNumber()).equals(key(p.serial))
                        || !key(p.existing.getItemCode()).equals(key(p.itemCode))
                        || !Objects.equals(normalizeRuleOrDefault(p.existing.getValidationRule()), p.rule)) {
                    keyChanged = true;
                }
            }
            if (!keyChanged) {
                continue; // e.g. a Comment edit on a serial number the Oracle load holds twice
            }
            // Also catches a row that takes the rule from the serial number's other records.
            for (FinalRow r : batchRows) {
                if (SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE.equals(r.rule) && r.planned.itemCode == null
                        && !r.planned.ruleGiven) {
                    errors.add(r.planned.row + "Item Code is required: Serial Number '" + r.planned.serial
                            + "' uses Validation Rule " + SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE + ".");
                }
            }
            // Each batch row against the stored rows and the batch rows before it.
            List<FinalRow> seen = new ArrayList<>(storedRows);
            for (FinalRow r : batchRows) {
                List<FinalRow> conflicts = new ArrayList<>();
                for (FinalRow o : seen) {
                    boolean serialOnly = SerialNumber.RULE_SERIAL_NUMBER.equals(r.rule)
                            || SerialNumber.RULE_SERIAL_NUMBER.equals(o.rule);
                    if (serialOnly || key(r.itemCode()).equals(key(o.itemCode()))) {
                        conflicts.add(o);
                    }
                }
                if (!conflicts.isEmpty()) {
                    errors.add(r.planned.row + conflictMessage(r, conflicts));
                }
                seen.add(r);
            }
        }

        // 4. What each update changes.
        SaveResult result = new SaveResult();
        for (Planned p : planned) {
            if (p.existing == null || p.rule == null) {
                continue;
            }
            SerialNumber e = p.existing;
            p.changes = new LinkedHashMap<>();
            diff(p.changes, "serialNumber", e.getSerialNumber(), p.serial);
            diff(p.changes, "itemCode", e.getItemCode(), p.itemCode);
            diff(p.changes, "organizationCode", e.getOrganizationCode(), p.organizationCode);
            diff(p.changes, "organizationName", e.getOrganizationName(), p.organizationName);
            diff(p.changes, "invoice", e.getInvoice(), p.invoice);
            diff(p.changes, "validationRule", e.getValidationRule(), p.rule);
            diff(p.changes, "comment", e.getComment(), p.comment);
            diff(p.changes, "status", e.getStatus(), p.status);
            if (p.changes.isEmpty()) {
                if (rejectUnchanged) {
                    errors.add(p.row + "No changes to save.");
                }
                result.unchanged++;
            }
        }

        if (!errors.isEmpty()) {
            throw new SerialNumberValidationException(errors);
        }
        if (!ruleChanges.isEmpty() && !confirmRuleChange) {
            throw new SerialNumberConfirmationException(confirmationMessage(ruleChanges), ruleChanges);
        }

        // 5. Write.
        long writes = cascaded.size()
                + planned.stream().filter(p -> p.existing == null || !p.changes.isEmpty()).count();
        String batchId = writes > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        String user = userColumn(userName);
        if (writes > 0) {
            resetAutoIncrementOnRollback();
        }
        List<SerialNumber> updated = new ArrayList<>();
        List<Object[]> auditRows = new ArrayList<>();
        List<Planned> creates = new ArrayList<>();
        for (Planned p : planned) {
            if (p.existing == null) {
                creates.add(p);
            } else if (!p.changes.isEmpty()) {
                SerialNumber s = p.existing;
                apply(s, p);
                s.setUpdatedBy(user);
                s.setUpdatedDatetime(now);
                updated.add(s);
                auditRows.add(auditRow(s.getRecordNo(), SerialNumberAuditLog.CHANGE_UPDATE, s.getSerialNumber(),
                        s.getItemCode(), p.changes, batchId, userId, userName));
                result.updated++;
            }
        }
        for (FinalRow r : cascaded) {
            SerialNumber s = r.stored;
            Map<String, Object[]> changes = new LinkedHashMap<>();
            diff(changes, "validationRule", s.getValidationRule(), r.rule);
            s.setValidationRule(r.rule);
            s.setUpdatedBy(user);
            s.setUpdatedDatetime(now);
            updated.add(s);
            auditRows.add(auditRow(s.getRecordNo(), SerialNumberAuditLog.CHANGE_UPDATE, s.getSerialNumber(),
                    s.getItemCode(), changes, batchId, userId, userName));
            result.ruleUpdatedOthers++;
        }
        if (!updated.isEmpty()) {
            serialNumberRepo.saveAll(updated);
            serialNumberRepo.flush();
        }

        // New rows go in as multi-row INSERTs: one JPA save per row made a large upload take so
        // long it failed part-way.
        if (!creates.isEmpty()) {
            List<Integer> newRecordNos = insertSerialNumbers(creates, user, now);
            for (int i = 0; i < creates.size(); i++) {
                Planned p = creates.get(i);
                // updatedBy/updatedDatetime stay empty until the row is actually updated or deleted.
                Map<String, Object[]> changes = new LinkedHashMap<>();
                putIfSet(changes, "serialNumber", p.serial);
                putIfSet(changes, "itemCode", p.itemCode);
                putIfSet(changes, "organizationCode", p.organizationCode);
                putIfSet(changes, "organizationName", p.organizationName);
                putIfSet(changes, "invoice", p.invoice);
                putIfSet(changes, "validationRule", p.rule);
                putIfSet(changes, "comment", p.comment);
                putIfSet(changes, "status", p.status);
                auditRows.add(auditRow(newRecordNos.get(i), SerialNumberAuditLog.CHANGE_CREATE, p.serial, p.itemCode,
                        changes, batchId, userId, userName));
                result.created++;
            }
        }
        insertAuditRows(auditRows);
        return result;
    }

    // ============================================================
    // Soft delete
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public int deleteSerialNumbers(List<Integer> recordNos, Integer userId, String userName) {
        if (recordNos == null || recordNos.isEmpty()) {
            throw new SerialNumberValidationException("No Serial Numbers selected.");
        }
        if (recordNos.size() > MAX_ITEMS_PER_SAVE) {
            throw new SerialNumberValidationException("At most " + MAX_ITEMS_PER_SAVE + " Serial Numbers can be deleted at once.");
        }
        List<String> errors = new ArrayList<>();
        List<SerialNumber> toDelete = new ArrayList<>();
        Map<Integer, SerialNumber> byId = new HashMap<>();
        for (SerialNumber s : serialNumberRepo.findAllById(new LinkedHashSet<>(recordNos))) {
            byId.put(s.getRecordNo(), s);
        }
        for (Integer recordNo : new LinkedHashSet<>(recordNos)) {
            SerialNumber s = byId.get(recordNo);
            if (s == null || SerialNumber.STATUS_DELETED.equals(s.getStatus())) {
                errors.add("Serial Number record " + recordNo + " no longer exists.");
            } else {
                toDelete.add(s);
            }
        }
        if (!errors.isEmpty()) {
            throw new SerialNumberValidationException(errors);
        }

        String batchId = toDelete.size() > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        String user = userColumn(userName);
        for (SerialNumber s : toDelete) {
            Map<String, Object[]> changes = new LinkedHashMap<>();
            diff(changes, "status", s.getStatus(), SerialNumber.STATUS_DELETED);
            s.setStatus(SerialNumber.STATUS_DELETED);
            s.setUpdatedBy(user);
            s.setUpdatedDatetime(now);
            serialNumberRepo.save(s);
            log(s, SerialNumberAuditLog.CHANGE_DELETE, changes, batchId, userId, userName);
        }
        return toDelete.size();
    }

    // ============================================================
    // Acceptance requests (APIController#postdcc)
    // ============================================================

    /**
     * True when this serial number has already been received for this item, by the Validation
     * Rule of its Active rows: 'SerialNumber' matches the serial number alone, 'SerialNumber +
     * ItemCode' the serial number with itemCode (the line's actual item code when it has one,
     * else its item code - the caller decides). Inactive and deleted rows never match.
     */
    public boolean isAlreadyReceived(String serialNumber, String itemCode) {
        String serial = trim(serialNumber);
        if (serial.isEmpty()) {
            return false;
        }
        String item = key(itemCode);
        for (SerialNumber s : serialNumberRepo.findActive(serial)) {
            if (SerialNumber.RULE_SERIAL_NUMBER.equals(normalizeRuleOrDefault(s.getValidationRule()))) {
                return true;
            }
            if (!item.isEmpty() && item.equals(key(s.getItemCode()))) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Inserts the new rows, INSERT_CHUNK per statement, and returns their Record Nos in the same
     * order. A serial number can have several rows, so the ids come from the statement's generated
     * keys (consecutive for a multi-row INSERT), not from looking the rows up again.
     */
    private List<Integer> insertSerialNumbers(List<Planned> creates, String user, LocalDateTime now) {
        Timestamp recordDatetime = Timestamp.valueOf(now);
        List<Integer> recordNos = new ArrayList<>();
        for (int i = 0; i < creates.size(); i += INSERT_CHUNK) {
            List<Planned> chunk = creates.subList(i, Math.min(i + INSERT_CHUNK, creates.size()));
            String sql = "INSERT INTO tb_SerialNumber (recordDatetime, serialNumber, itemCode, organizationCode, organizationName, invoice, validationRule, comment, status, createdBy) VALUES "
                    + String.join(",", Collections.nCopies(chunk.size(), "(?,?,?,?,?,?,?,?,?,?)"));
            List<Object> args = new ArrayList<>();
            for (Planned p : chunk) {
                Collections.addAll(args, recordDatetime, p.serial, p.itemCode, p.organizationCode, p.organizationName,
                        p.invoice, p.rule, p.comment, p.status, user);
            }
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int a = 0; a < args.size(); a++) {
                    ps.setObject(a + 1, args.get(a));
                }
                return ps;
            }, keys);
            if (keys.getKeyList().size() != chunk.size()) {
                throw new IllegalStateException("Expected " + chunk.size() + " new Serial Number ids, got "
                        + keys.getKeyList().size());
            }
            for (Map<String, Object> key : keys.getKeyList()) {
                recordNos.add(((Number) key.values().iterator().next()).intValue());
            }
        }
        return recordNos;
    }

    private Object[] auditRow(Integer serialRecordNo, String changeType, String serial, String itemCode,
            Map<String, Object[]> changes, String batchId, Integer userId, String userName) {
        return new Object[] { batchId, serialRecordNo, changeType, serial, itemCode, fieldChangesJson(changes), userId,
                userName };
    }

    private void insertAuditRows(List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += INSERT_CHUNK) {
            List<Object[]> chunk = rows.subList(i, Math.min(i + INSERT_CHUNK, rows.size()));
            String sql = "INSERT INTO tb_SerialNumber_Audit_Log (batchId, serialRecordNo, changeType, serialNumber, itemCode, fieldChanges, changedBy, changedByName) VALUES "
                    + String.join(",", Collections.nCopies(chunk.size(), "(?,?,?,?,?,?,?,?)"));
            List<Object> args = new ArrayList<>();
            chunk.forEach(row -> Collections.addAll(args, row));
            jdbcTemplate.update(sql, args.toArray());
        }
    }

    /**
     * InnoDB never hands back AUTO_INCREMENT values taken by a rolled-back insert, so a failed save
     * would leave a gap in the Record Nos. Once this transaction rolls back, move both counters back
     * to the highest stored id + 1 (MySQL won't set it below that, so rows saved meanwhile - e.g. by
     * the Oracle load - are safe).
     */
    private void resetAutoIncrementOnRollback() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                // A fresh connection: the rolled-back one is still bound to this thread.
                try (Connection con = jdbcTemplate.getDataSource().getConnection();
                        Statement st = con.createStatement()) {
                    st.execute("SET SESSION lock_wait_timeout = 5");
                    try {
                        st.execute("ALTER TABLE tb_SerialNumber AUTO_INCREMENT = 1");
                        st.execute("ALTER TABLE tb_SerialNumber_Audit_Log AUTO_INCREMENT = 1");
                    } finally {
                        st.execute("SET SESSION lock_wait_timeout = DEFAULT");
                    }
                } catch (Exception e) {
                    logger.warn("Could not reset the Serial Number AUTO_INCREMENT counters after a rollback", e);
                }
            }
        });
    }

    private List<SerialNumber> lockNonDeleted(Collection<String> serialNumbers) {
        List<SerialNumber> rows = new ArrayList<>();
        List<String> all = new ArrayList<>(serialNumbers);
        try {
            for (int i = 0; i < all.size(); i += IN_CHUNK) {
                rows.addAll(serialNumberRepo.lockNonDeleted(all.subList(i, Math.min(i + IN_CHUNK, all.size()))));
            }
        } catch (PessimisticLockingFailureException ex) {
            // Another save (or the Oracle load) holds these rows longer than the lock wait timeout.
            logger.warn("Serial Number save could not lock its rows", ex);
            throw new SerialNumberValidationException(
                    "These Serial Numbers are being changed by someone else right now. Try again in a moment.");
        }
        return rows;
    }

    /** tb_Supplier's name for this vendor number, or null when there is no such vendor; cached per save. */
    private String vendorName(String vendorNumber, Map<String, String> cache) {
        String name = cache.computeIfAbsent(vendorNumber, v -> {
            supplierdata supplier = supplierRepo.findBySupplierId(v);
            return supplier == null ? "" : (supplier.supplierName == null ? v : supplier.supplierName);
        });
        return name.isEmpty() ? null : name;
    }

    private static void apply(SerialNumber s, Planned p) {
        s.setSerialNumber(p.serial);
        s.setItemCode(p.itemCode);
        s.setOrganizationCode(p.organizationCode);
        s.setOrganizationName(p.organizationName);
        s.setInvoice(p.invoice);
        s.setValidationRule(p.rule);
        s.setComment(p.comment);
        s.setStatus(p.status);
    }

    private static String conflictMessage(FinalRow r, List<FinalRow> conflicts) {
        String listed = conflicts.stream().limit(MAX_LISTED_CONFLICTS).map(FinalRow::describe)
                .collect(Collectors.joining("; "));
        if (conflicts.size() > MAX_LISTED_CONFLICTS) {
            listed += "; and " + (conflicts.size() - MAX_LISTED_CONFLICTS) + " more";
        }
        if (SerialNumber.RULE_SERIAL_NUMBER.equals(r.rule)) {
            return "Serial Number '" + r.planned.serial + "' already exists (" + listed + "). With Validation Rule "
                    + SerialNumber.RULE_SERIAL_NUMBER + " a serial number can exist only once - use "
                    + SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE + " to keep it once per item code.";
        }
        return "Serial Number '" + r.planned.serial + "' with Item Code '" + nullToEmpty(r.planned.itemCode)
                + "' already exists (" + listed + "). With Validation Rule " + SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE
                + " each serial number and item code pair can exist only once.";
    }

    private static String confirmationMessage(List<SerialNumberConfirmationException.RuleChange> changes) {
        int records = changes.stream().mapToInt(c -> c.otherRecords).sum();
        StringBuilder sb = new StringBuilder("This also changes the Validation Rule of ")
                .append(records).append(records == 1 ? " other record" : " other records")
                .append(" with the same serial number: ");
        sb.append(changes.stream().limit(10)
                .map(c -> "'" + c.serialNumber + "' (" + c.otherRecords + " → " + c.validationRule + ")")
                .collect(Collectors.joining(", ")));
        if (changes.size() > 10) {
            sb.append(" and ").append(changes.size() - 10).append(" more serial numbers");
        }
        return sb.append(". All records of a serial number share one Validation Rule.").toString();
    }

    /** "SerialNumber" / "SerialNumber + ItemCode" in any case and spacing; null when it's anything else. */
    static String normalizeRule(String rule) {
        String compact = trim(rule).replace(" ", "").toLowerCase(Locale.ROOT);
        if (compact.equals("serialnumber")) {
            return SerialNumber.RULE_SERIAL_NUMBER;
        }
        if (compact.equals("serialnumber+itemcode")) {
            return SerialNumber.RULE_SERIAL_NUMBER_ITEM_CODE;
        }
        return null;
    }

    /** A stored rule; anything unreadable counts as the column default, SerialNumber. */
    private static String normalizeRuleOrDefault(String rule) {
        String normalized = normalizeRule(rule);
        return normalized != null ? normalized : SerialNumber.RULE_SERIAL_NUMBER;
    }

    /** "Active"/"InActive" in any case (also "Inactive"), or fallback when blank; null when it's anything else. */
    private static String normalizeStatus(String status, String fallback) {
        String s = trim(status).replace(" ", "");
        if (s.isEmpty()) {
            return fallback;
        }
        if (s.equalsIgnoreCase(SerialNumber.STATUS_ACTIVE)) {
            return SerialNumber.STATUS_ACTIVE;
        }
        if (s.equalsIgnoreCase(SerialNumber.STATUS_INACTIVE)) {
            return SerialNumber.STATUS_INACTIVE;
        }
        return null;
    }

    /** Given value (trimmed, "" = clear) or, when not given, the stored one. */
    private static String value(String given, String stored) {
        return given == null ? emptyToNull(trim(stored)) : emptyToNull(trim(given));
    }

    private static void checkLength(List<String> errors, String label, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(label + " is longer than " + max + " characters.");
        }
    }

    /** Serial numbers and item codes are compared case-insensitively ('ABC' = 'abc'). */
    private static String key(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String userColumn(String userName) {
        String user = trim(userName);
        return user.length() > MAX_USER_LENGTH ? user.substring(0, MAX_USER_LENGTH) : emptyToNull(user);
    }

    private static void diff(Map<String, Object[]> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(field, new Object[] { oldValue, newValue });
        }
    }

    private static void putIfSet(Map<String, Object[]> changes, String field, String value) {
        if (value != null) {
            changes.put(field, new Object[] { null, value });
        }
    }

    private void log(SerialNumber s, String changeType, Map<String, Object[]> changes, String batchId,
            Integer userId, String userName) {
        SerialNumberAuditLog entry = new SerialNumberAuditLog();
        entry.setBatchId(batchId);
        entry.setSerialRecordNo(s.getRecordNo());
        entry.setChangeType(changeType);
        entry.setSerialNumber(s.getSerialNumber());
        entry.setItemCode(s.getItemCode());
        entry.setFieldChanges(fieldChangesJson(changes));
        entry.setChangedBy(userId);
        entry.setChangedByName(userName);
        auditLogRepo.save(entry);
    }

    /** {"field": {"old": .., "new": ..}} as stored in tb_SerialNumber_Audit_Log.fieldChanges. */
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
            throw new IllegalStateException("Could not serialise Serial Number changes", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
