package com.zain.almksazain.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Entity
@Table(name = "tb_Delegation")
public class tbDelegation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegatorId", referencedColumnName = "userid", nullable = false)
    private User delegator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegateeId", referencedColumnName = "userid", nullable = false)
    private User delegatee;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    @Column(nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime recordDateTime;

    private String updatedBy = "";

    private LocalDateTime updateRecordDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DelegationStatus delegationStatus;

    @PrePersist
    protected void onCreate() {
        this.recordDateTime = LocalDateTime.now();
    }

    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }

    public User getDelegator() {
        return delegator;
    }

    public void setDelegator(User delegator) {
        this.delegator = delegator;
    }

    public User getDelegatee() {
        return delegatee;
    }

    public void setDelegatee(User delegatee) {
        this.delegatee = delegatee;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdateRecordDateTime() {
        return updateRecordDateTime;
    }

    public void setUpdateRecordDateTime(LocalDateTime updateRecordDateTime) {
        this.updateRecordDateTime = updateRecordDateTime;
    }

    public DelegationStatus getDelegationStatus() {
        return delegationStatus;
    }

    public void setDelegationStatus(DelegationStatus delegationStatus) {
        this.delegationStatus = delegationStatus;
    }

    public enum DelegationStatus {
        Scheduled,
        Active,
        Expired,
        Canceled
    }
}
