package com.zain.almksazain.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_Category_Approvals")
public class TbCategoryApprovals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long approvalId;

    private String approvalStatus;
    private String status;
    private Long approvalRecordId;
    private String approverName;
    private String comments;
    private LocalDateTime recordDateTime;
    private LocalDateTime approvedDate;

    // Getters and Setters
    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApprovalRecordId() {
        return approvalRecordId;
    }

    public void setApprovalRecordId(Long approvalRecordId) {
        this.approvalRecordId = approvalRecordId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }
}