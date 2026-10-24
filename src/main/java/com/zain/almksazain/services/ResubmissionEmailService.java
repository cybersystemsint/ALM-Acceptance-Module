package com.zain.almksazain.services;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The approval workflow emails ALM-Acceptance-Module sends, ported from
 * WorkFlow-Management so they keep the structure they had there
 * (ScopeApprovalService.constructFirstApproversEmail, constructResubmissionEmail
 * and formatEmailSubject; ActionHistoryService for the Action History table):
 * subject, summary, Request Lines and Action History.
 *   - sendFirstApproverEmail: to the first approver of a new acceptance request,
 *     or of a returned one resubmitted (it gets a fresh approval chain).
 *   - sendResubmissionEmail: to the approver who requested more information,
 *     once the vendor resubmits.
 * Data is read with plain SQL so the logic stays line-for-line with the
 * original; keep the two in step if either template changes.
 *
 * Sent through EmailService.sendWorkflowEmail, which posts to the same
 * notification endpoint WorkFlow-Management uses, so it is logged in
 * tb_Email_Logs.
 */
@Service
public class ResubmissionEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ResubmissionEmailService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public ResubmissionEmailService(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    /**
     * Builds and sends the resubmission email. Called after the resubmission has
     * committed; never throws, so a mail problem can't affect the vendor.
     */
    public void sendResubmissionEmail(int approvalRequestRecordNo, int approverUserId) {
        try {
            String[] email = buildResubmissionEmail(approvalRequestRecordNo, approverUserId);
            if (email == null) {
                return;
            }
            emailService.sendWorkflowEmail(email[0], email[1], email[2]);
            logger.info("| Sent resubmission notification email to approver: {}", email[0]);
        } catch (Exception e) {
            logger.error("| Failed to send resubmission email for approval request " + approvalRequestRecordNo
                    + " to approver " + approverUserId + ": " + e, e);
        }
    }

    /**
     * Builds and sends the email to the first approver of an acceptance
     * request's approval chain for scopeRecordNo (the newest approval request
     * of that scope). definesChain adds the note for the Level 1 approver of an
     * approver-defined chain scope (Others). Called after the request has
     * committed; never throws, so a mail problem can't affect the vendor.
     */
    public void sendFirstApproverEmail(int acceptanceRequestRecordNo, int scopeRecordNo, int approverUserId, boolean definesChain) {
        try {
            String[] email = buildFirstApproverEmail(acceptanceRequestRecordNo, scopeRecordNo, approverUserId, definesChain);
            if (email == null) {
                return;
            }
            emailService.sendWorkflowEmail(email[0], email[1], email[2]);
            logger.info("| Sent approval notification email to initial approver: {}", email[0]);
        } catch (Exception e) {
            logger.error("| Failed to send approval notification email for acceptance request " + acceptanceRequestRecordNo
                    + " to approver " + approverUserId + ": " + e, e);
        }
    }

    /** {to, subject, body}, or null when there is nobody to send it to. */
    String[] buildFirstApproverEmail(int acceptanceRequestRecordNo, int scopeRecordNo, int approverUserId, boolean definesChain) {
        List<Header> rows = jdbcTemplate.query(HEADER_SELECT + "WHERE r.acceptanceRequestRecordNo = ? AND r.scope = ? "
                + "ORDER BY r.recordNo DESC LIMIT 1", (rs, i) -> header(rs), acceptanceRequestRecordNo, scopeRecordNo);
        if (rows.isEmpty()) {
            logger.error("| Approval notification email not sent: no approval request for acceptance request {} and scope {}",
                    acceptanceRequestRecordNo, scopeRecordNo);
            return null;
        }
        Header request = rows.get(0);
        UserRow approver = findUserById(approverUserId);
        if (approver == null || approver.email == null || approver.email.isBlank()) {
            logger.warn("| Approval notification email not sent for acceptance request {}: no email address for approver {}",
                    acceptanceRequestRecordNo, approverUserId);
            return null;
        }

        String emailSubject = formatEmailSubject("Action Required: Approval", request);
        List<LineItem> lineItems = findLineItems(request.acceptanceRequestRecordNo);
        List<ActionHistoryRow> actionHistory = getActionHistoryRows(request.acceptanceRequestRecordNo);
        String closing = definesChain
                ? "<p>You will be asked to define the approval chain for this request: choose who approves after you, then save the chain before approving.</p>"
                : "";
        String emailBody = constructApproverEmail(request, approver, lineItems, actionHistory,
                "You have a pending approval request for:", closing);
        return new String[] {approver.email, emailSubject, emailBody};
    }

    /** {to, subject, body}, or null when there is nobody to send it to. */
    String[] buildResubmissionEmail(int approvalRequestRecordNo, int approverUserId) {
        Header request = findHeader(approvalRequestRecordNo);
        if (request == null) {
            logger.error("| Resubmission email not sent: approval request {} not found", approvalRequestRecordNo);
            return null;
        }
        UserRow approver = findUserById(approverUserId);
        if (approver == null || approver.email == null || approver.email.isBlank()) {
            logger.warn("| Resubmission email not sent for acceptance request {}: no email address for approver {}",
                    request.acceptanceRequestRecordNo, approverUserId);
            return null;
        }

        String emailSubject = formatEmailSubject("Action Required: Approval ", request);
        List<LineItem> lineItems = findLineItems(request.acceptanceRequestRecordNo);
        List<ActionHistoryRow> actionHistory = getActionHistoryRows(request.acceptanceRequestRecordNo);
        String emailBody = constructApproverEmail(request, approver, lineItems, actionHistory,
                "The vendor has resubmitted the acceptance request with additional information. It is now ready for your approval:", "");
        return new String[] {approver.email, emailSubject, emailBody};
    }

    // --- subject and body (WorkFlow-Management ScopeApprovalService) ---------

    private String formatEmailSubject(String actionPhrase, Header request) {
        String base = actionPhrase + " Acceptance# " + request.acceptanceRequestRecordNo + "/PO# " + request.poNumber;
        String idPart;

        if ("transmission".equalsIgnoreCase(request.scopeDomain)) {
            // Fetch Link ID from first line item
            List<LineItem> lineItems = findLineItems(request.acceptanceRequestRecordNo);
            if (!lineItems.isEmpty()) {
                String linkId = lineItems.get(0).linkId;
                idPart = (linkId != null && !linkId.isEmpty()) ? "/Link ID-" + linkId : "/Link ID-N/A";
            } else {
                idPart = "/Link ID-N/A";
            }
        } else {
            // Fetch Site ID (locationName)
            String siteId = fetchSiteLocation(request.acceptanceRequestRecordNo);
            idPart = (siteId != null && !siteId.isEmpty()) ? "/Site ID-" + siteId : "/Site ID-N/A";
        }

        return base + idPart;
    }

    /**
     * constructFirstApproversEmail / constructResubmissionEmail: the two differ
     * only in their opening line. extraParagraphs (HTML, may be empty) goes
     * just before the closing request to approve.
     */
    private String constructApproverEmail(Header request, UserRow approver, List<LineItem> lineItems,
            List<ActionHistoryRow> actionHistory, String openingLine, String extraParagraphs) {
        StringBuilder historyTable = new StringBuilder();
        StringBuilder lineItemsTable = new StringBuilder();

        // Build Action History table
        int seqNo = 1;
        for (ActionHistoryRow row : actionHistory) {
            historyTable.append(String.format("""
            <tr>
                <td>%d</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
            </tr>
        """,
                seqNo++,
                safeValue(row.action),
                safeValue(row.fromUser),
                safeValue(row.toUser),
                safeValue(row.approverName),
                safeValue(row.actionDate),
                safeValue(row.comments)
            ));
        }

        // Build Line Items table
        for (int i = 0; i < lineItems.size(); i++) {
            LineItem item = lineItems.get(i);
            lineItemsTable.append(String.format("""
            <tr>
                <td>%d</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
            </tr>
        """,
                i + 1,
                safeValue(item.lineNumber),
                safeValue(item.uplLineNumber),
                safeValue(item.itemCode),
                safeValue(item.uplItemDescription),
                safeValue(item.uoM),
                formatDoubleValue(item.deliveredQty),
                safeValue(item.serialNumber),
                safeValue(item.locationName),
                safeValue(item.scopeOfWork),
                safeValue(item.remarks)
            ));
        }

        UserRow requestedByUser = findUserByUsername(request.requestedBy);
        String requestedByFullName = requestedByUser != null ? requestedByUser.fullName : request.requestedBy;

        // Markup kept exactly as WorkFlow-Management sends it (including the
        // Request Lines header row without an opening <tr>).
        return String.format("""
      <html>
        <head>
            <style>
                body { font-family: "Segoe UI", Arial, sans-serif; }
                h3 { color: #333; }
                table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
                th, td { border: 1px solid #74B72E; padding: 6px; text-align: left; font-size: 12px; }
                th { background-color: #74B72E; color: white; }
                p { font-size: 12px; }
                .footer { margin-top: 10px; font-size: 10px; color: #c30010; }
            </style>
        </head>
        <body>
            <p>Dear %s,</p>
            <p>%s</p>
            <ul>
                <li><strong>Request Number:</strong> %d</li>
                <li><strong>Vendor Name:</strong> %s</li>
                <li><strong>Requested By:</strong> %s</li>
                <li><strong>Scope:</strong> %s</li>
                <li><strong>PO Number:</strong> %s</li>
            </ul>
            <hr/>
            <h3>Request Lines:</h3>
            <table>
                    <th>#</th>
                    <th>PO Line #</th>
                    <th>UPL Line #</th>
                    <th>UPL ItemCode</th>
                    <th>Description</th>
                    <th>UOM</th>
                    <th>Delivery QTY</th>
                    <th>Serial</th>
                    <th>Location</th>
                    <th>Scope Of Work</th>
                    <th>Remarks</th>
                </tr>
                %s
            </table>
            <h3>Action History:</h3>
            <table>
                    <tr>
                    <th>#</th>
                    <th>Action</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Approver Name</th>
                    <th>Action Date</th>
                    <th>Comments</th>
                </tr>
                %s
            </table>
            <hr/>
            %s<p>Please review and approve at your earliest convenience.</p>
            <p class="footer">Warning: This is an automated email. Please do not reply or forward.</p>
        </body>
        </html>
        """,
            approver.fullName,
            openingLine,
            request.acceptanceRequestRecordNo,
            request.vendorName,
            requestedByFullName,
            (request.scopeName != null ? request.scopeName : "-"),
            request.poNumber,
            lineItemsTable,
            historyTable,
            extraParagraphs
        );
    }

    private static String safeValue(String value) {
        return value != null ? value : "";
    }

    private static String formatDoubleValue(Double value) {
        return (value == null || value == 0.0) ? "" : String.format("%.8f", value);
    }

    // --- Action History (WorkFlow-Management ActionHistoryService) ----------

    List<ActionHistoryRow> getActionHistoryRows(int acceptanceRequestRecordNo) {
        List<ActionHistoryRow> actionHistoryRows = new ArrayList<>();
        List<Header> approvalRequests = findHeaders(acceptanceRequestRecordNo);
        if (approvalRequests.isEmpty()) {
            actionHistoryRows.add(createPlaceholderRow());
            return actionHistoryRows;
        }
        List<Header> returnedRequests = byStatus(approvalRequests, "returned");
        List<Header> pendingRequests = byStatus(approvalRequests, "pending");
        List<Header> approvedRequests = byStatus(approvalRequests, "approved");
        List<Header> rejectedRequests = byStatus(approvalRequests, "rejected");
        List<Header> requestInfoRequests = byStatus(approvalRequests, "request-info");

        // Returned first, then the (oldest) pending one, rejected, request-info
        List<Header> orderedRequests = new ArrayList<>(returnedRequests);
        if (!pendingRequests.isEmpty()) {
            orderedRequests.add(pendingRequests.get(0));
        }
        orderedRequests.addAll(rejectedRequests);
        orderedRequests.addAll(requestInfoRequests);

        int seqNo = 1;
        for (Header request : orderedRequests) {
            List<Task> approvals = findTasks(request.recordNo);
            if (approvals.isEmpty()) {
                actionHistoryRows.add(createPlaceholderRow());
                continue;
            }

            String requestedBy = request.requestedBy != null ? request.requestedBy : "-";

            // 1. Group by approvalLevelId and sort each group by approvalId ascending
            Map<Integer, List<Task>> groupedByLevel = approvals.stream()
                    .collect(Collectors.groupingBy(a -> a.approvalLevelId));

            List<List<ActionHistoryRow>> sortedGroups = new ArrayList<>();
            for (Map.Entry<Integer, List<Task>> entry : groupedByLevel.entrySet()) {
                List<Task> group = entry.getValue();
                group.sort(Comparator.comparingInt(t -> t.approvalId));
                ActionHistoryRow previousDto = null;
                List<ActionHistoryRow> dtos = new ArrayList<>();
                for (Task approval : group) {
                    ActionHistoryRow dto = buildDelegationRelatedDTO(approval, approvals, previousDto);
                    dtos.add(dto);
                    previousDto = dto;
                }
                sortedGroups.add(dtos);
            }

            // 2. Sort groups by the lowest approvalId in each group
            sortedGroups.sort((g1, g2) -> {
                int minId1 = g1.stream().mapToInt(r -> r.approvalId).min().orElse(Integer.MAX_VALUE);
                int minId2 = g2.stream().mapToInt(r -> r.approvalId).min().orElse(Integer.MAX_VALUE);
                return Integer.compare(minId1, minId2);
            });

            // 3. Flatten sorted groups into orderedRows
            List<ActionHistoryRow> orderedRows = new ArrayList<>();
            for (List<ActionHistoryRow> group : sortedGroups) {
                orderedRows.addAll(group);
            }

            // 4. Remove Delegated actions if Pending exists for the same approvalLevelId
            Map<Integer, Boolean> hasPendingForLevel = new HashMap<>();
            for (ActionHistoryRow row : orderedRows) {
                if ("Pending".equalsIgnoreCase(row.action) && row.approvalLevelId != null) {
                    hasPendingForLevel.put(row.approvalLevelId, true);
                }
            }
            List<ActionHistoryRow> filteredRows = orderedRows.stream()
                    .filter(row -> {
                        if ("Delegated".equalsIgnoreCase(row.action) && row.approvalLevelId != null) {
                            return !hasPendingForLevel.getOrDefault(row.approvalLevelId, false);
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // Remove Pending actions if request is Returned or Rejected
            boolean isReturnedOrRejected = "returned".equalsIgnoreCase(request.status) || "rejected".equalsIgnoreCase(request.status);
            if (isReturnedOrRejected) {
                filteredRows = filteredRows.stream()
                        .filter(row -> !"Pending".equalsIgnoreCase(row.action))
                        .collect(Collectors.toList());
            }

            // For Pending rows, set the To user from a Delegated row if one existed
            Map<Integer, String> delegatedToUserMap = orderedRows.stream()
                    .filter(row -> "Delegated".equalsIgnoreCase(row.action) && row.approvalLevelId != null)
                    .collect(Collectors.toMap(r -> r.approvalLevelId, r -> r.toUser, (a, b) -> a));

            for (ActionHistoryRow row : filteredRows) {
                if ("Pending".equalsIgnoreCase(row.action) && row.approvalLevelId != null) {
                    String delegatedToUser = delegatedToUserMap.get(row.approvalLevelId);
                    if (delegatedToUser != null && !"-".equals(delegatedToUser)) {
                        row.toUser = delegatedToUser;
                    }
                }
            }

            // Only the first Ready For Approval stays; fill From users and seqNo
            boolean readyForApprovalSeen = false;
            for (int i = 0; i < filteredRows.size(); i++) {
                ActionHistoryRow curr = filteredRows.get(i);
                if ("Ready For Approval".equalsIgnoreCase(curr.action)) {
                    if (!readyForApprovalSeen) {
                        readyForApprovalSeen = true;
                    } else {
                        curr.action = "Pending";
                        curr.comments = "_";
                    }
                }

                curr.seqNo = seqNo++;
                if (i == 0) {
                    curr.fromUser = getRequestedByUsername(requestedBy);
                    if ("Delegated".equalsIgnoreCase(curr.action)) {
                        curr.toUser = delegatorOrApprover(curr);
                    }
                } else if ("Request Info".equalsIgnoreCase(filteredRows.get(i - 1).action)
                        && Arrays.asList("Ready For Approval", "Approved", "Returned", "Rejected", "Reassigned",
                                "Delegated", "Request Info", "Canceled", "Expired").stream()
                                .anyMatch(a -> a.equalsIgnoreCase(curr.action))) {
                    curr.fromUser = getRequestedByUsername(requestedBy);
                    if ("Delegated".equalsIgnoreCase(curr.action)) {
                        curr.toUser = delegatorOrApprover(curr);
                    }
                } else if ("Delegated".equalsIgnoreCase(curr.action)) {
                    curr.toUser = curr.approverName != null ? curr.approverName : "-";
                    curr.fromUser = filteredRows.get(i - 1).toUser;
                } else if ("Pending".equalsIgnoreCase(curr.action)) {
                    ActionHistoryRow delegatedRow = filteredRows.stream()
                            .filter(row -> row.approvalLevelId != null
                                    && row.approvalLevelId.equals(curr.approvalLevelId)
                                    && "Delegated".equalsIgnoreCase(row.action))
                            .findFirst().orElse(null);
                    if (delegatedRow != null && delegatedRow.approverName != null && !"-".equals(delegatedRow.approverName)) {
                        curr.fromUser = delegatedRow.approverName;
                    } else {
                        curr.fromUser = filteredRows.get(i - 1).toUser;
                    }
                } else {
                    curr.fromUser = filteredRows.get(i - 1).toUser;
                }
                actionHistoryRows.add(curr);

                if ("Returned".equalsIgnoreCase(curr.action) || "Rejected".equalsIgnoreCase(curr.action)) {
                    break; // Stop adding further actions for this approval request
                }
            }
        }

        // Approved requests: every task, groups ordered by their last action
        for (Header request : approvedRequests) {
            List<Task> approvals = findTasks(request.recordNo);
            if (approvals.isEmpty()) {
                actionHistoryRows.add(createPlaceholderRow());
                continue;
            }

            String requestedBy = request.requestedBy != null ? request.requestedBy : "-";

            Map<Integer, List<Task>> grouped = approvals.stream()
                    .collect(Collectors.groupingBy(a -> a.approvalLevelId));

            List<List<Task>> sortedGroups = new ArrayList<>();
            for (List<Task> group : grouped.values()) {
                group.sort(Comparator.comparingInt(t -> t.approvalId));
                sortedGroups.add(group);
            }

            // Last element's approvedDate (nulls last), then last element's approvalId
            sortedGroups.sort(Comparator
                    .comparing((List<Task> g) -> g.isEmpty() ? null : g.get(g.size() - 1).approvedDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing((List<Task> g) -> g.isEmpty() ? Integer.MAX_VALUE : g.get(g.size() - 1).approvalId,
                            Comparator.naturalOrder()));

            List<Task> sortedApprovals = new ArrayList<>();
            for (List<Task> group : sortedGroups) {
                sortedApprovals.addAll(group);
            }

            ActionHistoryRow previousDto = null;
            for (int i = 0; i < sortedApprovals.size(); i++) {
                Task approval = sortedApprovals.get(i);
                ActionHistoryRow dto = buildDelegationRelatedDTO(approval, approvals, previousDto);
                dto.seqNo = seqNo++;

                if (i == 0) {
                    dto.fromUser = getRequestedByUsername(requestedBy);
                } else if (previousDto != null && "Request Info".equalsIgnoreCase(previousDto.action)) {
                    dto.fromUser = getRequestedByUsername(requestedBy);
                } else if (previousDto != null) {
                    dto.fromUser = previousDto.toUser;
                }

                actionHistoryRows.add(dto);
                previousDto = dto;
            }
        }

        // Closing row, unless the last action is Returned or Rejected
        if (!actionHistoryRows.isEmpty()
                && !"Returned".equalsIgnoreCase(actionHistoryRows.get(actionHistoryRows.size() - 1).action)
                && !"Rejected".equalsIgnoreCase(actionHistoryRows.get(actionHistoryRows.size() - 1).action)) {
            ActionHistoryRow mainApprovalRow = buildMainApprovalRow(approvalRequests, actionHistoryRows);
            if (mainApprovalRow != null) {
                mainApprovalRow.seqNo = actionHistoryRows.size() + 1;
                actionHistoryRows.add(mainApprovalRow);
            }
        }

        return actionHistoryRows;
    }

    // WorkFlow-Management never fills the row's delegationId, so this always
    // falls back to the approver name; kept as is so the output matches.
    private String delegatorOrApprover(ActionHistoryRow row) {
        String delegator = null;
        if (row.delegationId != null) {
            delegator = findDelegation(row.delegationId.intValue()).map(d -> d.delegatorFullName).orElse(null);
        }
        return (delegator != null && !delegator.equals("Unknown Delegator"))
                ? delegator
                : (row.approverName != null ? row.approverName : "-");
    }

    private ActionHistoryRow buildDelegationRelatedDTO(Task approval, List<Task> approvalsInRequest, ActionHistoryRow previousDto) {
        ActionHistoryRow dto = new ActionHistoryRow();
        dto.approvalId = approval.approvalId;
        dto.approvalLevelId = approval.approvalLevelId;

        String status = approval.status != null ? approval.status.toLowerCase() : "";
        String approvalStatus = approval.approvalStatus != null ? approval.approvalStatus.toLowerCase() : "";
        String action = getActionValue(status, approvalStatus, approval.delegationStatus);
        dto.action = action;

        // toUser is always approverName
        dto.toUser = approval.approverName != null ? approval.approverName : "-";

        // fromUser is previous record's toUser
        if (previousDto != null) {
            dto.fromUser = previousDto.toUser != null ? previousDto.toUser : "-";
        } else {
            dto.fromUser = "-";
        }

        if ("Canceled".equalsIgnoreCase(action) || "Expired".equalsIgnoreCase(action)) {
            dto.actionDate = format(approval.approvedDate);
        } else if ("Delegated".equalsIgnoreCase(action)) {
            Delegation delegation = approval.delegationId != null
                    ? findDelegation(approval.delegationId.intValue()).orElse(null)
                    : null;

            // The next approval within the same request (approvalId > current approvalId)
            LocalDateTime nextApprovalApprovedDate = approvalsInRequest.stream()
                    .filter(a -> a.approvalId > approval.approvalId)
                    .min(Comparator.comparingInt(a -> a.approvalId))
                    .map(a -> a.approvedDate)
                    .orElse(null);

            if (delegation != null && delegation.startDateTime != null) {
                LocalDateTime delegationStart = delegation.startDateTime;
                if (nextApprovalApprovedDate != null && delegationStart.isAfter(nextApprovalApprovedDate)) {
                    dto.actionDate = format(approval.approvedDate);
                } else {
                    dto.actionDate = delegationStart.format(DATE_TIME);
                }
            } else {
                dto.actionDate = format(approval.approvedDate);
            }
        } else {
            dto.actionDate = format(approval.approvedDate);
        }

        dto.comments = approval.comments != null ? approval.comments : "-";
        dto.approverName = approval.approvedByUsername != null ? approval.approvedByUsername : "-";
        return dto;
    }

    private static ActionHistoryRow createPlaceholderRow() {
        ActionHistoryRow placeholder = new ActionHistoryRow();
        placeholder.seqNo = 1;
        placeholder.actionDate = "-";
        placeholder.action = "No approval request found";
        placeholder.fromUser = "-";
        placeholder.toUser = "-";
        placeholder.approverName = "-";
        placeholder.comments = "-";
        return placeholder;
    }

    private static String getActionValue(String status, String approvalStatus, String delegationStatus) {
        String as = approvalStatus != null ? approvalStatus.toLowerCase() : "";
        String ds = delegationStatus != null ? delegationStatus.toLowerCase() : "";

        if ((("canceled".equals(as) || "returned".equals(as)) && "delegated".equals(status) && ds.startsWith("delegation-canceled"))
                || (("canceled".equals(as) || "returned".equals(as)) && "reassigned".equals(status) && ds.startsWith("delegation-reassigned-canceled"))) {
            return "Canceled";
        }
        if ((("expired".equals(as) || "returned".equals(as)) && "delegated".equals(status) && ds.startsWith("delegation-expired"))
                || (("expired".equals(as) || "returned".equals(as)) && "reassigned".equals(status) && ds.startsWith("delegation-reassigned-expired"))) {
            return "Expired";
        }
        if ("expired".equals(as) && ds.contains("-expired")) {
            return "Expired";
        }
        if ("canceled".equals(as) && ds.contains("-canceled")) {
            return "Canceled";
        }

        if ("request-info".equals(status) && "returned".equals(as)) return "Request Info";
        if ("request-info".equals(status) && "rejected".equals(as)) return "Request Info";
        if (("pending".equals(status) && "request-info".equals(as))
                || ("request-info".equals(status) && "request-info".equals(as))) return "Request Info";
        if ("pending".equals(status) && "readyforapproval".equals(as)) return "Ready For Approval";
        if ("pending".equals(status)) return "Pending";
        if ("approved".equals(status)) return "Approved";
        if ("reassigned".equals(status)) return "Reassigned";
        if ("delegated".equals(status)) return "Delegated";
        if ("returned".equals(status)) return "Returned";
        if ("rejected".equals(status)) return "Rejected";
        if ("expired".equals(status)) return "Expired";

        return "-";
    }

    private ActionHistoryRow buildMainApprovalRow(List<Header> approvalRequests, List<ActionHistoryRow> historyRows) {
        String lastToUser = historyRows.isEmpty() ? "-" : historyRows.get(historyRows.size() - 1).toUser;

        // 1. Approved with a received receipt
        for (Header request : approvalRequests) {
            if (!"approved".equalsIgnoreCase(request.status)) continue;
            Receipt receipt = findReceipt(request.recordNo);
            if (receipt != null && "received".equalsIgnoreCase(receipt.approvalStatus)) {
                ActionHistoryRow dto = new ActionHistoryRow();
                dto.seqNo = historyRows.size() + 1;
                dto.action = "Approved-received";
                dto.fromUser = lastToUser;
                String approvedBy = receipt.approvedByUsername != null ? receipt.approvedByUsername : "-";
                dto.toUser = approvedBy;
                dto.approverName = approvedBy;
                dto.actionDate = format(receipt.approvedDate);
                dto.comments = receipt.comments != null ? receipt.comments : "-";
                return dto;
            }
        }

        // 2. Approved but not received (or no receipt yet)
        for (Header request : approvalRequests) {
            if (!"approved".equalsIgnoreCase(request.status)) continue;
            Receipt receipt = findReceipt(request.recordNo);
            if (receipt == null || !"received".equalsIgnoreCase(receipt.approvalStatus)) {
                return pendingFinalReceiptRow(historyRows, lastToUser);
            }
        }

        // 3. Rejected with a rejected receipt
        for (Header request : approvalRequests) {
            if (!"rejected".equalsIgnoreCase(request.status)) continue;
            Receipt receipt = findReceipt(request.recordNo);
            if (receipt != null && "rejected".equalsIgnoreCase(receipt.approvalStatus)) {
                ActionHistoryRow dto = new ActionHistoryRow();
                dto.seqNo = historyRows.size() + 1;
                dto.action = "Rejected";
                dto.fromUser = lastToUser;
                String rejectedBy = receipt.approvedByUsername != null ? receipt.approvedByUsername : "-";
                dto.toUser = rejectedBy;
                dto.approverName = rejectedBy;
                dto.actionDate = format(receipt.approvedDate);
                dto.comments = receipt.comments != null ? receipt.comments : "-";
                return dto;
            }
        }

        // 4. Still waiting: pending, request-info or returned
        List<String> waitingStatuses = Arrays.asList("pending", "request-info", "returned");
        boolean waiting = approvalRequests.stream()
                .anyMatch(r -> r.status != null && waitingStatuses.contains(r.status.toLowerCase()));
        return waiting ? pendingFinalReceiptRow(historyRows, lastToUser) : null;
    }

    private static ActionHistoryRow pendingFinalReceiptRow(List<ActionHistoryRow> historyRows, String lastToUser) {
        ActionHistoryRow dto = new ActionHistoryRow();
        dto.seqNo = historyRows.size() + 1;
        dto.action = "Pending final receipt";
        dto.fromUser = lastToUser;
        dto.toUser = "workflow-system";
        dto.actionDate = "-";
        dto.comments = "-";
        dto.approverName = "-";
        return dto;
    }

    // WorkFlow-Management's getRequestedByFullName: despite the name it returns the username.
    private String getRequestedByUsername(String requestedBy) {
        UserRow user = findUserByUsername(requestedBy);
        return user != null ? user.username : requestedBy;
    }

    private static List<Header> byStatus(List<Header> requests, String status) {
        return requests.stream()
                .filter(r -> status.equalsIgnoreCase(r.status))
                .sorted(Comparator.comparing(r -> r.recordDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private static String format(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME) : "-";
    }

    // --- data ----------------------------------------------------------------

    private static final String HEADER_SELECT =
            "SELECT r.recordNo, r.recordDateTime, r.acceptanceRequestRecordNo, r.poNumber, r.vendorName, r.requestedBy, r.status, "
            + "s.scope AS scopeName, s.domain AS scopeDomain "
            + "FROM tb_Category_Approval_Requests r LEFT JOIN tb_Scope s ON s.recordNo = r.scope ";

    private Header findHeader(int recordNo) {
        List<Header> rows = jdbcTemplate.query(HEADER_SELECT + "WHERE r.recordNo = ?", (rs, i) -> header(rs), recordNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Header> findHeaders(int acceptanceRequestRecordNo) {
        return jdbcTemplate.query(HEADER_SELECT + "WHERE r.acceptanceRequestRecordNo = ? ORDER BY r.recordNo",
                (rs, i) -> header(rs), acceptanceRequestRecordNo);
    }

    private static Header header(java.sql.ResultSet rs) throws java.sql.SQLException {
        Header h = new Header();
        h.recordNo = rs.getInt("recordNo");
        h.recordDateTime = toLocalDateTime(rs.getTimestamp("recordDateTime"));
        h.acceptanceRequestRecordNo = rs.getInt("acceptanceRequestRecordNo");
        h.poNumber = rs.getString("poNumber");
        h.vendorName = rs.getString("vendorName");
        h.requestedBy = rs.getString("requestedBy");
        h.status = rs.getString("status");
        h.scopeName = rs.getString("scopeName");
        h.scopeDomain = rs.getString("scopeDomain");
        return h;
    }

    private List<Task> findTasks(int approvalRequestRecordNo) {
        return jdbcTemplate.query(
                "SELECT a.approvalId, a.approvalLevelId, a.status, a.approvalStatus, a.delegationStatus, a.delegationId, "
                + "a.approverName, a.approvedDate, a.comments, u.username AS approvedByUsername "
                + "FROM tb_Category_Approvals a LEFT JOIN tb_SystemUsers u ON u.userid = a.approvedBy "
                + "WHERE a.approvalRecordId = ? ORDER BY a.approvalId",
                (rs, i) -> {
                    Task t = new Task();
                    t.approvalId = rs.getInt("approvalId");
                    t.approvalLevelId = rs.getInt("approvalLevelId");
                    t.status = rs.getString("status");
                    t.approvalStatus = rs.getString("approvalStatus");
                    t.delegationStatus = rs.getString("delegationStatus");
                    long delegationId = rs.getLong("delegationId");
                    t.delegationId = rs.wasNull() ? null : delegationId;
                    t.approverName = rs.getString("approverName");
                    t.approvedDate = toLocalDateTime(rs.getTimestamp("approvedDate"));
                    t.comments = rs.getString("comments");
                    t.approvedByUsername = rs.getString("approvedByUsername");
                    return t;
                },
                approvalRequestRecordNo);
    }

    private Optional<Delegation> findDelegation(int recordNo) {
        List<Delegation> rows = jdbcTemplate.query(
                "SELECT d.startDateTime, u.fullName AS delegatorFullName FROM tb_Delegation d "
                + "LEFT JOIN tb_SystemUsers u ON u.userid = d.delegatorId WHERE d.recordNo = ?",
                (rs, i) -> {
                    Delegation d = new Delegation();
                    d.startDateTime = toLocalDateTime(rs.getTimestamp("startDateTime"));
                    d.delegatorFullName = rs.getString("delegatorFullName");
                    return d;
                },
                recordNo);
        return rows.stream().findFirst();
    }

    private Receipt findReceipt(int approvalRequestRecordNo) {
        List<Receipt> rows = jdbcTemplate.query(
                "SELECT rc.approvalStatus, rc.approvedDate, rc.comments, u.username AS approvedByUsername "
                + "FROM tb_AcceptanceRequest_Receipt rc LEFT JOIN tb_SystemUsers u ON u.userid = rc.approvedBy "
                + "WHERE rc.categoryApprovalRequestId = ? ORDER BY rc.recordNo DESC LIMIT 1",
                (rs, i) -> {
                    Receipt r = new Receipt();
                    r.approvalStatus = rs.getString("approvalStatus");
                    r.approvedDate = toLocalDateTime(rs.getTimestamp("approvedDate"));
                    r.comments = rs.getString("comments");
                    r.approvedByUsername = rs.getString("approvedByUsername");
                    return r;
                },
                approvalRequestRecordNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<LineItem> findLineItems(int acceptanceRequestRecordNo) {
        return jdbcTemplate.query(
                "SELECT * FROM tb_DCC_LN WHERE dccId = (SELECT recordNo FROM tb_DCC WHERE recordNo = ?) ORDER BY recordNo",
                (rs, i) -> {
                    LineItem l = new LineItem();
                    l.lineNumber = rs.getString("lineNumber");
                    l.uplLineNumber = rs.getString("uplLineNumber");
                    l.itemCode = rs.getString("itemCode");
                    l.uplItemDescription = rs.getString("uplItemDescription");
                    l.uoM = rs.getString("UoM");
                    l.deliveredQty = rs.getDouble("deliveredQty");
                    l.serialNumber = rs.getString("serialNumber");
                    l.locationName = rs.getString("locationName");
                    l.scopeOfWork = rs.getString("scopeOfWork");
                    l.remarks = rs.getString("remarks");
                    l.linkId = rs.getString("linkId");
                    return l;
                },
                acceptanceRequestRecordNo);
    }

    private String fetchSiteLocation(int acceptanceRequestRecordNo) {
        List<String> rows = jdbcTemplate.query(
                "SELECT ln.locationName FROM tb_DCC_LN ln WHERE ln.dccId = (SELECT dcc.recordNo FROM tb_DCC dcc WHERE dcc.recordNo = ?) LIMIT 1",
                (rs, i) -> rs.getString("locationName"), acceptanceRequestRecordNo);
        return rows.isEmpty() ? "N/A" : rows.get(0);
    }

    private UserRow findUserById(int userId) {
        List<UserRow> rows = jdbcTemplate.query(
                "SELECT username, fullName, emailaddress FROM tb_SystemUsers WHERE userid = ?", (rs, i) -> user(rs), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UserRow findUserByUsername(String username) {
        if (username == null) {
            return null;
        }
        List<UserRow> rows = jdbcTemplate.query(
                "SELECT username, fullName, emailaddress FROM tb_SystemUsers WHERE username = ? LIMIT 1", (rs, i) -> user(rs), username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static UserRow user(java.sql.ResultSet rs) throws java.sql.SQLException {
        UserRow u = new UserRow();
        u.username = rs.getString("username");
        u.fullName = rs.getString("fullName");
        u.email = rs.getString("emailaddress");
        return u;
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    // --- rows ----------------------------------------------------------------

    private static class Header {
        int recordNo;
        LocalDateTime recordDateTime;
        int acceptanceRequestRecordNo;
        String poNumber;
        String vendorName;
        String requestedBy;
        String status;
        String scopeName;
        String scopeDomain;
    }

    private static class Task {
        int approvalId;
        Integer approvalLevelId;
        String status;
        String approvalStatus;
        String delegationStatus;
        Long delegationId;
        String approverName;
        LocalDateTime approvedDate;
        String comments;
        String approvedByUsername;
    }

    private static class UserRow {
        String username;
        String fullName;
        String email;
    }

    private static class Delegation {
        LocalDateTime startDateTime;
        String delegatorFullName;
    }

    private static class Receipt {
        String approvalStatus;
        LocalDateTime approvedDate;
        String comments;
        String approvedByUsername;
    }

    private static class LineItem {
        String lineNumber;
        String uplLineNumber;
        String itemCode;
        String uplItemDescription;
        String uoM;
        Double deliveredQty;
        String serialNumber;
        String locationName;
        String scopeOfWork;
        String remarks;
        String linkId;
    }

    static class ActionHistoryRow {
        Integer seqNo;
        String actionDate;
        String action;
        String fromUser;
        String toUser;
        String approverName;
        String comments;
        Integer approvalId;
        Integer approvalLevelId;
        Long delegationId; // never set, as in WorkFlow-Management
    }
}
