package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 * Maps to tb_InApp_Notifications — the same physical table the notification
 * bell (WorkFlow-Management's NotificationController / InAppNotification)
 * already reads. Writing rows here from this module surfaces them in the
 * existing bell UI with no frontend change: the two services share one
 * MySQL schema even though they're deployed separately.
 *
 * Field names deliberately mirror WorkFlow-Management's InAppNotification —
 * only the fields this feature needs are declared.
 */
@Entity
@Table(name = "tb_InApp_Notifications")
public class UplInAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer requestRecordNo;
    private Integer approverId;

    @Column(name = "approval_id")
    private Integer approvalId;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String notificationType;
    private boolean isRead;

    @Column(name = "is_active")
    private boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getRequestRecordNo() {
        return requestRecordNo;
    }

    public void setRequestRecordNo(Integer requestRecordNo) {
        this.requestRecordNo = requestRecordNo;
    }

    public Integer getApproverId() {
        return approverId;
    }

    public void setApproverId(Integer approverId) {
        this.approverId = approverId;
    }

    public Integer getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Integer approvalId) {
        this.approvalId = approvalId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
