package com.zain.almksazain.services;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.zain.almksazain.model.User;
import com.zain.almksazain.repo.UserRepository;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SlaNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SlaNotificationService.class);

    @Autowired private DccPoCombinedService dccPoCombinedService;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;

    // Stage 1: daily at 08:00 AM - reminder to pending approvers where userAgingInDays > 5
    @Scheduled(cron = "0 13 14 * * *" )
    public void runStage1Reminders() {
        runStage1RemindersWithFilters(Collections.emptyMap());
    }

    // Exposed public method to trigger stage 1 manually and pass filters.
    // filters will be forwarded to getAgingReportWithMultipleFilters as the filter map.
    public void runStage1RemindersWithFilters(Map<String, Object> filters) {
        logger.info("SLA Stage 1 (manual) job started with filters={}", filters);
        try {
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> stringFilters = filters == null ? Collections.emptyMap() : 
                filters.entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                    ));
            Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 1000);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());

            // Filter rows where user aging >= 5 days (changed from > 5)
            List<Map<String, Object>> stage1Rows = data.stream()
                    .filter(row -> numericDays(row.get("userAgingInDays")) >= 5)
                    .collect(Collectors.toList());

            // Group by pending approver full name (fall back to "Unassigned")
            Map<String, List<Map<String, Object>>> byApprover = stage1Rows.stream()
                    .collect(Collectors.groupingBy(row -> safeString(row.get("pendingApprovers"), "Unassigned")));

            for (Map.Entry<String, List<Map<String, Object>>> entry : byApprover.entrySet()) {
                String approverFullName = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();

                // Resolve approver email using row-level hints (username) if available
                Optional<String> approverEmailOpt = resolveApproverEmail(rows);

                if (approverEmailOpt.isEmpty()) {
                    logger.warn("Stage1 - Approver email not found for approver='{}' -> skipping {} rows", approverFullName, rows.size());
                    continue;
                }

                String approverEmail = approverEmailOpt.get();

                // Build HTML email body using frontend-styled columns
                String rowsPreview = buildFrontendStyledRowsTable(rows);
                String body = constructSlaReminderHtml(approverFullName, rowsPreview, 1);

                String subject = String.format("Reminder: Pending approval requests (User aging > 5 days) - %d request(s)", rows.size());
                emailService.sendEmail(approverEmail, subject, body,null);

                logger.info("Stage1 reminder sent to {} ({} requests)", approverFullName, rows.size());
            }

        } catch (Exception e) {
            logger.error("Error running SLA Stage 1 job", e);
        }
    }


    // Exposed public method to trigger stage 2 manually and pass filters.
    public void runStage2EscalationsWithFilters(Map<String, Object> filters) {
        logger.info("SLA Stage 2 (manual) job started with filters={}", filters);
        try {
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> stringFilters = filters == null ? Collections.emptyMap() : 
                filters.entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                    ));
            Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 10000);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());

            // Filter rows where user aging > 10 days
            List<Map<String, Object>> stage2Rows = data.stream()
                    .filter(row -> numericDays(row.get("userAgingInDays")) > 10)
                    .collect(Collectors.toList());

            // Group rows by departmentId derived from pending approver (we resolve user to get department)
            Map<Long, List<Map<String, Object>>> rowsByDepartment = new HashMap<>();

            for (Map<String, Object> row : stage2Rows) {
                String pendingApproverFull = safeString(row.get("pendingApprovers"), null);
                if (pendingApproverFull == null) {
                    logger.warn("Stage2 - no pending approver in row recordNo={}", row.get("recordNo"));
                    continue;
                }

                Optional<User> pendingUserOpt = findUserByRow(row, pendingApproverFull);
                if (pendingUserOpt.isEmpty()) {
                    logger.warn("Stage2 - cannot resolve pending approver '{}' to user object", pendingApproverFull);
                    continue;
                }
                User pendingUser = pendingUserOpt.get();
                Integer deptId = pendingUser.getDepartmentId();
                if (deptId == null) {
                    logger.warn("Stage2 - pending approver '{}' has no department -> skipping escalation", pendingApproverFull);
                    continue;
                }
                long depLong = deptId.longValue();
                rowsByDepartment.computeIfAbsent(depLong, k -> new ArrayList<>()).add(row);
            }

            // For each department create escalation email to managers + team
            for (Map.Entry<Long, List<Map<String, Object>>> depEntry : rowsByDepartment.entrySet()) {
                Long depId = depEntry.getKey();
                List<Map<String, Object>> depRows = depEntry.getValue();

                // Find users in the department
                List<User> deptUsers = userRepository.findAll().stream()
                        .filter(u -> u.getDepartmentId() != null && u.getDepartmentId().longValue() == depId)
                        .collect(Collectors.toList());

                // Identify managers by userPosition contains "manager" (case-insensitive)
                List<User> managers = deptUsers.stream()
                        .filter(u -> u.getUserPosition() != null && u.getUserPosition().toLowerCase().contains("manager"))
                        .collect(Collectors.toList());

                // Build recipient list: managers first, then approvers in dept (canApprove == true)
                Set<String> recipientEmails = new LinkedHashSet<>();
                if (!managers.isEmpty()) {
                    managers.stream().map(User::getEmailAddress).filter(Objects::nonNull).forEach(recipientEmails::add);
                    deptUsers.stream()
                            .filter(u -> Boolean.TRUE.equals(u.getCanApprove()))
                            .map(User::getEmailAddress)
                            .filter(Objects::nonNull)
                            .forEach(recipientEmails::add);
                } else {
                    // fallback: all department emails
                    deptUsers.stream().map(User::getEmailAddress).filter(Objects::nonNull).forEach(recipientEmails::add);
                }

                if (recipientEmails.isEmpty()) {
                    logger.warn("Stage2 - no recipients found for department {}", depId);
                    continue;
                }

                // Attach full aging report for this department

                // Build HTML email body using frontend-styled columns
                String rowsPreview = buildFrontendStyledRowsTable(depRows);
                String body = constructSlaEscalationHtml(depId, rowsPreview);

                String subject = String.format("Escalation: Pending acceptance approvals (User aging > 10 days) - Dept %d - %d request(s)", depId, depRows.size());

                // join recipients into single to (emailService accepts comma-separated to)
                String toCsv = recipientEmails.stream().collect(Collectors.joining(","));
                emailService.sendEmail(toCsv, subject, body, null);

                logger.info("Stage2 escalation sent to dept {} recipientsCount={} requests={}", depId, recipientEmails.size(), depRows.size());
            }

        } catch (Exception e) {
            logger.error("Error running SLA Stage 2 job", e);
        }
    }

    // Helper: get numeric days safely from the data row value
    private int numericDays(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            try {
                Double d = Double.parseDouble(val.toString());
                return d.intValue();
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    private String safeString(Object o, String defaultVal) {
        return o == null ? defaultVal : o.toString();
    }

    private String sanitizeFilename(String s) {
        return s == null ? "report" : s.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
    }

    /**
     * Resolve approver email from a list of rows for that approver.
     * - If rows contain "pendingApproverUsername", prefer lookup by username
     * - Otherwise attempt to resolve by pendingApprovers (full name)
     */
    private Optional<String> resolveApproverEmail(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return Optional.empty();

        // Try to find a username hint in any row
        for (Map<String, Object> row : rows) {
            Object usernameObj = row.get("pendingApproverUsername");
            if (usernameObj != null) {
                String username = usernameObj.toString();
                try {
                    Optional<User> u = userRepository.findByUsername(username);
                    if (u != null && u.isPresent() && u.get().getEmailAddress() != null) {
                        return Optional.of(u.get().getEmailAddress());
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Fallback: try by full name (pendingApprovers) - pick the first non-null
        String fullName = rows.stream()
                .map(r -> safeString(r.get("pendingApprovers"), ""))
                .filter(s -> s != null && !s.trim().isEmpty() && !"Unassigned".equalsIgnoreCase(s))
                .findFirst()
                .orElse(null);

        if (fullName == null) return Optional.empty();

        Optional<User> byRow = findUserByFullName(fullName);
        return byRow.map(User::getEmailAddress).filter(Objects::nonNull);
    }

    /**
     * Find a user using row metadata: check pendingApproverUsername first then full name.
     */
    private Optional<User> findUserByRow(Map<String, Object> row, String pendingApproverFull) {
        if (row == null) return Optional.empty();

        Object usernameObj = row.get("pendingApproverUsername");
        if (usernameObj != null && !usernameObj.toString().trim().isEmpty()) {
            try {
                Optional<User> byUsername = userRepository.findByUsername(usernameObj.toString());
                if (byUsername != null && byUsername.isPresent()) return byUsername;
            } catch (Throwable ignored) {}
        }
        // fallback to full name lookup
        return findUserByFullName(pendingApproverFull);
    }

    /**
     * Find user by full name. Tries repository method then falls back to scanning all users.
     */
    private Optional<User> findUserByFullName(String fullName) {
        if (fullName == null) return Optional.empty();
        try {
            // if repository exposes findFirstByFullName, use it
            Optional<User> byRepo = userRepository.findFirstByFullName(fullName);
            if (byRepo != null && byRepo.isPresent()) return byRepo;
        } catch (Throwable ignored) { /* method may not exist in repo */ }

        // fallback scan
        return userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && u.getFullName().equalsIgnoreCase(fullName))
                .findFirst();
    }

    // ---------------------------
    // Email HTML builders
    // ---------------------------

    private String constructSlaReminderHtml(String approverFullName, String rowsPreviewHtml, int stage) {
        String approverDisplay = approverFullName == null ? "Approver" : approverFullName;
        String salutation = String.format("<p>Dear %s,</p>", escapeHtml(approverDisplay));
        String requestCount = rowsPreviewHtml == null ? "0" : String.valueOf(countTableRows(rowsPreviewHtml));
        return String.format("""
            <html>
             <head>
              <meta charset="utf-8"/>
              <style>
                body{font-family: 'Segoe UI', Arial, sans-serif; color:#333; margin:0; padding:0;}
                .container{max-width:1050px; margin:18px auto; padding:14px;}
                .header{display:flex; align-items:center; gap:12px; margin-bottom:8px;}
                .title{font-size:16px; color:#2b5311; font-weight:700;}
                .summary{margin:8px 0 14px 0; font-size:13px; color:#222;}
                .summary ul{padding-left:18px; margin:6px 0;}
                .summary li{margin:6px 0;}
                .note{font-size:12px; color:#555; margin:8px 0 12px;}
                table{width:100%; border-collapse:collapse; margin-top:12px; font-size:12px;}
                th, td{border:1px solid #dfeede; padding:7px 8px; vertical-align:top;}
                thead th{background:#74B72E; color:#ffffff; font-weight:700;}
                tbody tr:nth-child(even) td{background:#fbfff9;}
                .small{font-size:11px; color:#666;}
                .footer{margin-top:12px; font-size:11px; color:#9c1b1b;}
                hr{border:none; border-top:1px solid #e6e6e6; margin:14px 0;}
              </style>
             </head>
             <body>
              <div class="container">
                <div class="header">
                  <div>
                    <div class="title">Notification: Pending Acceptance Approval Requests Reminder</div>
                    <div class="small">This is an automated notification from the Acceptance system.</div>
                  </div>
                </div>

                %s

                <!-- Request summary (fill values from the first row or pass separately if available) -->
                <div class="summary">
                  <ul>
                    <li><strong>Request(s):</strong> %s</li>
                    <li><strong>Approver:</strong> %s</li>
                    <li><strong>Note:</strong> These requests have exceeded (user aging &gt; 5 days).</li>
                  </ul>
                </div>

                <div class="note">Please review and action the requests listed below. A full aging report is attached.</div>

                <!-- Rows table (HTML fragment passed in) -->
                %s

                <hr/>

                <div class="footer">Warning: This is an automated email. Please do not reply or forward.</div>
              </div>
             </body>
            </html>
        """, salutation, requestCount, approverDisplay, rowsPreviewHtml == null ? "" : rowsPreviewHtml);
    }

    // Escalation email for managers
    private String constructSlaEscalationHtml(Long departmentId, String rowsPreviewHtml) {
        String depLabel = departmentId == null ? "Department" : "Department " + departmentId;
        String salutation = String.format("<p>Dear %s Team,</p>", escapeHtml(depLabel));
        return String.format("""
            <html>
             <head>
              <meta charset="utf-8"/>
              <style>
                body{font-family: 'Segoe UI', Arial, sans-serif; color:#333; margin:0; padding:0;}
                .container{max-width:1200px; margin:18px auto; padding:14px;}
                .header{display:flex; align-items:center; gap:12px; margin-bottom:8px;}
                .title{font-size:16px; color:#9b2b1b; font-weight:700;}
                .note{font-size:12px; color:#555; margin:8px 0 12px;}
                table{width:100%; border-collapse:collapse; margin-top:12px; font-size:12px;}
                th, td{border:1px solid #e6efe8; padding:7px 8px; vertical-align:top;}
                thead th{background:#74B72E; color:#ffffff; font-weight:700;}
                tbody tr:nth-child(even) td{background:#fbfff9;}
                .footer{margin-top:12px; font-size:11px; color:#9c1b1b;}
                hr{border:none; border-top:1px solid #e6e6e6; margin:14px 0;}
              </style>
             </head>
             <body>
              <div class="container">
                <div class="header">
                  <div>
                    <div class="title">Notification: Pending Acceptance Approvals - %s</div>
                    <div class="small">Requests shown below have exceeded (user aging &gt; 10 days).</div>
                  </div>
                </div>

                %s

                <div class="note">Please coordinate with your approvers for immediate action. A full aging report is attached to this email.</div>

                %s

                <hr/>

                <div class="footer">Warning: This is an automated email. Please do not reply or forward.</div>
              </div>
             </body>
            </html>
        """, depLabel, salutation, rowsPreviewHtml == null ? "" : rowsPreviewHtml);
    }

    /**
     * Improved table builder — same columns as before but tuned to screenshot:
     * - green header (#74B72E)
     * - tighter padding and consistent borders
     * Accepts the grouped rows list and returns the full <table> HTML.
     */
    private String buildFrontendStyledRowsTable(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width:100%; border-collapse:collapse; font-size:12px;\">")
          .append("<thead><tr>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">#</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Request No</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">PO Number</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Project Name</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Acceptance Type</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Status</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Created Date</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Approval Date</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:right\">Request Amount (SAR)</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Location</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Scope of Work</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">In Service Date</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Vendor</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Requested By</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:center\">Remaining Approval Count</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Pending Approver</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Department</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">User Aging</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:center\">User Aging (days)</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:left\">Total Aging</th>")
          .append("<th style=\"border:1px solid #dfeede;padding:6px;text-align:center\">Total Aging (days)</th>")
          .append("</tr></thead><tbody>");

        int idx = 1;
        for (Map<String, Object> row : rows) {
            sb.append("<tr>")
              .append("<td style=\"border:1px solid #eef6ea;padding:6px;vertical-align:top\">").append(idx++).append("</td>")
              .append(cell(row.get("dccId")))
              .append(cell(row.get("poNumber")))
              .append(cell(row.get("projectName")))
              .append(cell(row.get("dccAcceptanceType")))
              .append(cell(row.get("dccStatus")))
              .append(cell(row.get("dccCreatedDate")))
              .append(cell(row.get("dateApproved")))
              .append(cellRight(row.get("requestAmountSAR")))
              .append(cell(row.get("lnLocationName")))
              .append(cell(row.get("lnScopeOfWork")))
              .append(cell(row.get("lnInserviceDate")))
              .append(cell(row.get("vendorName")))
              .append(cell(row.get("requestedBy")))
              .append(cellCenter(row.get("approvalCount")))
              .append(cell(row.get("pendingApprovers")))
              .append(cell(row.get("departmentName")))
              .append(cell(row.get("userAging")))
              .append(cellCenter(row.get("userAgingInDays")))
              .append(cell(row.get("totalAging")))
              .append(cellCenter(row.get("totalAgingInDays")))
              .append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    // small helpers for consistent td output
    private String cell(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;vertical-align:top\">" + escapeHtml(safeString(v, "")) + "</td>";
    }
    private String cellRight(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;text-align:right;vertical-align:top\">" + escapeHtml(safeString(v, "")) + "</td>";
    }
    private String cellCenter(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;text-align:center;vertical-align:top\">" + escapeHtml(safeString(v, "")) + "</td>";
    }

    // primitive HTML escape; replace with a proper util if available
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // counts <tr> in the fragment for display in summary; naive but works for our generated fragments
    private int countTableRows(String tableFragment) {
        if (tableFragment == null) return 0;
        int count = 0;
        String lower = tableFragment.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf("<tr", idx)) != -1) {
            count++;
            idx += 3;
        }
        // subtract header row
        return Math.max(0, count - 1);
    }
}