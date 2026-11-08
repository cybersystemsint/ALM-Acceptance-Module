package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only mirror of WorkFlow-Management's tb_Workflow_Approval_Levels,
 * configured through Configurations > Approval Levels for a given workflow.
 * approvalNumber is that screen's level number (1-based); WorkflowController
 * enforces it's contiguous 1..N with no gaps on every save.
 */
@Entity
@Table(name = "tb_Workflow_Approval_Levels")
public class StdWorkflowApprovalLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @Column(name = "workflowId")
    private Integer workflowId;

    private Integer approvalNumber;

    private Integer status;

    public Integer getRecordNo() {
        return recordNo;
    }

    public Integer getWorkflowId() {
        return workflowId;
    }

    public Integer getApprovalNumber() {
        return approvalNumber;
    }

    public Integer getStatus() {
        return status;
    }
}
