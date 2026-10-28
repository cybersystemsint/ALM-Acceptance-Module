package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A TAG Number (Configurations > Acceptance Workflow > TAG Numbers), registered to one vendor.
 * The TAG Number alone is unique among non-deleted rows, compared case-insensitively ('ABC' and
 * 'abc' are the same TAG) - see sql/tag_number_schema.sql. Status is STATUS_UNUSED (free to go on
 * an acceptance request), STATUS_USED, or STATUS_DELETED; a delete is soft and such rows never
 * reach the UI.
 */
@Entity
@Table(name = "tb_TagNumber")
public class TagNumber {

    public static final String STATUS_USED = "Used";
    public static final String STATUS_UNUSED = "Unused";
    public static final String STATUS_DELETED = "Deleted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private String tagNumber;

    private String vendorNumber;

    private String vendorName;

    private String status;

    private String createdBy;

    private LocalDateTime createdDate;

    private String lastUpdatedBy;

    private LocalDateTime lastUpdatedDate;

    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
}
