package com.zain.almksazain.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zain.almksazain.model.User;
import com.zain.almksazain.model.departmentsdata;
import com.zain.almksazain.repo.RoleRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.deptsrepo;
@Service
public class SlaNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(SlaNotificationService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private DccPoCombinedService dccPoCombinedService;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;
    @Autowired private deptsrepo deptsRepo;
    @Autowired private RoleRepository roleRepository;
    
    // @Scheduled(cron = "0 0 5 * * *")
    public void runStage1Reminders() {
        runStage1RemindersWithFilters(Collections.emptyMap());
    }



   public void runStage1RemindersWithFilters(Map<String, Object> filters) {
        logger.info("SLA Stage 1 (manual) job started with filters={}", filters);
        try {
            // Extract optional cc/bcc provided via config filters (may be null)
            Object rawCc = filters != null ? filters.get("cc") : null;
            Object rawBcc = filters != null ? filters.get("bcc") : null;

            // Resolve cc/bcc early (so we log and pass resolved addresses to EmailService)
            String resolvedCc = resolveRecipientsToEmails(rawCc);
            String resolvedBcc = resolveRecipientsToEmails(rawBcc);
            logger.debug("Resolved cc='{}' bcc='{}' (rawCc='{}' rawBcc='{}')", resolvedCc, resolvedBcc, rawCc, rawBcc);

            // Let upstream apply non-local filters first
            Map<String, Object> upstreamFilters = buildUpstreamFilters(filters);
            // Convert Map<String,Object> to Map<String,String> for the method call
            Map<String, String> stringFilters = upstreamFilters.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                ));
            Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 1000);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());
            logger.debug("Upstream returned {} rows (before local filtering).", data.size());

            // Determine effective department & userAging threshold from filters (fallback to defaults)
            List<String> deptFilterList = extractDepartmentFilter(filters); 
            Integer configuredThreshold = extractUserAgingFilter(filters);
            int effectiveThreshold = configuredThreshold == null ? 5 : configuredThreshold;

            // Enforce filters in-memory (guarantees behavior even if upstream ignores filters)
            List<Map<String, Object>> stage1Rows = data.stream()
                .filter(row -> {
                    if (deptFilterList != null && !deptFilterList.isEmpty()) {
                        String rowDept = normalizeDept(safeString(row.get("departmentName"), ""));
                        boolean anyMatch = deptFilterList.stream()
                                .map(this::normalizeDept)
                                .anyMatch(df -> !df.isEmpty() && df.equals(rowDept));
                        if (!anyMatch) return false;
                    }
                    Object agingVal = row.get("userAgingInDays") != null ? row.get("userAgingInDays") : row.get("userAging");
                    int days = numericDays(agingVal);

                    // optional debug: log a few sample parsed values to help troubleshooting
                    if (logger.isDebugEnabled() && Math.random() < 0.01) {
                        logger.debug("Sample aging parse: dccId={}, rawAgingVal={} -> days={}", row.get("dccId"), agingVal, days);
                    }

                    return days >= effectiveThreshold;
                })
                .collect(Collectors.toList());

            logger.info("Stage1 - upstreamRows={}, rowsAfterLocalFilter={}, deptFilter={}, threshold={}",
                    data.size(), stage1Rows.size(), deptFilterList, effectiveThreshold);

            Map<String, List<Map<String, Object>>> byApprover = groupRowsByApprover(stage1Rows);
            logger.debug("Grouped into {} approver groups: {}", byApprover.size(),
                    byApprover.keySet().stream().limit(50).collect(Collectors.joining(", ")));

            for (Map.Entry<String, List<Map<String, Object>>> entry : byApprover.entrySet()) {
                String approverKey = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();

                logger.debug("Processing approverKey='{}' rowsCount={}", approverKey, rows.size());

                Optional<String> approverEmailOpt = resolveApproverEmail(rows, approverKey);
                String firstRecordNo = rows.isEmpty() ? "n/a" : String.valueOf(rows.get(0).get("recordNo"));

                if (approverEmailOpt.isEmpty()) {
                    logger.warn("Stage1 - Approver email not found for approverKey='{}' -> skipping {} rows (sample recordNo={})",
                            approverKey, rows.size(), firstRecordNo);
                    // additional debug: sample the pendingApprovers values for this group
                    if (logger.isDebugEnabled()) {
                        List<String> samples = rows.stream()
                                .map(r -> safeString(r.get("pendingApprovers"), "") + "|" + safeString(r.get("pendingApproverUsername"), ""))
                                .limit(5).collect(Collectors.toList());
                        logger.debug("Stage1 - Sample pendingApprovers for '{}': {}", approverKey, samples);
                    }
                    continue;
                }
                String approverEmail = approverEmailOpt.get();

                // Now build aggregated dashboard-style table (one row per approver, counts per bucket)
                String rowsPreview = buildFrontendStyledRowsTable(rows);

                String approverDisplay = rows.stream()
                        .map(r -> safeString(r.get("pendingApprovers"), ""))
                        .filter(s -> !s.isBlank() && !"Unassigned".equalsIgnoreCase(s))
                        .findFirst()
                        .orElse( approverKey == null || approverKey.equals("Unassigned") ? null : approverKey );

                // Use effectiveThreshold (configured/cron threshold) for subject and note
                String subject = buildStage1Subject(rows.size(), firstRecordNo,
                        rows.stream().map(r -> safeString(r.get("poNumber"), "")).filter(s -> !s.isEmpty()).findFirst().orElse(""),
                        rows.stream().map(r -> safeString(r.get("dccId"), "")).filter(s -> !s.isEmpty()).findFirst().orElse(""),
                        effectiveThreshold
                );

                // presentation department (first available original-case value)
                String department = rows.stream()
                        .map(r -> safeString(r.get("departmentName"), ""))
                        .filter(s -> !s.isBlank())
                        .findFirst().orElse(null);

                String userName = rows.stream()
                        .map(r -> safeString(r.get("pendingApproverUsername"), ""))
                        .filter(s -> !s.isBlank() && !"Unassigned".equalsIgnoreCase(s))
                        .findFirst().orElse(approverKey);

                String role = "approver";

                logger.info("About to send Stage1 reminder: approver='{}' email='{}' requests={} dept='{}' user='{}' role='{}' cc='{}' bcc='{}'",
                        approverDisplay, approverEmail, rows.size(), department, userName, role, resolvedCc, resolvedBcc);
                logger.debug("Stage1 email subject='{}' bodyPreview='{}'", subject, rowsPreview.length() > 200 ? rowsPreview.substring(0,200) + "..." : rowsPreview);

                // Pass actual request count (rows.size()) into the HTML builder so Request(s) shows the real number
                String body = constructSlaReminderHtml(approverDisplay, rowsPreview, rows.size(), department, effectiveThreshold);

                Integer requestCount = rows.size();

                // Pass department, user info and resolved cc/bcc into sendEmail
                emailService.sendEmail(approverEmail, subject, body, null, department, userName, role, requestCount, resolvedCc, resolvedBcc);
                logger.info("Stage1 reminder queued/sent for approver='{}' email='{}' ({} requests)", approverDisplay, approverEmail, rows.size());
            }
        } catch (Exception e) {
            logger.error("Error running SLA Stage 1 job", e);
        }
    }
      
