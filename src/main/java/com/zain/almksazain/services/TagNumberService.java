package com.zain.almksazain.services;

import java.sql.Connection;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.model.TagNumber;
import com.zain.almksazain.model.TagNumberAuditLog;
import com.zain.almksazain.model.supplierdata;
import com.zain.almksazain.repo.TagNumberAuditLogRepo;
import com.zain.almksazain.repo.TagNumberRepo;
import com.zain.almksazain.repo.supplierrepo;
import com.zain.almksazain.specs.TagNumberFilterBuilder;

/**
 * Create / update / soft-delete of tb_TagNumber rows, each one logged to tb_TagNumber_Audit_Log.
 *
 * Every write is all-or-nothing: all items are validated first and, if any fails, a
 * TagNumberValidationException listing every error is thrown before anything is saved (same
 * "no partial uploads" rule as the UPL bulk upload). TAG Numbers are trimmed and compared
 * case-insensitively, and a TAG Number can exist only once among non-deleted rows, whatever its
 * vendor. A TAG used on an active acceptance request can't be edited (except for its Comment) or
 * deleted.
 */
@Service
public class TagNumberService {

    private static final Logger logger = LoggerFactory.getLogger(TagNumberService.class);

    /** Matches tb_TagNumber.tagNumber / tb_DCC_LN.tagNumber (VARCHAR(50)). */
    public static final int MAX_TAG_LENGTH = 50;

    /** Matches tb_TagNumber.comment (VARCHAR(500)). */
    public static final int MAX_COMMENT_LENGTH = 500;

    /** Keeps IN (...) lists short on large bulk uploads. */
    private static final int IN_CHUNK = 500;

    @Autowired
    private TagNumberRepo tagNumberRepo;

    @Autowired
    private TagNumberAuditLogRepo auditLogRepo;

    @Autowired
    private supplierrepo supplierRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One TAG to save: recordNo set means update that row, null means create. rowNumber is the
     * spreadsheet row on a bulk upload (null for the form). comment null means "not given" (an
     * update keeps the stored comment - e.g. a file without a Comment column); "" clears it.
     */
    public static class TagNumberItem {
        public Integer recordNo;
        public String tagNumber;
        public String vendorNumber;
        public String status;
        public String comment;
        public Integer rowNumber;
    }

    public static class SaveResult {
        public int created;
        public int updated;
        public int unchanged;
    }

    /** TAGs on an acceptance request that can't be used, grouped by reason (original spelling kept). */
    public static class RequestTagCheck {
        public final List<String> notRegistered = new ArrayList<>();
        public final List<String> otherVendor = new ArrayList<>();
        public final List<String> alreadyUsed = new ArrayList<>();

        public boolean isEmpty() {
            return notRegistered.isEmpty() && otherVendor.isEmpty() && alreadyUsed.isEmpty();
        }
    }

    // One validated item waiting to be written.
    private static class Planned {
        String row;
        String tag;
        String vendor;
        String status;
        String comment; // null when empty
        TagNumber existing; // null for a create
        Map<String, Object[]> changes;
    }

    // ============================================================
    // Create / update (form and bulk upload share this)
    // ============================================================

