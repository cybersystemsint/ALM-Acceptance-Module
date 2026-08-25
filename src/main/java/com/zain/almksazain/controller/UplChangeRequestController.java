package com.zain.almksazain.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zain.almksazain.model.UplActionType;
import com.zain.almksazain.model.UplChangeRequest;
import com.zain.almksazain.model.UplChangeRequestDecision;
import com.zain.almksazain.model.UplDecision;
import com.zain.almksazain.services.UplChangeRequestBatchResult;
import com.zain.almksazain.services.UplChangeRequestItem;
import com.zain.almksazain.services.UplChangeRequestService;
import com.zain.almksazain.services.UplValidationException;

/**
 * Four endpoints backing the UPL edit/delete/approval feature: create, list,
 * decide, and detail. Approval levels themselves are configured through the
 * existing Standard Workflow screens (module "Unified Price List", action
 * types Update/Delete) — see UplChangeRequestService for how levels are
 * resolved from tb_Workflows / tb_Workflow_Approval_Levels.
 * See design doc "UPL Edit, Delete & Approval — Design System" (RQ: 2-102025).
 */
@RestController
public class UplChangeRequestController {

    private static final Logger logger = LoggerFactory.getLogger(UplChangeRequestController.class);

    @Autowired
    private UplChangeRequestService service;

    // ============================================================
    // Change requests
    // ============================================================

    @PostMapping(value = "/upl/change-requests", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateChangeRequestsPayload payload) {
        try {
            String batchId = (payload.getItems() != null && payload.getItems().size() > 1)
                    ? UUID.randomUUID().toString()
                    : null;
            UplChangeRequestBatchResult result = service.createChangeRequests(payload.getItems(), payload.getRequestedBy(), batchId);
            Map<String, Object> body = success(result.getCreated());
            body.put("failures", result.getFailures());
            return ResponseEntity.ok(body);
        } catch (UplValidationException ex) {
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Failed to create UPL change request(s)", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    @PostMapping(value = "/upl/change-requests/filter", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filter(@RequestBody FilterChangeRequestsPayload payload) {
        try {
            List<UplChangeRequest> result;
            String view = payload.getView() == null ? "assigned-to-me" : payload.getView();
            switch (view) {
                case "assigned-to-me":
                    result = service.findAssignedToApprover(payload.getUserId());
                    break;
                case "mine":
                    result = service.findMine(payload.getUserId());
                    break;
                case "batch":
                    result = service.findByBatch(payload.getBatchId());
                    break;
                case "pending":
                    result = service.findPending(payload.getChangeType(), payload.getCurrentLevelNo());
                    break;
                default:
                    return ResponseEntity.badRequest().body(error("Unknown view: " + view));
            }
            return ResponseEntity.ok(success(result));
        } catch (Exception ex) {
            logger.error("Failed to filter UPL change requests", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Unexpected error: " + ex.getMessage()));
        }
    }

    @PostMapping(value = "/upl/change-requests/decide", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> decide(@RequestBody DecideChangeRequestsPayload payload) {
        List<UplChangeRequest> decided = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        for (Long id : payload.getIds()) {
            try {
                decided.add(service.decide(id, payload.getDecidedBy(), payload.getDecision(), payload.getComments()));
            } catch (UplValidationException ex) {
                Map<String, String> failure = new HashMap<>();
                failure.put("recordId", String.valueOf(id));
                failure.put("reason", ex.getMessage());
                failures.add(failure);
            } catch (Exception ex) {
                logger.error("Failed to decide UPL change request {}", id, ex);
                Map<String, String> failure = new HashMap<>();
                failure.put("recordId", String.valueOf(id));
                failure.put("reason", "Unexpected error: " + ex.getMessage());
                failures.add(failure);
            }
        }
        Map<String, Object> body = success(decided);
        body.put("failures", failures);
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/upl/change-requests/{recordId}", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long recordId) {
        return service.findById(recordId)
                .map(cr -> {
                    List<UplChangeRequestDecision> decisions = service.findDecisions(recordId);
                    Map<String, Object> body = new HashMap<>();
                    body.put("responseCode", "0");
                    body.put("request", cr);
                    body.put("decisions", decisions);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Change request not found")));
    }

    // ============================================================
    // Response helpers + request payload DTOs
    // ============================================================

    private Map<String, Object> success(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "0");
        body.put("data", data);
        return body;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("responseCode", "1");
        body.put("responseMessage", message);
        return body;
    }

    public static class CreateChangeRequestsPayload {
        private List<UplChangeRequestItem> items;
        private Integer requestedBy;

        public List<UplChangeRequestItem> getItems() { return items; }
        public void setItems(List<UplChangeRequestItem> items) { this.items = items; }
        public Integer getRequestedBy() { return requestedBy; }
        public void setRequestedBy(Integer requestedBy) { this.requestedBy = requestedBy; }
    }

    public static class FilterChangeRequestsPayload {
        private String view; // assigned-to-me | mine | batch | pending
        private Integer userId;
        private String batchId;
        private UplActionType changeType;
        private Integer currentLevelNo;

        public String getView() { return view; }
        public void setView(String view) { this.view = view; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public UplActionType getChangeType() { return changeType; }
        public void setChangeType(UplActionType changeType) { this.changeType = changeType; }
        public Integer getCurrentLevelNo() { return currentLevelNo; }
        public void setCurrentLevelNo(Integer currentLevelNo) { this.currentLevelNo = currentLevelNo; }
    }

    public static class DecideChangeRequestsPayload {
        private List<Long> ids;
        private Integer decidedBy;
        private UplDecision decision;
        private String comments;

        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
        public Integer getDecidedBy() { return decidedBy; }
        public void setDecidedBy(Integer decidedBy) { this.decidedBy = decidedBy; }
        public UplDecision getDecision() { return decision; }
        public void setDecision(UplDecision decision) { this.decision = decision; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
    }

}
