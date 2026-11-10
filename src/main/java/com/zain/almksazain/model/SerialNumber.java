package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

/**
 * A received serial number (Configurations > Acceptance Workflow > Serial Numbers). Most rows are
 * loaded from Oracle (createdBy 'DBVIEW'); the page adds and edits the rest - see
 * sql/serial_number_module_migration.sql.
 *
 * validationRule decides what an Active row blocks on an acceptance request (APIController#postdcc):
 * RULE_SERIAL_NUMBER blocks the serial number whatever the item code, RULE_SERIAL_NUMBER_ITEM_CODE
 * only that serial number with this item code. Every row of one serial number carries the same rule.
 * Status is STATUS_ACTIVE, STATUS_INACTIVE (kept, but no longer validated) or STATUS_DELETED; a
 * delete is soft and such rows never reach the UI.
 *
 * Only the columns the page uses are mapped, and @DynamicUpdate writes only the ones that
 * changed, so an update never touches the Oracle-loaded columns (serialStatus, location, ...).
 */
@Entity
@Table(name = "tb_SerialNumber")
@DynamicUpdate
public class SerialNumber {

    public static final String RULE_SERIAL_NUMBER = "SerialNumber";
    public static final String RULE_SERIAL_NUMBER_ITEM_CODE = "SerialNumber + ItemCode";

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "InActive";
    public static final String STATUS_DELETED = "Deleted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private LocalDateTime recordDatetime;

    private String serialNumber;

    private String itemCode;

    private String organizationCode;

    private String organizationName;

    private String invoice;

    private String validationRule;

    private String comment;

    private String status;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime updatedDatetime;

    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }

    public LocalDateTime getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(LocalDateTime recordDatetime) {
        this.recordDatetime = recordDatetime;
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

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    public String getValidationRule() {
        return validationRule;
    }

    public void setValidationRule(String validationRule) {
        this.validationRule = validationRule;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedDatetime() {
        return updatedDatetime;
    }

    public void setUpdatedDatetime(LocalDateTime updatedDatetime) {
        this.updatedDatetime = updatedDatetime;
    }
}
