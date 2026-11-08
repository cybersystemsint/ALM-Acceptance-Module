package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * One row per create/update/delete of a tb_SerialNumber row - the Serial Number audit trail.
 * fieldChanges holds the same {field: {old, new}} JSON shape as tb_TagNumber_Audit_Log;
 * serialNumber/itemCode snapshot the row as it stood after the change.
 */
@Entity
@Table(name = "tb_SerialNumber_Audit_Log")
public class SerialNumberAuditLog {

    public static final String CHANGE_CREATE = "CREATE";
    public static final String CHANGE_UPDATE = "UPDATE";
    public static final String CHANGE_DELETE = "DELETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    private String batchId;

    private Integer serialRecordNo;

    private String changeType;

    private String serialNumber;

    private String itemCode;

    @Column(columnDefinition = "JSON")
    private String fieldChanges;

    private Integer changedBy;

    private String changedByName;

    @Column(insertable = false, updatable = false)
    private LocalDateTime changedAt;

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

    public Integer getSerialRecordNo() {
        return serialRecordNo;
    }

    public void setSerialRecordNo(Integer serialRecordNo) {
        this.serialRecordNo = serialRecordNo;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getFieldChanges() {
        return fieldChanges;
    }

    public void setFieldChanges(String fieldChanges) {
        this.fieldChanges = fieldChanges;
    }

    public Integer getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Integer changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public void setChangedByName(String changedByName) {
        this.changedByName = changedByName;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
