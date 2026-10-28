package com.zain.almksazain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Audit trail of approver-defined chain changes (Others scope). This module
 * only writes REAPPLIED rows, when a resubmitted request gets its earlier chain
 * back; WorkFlow-Management's ApprovalChainService writes the edit rows.
 */
@Entity
@Table(name = "tb_Approval_Chain_Change_Logs")
public class tbApprovalChainChangeLogs implements Serializable {

    public static final String REAPPLIED = "REAPPLIED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;
    private Integer acceptanceRequestRecordNo;
    private Integer approvalRequestRecordNo;
    private String changeType;
    private Integer levelNumber;
    private String oldApproverName;
    private String newApproverName;
    private String performedBy;
    private LocalDateTime performedDateTime;

    public tbApprovalChainChangeLogs() {
    }

    public static tbApprovalChainChangeLogs reapplied(tbCategoryApprovalRequests approvalRequest, String performedBy) {
        tbApprovalChainChangeLogs log = new tbApprovalChainChangeLogs();
        log.acceptanceRequestRecordNo = approvalRequest.getAcceptanceRequestRecordNo();
        log.approvalRequestRecordNo = approvalRequest.getRecordNo();
        log.changeType = REAPPLIED;
        log.performedBy = performedBy;
        log.performedDateTime = LocalDateTime.now();
        return log;
    }

    public Integer getRecordNo() {
        return recordNo;
    }

    public Integer getAcceptanceRequestRecordNo() {
        return acceptanceRequestRecordNo;
    }

    public Integer getApprovalRequestRecordNo() {
        return approvalRequestRecordNo;
    }

    public String getChangeType() {
        return changeType;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public String getOldApproverName() {
        return oldApproverName;
    }

    public String getNewApproverName() {
        return newApproverName;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public LocalDateTime getPerformedDateTime() {
        return performedDateTime;
    }
}
