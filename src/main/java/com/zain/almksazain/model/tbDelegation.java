package com.zain.almksazain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Local, read-only mapping onto the same physical table WorkFlow-Management's
 * Delegation entity uses (tb_Delegation) - used here only to resolve whether
 * a level's configured approver has an active delegate, matching
 * ScopeApprovalService.getDelegatedApprover()'s logic.
 */
@Entity
@Table(name = "tb_Delegation")
public class tbDelegation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;

    private int delegatorId;
    private int delegateeId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private boolean isActive;

    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public int getDelegatorId() {
        return delegatorId;
    }

    public void setDelegatorId(int delegatorId) {
        this.delegatorId = delegatorId;
    }

    public int getDelegateeId() {
        return delegateeId;
    }

    public void setDelegateeId(int delegateeId) {
        this.delegateeId = delegateeId;
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
}