// NOTE: This version filters out rows that have no escalation manager before grouping so only rows with an escalation manager are sent.

public void runStage2EscalationsWithFilters(Map<String, Object> filters) {
    logger.info("SLA Stage 2 (escalation) job started with filters={}", filters);
    try {
        Object rawCcObj = filters != null ? filters.get("cc") : null;
        Object rawBccObj = filters != null ? filters.get("bcc") : null;

        String resolvedCc = resolveRecipientsToEmails(rawCcObj);
        String resolvedBcc = resolveRecipientsToEmails(rawBccObj);

        logger.debug("Stage2 - resolved cc='{}' bcc='{}' (rawCc='{}' rawBcc='{}')",
                resolvedCc, resolvedBcc, rawCcObj, rawBccObj);

        // Use resolved values when sending
        String configCc = resolvedCc;
        String configBcc = resolvedBcc;

        // Let upstream apply non-local filters first
        Map<String, Object> upstreamFilters = buildUpstreamFilters(filters);
        // Convert Map<String,Object> to Map<String,String> for the method call
        Map<String, String> stringFilters = upstreamFilters.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() != null ? entry.getValue().toString() : ""
            ));
        Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 1000);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());
        logger.debug("Upstream returned {} rows (before local filtering).", data.size());

        // extract filters
        List<String> deptFilterList = extractDepartmentFilter(filters);
        Integer configuredThreshold = extractUserAgingFilter(filters);
        int effectiveThreshold = configuredThreshold == null ? 10 : configuredThreshold; // default Stage2: 10 days

        // in-memory filtering: department + threshold + require escalationManager present (skip rows without one)
        List<Map<String, Object>> stage2Rows = data.stream()
            .filter(row -> {
                if (deptFilterList != null && !deptFilterList.isEmpty()) {
                    String deptName = normalizeDept(safeString(row.get("departmentName"), ""));
                    boolean anyMatch = deptFilterList.stream()
                            .map(this::normalizeDept)
                            .anyMatch(df -> !df.isEmpty() && df.equals(deptName));
                    if (!anyMatch) return false;
                }

                Object agingVal = row.get("userAgingInDays") != null ? row.get("userAgingInDays") : row.get("userAging");
                int days = numericDays(agingVal);

                if (logger.isDebugEnabled() && Math.random() < 0.01) {
                    logger.debug("Stage2 sample aging parse: dccId={}, rawAgingVal={} -> days={}", row.get("dccId"), agingVal, days);
                }

                if (days < effectiveThreshold) return false;

                  String resolvedEscalationUsername = resolveEscalationUsernameForRow(row);
        if (resolvedEscalationUsername == null || resolvedEscalationUsername.isBlank() || "UNASSIGNED".equalsIgnoreCase(resolvedEscalationUsername)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Stage2 skipping row dccId={} recordNo={} because escalation manager could not be resolved from approver(s) {}",
                        row.get("dccId"), row.get("recordNo"), safeString(row.get("pendingApprovers"), ""));
            }
            return false;
        }

                return true;
            })
            .collect(Collectors.toList());

        logger.debug("Stage2 - total data rows returned={}, stage2 rows after filter={}", data.size(), stage2Rows.size());

        // group only the rows that have escalationManager set
        Map<String, List<Map<String, Object>>> byManager = groupRowsByManager(stage2Rows);

        for (Map.Entry<String, List<Map<String, Object>>> me : byManager.entrySet()) {
            String managerKey = me.getKey();
            List<Map<String, Object>> rowsForManager = me.getValue();

            String sampleRecordNo = rowsForManager.isEmpty() ? "n/a" : String.valueOf(rowsForManager.get(0).get("recordNo"));

            // Resolve the manager user record (username is managerKey)
            Optional<User> managerUserOpt = tryFindUserByUsername(managerKey);
            if (managerUserOpt.isEmpty()) {
                logger.warn("Stage2 - escalation manager username '{}' not found -> skipping {} rows (sample recordNo={})",
                        managerKey, rowsForManager.size(), sampleRecordNo);
                continue;
            }
            User managerUser = managerUserOpt.get();

            if (managerUser.getEmailAddress() == null || managerUser.getEmailAddress().isBlank()) {
                logger.warn("Stage2 - escalation manager '{}' found but has no email -> skipping {} rows (sample recordNo={})",
                        managerUser.getUsername(), rowsForManager.size(), sampleRecordNo);
                continue;
            }

            // Build rows preview only for rowsForManager (they may come from different departments)
            String rowsPreview = buildFrontendStyledRowsTable(rowsForManager);

            String managerDisplayName = managerUser.getFullName() != null && !managerUser.getFullName().isBlank()
                    ? managerUser.getFullName() : managerUser.getUsername();

            Map<String, Integer> approverCounts = new LinkedHashMap<>();
            for (Map<String, Object> r : rowsForManager) {
                List<String> keys = extractApproverKeysFromRow(r);
                for (String k : keys) {
                    if (k == null) continue;
                    String trimmed = k.trim();
                    if (trimmed.isEmpty() || "Unassigned".equalsIgnoreCase(trimmed)) continue;
                    approverCounts.put(trimmed, approverCounts.getOrDefault(trimmed, 0) + 1);
                }
            }

            // determine department(s) to record (comma-separated unique list), null if none
            String department = rowsForManager.stream()
                    .map(r -> safeString(r.get("departmentName"), ""))
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (department.isBlank()) department = null;

            String body = constructSlaEscalationHtml(managerDisplayName, rowsForManager.size(), rowsPreview, department, approverCounts, effectiveThreshold);
            String subject = buildStage2Subject(rowsForManager.size(), effectiveThreshold);

            String userName = managerUser.getUsername();
            String role = "manager";

            logger.info("About to send Stage2 escalation: manager='{}' email='{}' requests={} subject={} dept='{}' user='{}' role='{}' cc='{}' bcc='{}'",
                    managerDisplayName, managerUser.getEmailAddress(), rowsForManager.size(), subject, department, userName, role, configCc, configBcc);
            logger.debug("Stage2 email bodyPreview='{}'", rowsPreview.length() > 200 ? rowsPreview.substring(0,200) + "..." : rowsPreview);

            emailService.sendEmail(
                    managerUser.getEmailAddress(),
                    subject,
                    body,
                    null,
                    department,
                    userName,
                    role,
                    rowsForManager.size(),
                    configCc,
                    configBcc
            );
            logger.info("Stage2 escalation scheduled/sent to {} ({} requests)", managerUser.getEmailAddress(), rowsForManager.size());
        }

    } catch (Exception e) {
        logger.error("Error running SLA Stage 2 job", e);
    }
}
    private String resolveRecipientsToEmails(Object v) {
        if (v == null) return null;

        List<String> tokens = new ArrayList<>();
        try {
            if (v instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) v;
                for (Object o : rawList) if (o != null) tokens.add(o.toString().trim());
            } else {
                String s = v.toString().trim();
                if (s.startsWith("[")) {
                    // try parse JSON array
                    try {
                        List<String> parsed = objectMapper.readValue(s, new TypeReference<List<String>>() {});
                        for (String p : parsed) if (p != null) tokens.add(p.trim());
                    } catch (Exception ex) {
                        // fallback to simple split
                        tokens.addAll(java.util.Arrays.stream(s.replaceAll("[\\[\\]\"]", "").split("\\s*,\\s*"))
                                .map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toList()));
                    }
                } else if (s.contains(",")) {
                    tokens.addAll(java.util.Arrays.stream(s.split("\\s*,\\s*")).map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toList()));
                } else {
                    tokens.add(s);
                }
            }
        } catch (Exception ex) {
            logger.debug("resolveRecipientsToEmails failed to normalize input '{}': {}", v, ex.getMessage());
            return null;
        }

        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            if (t == null || t.isEmpty()) continue;
            if (t.contains("@")) {
                out.add(t);
            } else {
                // try lookup username -> email
                try {
                    Optional<User> u = userRepository.findByUsername(t);
                    if (u != null && u.isPresent()) {
                        String email = u.get().getEmailAddress();
                        if (email != null && !email.isBlank()) {
                            out.add(email.trim());
                            continue;
                        } else {
                            logger.debug("resolveRecipientsToEmails: found user '{}' but email empty", t);
                        }
                    } else {
                        // fallback try full name
                        Optional<User> byFull = findUserByFullName(t);
                        if (byFull != null && byFull.isPresent()) {
                            if (byFull.get().getEmailAddress() != null && !byFull.get().getEmailAddress().isBlank()) {
                                out.add(byFull.get().getEmailAddress().trim());
                                continue;
                            }
                        }
                        logger.debug("resolveRecipientsToEmails: could not resolve token '{}'", t);
                    }
                } catch (Throwable ex) {
                    logger.warn("resolveRecipientsToEmails: error resolving '{}': {}", t, ex.getMessage());
                }
            }
        }

        if (out.isEmpty()) return null;
        // dedupe while preserving order
        return out.stream().distinct().collect(Collectors.joining(","));
    }



