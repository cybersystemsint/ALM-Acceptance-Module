package com.zain.almksazain.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.model.TagNumber;
import com.zain.almksazain.model.TagNumberAuditLog;
import com.zain.almksazain.model.supplierdata;
import com.zain.almksazain.repo.TagNumberAuditLogRepo;
import com.zain.almksazain.repo.TagNumberRepo;
import com.zain.almksazain.repo.supplierrepo;

/**
 * Create / update / soft-delete of tb_TagNumber rows, each one logged to tb_TagNumber_Audit_Log.
 *
 * Every write is all-or-nothing: all items are validated first and, if any fails, a
 * TagNumberValidationException listing every error is thrown before anything is saved (same
 * "no partial uploads" rule as the UPL bulk upload). TAG Numbers are trimmed and compared
 * case-sensitively; uniqueness is (tagNumber + vendorNumber) among non-deleted rows.
 */
@Service
public class TagNumberService {

    private static final Logger logger = LoggerFactory.getLogger(TagNumberService.class);

    /** Matches tb_TagNumber.tagNumber / tb_DCC_LN.tagNumber (VARCHAR(50)). */
    public static final int MAX_TAG_LENGTH = 50;

    /** Same definition of an "active" acceptance request as the duplicate-TAG check in APIController#postdcc. */
    private static final String INACTIVE_DCC_STATUSES = "'incomplete', 'rejected', 'returned'";

    @Autowired
    private TagNumberRepo tagNumberRepo;

    @Autowired
    private TagNumberAuditLogRepo auditLogRepo;

    @Autowired
    private supplierrepo supplierRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** One TAG to create; rowNumber is the spreadsheet row on a bulk upload (null for the form). */
    public static class TagNumberItem {
        public String tagNumber;
        public String vendorNumber;
        public String status;
        public Integer rowNumber;
    }