    /**
     * Creates the items without a recordNo and updates the ones with one. When rejectUnchanged is
     * set (the edit form), an update that changes nothing is an error; on a bulk upload such rows
     * are just counted as unchanged, so a re-uploaded export only touches the rows that differ.
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult saveTags(List<TagNumberItem> items, Integer userId, String userName, boolean rejectUnchanged) {
        if (items == null || items.isEmpty()) {
            throw new TagNumberValidationException("No TAG Numbers provided.");
        }
        boolean single = items.size() == 1;
        List<String> errors = new ArrayList<>();
        Map<String, String> vendorNames = new HashMap<>();
        Map<String, String> firstRowByTag = new HashMap<>();
        Map<Integer, String> firstRowByRecordNo = new HashMap<>();
        List<Planned> planned = new ArrayList<>();

        Set<Integer> recordNos = new HashSet<>();
        for (TagNumberItem item : items) {
            if (item.recordNo != null && item.recordNo > 0) {
                recordNos.add(item.recordNo);
            }
        }
        Map<Integer, TagNumber> existingById = new HashMap<>();
        for (TagNumber t : tagNumberRepo.findAllById(recordNos)) {
            existingById.put(t.getRecordNo(), t);
        }

        for (int i = 0; i < items.size(); i++) {
            TagNumberItem item = items.get(i);
            String row = single ? "" : "Row " + (item.rowNumber != null ? item.rowNumber : i + 1) + ": ";
            String rowName = single ? "this request" : row.substring(0, row.length() - 2);
            boolean isUpdate = item.recordNo != null;
            TagNumber existing = isUpdate ? existingById.get(item.recordNo) : null;

            List<String> rowErrors = new ArrayList<>();
            if (isUpdate) {
                if (item.recordNo <= 0) {
                    rowErrors.add("Record No must be a whole number (leave it empty to add a new TAG Number).");
                } else if (existing == null || TagNumber.STATUS_DELETED.equals(existing.getStatus())) {
                    rowErrors.add("TAG Number record " + item.recordNo + " does not exist.");
                } else if (firstRowByRecordNo.containsKey(item.recordNo)) {
                    rowErrors.add("Record No " + item.recordNo + " is duplicated (also in "
                            + firstRowByRecordNo.get(item.recordNo) + ").");
                } else {
                    firstRowByRecordNo.put(item.recordNo, rowName);
                }
            }
            String tag = trim(item.tagNumber);
            String vendor = trim(item.vendorNumber);
            String status = normalizeStatus(item.status,
                    existing != null ? existing.getStatus() : TagNumber.STATUS_UNUSED);
            String comment = item.comment == null
                    ? (existing != null ? existing.getComment() : null)
                    : emptyToNull(trim(item.comment));
            rowErrors.addAll(validateTagAndVendor(tag, vendor, vendorNames));
            if (status == null) {
                rowErrors.add("Status must be Used or Unused.");
            }
            if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
                rowErrors.add("Comment is longer than " + MAX_COMMENT_LENGTH + " characters.");
            }
            if (!rowErrors.isEmpty()) {
                rowErrors.forEach(e -> errors.add(row + e));
                continue;
            }

            String key = tagKey(tag);
            if (firstRowByTag.containsKey(key)) {
                errors.add(row + "TAG Number '" + tag + "' is duplicated (also in " + firstRowByTag.get(key)
                        + "). TAG Numbers are not case-sensitive.");
                continue;
            }
            firstRowByTag.put(key, rowName);

            Planned p = new Planned();
            p.row = row;
            p.tag = tag;
            p.vendor = vendor;
            p.status = status;
            p.comment = comment;
            p.existing = existing;
            planned.add(p);
        }

        // Against what's already stored: one non-deleted row per TAG, whatever its vendor.
        if (!planned.isEmpty()) {
            Set<String> tags = new LinkedHashSet<>();
            planned.forEach(p -> tags.add(p.tag));
            Map<String, TagNumber> storedByTag = new HashMap<>();
            for (TagNumber t : findNonDeleted(tags)) {
                storedByTag.put(tagKey(t.getTagNumber()), t);
            }
            for (Planned p : planned) {
                TagNumber stored = storedByTag.get(tagKey(p.tag));
                if (stored != null && (p.existing == null || !stored.getRecordNo().equals(p.existing.getRecordNo()))) {
                    errors.add(p.row + "TAG Number '" + p.tag + "' already exists as '" + stored.getTagNumber()
                            + "' (Record No " + stored.getRecordNo() + ", vendor " + stored.getVendorNumber()
                            + "). TAG Numbers are not case-sensitive.");
                }
            }
        }

        // Updates: what changes, and whether the TAG is locked by an active acceptance request.
        SaveResult result = new SaveResult();
        List<Planned> updates = new ArrayList<>();
        for (Planned p : planned) {
            if (p.existing == null) {
                continue;
            }
            p.changes = new LinkedHashMap<>();
            diff(p.changes, "tagNumber", p.existing.getTagNumber(), p.tag);
            diff(p.changes, "vendorNumber", p.existing.getVendorNumber(), p.vendor);
            diff(p.changes, "vendorName", p.existing.getVendorName(), vendorNames.get(p.vendor));
            diff(p.changes, "status", p.existing.getStatus(), p.status);
            diff(p.changes, "comment", p.existing.getComment(), p.comment);
            if (p.changes.isEmpty()) {
                if (rejectUnchanged) {
                    errors.add(p.row + "No changes to save.");
                }
                result.unchanged++;
            } else {
                updates.add(p);
            }
        }
        // A comment-only change is allowed even while the TAG is locked: it doesn't touch the request.
        List<Planned> lockable = new ArrayList<>();
        for (Planned p : updates) {
            if (!p.changes.keySet().equals(Collections.singleton("comment"))) {
                lockable.add(p);
            }
        }
        if (!lockable.isEmpty()) {
            Set<String> tags = new LinkedHashSet<>();
            lockable.forEach(p -> tags.add(p.existing.getTagNumber()));
            Map<String, Integer> refs = countActiveReferences(tags);
            for (Planned p : lockable) {
                int count = refs.getOrDefault(tagKey(p.existing.getTagNumber()), 0);
                if (count > 0) {
                    errors.add(p.row + "TAG Number '" + p.existing.getTagNumber() + "' is used on " + count
                            + " active acceptance request line(s) and cannot be edited (only its Comment can be changed).");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new TagNumberValidationException(errors);
        }

        long writes = planned.stream().filter(p -> p.existing == null || !p.changes.isEmpty()).count();
        String batchId = writes > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        if (writes > 0) {
            resetAutoIncrementOnRollback();
        }
        try {
            List<TagNumber> updated = new ArrayList<>();
            List<Object[]> auditRows = new ArrayList<>();
            for (Planned p : planned) {
                if (p.existing != null && !p.changes.isEmpty()) {
                    TagNumber t = p.existing;
                    t.setTagNumber(p.tag);
                    t.setVendorNumber(p.vendor);
                    t.setVendorName(vendorNames.get(p.vendor));
                    t.setStatus(p.status);
                    t.setComment(p.comment);
                    t.setLastUpdatedBy(userName);
                    t.setLastUpdatedDate(now);
                    updated.add(t);
                    auditRows.add(auditRow(t.getRecordNo(), TagNumberAuditLog.CHANGE_UPDATE, p.tag, p.vendor,
                            t.getVendorName(), p.changes, batchId, userId, userName));
                    result.updated++;
                }
            }
            if (!updated.isEmpty()) {
                tagNumberRepo.saveAll(updated);
                tagNumberRepo.flush();
            }

            // New rows go in as multi-row INSERTs: one JPA save per row made a 100k-row upload take
            // so long it failed part-way.
            List<Planned> creates = new ArrayList<>();
            for (Planned p : planned) {
                if (p.existing == null) {
                    creates.add(p);
                }
            }
            if (!creates.isEmpty()) {
                insertTags(creates, vendorNames, userName, now);
                Map<String, Integer> recordNoByTag = recordNosByTag(creates);
                for (Planned p : creates) {
                    Map<String, Object[]> changes = new LinkedHashMap<>();
                    changes.put("tagNumber", new Object[] { null, p.tag });
                    changes.put("vendorNumber", new Object[] { null, p.vendor });
                    changes.put("vendorName", new Object[] { null, vendorNames.get(p.vendor) });
                    changes.put("status", new Object[] { null, p.status });
                    if (p.comment != null) {
                        changes.put("comment", new Object[] { null, p.comment });
                    }
                    auditRows.add(auditRow(recordNoByTag.get(tagKey(p.tag)), TagNumberAuditLog.CHANGE_CREATE, p.tag,
                            p.vendor, vendorNames.get(p.vendor), changes, batchId, userId, userName));
                    result.created++;
                }
            }
            insertAuditRows(auditRows);
        } catch (DataIntegrityViolationException ex) {
            // Someone else saved the same TAG between our check and the write.
            logger.warn("TAG Number save hit the unique key", ex);
            throw new TagNumberValidationException(
                    "One or more of these TAG Numbers were saved by someone else just now. Refresh and try again.");
        }
        return result;
    }

    // ============================================================
    // Soft delete
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public int deleteTags(List<Integer> recordNos, Integer userId, String userName) {
        if (recordNos == null || recordNos.isEmpty()) {
            throw new TagNumberValidationException("No TAG Numbers selected.");
        }
        List<String> errors = new ArrayList<>();
        List<TagNumber> toDelete = new ArrayList<>();
        Map<Integer, TagNumber> byId = new HashMap<>();
        for (TagNumber t : tagNumberRepo.findAllById(new LinkedHashSet<>(recordNos))) {
            byId.put(t.getRecordNo(), t);
        }
        for (Integer recordNo : new LinkedHashSet<>(recordNos)) {
            TagNumber t = byId.get(recordNo);
            if (t == null || TagNumber.STATUS_DELETED.equals(t.getStatus())) {
                errors.add("TAG Number record " + recordNo + " no longer exists.");
            } else {
                toDelete.add(t);
            }
        }
        Set<String> tags = new LinkedHashSet<>();
        toDelete.forEach(t -> tags.add(t.getTagNumber()));
        Map<String, Integer> refs = countActiveReferences(tags);
        for (TagNumber t : toDelete) {
            int count = refs.getOrDefault(tagKey(t.getTagNumber()), 0);
            if (count > 0) {
                errors.add("TAG Number '" + t.getTagNumber() + "' is used on " + count
                        + " active acceptance request line(s) and cannot be deleted.");
            }
        }
        if (!errors.isEmpty()) {
            throw new TagNumberValidationException(errors);
        }

        String batchId = toDelete.size() > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        for (TagNumber t : toDelete) {
            Map<String, Object[]> changes = new LinkedHashMap<>();
            diff(changes, "status", t.getStatus(), TagNumber.STATUS_DELETED);
            t.setStatus(TagNumber.STATUS_DELETED);
            t.setLastUpdatedBy(userName);
            t.setLastUpdatedDate(now);
            tagNumberRepo.save(t);
            log(t, TagNumberAuditLog.CHANGE_DELETE, changes, batchId, userId, userName);
        }
        return toDelete.size();
    }

    // ============================================================
    // Acceptance requests (APIController#postdcc)
    // ============================================================

    /**
     * Checks the TAGs (already trimmed) of one acceptance request being submitted: each must be
     * registered to this vendor and still 'Unused'. A TAG already on this same request (a draft or
     * returned request being resubmitted) may be 'Used' - it was marked so by this request.
     */
    public RequestTagCheck checkTagsForRequest(String vendorNumber, Collection<String> tagNumbers,
            Collection<String> tagsAlreadyOnRequest) {
        RequestTagCheck check = new RequestTagCheck();
        Set<String> wanted = new LinkedHashSet<>(tagNumbers);
        if (wanted.isEmpty()) {
            return check;
        }
        Map<String, TagNumber> storedByTag = new HashMap<>();
        for (TagNumber t : findNonDeleted(wanted)) {
            storedByTag.put(tagKey(t.getTagNumber()), t);
        }
        Set<String> ownTags = new HashSet<>();
        for (String tag : tagsAlreadyOnRequest) {
            ownTags.add(tagKey(tag));
        }
        String vendor = trim(vendorNumber);
        for (String tag : wanted) {
            TagNumber stored = storedByTag.get(tagKey(tag));
            if (stored == null) {
                check.notRegistered.add(tag);
            } else if (!vendor.equals(stored.getVendorNumber())) {
                check.otherVendor.add(tag);
            } else if (!TagNumber.STATUS_UNUSED.equals(stored.getStatus()) && !ownTags.contains(tagKey(tag))) {
                check.alreadyUsed.add(tag);
            }
        }
        return check;
    }