private Map<String, List<Map<String, Object>>> groupRowsByManager(List<Map<String, Object>> rows) {
    Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
    if (rows == null || rows.isEmpty()) return map;
    final String UNASSIGNED = "UNASSIGNED";
    for (Map<String, Object> row : rows) {
        String mgr = resolveEscalationUsernameForRow(row);
        String key = (mgr == null || mgr.isBlank()) ? UNASSIGNED : mgr;
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }
    return map;
}

    private Map<String, List<Map<String, Object>>> groupRowsByApprover(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) return map;

        for (Map<String, Object> row : rows) {
            List<String> keys = extractApproverKeysFromRow(row);
            for (String k : keys) {
                map.computeIfAbsent(k, kk -> new ArrayList<>()).add(row);
            }
        }
        return map;
    }

    private List<String> extractApproverKeysFromRow(Map<String, Object> row) {
        List<String> keys = new ArrayList<>();
        if (row == null) return keys;

        String username = safeString(row.get("pendingApproverUsername"), "").trim();
        if (!username.isBlank() && !"Unassigned".equalsIgnoreCase(username)) {
            keys.add(username);
        }

        String pendingApprovers = safeString(row.get("pendingApprovers"), "").trim();
        if (!pendingApprovers.isBlank() && !"Unassigned".equalsIgnoreCase(pendingApprovers)) {
            // split by common separators (comma, semicolon, pipe, slash) and " and "
            String[] parts = pendingApprovers.split("\\s*(,|;|\\||/|\\band\\b)\\s*");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !"Unassigned".equalsIgnoreCase(trimmed) && !keys.contains(trimmed)) {
                    keys.add(trimmed);
                }
            }
        }

        if (keys.isEmpty()) keys.add("Unassigned");
        return keys;
    }

    private Optional<String> resolveApproverEmail(List<Map<String, Object>> rows, String approverKeyHint) {
        if (rows == null || rows.isEmpty()) return Optional.empty();

        if (approverKeyHint != null && !approverKeyHint.isBlank() && !"Unassigned".equalsIgnoreCase(approverKeyHint)) {
            try {
                Optional<User> u = userRepository.findByUsername(approverKeyHint);
                if (u != null && u.isPresent() && u.get().getEmailAddress() != null && !u.get().getEmailAddress().isBlank()) {
                    return Optional.of(u.get().getEmailAddress());
                }
            } catch (Throwable ignored) {}
        }

        for (Map<String, Object> row : rows) {
            Object usernameObj = row.get("pendingApproverUsername");
            String usernameCandidate = null;
            if (usernameObj != null && !usernameObj.toString().isBlank() && !"Unassigned".equalsIgnoreCase(usernameObj.toString())) {
                usernameCandidate = usernameObj.toString();
            } else {
                Object pendingApproversObj = row.get("pendingApprovers");
                if (pendingApproversObj != null && !pendingApproversObj.toString().isBlank() && !"Unassigned".equalsIgnoreCase(pendingApproversObj.toString())) {
                    if (approverKeyHint != null && !approverKeyHint.isBlank()) {
                        usernameCandidate = approverKeyHint;
                    } else {
                        String[] parts = pendingApproversObj.toString().split("\\s*(,|;|\\||/|\\band\\b)\\s*");
                        if (parts.length > 0) usernameCandidate = parts[0].trim();
                    }
                }
            }

            if (usernameCandidate != null) {
                try {
                    Optional<User> u = userRepository.findByUsername(usernameCandidate);
                    if (u != null && u.isPresent() && u.get().getEmailAddress() != null && !u.get().getEmailAddress().isBlank()) {
                        return Optional.of(u.get().getEmailAddress());
                    }
                } catch (Throwable ignored) {}
            }
        }

        // If username-based resolution failed, try resolving by full name (existing fallback)
        String fullName = rows.stream()
                .map(r -> safeString(r.get("pendingApprovers"), ""))
                .filter(s -> s != null && !s.trim().isEmpty() && !"Unassigned".equalsIgnoreCase(s))
                .findFirst()
                .orElse(approverKeyHint);

        if (fullName == null) return Optional.empty();
        Optional<User> byRow = findUserByFullName(fullName);
        return byRow.map(User::getEmailAddress).filter(Objects::nonNull).filter(s -> !s.isBlank());
    }
