
package com.zain.almksazain.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zain.almksazain.model.User;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.model.departmentsdata;
import com.zain.almksazain.repo.deptsrepo;

@Service
public class SlaNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(SlaNotificationService.class);

    @Autowired private DccPoCombinedService dccPoCombinedService;
    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepository;
    @Autowired private deptsrepo deptsRepo;

    @Scheduled(cron = "0 13 14 * * *")
    public void runStage1Reminders() {
        runStage1RemindersWithFilters(Collections.emptyMap());
    }


    public void runStage1RemindersWithFilters(Map<String, Object> filters) {
        logger.info("SLA Stage 1 (manual) job started with filters={}", filters);
        try {
            Map<String, String> stringFilters = filters == null ? Collections.emptyMap() :
                filters.entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                    ));
            Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 1000);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());

            List<Map<String, Object>> stage1Rows = data.stream()
                    .filter(row -> numericDays(row.get("userAgingInDays")) >= 5)
                    .collect(Collectors.toList());

            logger.debug("Stage1 - total data rows returned={}, stage1 rows after filter={}", data.size(), stage1Rows.size());

            // Group by username (unique). Use "Unassigned" when username is missing.
            Map<String, List<Map<String, Object>>> byApprover = stage1Rows.stream()
                    .collect(Collectors.groupingBy(row -> safeString(row.get("pendingApproverUsername"), "Unassigned")));

            for (Map.Entry<String, List<Map<String, Object>>> entry : byApprover.entrySet()) {
                String approverUsername = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();

                Optional<String> approverEmailOpt = resolveApproverEmail(rows);
                String firstRecordNo = rows.isEmpty() ? "n/a" : String.valueOf(rows.get(0).get("recordNo"));

                if (approverEmailOpt.isEmpty()) {
                    logger.warn("Stage1 - Approver email not found for username='{}' -> skipping {} rows (sample recordNo={})",
                            approverUsername, rows.size(), firstRecordNo);
                    continue;
                }
                String approverEmail = approverEmailOpt.get();

                String rowsPreview = buildFrontendStyledRowsTable(rows);

                String approverDisplay = rows.stream()
                        .map(r -> safeString(r.get("pendingApprovers"), ""))
                        .filter(s -> !s.isBlank() && !"Unassigned".equalsIgnoreCase(s))
                        .findFirst()
                        .orElse( approverUsername == null || approverUsername.equals("Unassigned") ? null : approverUsername );

                String body = constructSlaReminderHtml(approverDisplay, rowsPreview, 1);

                String samplePo = rows.stream().map(r -> safeString(r.get("poNumber"), "")).filter(s -> !s.isEmpty()).findFirst().orElse("");
                String sampleReq = rows.stream().map(r -> safeString(r.get("dccId"), "")).filter(s -> !s.isEmpty()).findFirst().orElse("");
                int sampleAgingDays = rows.stream().map(r -> numericDays(r.get("userAgingInDays"))).filter(d -> d > 0).findFirst().orElse(0);

                String subject = buildStage1Subject(rows.size(), firstRecordNo, samplePo, sampleReq, sampleAgingDays);

                // --- extract department, username and set role = "approver" ---
                String department = rows.stream()
                        .map(r -> safeString(r.get("departmentName"), ""))
                        .filter(s -> !s.isBlank())
                        .findFirst().orElse(null);

                // Prefer pendingApproverUsername; if missing use pendingApprovers (the report may have username there)
                String userName = rows.stream()
                        .map(r -> safeString(r.get("pendingApproverUsername"), ""))
                        .filter(s -> !s.isBlank() && !"Unassigned".equalsIgnoreCase(s))
                        .findFirst()
                        .orElseGet(() -> rows.stream()
                                .map(r -> safeString(r.get("pendingApprovers"), ""))
                                .filter(s -> !s.isBlank() && !"Unassigned".equalsIgnoreCase(s))
                                .findFirst()
                                .orElse(null)
                        );

                String role = "approver";

                logger.info("About to send Stage1 reminder: username='{}' display='{}' email='{}' requests={} subject={} dept={} user={} role={}",
                        approverUsername, approverDisplay, approverEmail, rows.size(), subject, department, userName, role);
                logger.debug("Stage1 email bodyPreview='{}'", rowsPreview.length() > 200 ? rowsPreview.substring(0,200) + "..." : rowsPreview);

                emailService.sendEmail(approverEmail, subject, body, null, department, userName, role);
                logger.info("Stage1 reminder scheduled/sent to {} ({} requests)", approverEmail, rows.size());
            }
        } catch (Exception e) {
            logger.error("Error running SLA Stage 1 job", e);
        }
    }

 
    public void runStage2EscalationsWithFilters(Map<String, Object> filters) {
        logger.info("SLA Stage 2 (escalation) job started with filters={}", filters);
        try {
            Map<String, String> stringFilters = filters == null ? Collections.emptyMap() :
                filters.entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                    ));
            Map<String, Object> response = dccPoCombinedService.getAgingReportWithMultipleFilters(null, stringFilters, 1, 1000);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getOrDefault("data", Collections.emptyList());

            List<Map<String, Object>> stage2Rows = data.stream()
                    .filter(row -> numericDays(row.get("userAgingInDays")) >= 10)
                    .collect(Collectors.toList());

            logger.debug("Stage2 - total data rows returned={}, stage2 rows after filter={}", data.size(), stage2Rows.size());

            Map<String, List<Map<String, Object>>> byDepartment = stage2Rows.stream()
                    .collect(Collectors.groupingBy(row -> safeString(row.get("departmentName"), "Unassigned")));

            for (Map.Entry<String, List<Map<String, Object>>> entry : byDepartment.entrySet()) {
                String departmentName = entry.getKey();
                List<Map<String, Object>> rows = entry.getValue();

                String firstRecordNo = rows.isEmpty() ? "n/a" : String.valueOf(rows.get(0).get("recordNo"));

                // Resolve manager user (prefer explicit managerUsername fields, then department lookup -> user position)
                Optional<User> managerUserOpt = resolveManagerUser(rows, departmentName);

                if (managerUserOpt.isEmpty()) {
                    logger.warn("Stage2 - Manager user not found for department='{}' -> skipping {} rows (sample recordNo={})",
                            departmentName, rows.size(), firstRecordNo);
                    continue;
                }
                User managerUser = managerUserOpt.get();

                if (managerUser.getEmailAddress() == null || managerUser.getEmailAddress().isBlank()) {
                    logger.warn("Stage2 - Manager email missing for user='{}' dept='{}' -> skipping", managerUser.getUsername(), departmentName);
                    continue;
                }
                String managerEmail = managerUser.getEmailAddress();

                String rowsPreview = buildFrontendStyledRowsTable(rows);

                // Use manager full name (prefer fullName, fall back to username)
                String managerDisplayName = managerUser.getFullName() != null && !managerUser.getFullName().isBlank()
                        ? managerUser.getFullName() : managerUser.getUsername();

                String body = constructSlaEscalationHtml(managerDisplayName, rows.size(), rowsPreview); // include manager name, count and note

                String subject = buildStage2Subject(rows.size());

                String userName = managerUser.getUsername();
                String role = "manager";
                String department = departmentName != null && !departmentName.isBlank() ? departmentName : null;

                logger.info("About to send Stage2 escalation: department='{}' manager='{}' email='{}' requests={} subject={} dept={} user={} role={}",
                        departmentName, managerDisplayName, managerEmail, rows.size(), subject, department, userName, role);
                logger.debug("Stage2 email bodyPreview='{}'", rowsPreview.length() > 200 ? rowsPreview.substring(0,200) + "..." : rowsPreview);

                emailService.sendEmail(managerEmail, subject, body, null, department, userName, role);
                logger.info("Stage2 escalation scheduled/sent to {} (dept={}, {} requests)", managerEmail, departmentName, rows.size());
            }
        } catch (Exception e) {
            logger.error("Error running SLA Stage 2 job", e);
        }
    }

    private Optional<String> resolveApproverEmail(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return Optional.empty();
        for (Map<String, Object> row : rows) {
            // First try explicit username field
            Object usernameObj = row.get("pendingApproverUsername");
            String usernameCandidate = null;
            if (usernameObj != null && !usernameObj.toString().isBlank() && !"Unassigned".equalsIgnoreCase(usernameObj.toString())) {
                usernameCandidate = usernameObj.toString();
            } else {
                // If pendingApproverUsername is missing, try pendingApprovers as a username candidate
                Object pendingApproversObj = row.get("pendingApprovers");
                if (pendingApproversObj != null && !pendingApproversObj.toString().isBlank() && !"Unassigned".equalsIgnoreCase(pendingApproversObj.toString())) {
                    usernameCandidate = pendingApproversObj.toString();
                }
            }

            if (usernameCandidate != null) {
                try {
                    Optional<User> u = userRepository.findByUsername(usernameCandidate);
                    if (u != null && u.isPresent() && u.get().getEmailAddress() != null) {
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
                .orElse(null);
        if (fullName == null) return Optional.empty();
        Optional<User> byRow = findUserByFullName(fullName);
        return byRow.map(User::getEmailAddress).filter(Objects::nonNull);
    }


    private Optional<User> resolveManagerUser(List<Map<String, Object>> rows, String departmentName) {
        if (rows == null || rows.isEmpty()) return Optional.empty();

        for (Map<String, Object> row : rows) {
            Object managerUsernameObj = row.get("departmentManagerUsername");
            if (managerUsernameObj == null) managerUsernameObj = row.get("managerUsername");
            if (managerUsernameObj != null) {
                String mUser = managerUsernameObj.toString();
                try {
                    Optional<User> mu = userRepository.findByUsername(mUser);
                    if (mu != null && mu.isPresent()) {
                        return mu;
                    }
                } catch (Throwable ignored) {}
            }
            // try explicit email field as last resort for this row, by matching to a user by email
            Object managerEmailObj = row.get("departmentManagerEmail");
            if (managerEmailObj != null && managerEmailObj.toString().contains("@")) {
                try {
                    Optional<User> uByEmail = userRepository.findFirstByEmailAddress(managerEmailObj.toString());
                    if (uByEmail != null && uByEmail.isPresent()) return uByEmail;
                } catch (Throwable ignored) {}
            }
        }

        // 2) Try department lookup -> find a user with manager-like userPosition in that department
        if (departmentName != null && !departmentName.isBlank()) {
            try {
                departmentsdata dept = deptsRepo.findByDeptName(departmentName);
                if (dept != null) {
                    long deptRecordNo = dept.getRecordNo();
                    // find a user in this department whose userPosition contains "manager" (case-insensitive)
                    Optional<User> managerCandidate = userRepository.findAll().stream()
                            .filter(u -> u.getDepartmentId() != null && u.getDepartmentId().longValue() == deptRecordNo)
                            .filter(u -> u.getUserPosition() != null && u.getUserPosition().toLowerCase().contains("manager"))
                            .findFirst();
                    if (managerCandidate.isPresent()) return managerCandidate;
                }
            } catch (Throwable ignored) {}
        }

        // 3) fallback: try to pick any approver contact for the department
        Optional<String> approverEmail = resolveApproverEmail(rows);
        if (approverEmail.isPresent()) {
            try {
                Optional<User> byEmail = userRepository.findFirstByEmailAddress(approverEmail.get());
                if (byEmail != null && byEmail.isPresent()) return byEmail;
            } catch (Throwable ignored) {}
        }

        return Optional.empty();
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

    // ---------------------------
    // Email HTML builders
    // ---------------------------
private String buildStage1Subject(int count, String recordNo, String poNumber, String requestId, int agingDays) {
    return String.format("SLA Reminder: Action Required on %d request(s)", count);
}

    private String buildStage2Subject(int count) {
        // follow same theme as stage1: include request count
        StringBuilder s = new StringBuilder();
        s.append("SLA Escalation: Management Intervention Required - ").append(count).append(" request(s)");
        return s.toString();
    }

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
        return s == null ? "report" : s.replaceAll("[^a-zA-Z0-9\\-\\*\\.]", "_");
    }

    // ---------------------------
    // HTML builders 
    // ---------------------------
    private String constructSlaReminderHtml(String approverFullName, String rowsPreviewHtml, int stage) {
        String approverDisplay = approverFullName == null ? "Approver" : approverFullName;
        String salutation = "<p style=\"margin:0 0 10px 0;\">Dear " + escapeHtml(approverDisplay) + ",</p>";
        String requestCount = rowsPreviewHtml == null ? "0" : String.valueOf(countTableRows(rowsPreviewHtml));
        StringBuilder sb = new StringBuilder(8192);

        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"/>")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale:1\"/>")
          .append("</head>");

        sb.append("<body style=\"margin:0;padding:0;background:#ffffff;color:#333;font-family:Arial,Helvetica,sans-serif;\">");
        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
          .append("<tr><td align=\"right\" style=\"padding:0;\">");
        sb.append("<table role=\"presentation\" align=\"right\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"width:auto;background:#ffffff;margin:18px 0 18px auto;padding:14px;border-collapse:collapse;\">");

        sb.append("<tr><td style=\"padding:10px 0 6px 0;font-size:13px;color:#222;\">")
          .append(salutation)
          .append("</td></tr>");

        sb.append("<tr><td style=\"padding:0 0 6px 0;font-size:13px;color:#222;\">")
          .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
          .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;width:160px;font-weight:700\">Request(s):</td><td style=\"padding:0 0 6px 0;\">").append(escapeHtml(requestCount)).append("</td></tr>")
          .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Approver:</td><td style=\"padding:0 0 6px 0;\">").append(escapeHtml(approverDisplay)).append("</td></tr>")
          .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Note:</td><td style=\"padding:0 0 6px 0;\">These requests have exceeded (user aging &gt; 5 days).</td></tr>")
          .append("</table>")
          .append("</td></tr>");

        sb.append("<tr><td style=\"padding:8px 0 12px 0;font-size:12px;color:#555;\">Please review and action the requests listed below. A full aging report is attached.</td></tr>");

        if (rowsPreviewHtml != null && !rowsPreviewHtml.isEmpty()) {
            sb.append("<tr><td style=\"padding:6px 0\"><div style=\"overflow:auto;\">")
              .append(rowsPreviewHtml.replaceFirst("<table", "<table dir=\"ltr\""))
              .append("</div></td></tr>");
        }

        sb.append("<tr><td style=\"padding-top:12px;border-top:1px solid #e6e6e6;font-size:11px;color:#9c1b1b;\">Warning: This is an automated email. Please do not reply or forward.</td></tr>");

        sb.append("</table>");
        sb.append("</td></tr></table>");
        sb.append("</body></html>");
        return sb.toString();
    }