    /**
     * Marks these TAGs 'Used' once acceptance request requestNo has been submitted with them;
     * TAGs already 'Used' are left as they are. Logged like any other update, with the request.
     */
    @Transactional(rollbackFor = Exception.class)
    public void markTagsUsed(Collection<String> tagNumbers, String requestNo, Integer userId, String userName) {
        if (tagNumbers == null || tagNumbers.isEmpty()) {
            return;
        }
        List<TagNumber> toMark = new ArrayList<>();
        for (TagNumber t : findNonDeleted(new LinkedHashSet<>(tagNumbers))) {
            if (TagNumber.STATUS_UNUSED.equals(t.getStatus())) {
                toMark.add(t);
            }
        }
        String batchId = toMark.size() > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        for (TagNumber t : toMark) {
            Map<String, Object[]> changes = new LinkedHashMap<>();
            changes.put("status", new Object[] { t.getStatus(), TagNumber.STATUS_USED });
            changes.put("requestNo", new Object[] { null, requestNo });
            t.setStatus(TagNumber.STATUS_USED);
            t.setLastUpdatedBy(userName);
            t.setLastUpdatedDate(now);
            tagNumberRepo.save(t);
            log(t, TagNumberAuditLog.CHANGE_UPDATE, changes, batchId, userId, userName);
        }
    }