private String normalizeToken(String t) {
    if (t == null) return null;
    // replace common invisible chars and normalize whitespace
    String s = t.replace('\u00A0', ' ')  // NBSP
                .replace("\u200B", "")  // ZERO WIDTH SPACE
                .replace("\u200C", "")  // ZERO WIDTH NON-JOINER
                .replace("\u200D", "")  // ZERO WIDTH JOINER
                .trim();
    s = s.replaceAll("\\s+", " ");
    return s;
}

private Optional<User> tryFindUserByUsername(String username) {
    if (username == null) return Optional.empty();
    String trimmed = normalizeToken(username);
    if (trimmed == null || trimmed.isEmpty()) return Optional.empty();

    try {
        Optional<User> direct = userRepository.findByUsername(trimmed);
        if (direct != null && direct.isPresent()) return direct;
    } catch (Throwable t) {
        logger.debug("tryFindUserByUsername: repo.findByUsername failed for '{}': {}", trimmed, t.getMessage());
    }

    try {
        return userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(trimmed))
                .findFirst();
    } catch (Throwable t) {
        logger.debug("tryFindUserByUsername: fallback scan failed for '{}': {}", trimmed, t.getMessage());
    }
    return Optional.empty();
}

private Optional<User> findUserByEmail(String email) {
    if (email == null) return Optional.empty();
    String e = normalizeToken(email);
    if (e == null || e.isEmpty()) return Optional.empty();
    try {
        // If you have a repository method findByEmail you can prefer it:
        // Optional<User> byRepo = userRepository.findByEmail(e);
        // if (byRepo != null && byRepo.isPresent()) return byRepo;
        return userRepository.findAll().stream()
                .filter(u -> u.getEmailAddress() != null && u.getEmailAddress().equalsIgnoreCase(e))
                .findFirst();
    } catch (Throwable t) {
        logger.debug("findUserByEmail fallback scan failed for '{}': {}", e, t.getMessage());
        return Optional.empty();
    }
}

