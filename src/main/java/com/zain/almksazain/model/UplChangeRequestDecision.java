package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * One row per level decision on a change request. Together with its parent
 * UplChangeRequest, this table IS the permanent audit trail — who decided
 * what, at which level, with what comment, and when.
 */
@Entity
@Table(name = "tb_UPL_Change_Request_Decision", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "changeRequestId", "levelNo" })
})
public class UplChangeRequestDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    private Long changeRequestId;

    private Integer levelNo;

    @Enumerated(EnumType.STRING)
    private UplDecision decision;

    private Integer decidedBy;

    private String decidedByName;

    @Column(insertable = false, updatable = false)
    private LocalDateTime decidedAt;

    private String comments;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getChangeRequestId() {
        return changeRequestId;
    }

    public void setChangeRequestId(Long changeRequestId) {
        this.changeRequestId = changeRequestId;
    }

    public Integer getLevelNo() {
        return levelNo;
    }

    public void setLevelNo(Integer levelNo) {
        this.levelNo = levelNo;
    }

    public UplDecision getDecision() {
        return decision;
    }

    public void setDecision(UplDecision decision) {
        this.decision = decision;
    }

    public Integer getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Integer decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecidedByName() {
        return decidedByName;
    }

    public void setDecidedByName(String decidedByName) {
        this.decidedByName = decidedByName;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
