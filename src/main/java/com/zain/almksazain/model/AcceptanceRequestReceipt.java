package com.zain.almksazain.model;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_AcceptanceRequest_Receipt")
public class AcceptanceRequestReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @Column(name = "recordDateTime", nullable = false, updatable = false)
    private LocalDateTime recordDateTime;

    @Column(name = "categoryApprovalRequestId", nullable = false)
    private Integer categoryApprovalRequestId;

    @Column(name = "approvedBy")
    private Integer approvedBy;

    @Column(name = "approvalStatus", length = 50)
    private String approvalStatus;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "approvedDate")
    private LocalDateTime approvedDate;

    // Getters and setters
    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    @PrePersist
    protected void onCreate() {
        this.recordDateTime = LocalDateTime.now();
    }

    public Integer getCategoryApprovalRequestId() {
        return categoryApprovalRequestId;
    }

    public void setCategoryApprovalRequestId(Integer categoryApprovalRequestId) {
        this.categoryApprovalRequestId = categoryApprovalRequestId;
    }

    public Integer getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Integer approvedBy) {
        this.approvedBy = approvedBy;
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

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }
}