private String resolveEscalationUsernameForRow(Map<String, Object> row) {
    if (row == null) return null;
    List<String> approverKeys = extractApproverKeysFromRow(row);
    for (String ak : approverKeys) {
        if (ak == null) continue;
        String normalizedKey = normalizeToken(ak);
        if (normalizedKey == null || normalizedKey.isBlank() || "Unassigned".equalsIgnoreCase(normalizedKey)) continue;

        try {
            // 1) Try to find approver by username
            Optional<User> approverUserOpt = tryFindUserByUsername(normalizedKey);

            // 2) If not found by username, try by full name
            if (approverUserOpt.isEmpty()) {
                Optional<User> byFull = findUserByFullName(normalizedKey);
                if (byFull != null && byFull.isPresent()) {
                    approverUserOpt = byFull;
                }
            }

            if (approverUserOpt.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Approver key '{}' not found as username or full name in User repo", normalizedKey);
                }
                continue; // try next approver token
            }

            User approverUser = approverUserOpt.get();
            String esc = approverUser.getEscalationManager();
            if (esc == null || esc.trim().isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Approver '{}' has no escalationManager configured on their user record (email={})",
                            normalizedKey, safeString(approverUser.getEmailAddress(), "<no-email>"));
                }
                continue; // try next approver token
            }

            String escTrim = normalizeToken(esc);

            // If escalationManager is an email -> find user by email
            if (escTrim.contains("@")) {
                Optional<User> mgrByEmail = findUserByEmail(escTrim);
                if (mgrByEmail.isPresent()) {
                    String resolved = mgrByEmail.get().getUsername();
                    if (logger.isDebugEnabled()) logger.debug("Resolved approver '{}' -> escalation manager by email '{}' -> username '{}'",
                            normalizedKey, escTrim, resolved);
                    return resolved;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Escalation manager value '{}' for approver '{}' looks like an email but no user found by that email",
                            escTrim, normalizedKey);
                }
            }

            // If escalationManager is a username -> return username if user exists
            Optional<User> mgrByUsername = tryFindUserByUsername(escTrim);
            if (mgrByUsername.isPresent()) {
                String resolved = mgrByUsername.get().getUsername();
                if (logger.isDebugEnabled()) logger.debug("Resolved approver '{}' -> escalation manager username '{}'", normalizedKey, resolved);
                return resolved;
            }

            // Try resolving escalationManager as full name
            Optional<User> mgrByFullName = findUserByFullName(escTrim);
            if (mgrByFullName.isPresent()) {
                String resolved = mgrByFullName.get().getUsername();
                if (logger.isDebugEnabled()) logger.debug("Resolved approver '{}' -> escalation manager fullName '{}' -> username '{}'",
                        normalizedKey, escTrim, resolved);
                return resolved;
            }

            // Last resort: return the normalized raw value (so grouping will still work by that key)
            if (logger.isDebugEnabled()) {
                logger.debug("Escalation manager '{}' for approver '{}' could not be resolved to a User record - returning raw value",
                        escTrim, normalizedKey);
            }
            return escTrim;
        } catch (Throwable t) {
            logger.debug("resolveEscalationUsernameForRow error for approver '{}': {}", ak, t.getMessage());
        }
    }
    return null;
}

    private Optional<User> findUserByFullName(String fullName) {
        if (fullName == null) return Optional.empty();
        try {
            Optional<User> byRepo = userRepository.findFirstByFullName(fullName);
            if (byRepo != null && byRepo.isPresent()) return byRepo;
        } catch (Throwable ignored) { }
        return userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && u.getFullName().equalsIgnoreCase(fullName))
                .findFirst();
    }

    private String buildStage1Subject(int count, String recordNo, String poNumber, String requestId, int agingDays) {
    try {
        return String.format("Reminder: Pending acceptance requests exceeded SLA",
                Math.max(0, count),
                Math.max(0, agingDays));
    } catch (Exception e) {
        return "Reminder: Pending acceptance requests exceeded SLA";
    }
}

private String buildStage2Subject(int count, int agingDays) {
    try {
        return String.format("Notification: Pending acceptance requests exceeded SLA",
                Math.max(0, count),
                Math.max(0, agingDays));
    } catch (Exception e) {
        return "Notification: Pending acceptance requests exceeded SLA";
    }
}

    private int numericDays(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();

        String s = val.toString().trim();
        if (s.isEmpty()) return 0;

        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {}

        try {
            double d = Double.parseDouble(s);
            return (int) d;
        } catch (NumberFormatException ignored) {}

        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:^|\\D)(\\d+)\\s*(?:day|days)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(s);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            p = java.util.regex.Pattern.compile("(-?\\d+)");
            m = p.matcher(s);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private String safeString(Object o, String defaultVal) {
        return o == null ? defaultVal : o.toString();
    }