    /**
     * Frees TAGs that acceptance request requestNo no longer carries (taken off its lines on a
     * resubmit): each 'Used' one goes back to 'Unused' unless another active request still has it.
     * Status changes of the request itself are handled by trg_tb_DCC_sync_tag_numbers.
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseTags(Collection<String> tagNumbers, String requestNo, Integer userId, String userName) {
        if (tagNumbers == null || tagNumbers.isEmpty()) {
            return;
        }
        Set<String> tags = new LinkedHashSet<>(tagNumbers);
        Map<String, Integer> refs = countActiveReferences(tags);
        List<TagNumber> toRelease = new ArrayList<>();
        for (TagNumber t : findNonDeleted(tags)) {
            if (TagNumber.STATUS_USED.equals(t.getStatus()) && refs.getOrDefault(tagKey(t.getTagNumber()), 0) == 0) {
                toRelease.add(t);
            }
        }
        String batchId = toRelease.size() > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        for (TagNumber t : toRelease) {
            Map<String, Object[]> changes = new LinkedHashMap<>();
            changes.put("status", new Object[] { t.getStatus(), TagNumber.STATUS_UNUSED });
            changes.put("requestNo", new Object[] { requestNo, null });
            t.setStatus(TagNumber.STATUS_UNUSED);
            t.setLastUpdatedBy(userName);
            t.setLastUpdatedDate(now);
            tagNumberRepo.save(t);
            log(t, TagNumberAuditLog.CHANGE_UPDATE, changes, batchId, userId, userName);
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static final int INSERT_CHUNK = 1000;

    private void insertTags(List<Planned> creates, Map<String, String> vendorNames, String userName,
            LocalDateTime now) {
        Timestamp createdDate = Timestamp.valueOf(now);
        for (int i = 0; i < creates.size(); i += INSERT_CHUNK) {
            List<Planned> chunk = creates.subList(i, Math.min(i + INSERT_CHUNK, creates.size()));
            // lastUpdatedBy/lastUpdatedDate stay empty until the row is actually updated or deleted.
            String sql = "INSERT INTO tb_TagNumber (tagNumber, vendorNumber, vendorName, status, comment, createdBy, createdDate) VALUES "
                    + String.join(",", Collections.nCopies(chunk.size(), "(?,?,?,?,?,?,?)"));
            List<Object> args = new ArrayList<>();
            for (Planned p : chunk) {
                Collections.addAll(args, p.tag, p.vendor, vendorNames.get(p.vendor), p.status, p.comment,
                        userName, createdDate);
            }
            jdbcTemplate.update(sql, args.toArray());
        }
    }

    /** recordNo of each just-created TAG, keyed by tagKey (a TAG has one non-deleted row). */
    private Map<String, Integer> recordNosByTag(List<Planned> creates) {
        Set<String> tags = new LinkedHashSet<>();
        creates.forEach(p -> tags.add(p.tag));
        Map<String, Integer> ids = new HashMap<>();
        for (List<String> chunk : chunks(tags)) {
            String sql = "SELECT recordNo, tagNumber FROM tb_TagNumber WHERE status <> '" + TagNumber.STATUS_DELETED
                    + "' AND tagNumber IN (" + String.join(",", Collections.nCopies(chunk.size(), "?")) + ")";
            jdbcTemplate.query(sql, rs -> {
                ids.put(tagKey(rs.getString("tagNumber")), rs.getInt("recordNo"));
            }, chunk.toArray());
        }
        return ids;
    }

