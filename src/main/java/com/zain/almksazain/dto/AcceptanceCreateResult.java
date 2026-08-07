package com.zain.almksazain.dto;

import java.util.ArrayList;
import java.util.List;

public class AcceptanceCreateResult {

    private final long dccId;
    private final List<WorkflowInitResult> workflowInitResults = new ArrayList<>();

    public AcceptanceCreateResult(long dccId) {
        this.dccId = dccId;
    }

    public long getDccId() {
        return dccId;
    }

    public List<WorkflowInitResult> getWorkflowInitResults() {
        return workflowInitResults;
    }

    public void addWorkflowInitResult(WorkflowInitResult result) {
        if (result != null) {
            workflowInitResults.add(result);
        }
    }
}