// Updated: Added inline styling to enhance compatibility with email clients like Gmail; simplified and enforced table-based layout for consistency.
private String constructSlaReminderHtml(String approverFullName, String rowsPreviewHtml, int requestCount, String department, int agingDays) {
    String approverDisplay = approverFullName == null ? "Approver" : approverFullName;
    String salutation = "<p style=\"margin:0 0 10px 0; font-family: Arial, Helvetica, sans-serif;\">Dear " + escapeHtml(approverDisplay) + ",</p>";
    String requestCountStr = String.valueOf(Math.max(0, requestCount));
    StringBuilder sb = new StringBuilder(8192);

    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"/>")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale:1\"/></head>")
      .append("<body style=\"margin:0;padding:0;background:#ffffff;color:#333;font-family:Arial,Helvetica,sans-serif;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
      .append("<tr><td align=\"right\" style=\"padding:0;\">")
      .append("<table role=\"presentation\" align=\"right\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
              + "style=\"width:auto;background:#ffffff;margin:18px 0 18px auto;padding:14px;border-collapse:collapse;\">");

    sb.append("<tr><td style=\"padding:10px 0 6px 0;font-size:13px;color:#222;font-family:Arial,Helvetica,sans-serif;\">")
      .append(salutation)
      .append("</td></tr>");

    sb.append("<tr><td style=\"padding:0 0 6px 0;font-size:13px;color:#222;font-family:Arial,Helvetica,sans-serif;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
      .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;width:160px;font-weight:700\">Request(s):</td>")
      .append("<td style=\"padding:0 0 6px 0;\">" + escapeHtml(requestCountStr) + "</td></tr>")
      .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Approver:</td>")
      .append("<td style=\"padding:0 0 6px 0;\">" + escapeHtml(approverDisplay) + "</td></tr>");
    if (department != null && !department.isBlank()) {
        sb.append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Department:</td>")
          .append("<td style=\"padding:0 0 6px 0;\">" + escapeHtml(department) + "</td></tr>");
    }
    sb.append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Note:</td>")
      .append("<td style=\"padding:0 0 6px 0;\">These requests have exceeded (user aging &ge; " + escapeHtml(String.valueOf(Math.max(0, agingDays))) + " days).</td></tr>")
      .append("</table>")
      .append("</td></tr>");

    sb.append("<tr><td style=\"padding:8px 0 12px 0;font-size:12px;color:#555;font-family:Arial,Helvetica,sans-serif;\">Please review and action the requests listed below. A full aging dashboard-style attachment is included below.</td></tr>");

    if (rowsPreviewHtml != null && !rowsPreviewHtml.isEmpty()) {
        sb.append("<tr><td style=\"padding:6px 0\">")
          .append("<table style=\"overflow:auto;\">" + rowsPreviewHtml.replaceFirst("<table", "<table dir=\"ltr\"") + "</table>")
          .append("</td></tr>");
    }

    sb.append("<tr><td style=\"padding-top:12px;border-top:1px solid #e6e6e6;font-size:11px;color:#9c1b1b;font-family:Arial,Helvetica,sans-serif;\">Warning: This is an automated email. Please do not reply or forward.</td></tr>")
      .append("</table>")
      .append("</td></tr></table>")
      .append("</body></html>");

    return sb.toString();
}