private String constructSlaEscalationHtml(String managerFullName, int requestCount, String rowsPreviewHtml) {
    String managerDisplay = managerFullName == null ? "Manager" : managerFullName;
    String salutation = "<p style=\"margin:0 0 10px 0;\">Dear " + escapeHtml(managerDisplay) + ",</p>";
    StringBuilder sb = new StringBuilder(4096);

    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"/>")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale:1\"/></head>")
      .append("<body style=\"margin:0;padding:0;background:#ffffff;color:#333;font-family:Arial,Helvetica,sans-serif;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\">")
      .append("<tr><td align=\"right\" style=\"padding:0;\">")
      .append("<table role=\"presentation\" align=\"right\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
              + "style=\"width:auto;margin:18px 0 18px auto;padding:14px;border-collapse:collapse;\">");

    // 1) SALUTATION: now appended after the header
    sb.append("<tr><td style=\"padding:10px 0 6px 0;font-size:13px;color:#222;\">")
      .append(salutation)
      .append("</td></tr>");

    // 2) HEADER: Request count and Note (appears first)
    sb.append("<tr><td style=\"padding:0 0 6px 0;font-size:13px;color:#222;\">")
      .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"border-collapse:collapse;\">")
      .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;width:160px;font-weight:700\">Request(s):</td>")
      .append("<td style=\"padding:0 0 6px 0;\">").append(escapeHtml(String.valueOf(requestCount))).append("</td></tr>")
      .append("<tr><td style=\"vertical-align:top;padding:0 8px 0 0;font-weight:700\">Note:</td>")
      .append("<td style=\"padding:0 0 6px 0;\">Requests shown below have exceeded (user aging &gt; 10 days).</td></tr>")
      .append("</table></td></tr>");


    // 3) Intro / instructions
    sb.append("<tr><td style=\"padding:0 0 12px 0;font-size:12px;color:#555;\">Please coordinate with your approvers for immediate action. A full aging report is attached to this email.</td></tr>");

    // 4) Rows (table preview)
    if (rowsPreviewHtml != null && !rowsPreviewHtml.isEmpty()) {
        sb.append("<tr><td style=\"padding:6px 0\"><div style=\"overflow:auto;\">")
          .append(rowsPreviewHtml.replaceFirst("<table", "<table dir=\"ltr\""))
          .append("</div></td></tr>");
    }

    // 5) Footer warning
    sb.append("<tr><td style=\"padding-top:12px;border-top:1px solid #e6e6e6;font-size:11px;color:#9c1b1b;\">Warning: This is an automated email. Please do not reply or forward.</td></tr>")
      .append("</table></td></tr></table></body></html>");

    return sb.toString();
}

    private String buildFrontendStyledRowsTable(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();

        sb.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"border-collapse:collapse;table-layout:auto;font-size:12px;word-break:break-word;width:auto;\">");

        sb.append("<thead><tr>");
        String thBase = "style=\"background:#74B72E;color:#ffffff;font-weight:700;padding:8px;border:1px solid #dfeede;text-align:left;white-space:nowrap;min-width:80px;\"";
        sb.append("<th ").append(thBase).append(">#</th>");
        sb.append("<th ").append(thBase).append(">Request No</th>");
        sb.append("<th ").append(thBase).append(">PO Number</th>");
        sb.append("<th ").append(thBase).append(">Project Name</th>");
        sb.append("<th ").append(thBase).append(">Acceptance Type</th>");
        sb.append("<th ").append(thBase).append(">Status</th>");
        sb.append("<th ").append(thBase).append(">Created Date</th>");
        sb.append("<th ").append(thBase).append(">Approval Date</th>");
        sb.append("<th ").append(thBase).append(" style=\"text-align:right;\">Request Amount (SAR)</th>");
        sb.append("<th ").append(thBase).append(">Location</th>");
        sb.append("<th ").append(thBase).append(">Scope of Work</th>");
        sb.append("<th ").append(thBase).append(">In Service Date</th>");
        sb.append("<th ").append(thBase).append(">Vendor</th>");
        sb.append("<th ").append(thBase).append(">Requested By</th>");
        sb.append("<th ").append(thBase).append(">Remaining Approval Count</th>");
        sb.append("<th ").append(thBase).append(">Pending Approver</th>");
        sb.append("<th ").append(thBase).append(">Department</th>");
        sb.append("<th ").append(thBase).append(">User Aging</th>");
        sb.append("<th ").append(thBase).append(">User Aging (days)</th>");
        sb.append("<th ").append(thBase).append(">Total Aging</th>");
        sb.append("<th ").append(thBase).append(">Total Aging (days)</th>");
        sb.append("</tr></thead><tbody>");

        int idx = 1;
        for (Map<String, Object> row : rows) {
            String rowBg = (idx % 2 == 0) ? "background:#fbfff9;" : "background:#ffffff;";
            sb.append("<tr style=\"").append(rowBg).append("\">")
              .append("<td style=\"border:1px solid #eef6ea;padding:6px;vertical-align:top;word-break:break-word;overflow-wrap:break-word;min-width:40px;\">").append(idx++).append("</td>")
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

    private String cell(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;vertical-align:top;word-break:break-word;overflow-wrap:break-word;min-width:100px;\">"
                + escapeHtml(safeString(v, "")) + "</td>";
    }
    private String cellRight(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;text-align:right;vertical-align:top;word-break:break-word;overflow-wrap:break-word;min-width:100px;\">"
                + escapeHtml(safeString(v, "")) + "</td>";
    }
    private String cellCenter(Object v) {
        return "<td style=\"border:1px solid #eef6ea;padding:6px;text-align:center;vertical-align:top;word-break:break-word;overflow-wrap:break-word;min-width:80px;\">"
                + escapeHtml(safeString(v, "")) + "</td>";
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
}