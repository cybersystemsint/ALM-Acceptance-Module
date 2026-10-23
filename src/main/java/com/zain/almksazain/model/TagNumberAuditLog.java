package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * One row per create/update/delete of a tb_TagNumber row - the TAG Number audit trail.
 * fieldChanges holds the same {field: {old, new}} JSON shape tb_UPL_Change_Request uses;
 * tagNumber/vendorNumber/vendorName snapshot the row as it stood after the change.
 */
@Entity
@Table(name = "tb_TagNumber_Audit_Log")
public class TagNumberAuditLog {

    public static final String CHANGE_CREATE = "CREATE";
    public static final String CHANGE_UPDATE = "UPDATE";
    public static final String CHANGE_DELETE = "DELETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    private String batchId;

    private Integer tagRecordNo;

    private String changeType;

    private String tagNumber;

    private String vendorNumber;

    private String vendorName;

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

    public Integer getTagRecordNo() {
        return tagRecordNo;
    }

    public void setTagRecordNo(Integer tagRecordNo) {
        this.tagRecordNo = tagRecordNo;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
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