private String constructSlaEscalationHtml(String managerFullName, int requestCount, String rowsPreviewHtml, String department, Map<String, Integer> approverCounts, int agingDays) {
    String managerDisplay = managerFullName == null ? "Manager" : managerFullName;
    String salutation = "<p style=\"margin:0 0 10px 0;font-family: Arial, Helvetica, sans-serif;\">Dear " + escapeHtml(managerDisplay) + ",</p>";
    StringBuilder sb = new StringBuilder(4096);

    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"/>")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale:1\"/></head>")
      .append("<body style=\"margin:0;padding:0;background:#ffffff;color:#333;font-family:Arial,Helvetica,sans-serif;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
      .append("<tr><td align=\"right\" style=\"padding:0;\">")
      .append("<table role=\"presentation\" align=\"right\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
              + "style=\"width:auto;margin:18px 0 18px auto;padding:14px;border-collapse:collapse;\">");

    sb.append("<tr><td style=\"padding:10px 0 6px 0;font-size:13px;color:#222;font-family:Arial,Helvetica,sans-serif;\">")
      .append(salutation)
      .append("</td></tr>");

    sb.append("<tr><td style=\"padding:0 0 6px 0;font-size:13px;color:#222;font-family:Arial,Helvetica,sans-serif;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
      .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;width:160px;font-weight:700\">Request(s):</td>")
      .append("<td style=\"padding:0 0 6px 0;\">" + escapeHtml(String.valueOf(requestCount)) + "</td></tr>");
    if (department != null && !department.isBlank()) {
        sb.append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Department:</td>")
          .append("<td style=\"padding:0 0 6px 0;\">" + escapeHtml(department) + "</td></tr>");
    }
    sb.append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Note:</td>")
      .append("<td style=\"padding:0 0 6px 0;\">Requests shown below have exceeded (user aging &ge; " + escapeHtml(String.valueOf(Math.max(0, agingDays))) + " days).</td></tr>")
      .append("</table>")
      .append("</td></tr>");

    sb.append("<tr><td style=\"padding:0 0 12px 0;font-size:12px;color:#555;font-family:Arial,Helvetica,sans-serif;\">Please coordinate with your approvers for immediate action. A full aging report is attached to this email.</td></tr>");

    if (approverCounts != null && !approverCounts.isEmpty()) {
        sb.append("<tr><td style=\"padding:6px 0 0 0;font-size:13px;color:#222;font-family:Arial,Helvetica,sans-serif;\">")
          .append("<table role=\"presentation\" cellpadding=\"4\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
          .append("<tr><td style=\"font-weight:700;padding:6px 0 4px 0;\">Approvers & Request Counts:</td></tr>");
        for (Map.Entry<String, Integer> ac : approverCounts.entrySet()) {
            sb.append("<tr><td style=\"padding:2px 0 2px 8px;font-size:12px;color:#333;font-family:Arial,Helvetica,sans-serif;\">")
              .append(escapeHtml(ac.getKey()))
              .append(" : ")
              .append(escapeHtml(String.valueOf(ac.getValue())))
              .append("</td></tr>");
        }
        sb.append("</table>")
          .append("</td></tr>");
    }

    if (rowsPreviewHtml != null && !rowsPreviewHtml.isEmpty()) {
        sb.append("<tr><td style=\"padding:6px 0\">")
          .append("<table style=\"overflow:auto;\">" + rowsPreviewHtml.replaceFirst("<table", "<table dir=\"ltr\"") + "</table>")
          .append("</td></tr>");
    }

    sb.append("<tr><td style=\"padding-top:12px;border-top:1px solid #e6e6e6;font-size:11px;color:#9c1b1b;font-family:Arial,Helvetica,sans-serif;\">Warning: This is an automated email. Please do not reply or forward.</td></tr>")
      .append("</table>")
      .append("</td></tr></table>")
      .append("</body></html>");

    return sb.toString();
}

    private String buildFrontendStyledRowsTable(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder(8192);
        // Outer table uses black borders for visibility
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"border-collapse:collapse;table-layout:fixed;font-size:12px;word-break:break-word;width:100%;border:1px solid #000000;\">");

        // Header
        sb.append("<thead><tr>");
        // header uses black border on th
        String thBase = "style=\"background:#74B72E;color:#ffffff;font-weight:700;padding:10px 8px;border:1px solid #000000;text-align:left;white-space:nowrap;\"";
        sb.append("<th ").append(thBase).append(">Department</th>");
        sb.append("<th ").append(thBase).append(">Approver</th>");
        sb.append("<th ").append(thBase).append(">Status</th>");
        sb.append("<th ").append(thBase).append(">Same Day</th>");
        sb.append("<th ").append(thBase).append(">1 Day</th>");
        sb.append("<th ").append(thBase).append(">2 Days</th>");
        sb.append("<th ").append(thBase).append(">3 Days</th>");
        sb.append("<th ").append(thBase).append(">4-7 Days</th>");
        sb.append("<th ").append(thBase).append(">1-2 Weeks</th>");
        sb.append("<th ").append(thBase).append(">2-4 Weeks</th>");
        sb.append("<th ").append(thBase).append(">1-2 Months</th>");
        sb.append("<th ").append(thBase).append(">2-3 Months</th>");
        sb.append("<th ").append(thBase).append(">3+ Months</th>");
        sb.append("<th ").append(thBase).append(" style=\"text-align:right;\">Total</th>");
        sb.append("</tr></thead><tbody>");

        if (rows == null || rows.isEmpty()) {
            // colspan now 14 (previously 15 including Value column)
            sb.append("<tr><td colspan=\"14\" style=\"padding:8px;border:1px solid #000000;\">No requests</td></tr>");
            sb.append("</tbody></table>");
            return sb.toString();
        }

        // Group rows by approver key (use same extraction logic as other places)
        Map<String, List<Map<String, Object>>> byApprover = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String approver = safeString(row.get("pendingApproverUsername"),
                    safeString(row.get("pendingApprovers"), "")).trim();
            if (approver.isBlank()) approver = "Unassigned";
            byApprover.computeIfAbsent(approver, k -> new ArrayList<>()).add(row);
        }

        int rowIndex = 0;
        for (Map.Entry<String, List<Map<String, Object>>> e : byApprover.entrySet()) {
            String approver = e.getKey();
            List<Map<String, Object>> list = e.getValue();

            String rowBg = (rowIndex % 2 == 0) ? "background:#ffffff;" : "background:#fbfff9;";
            sb.append("<tr style=\"").append(rowBg).append("\">");

            // Department: prefer first non-empty departmentName from list
            String department = list.stream()
                    .map(r -> safeString(r.get("departmentName"), ""))
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst().orElse("");

            sb.append("<td style=\"border:1px solid #000000;padding:8px;vertical-align:top;min-width:160px;\">")
              .append(escapeHtml(department))
              .append("</td>");

            // Approver
            sb.append("<td style=\"border:1px solid #000000;padding:8px;vertical-align:top;min-width:160px;\">")
              .append(escapeHtml(approver))
              .append("</td>");

            // Status: show first status (or 'inprocess' fallback)
            String status = list.stream()
                    .map(r -> safeString(r.get("dccStatus"), safeString(r.get("status"), "")))
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst().orElse("inprocess");
            sb.append("<td style=\"border:1px solid #000000;padding:8px;vertical-align:top;min-width:110px;\">")
              .append(escapeHtml(status))
              .append("</td>");

            // compute counts per bucket and total
            int[] counts = new int[10];
            int total = 0;
            for (Map<String, Object> r : list) {
                Object agingVal = r.get("userAgingInDays") != null ? r.get("userAgingInDays") : r.get("userAging");
                int days = numericDays(agingVal);
                int bucket = bucketIndexFromDays(days);
                if (bucket < 0 || bucket >= counts.length) bucket = counts.length - 1;
                counts[bucket] += 1;
                total += 1;
            }

            // render bucket counts (if zero - empty cell; if >0 - show badge with number)
            for (int b = 0; b < counts.length; b++) {
                if (counts[b] > 0) {
                    sb.append("<td style=\"border:1px solid #000000;padding:6px;text-align:center;vertical-align:middle;\">")
                      .append("<span style=\"display:inline-block;padding:6px 8px;border-radius:6px;background:")
                      .append(";color:#131313ff;font-weight:700;\">")
                      .append(counts[b])
                      .append("</span></td>");
                } else {
                    sb.append("<td style=\"border:1px solid #000000;padding:6px;text-align:center;vertical-align:middle;\"></td>");
                }
            }

            // Total
            sb.append("<td style=\"border:1px solid #000000;padding:6px;text-align:right;vertical-align:middle;\">")
              .append(total)
              .append("</td>");

            sb.append("</tr>");
            rowIndex++;
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }



    
    private int bucketIndexFromDays(int days) {
        if (days <= 0) return 0;
        if (days == 1) return 1;
        if (days == 2) return 2;
        if (days == 3) return 3;
        if (days >= 4 && days <= 7) return 4;
        if (days >= 8 && days <= 14) return 5;
        if (days >= 15 && days <= 28) return 6;
        if (days >= 29 && days <= 60) return 7;
        if (days >= 61 && days <= 90) return 8;
        return 9;
    }



    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private int countTableRows(String tableFragment) {
        if (tableFragment == null) return 0;
        int count = 0;
        String lower = tableFragment.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf("<tr", idx)) != -1) {
            count++;
            idx += 3;
        }
        return Math.max(0, count - 1);
    }


    private List<String> extractDepartmentFilter(Map<String, Object> filters) {
        if (filters == null) return Collections.emptyList();
        Object v = null;
        if (filters.containsKey("department")) v = filters.get("department");
        else if (filters.containsKey("departmentName")) v = filters.get("departmentName");
        if (v == null) return Collections.emptyList();

        // If it's a List already, normalize to List<String>
        if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) v;
            List<String> out = new ArrayList<>();
            for (Object o : rawList) {
                if (o == null) continue;
                String s = o.toString().trim();
                if (s.isEmpty()) continue;
                if ("all".equalsIgnoreCase(s)) return Collections.emptyList(); // explicit ALL -> treat as no filter
                out.add(s);
            }
            return out;
        }

        // If it's a String: either "ALL" / single name / JSON array / comma-separated
        String s = v.toString().trim();
        if (s.isEmpty()) return Collections.emptyList();
        if ("all".equalsIgnoreCase(s)) return Collections.emptyList();

        // try JSON array
        if (s.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                List<String> parsed = om.readValue(s, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                List<String> out = new ArrayList<>();
                for (String p : parsed) {
                    if (p == null) continue;
                    String t = p.trim();
                    if (!t.isEmpty()) out.add(t);
                }
                return out;
            } catch (Exception ignored) { }
        }

        // comma-separated fallback
        if (s.contains(",")) {
            String[] parts = s.split("\\s*,\\s*");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                if (p == null) continue;
                String t = p.trim();
                if (!t.isEmpty() && !"all".equalsIgnoreCase(t)) out.add(t);
            }
            return out;
        }

        // single value
        return List.of(s);
    }

    private Integer extractUserAgingFilter(Map<String, Object> filters) {
        if (filters == null) return null;

        String[] keys = new String[] { "userAging", "minUserAging", "userAgingInDays", "minUserAgingInDays" };
        for (String k : keys) {
            if (filters.containsKey(k)) {
                Object raw = filters.get(k);
                if (raw == null) continue;
                // if already a Number
                if (raw instanceof Number) {
                    int v = ((Number) raw).intValue();
                    if (v >= 0) return v;
                    else continue;
                }
                String s = raw.toString().trim();
                if (s.isEmpty()) continue;
                // remove leading comparator if present (>=, <=, >, <, =)
                s = s.replaceAll("^\\s*(>=|<=|>|<|=)\\s*", "");
                // remove trailing non-digit characters like "d", "days"
                s = s.replaceAll("[^0-9-].*$", "");
                try {
                    int parsed = Integer.parseInt(s);
                    if (parsed >= 0) return parsed;
                } catch (NumberFormatException e) {
                    // ignore and continue
                }
            }
        }
        return null;
    }

    private String normalizeDept(String d) {
        return d == null ? "" : d.trim().toLowerCase();
    }



private Map<String, Object> buildUpstreamFilters(Map<String, Object> incoming) {
    if (incoming == null || incoming.isEmpty()) return Collections.emptyMap();
    Map<String, Object> upstream = new LinkedHashMap<>();

    // keys we handle locally and must NOT forward to upstream (case-insensitive)
    Set<String> localOnlyLower = Set.of(
        "department", "departmentname",
        "useraging", "minuseraging", "useragingindays", "minuseragingindays",
        "cc", "bcc", "cclist", "bcclist"
    );

    for (Map.Entry<String, Object> e : incoming.entrySet()) {
        String k = e.getKey();
        if (k == null) continue;
        String kk = k.trim();
        if (kk.isEmpty()) continue;

        if (localOnlyLower.contains(kk.toLowerCase())) {
            logger.debug("Omitting local-only filter key from upstream: {}", kk);
            continue;
        }

        upstream.put(kk, e.getValue());
    }

    logger.debug("Built upstream filters (local-only keys removed): {}", upstream);
    return upstream;
}




}