package com.zain.almksazain.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_Category_Approvals")
public class TbCategoryApprovals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approvalId")
    private Long approvalId;

    @Column(name = "approvalRecordId")
    private Long approvalRecordId;

    @Column(name = "approvalLevelId")
    private Long approvalLevelId;

    @Column(name = "approverId")
    private Long approverId;

    @Column(name = "approverName")
    private String approverName;

    @Column(name = "regionId")
    private Long regionId;

    @Column(name = "status")
    private String status;

    @Column(name = "approvalStatus")
    private String approvalStatus;

    @Column(name = "comments")
    private String comments;

    @Column(name = "approvedBy")
    private String approvedBy;

    @Column(name = "actionTypeId")
    private Long actionTypeId;

    @Column(name = "approvedDate")
    private LocalDateTime approvedDate;

    @Column(name = "recordDateTime")
    private LocalDateTime recordDateTime;

    @Column(name = "display")
    private Boolean display;

    // Getters and Setters
    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Long getApprovalRecordId() {
        return approvalRecordId;
    }

    public void setApprovalRecordId(Long approvalRecordId) {
        this.approvalRecordId = approvalRecordId;
    }

    public Long getApprovalLevelId() {
        return approvalLevelId;
    }

    public void setApprovalLevelId(Long approvalLevelId) {
        this.approvalLevelId = approvalLevelId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Long getActionTypeId() {
        return actionTypeId;
    }

    public void setActionTypeId(Long actionTypeId) {
        this.actionTypeId = actionTypeId;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public Boolean getDisplay() {
        return display;
    }

    public void setDisplay(Boolean display) {
        this.display = display;
    }
}