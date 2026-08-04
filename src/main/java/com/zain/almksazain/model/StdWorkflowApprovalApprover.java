package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/** Read-only mirror of WorkFlow-Management's tb_Workflow_Approval_Approvers. */
@Entity
@Table(name = "tb_Workflow_Approval_Approvers")
public class StdWorkflowApprovalApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @Column(name = "approvalLevelId")
    private Integer approvalLevelId;

    @Column(name = "approverId")
    private Integer approverId;

    private Integer status;

    public Integer getRecordNo() {
        return recordNo;
    }

    public Integer getApprovalLevelId() {
        return approvalLevelId;
    }

    public Integer getApproverId() {
        return approverId;
    }

    public Integer getStatus() {
        return status;
    }
}