    // ============================================================
    // Create (one-by-one form and bulk upload share this)
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public List<TagNumber> createTags(List<TagNumberItem> items, Integer userId, String userName) {
        if (items == null || items.isEmpty()) {
            throw new TagNumberValidationException("No TAG Numbers provided.");
        }
        boolean single = items.size() == 1;
        List<String> errors = new ArrayList<>();
        Map<String, String> vendorNames = new HashMap<>();
        Map<String, String> firstRowByKey = new HashMap<>();
        List<String[]> valid = new ArrayList<>(); // [tagNumber, vendorNumber, status, rowLabel]

        for (int i = 0; i < items.size(); i++) {
            TagNumberItem item = items.get(i);
            String row = single ? "" : "Row " + (item.rowNumber != null ? item.rowNumber : i + 1) + ": ";
            String tag = trim(item.tagNumber);
            String vendor = trim(item.vendorNumber);
            String status = normalizeStatus(item.status, TagNumber.STATUS_ACTIVE);

            List<String> rowErrors = new ArrayList<>();
            rowErrors.addAll(validateTagAndVendor(tag, vendor, vendorNames));
            if (status == null) {
                rowErrors.add("Status must be Active or Inactive.");
            }
            if (!rowErrors.isEmpty()) {
                rowErrors.forEach(e -> errors.add(row + e));
                continue;
            }

            String key = tag + "\u0000" + vendor;
            if (firstRowByKey.containsKey(key)) {
                errors.add(row + "TAG Number '" + tag + "' for vendor " + vendor
                        + " is duplicated (also in " + firstRowByKey.get(key) + ").");
                continue;
            }
            firstRowByKey.put(key, single ? "this request" : row.substring(0, row.length() - 2));
            valid.add(new String[] { tag, vendor, status, row });
        }

        // Against what's already stored (non-deleted rows only).
        if (!valid.isEmpty()) {
            Set<String> tags = new HashSet<>();
            Set<String> vendors = new HashSet<>();
            for (String[] v : valid) {
                tags.add(v[0]);
                vendors.add(v[1]);
            }
            Set<String> existingKeys = new HashSet<>();
            for (TagNumber t : tagNumberRepo.findNonDeleted(vendors, tags)) {
                existingKeys.add(t.getTagNumber() + "\u0000" + t.getVendorNumber());
            }
            for (String[] v : valid) {
                if (existingKeys.contains(v[0] + "\u0000" + v[1])) {
                    errors.add(v[3] + "TAG Number '" + v[0] + "' already exists for vendor " + v[1] + ".");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new TagNumberValidationException(errors);
        }

        String batchId = valid.size() > 1 ? UUID.randomUUID().toString() : null;
        LocalDateTime now = LocalDateTime.now();
        List<TagNumber> created = new ArrayList<>();
        try {
            for (String[] v : valid) {
                TagNumber t = new TagNumber();
                t.setTagNumber(v[0]);
                t.setVendorNumber(v[1]);
                t.setVendorName(vendorNames.get(v[1]));
                t.setStatus(v[2]);
                t.setCreatedBy(userName);
                t.setCreatedDate(now);
                // lastUpdatedBy/lastUpdatedDate stay empty until the row is actually updated or deleted.
                t = tagNumberRepo.saveAndFlush(t);
                created.add(t);

                Map<String, Object[]> changes = new LinkedHashMap<>();
                changes.put("tagNumber", new Object[] { null, t.getTagNumber() });
                changes.put("vendorNumber", new Object[] { null, t.getVendorNumber() });
                changes.put("vendorName", new Object[] { null, t.getVendorName() });
                changes.put("status", new Object[] { null, t.getStatus() });
                log(t, TagNumberAuditLog.CHANGE_CREATE, changes, batchId, userId, userName);
            }
        } catch (DataIntegrityViolationException ex) {
            // Someone else created the same TAG + vendor between our check and the insert.
            logger.warn("TAG Number create hit the unique key", ex);
            throw new TagNumberValidationException(
                    "One or more TAG Numbers were created by someone else just now. Refresh and try again.");
        }
        return created;
    }

    // ============================================================
    // Update (TAG Number, vendor, Active/Inactive)
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public TagNumber updateTag(Integer recordNo, String tagNumber, String vendorNumber, String status,
            Integer userId, String userName) {
        TagNumber t = findNonDeleted(recordNo);
        String tag = trim(tagNumber);
        String vendor = trim(vendorNumber);
        String newStatus = normalizeStatus(status, t.getStatus());

        Map<String, String> vendorNames = new HashMap<>();
        List<String> errors = new ArrayList<>(validateTagAndVendor(tag, vendor, vendorNames));
        if (newStatus == null) {
            errors.add("Status must be Active or Inactive.");
        }
        if (!errors.isEmpty()) {
            throw new TagNumberValidationException(errors);
        }

        boolean keyChanging = !tag.equals(t.getTagNumber()) || !vendor.equals(t.getVendorNumber());
        if (keyChanging) {
            int refs = countActiveReferences(t.getTagNumber(), t.getVendorNumber());
            if (refs > 0) {
                throw new TagNumberValidationException("TAG Number '" + t.getTagNumber()
                        + "' is used on " + refs + " active acceptance request line(s), so its TAG Number and"
                        + " Vendor cannot be changed.");
            }
            for (TagNumber other : tagNumberRepo.findNonDeleted(Collections.singleton(vendor),
                    Collections.singleton(tag))) {
                if (!other.getRecordNo().equals(t.getRecordNo())) {
                    throw new TagNumberValidationException(
                            "TAG Number '" + tag + "' already exists for vendor " + vendor + ".");
                }
            }
        }

        Map<String, Object[]> changes = new LinkedHashMap<>();
        diff(changes, "tagNumber", t.getTagNumber(), tag);
        diff(changes, "vendorNumber", t.getVendorNumber(), vendor);
        diff(changes, "vendorName", t.getVendorName(), vendorNames.get(vendor));
        diff(changes, "status", t.getStatus(), newStatus);
        if (changes.isEmpty()) {
            throw new TagNumberValidationException("No changes to save.");
        }

        t.setTagNumber(tag);
        t.setVendorNumber(vendor);
        t.setVendorName(vendorNames.get(vendor));
        t.setStatus(newStatus);
        t.setLastUpdatedBy(userName);
        t.setLastUpdatedDate(LocalDateTime.now());
        try {
            t = tagNumberRepo.saveAndFlush(t);
        } catch (DataIntegrityViolationException ex) {
            logger.warn("TAG Number update hit the unique key", ex);
            throw new TagNumberValidationException(
                    "TAG Number '" + tag + "' already exists for vendor " + vendor + ".");
        }
        log(t, TagNumberAuditLog.CHANGE_UPDATE, changes, null, userId, userName);
        return t;
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
        for (Integer recordNo : new LinkedHashSet<>(recordNos)) {
            TagNumber t = tagNumberRepo.findById(recordNo).orElse(null);
            if (t == null || TagNumber.STATUS_DELETED.equals(t.getStatus())) {
                errors.add("TAG Number record " + recordNo + " no longer exists.");
                continue;
            }
            int refs = countActiveReferences(t.getTagNumber(), t.getVendorNumber());
            if (refs > 0) {
                errors.add("TAG Number '" + t.getTagNumber() + "' (vendor " + t.getVendorNumber()
                        + ") is used on " + refs + " active acceptance request line(s) and cannot be deleted.");
                continue;
            }
            toDelete.add(t);
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
    // Acceptance request check (APIController#postdcc)
    // ============================================================

    /**
     * Returns those of tagNumbers (already trimmed) that are NOT registered as 'Active' for this
     * vendor, keeping the caller's order. Exact, case-sensitive match.
     */
    public List<String> findTagsNotActiveForVendor(String vendorNumber, Collection<String> tagNumbers) {
        Set<String> wanted = new LinkedHashSet<>(tagNumbers);
        if (wanted.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> active = new HashSet<>();
        String vendor = trim(vendorNumber);
        if (!vendor.isEmpty()) {
            for (TagNumber t : tagNumberRepo.findActiveForVendor(vendor, wanted)) {
                active.add(t.getTagNumber());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String tag : wanted) {
            if (!active.contains(tag)) {
                missing.add(tag);
            }
        }
        return missing;
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Lines on active acceptance requests (status not incomplete/rejected/returned) of this vendor
     * that carry exactly this TAG. tb_DCC_LN.tagNumber has the schema's case-insensitive collation,
     * hence the explicit binary COLLATE.
     */
    private int countActiveReferences(String tagNumber, String vendorNumber) {
        String sql = "SELECT COUNT(*) FROM tb_DCC_LN ln JOIN tb_DCC d ON d.recordNo = ln.dccId "
                + "WHERE TRIM(ln.tagNumber) COLLATE utf8mb4_0900_bin = ? AND d.vendorNumber = ? "
                + "AND LOWER(d.status) NOT IN (" + INACTIVE_DCC_STATUSES + ")";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tagNumber, vendorNumber);
        return count == null ? 0 : count;
    }

    private TagNumber findNonDeleted(Integer recordNo) {
        TagNumber t = recordNo == null ? null : tagNumberRepo.findById(recordNo).orElse(null);
        if (t == null || TagNumber.STATUS_DELETED.equals(t.getStatus())) {
            throw new TagNumberValidationException("TAG Number record " + recordNo + " no longer exists.");
        }
        return t;
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

    /** "Active"/"Inactive" in any case, or fallback when blank; null when it's anything else. */
    private static String normalizeStatus(String status, String fallback) {
        String s = trim(status);
        if (s.isEmpty()) {
            return fallback;
        }
        if (s.equalsIgnoreCase(TagNumber.STATUS_ACTIVE)) {
            return TagNumber.STATUS_ACTIVE;
        }
        if (s.equalsIgnoreCase(TagNumber.STATUS_INACTIVE)) {
            return TagNumber.STATUS_INACTIVE;
        }
        return null;
    }

    private static void diff(Map<String, Object[]> changes, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(field, new Object[] { oldValue, newValue });
        }
    }

    private void log(TagNumber t, String changeType, Map<String, Object[]> changes, String batchId,
            Integer userId, String userName) {
        Map<String, Map<String, Object>> json = new LinkedHashMap<>();
        changes.forEach((field, values) -> {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("old", values[0]);
            pair.put("new", values[1]);
            json.put(field, pair);
        });
        TagNumberAuditLog entry = new TagNumberAuditLog();
        entry.setBatchId(batchId);
        entry.setTagRecordNo(t.getRecordNo());
        entry.setChangeType(changeType);
        entry.setTagNumber(t.getTagNumber());
        entry.setVendorNumber(t.getVendorNumber());
        entry.setVendorName(t.getVendorName());
        try {
            entry.setFieldChanges(objectMapper.writeValueAsString(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise TAG Number changes", e);
        }
        entry.setChangedBy(userId);
        entry.setChangedByName(userName);
        auditLogRepo.save(entry);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
