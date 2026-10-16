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

/**
 * A pending or decided edit/delete request against one tb_PurchaseOrderUPL
 * line. totalLevels is a snapshot of tb_UPL_Approval_Level's level count for
 * this changeType, taken at submission time, so reconfiguring the workflow
 * later never moves a request already in flight.
 */
@Entity
@Table(name = "tb_UPL_Change_Request")
public class UplChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    private String batchId;

    private Long uplRecordNo;

    @Enumerated(EnumType.STRING)
    private UplActionType changeType;

    @Column(columnDefinition = "JSON")
    private String fieldChanges;

    private Integer totalLevels;

    private Integer currentLevelNo;

    @Enumerated(EnumType.STRING)
    private UplChangeRequestStatus status;

    private Integer requestedBy;

    private String requestedByName;

    @Column(insertable = false, updatable = false)
    private LocalDateTime requestedAt;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Long getUplRecordNo() {
        return uplRecordNo;
    }

    public void setUplRecordNo(Long uplRecordNo) {
        this.uplRecordNo = uplRecordNo;
    }

    public UplActionType getChangeType() {
        return changeType;
    }

    public void setChangeType(UplActionType changeType) {
        this.changeType = changeType;
    }

    public String getFieldChanges() {
        return fieldChanges;
    }

    public void setFieldChanges(String fieldChanges) {
        this.fieldChanges = fieldChanges;
    }

    public Integer getTotalLevels() {
        return totalLevels;
    }

    public void setTotalLevels(Integer totalLevels) {
        this.totalLevels = totalLevels;
    }

    public Integer getCurrentLevelNo() {
        return currentLevelNo;
    }

    public void setCurrentLevelNo(Integer currentLevelNo) {
        this.currentLevelNo = currentLevelNo;
    }

    public UplChangeRequestStatus getStatus() {
        return status;
    }

    public void setStatus(UplChangeRequestStatus status) {
        this.status = status;
    }

    public Integer getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Integer requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public void setRequestedByName(String requestedByName) {
        this.requestedByName = requestedByName;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }
}