    private Object[] auditRow(Integer tagRecordNo, String changeType, String tag, String vendor, String vendorName,
            Map<String, Object[]> changes, String batchId, Integer userId, String userName) {
        return new Object[] { batchId, tagRecordNo, changeType, tag, vendor, vendorName, fieldChangesJson(changes),
                userId, userName };
    }

    private void insertAuditRows(List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i += INSERT_CHUNK) {
            List<Object[]> chunk = rows.subList(i, Math.min(i + INSERT_CHUNK, rows.size()));
            String sql = "INSERT INTO tb_TagNumber_Audit_Log (batchId, tagRecordNo, changeType, tagNumber, vendorNumber, vendorName, fieldChanges, changedBy, changedByName) VALUES "
                    + String.join(",", Collections.nCopies(chunk.size(), "(?,?,?,?,?,?,?,?,?)"));
            List<Object> args = new ArrayList<>();
            chunk.forEach(row -> Collections.addAll(args, row));
            jdbcTemplate.update(sql, args.toArray());
        }
    }

    /**
     * InnoDB never hands back AUTO_INCREMENT values taken by a rolled-back insert, so a failed save
     * would leave a gap in the Record Nos. Once this transaction rolls back, move both counters back
     * to the highest stored id + 1 (MySQL won't set it below that, so rows saved meanwhile are safe).
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
                        st.execute("ALTER TABLE tb_TagNumber AUTO_INCREMENT = 1");
                        st.execute("ALTER TABLE tb_TagNumber_Audit_Log AUTO_INCREMENT = 1");
                    } finally {
                        st.execute("SET SESSION lock_wait_timeout = DEFAULT");
                    }
                } catch (Exception e) {
                    logger.warn("Could not reset the TAG Number AUTO_INCREMENT counters after a rollback", e);
                }
            }
        });
    }

    private List<TagNumber> findNonDeleted(Collection<String> tagNumbers) {
        List<TagNumber> rows = new ArrayList<>();
        for (List<String> chunk : chunks(tagNumbers)) {
            rows.addAll(tagNumberRepo.findNonDeleted(chunk));
        }
        return rows;
    }

    /**
     * Lines on active acceptance requests (status not incomplete/rejected/returned) carrying each
     * of these TAGs, any vendor, keyed by tagKey. tb_DCC_LN.tagNumber's _ci collation matches
     * case-insensitively, and its tagNumber index keeps this to index lookups.
     */
    private Map<String, Integer> countActiveReferences(Collection<String> tagNumbers) {
        // Plain JDBC doesn't see pending JPA changes in this transaction (e.g. the last line
        // item a resubmit re-tagged), so write them out first or a dropped TAG still looks in use.
        tagNumberRepo.flush();
        Map<String, Integer> counts = new HashMap<>();
        for (List<String> chunk : chunks(tagNumbers)) {
            String sql = "SELECT ln.tagNumber AS tagNumber, COUNT(*) AS cnt FROM tb_DCC_LN ln "
                    + "JOIN tb_DCC d ON d.recordNo = ln.dccId "
                    + "WHERE ln.tagNumber IN (" + String.join(",", Collections.nCopies(chunk.size(), "?")) + ") "
                    + "AND LOWER(d.status) NOT IN (" + TagNumberFilterBuilder.INACTIVE_DCC_STATUSES + ") "
                    + "GROUP BY ln.tagNumber";
            jdbcTemplate.query(sql, rs -> {
                counts.merge(tagKey(rs.getString("tagNumber")), rs.getInt("cnt"), Integer::sum);
            }, chunk.toArray());
        }
        return counts;
    }

    /** Field checks shared by create and update; fills vendorNames with tb_Supplier's name on success. */
    private List<String> validateTagAndVendor(String tag, String vendor, Map<String, String> vendorNames) {
        List<String> errors = new ArrayList<>();
        if (tag.isEmpty()) {
            errors.add("TAG Number is required.");
        } else if (tag.length() > MAX_TAG_LENGTH) {
            errors.add("TAG Number '" + tag + "' is longer than " + MAX_TAG_LENGTH + " characters.");
        }
        if (vendor.isEmpty()) {
            errors.add("Vendor Number is required.");
        } else if (!vendorNames.containsKey(vendor)) {
            supplierdata supplier = supplierRepo.findBySupplierId(vendor);
            if (supplier == null) {
                errors.add("Vendor Number " + vendor + " does not exist.");
            } else {
                vendorNames.put(vendor, supplier.supplierName);
            }
        }
        return errors;
    }

    /** "Used"/"Unused" in any case, or fallback when blank; null when it's anything else. */
    private static String normalizeStatus(String status, String fallback) {
        String s = trim(status);
        if (s.isEmpty()) {
            return fallback;
        }
        if (s.equalsIgnoreCase(TagNumber.STATUS_USED)) {
            return TagNumber.STATUS_USED;
        }
        if (s.equalsIgnoreCase(TagNumber.STATUS_UNUSED)) {
            return TagNumber.STATUS_UNUSED;
        }
        return null;
    }

    /** TAG Numbers are compared case-insensitively ('ABC' = 'abc'). */
    private static String tagKey(String tag) {
        return trim(tag).toLowerCase(Locale.ROOT);
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<String> all = new ArrayList<>(values);
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            out.add(all.subList(i, Math.min(i + IN_CHUNK, all.size())));
        }
        return out;
    }

    private static void diff(Map<String, Object[]> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(field, new Object[] { oldValue, newValue });
        }
    }

    private void log(TagNumber t, String changeType, Map<String, Object[]> changes, String batchId,
            Integer userId, String userName) {
        TagNumberAuditLog entry = new TagNumberAuditLog();
        entry.setBatchId(batchId);
        entry.setTagRecordNo(t.getRecordNo());
        entry.setChangeType(changeType);
        entry.setTagNumber(t.getTagNumber());
        entry.setVendorNumber(t.getVendorNumber());
        entry.setVendorName(t.getVendorName());
        entry.setFieldChanges(fieldChangesJson(changes));
        entry.setChangedBy(userId);
        entry.setChangedByName(userName);
        auditLogRepo.save(entry);
    }

    /** {"field": {"old": .., "new": ..}} as stored in tb_TagNumber_Audit_Log.fieldChanges. */
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
            throw new IllegalStateException("Could not serialise TAG Number changes", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
