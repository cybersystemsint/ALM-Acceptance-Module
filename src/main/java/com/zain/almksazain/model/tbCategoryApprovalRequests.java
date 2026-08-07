/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_Category_Approval_Requests")
public class tbCategoryApprovalRequests implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;

    @Column(name = "recordDatetime")
    private LocalDateTime recordDateTime;

    private int acceptanceRequestRecordNo;

    private String poNumber;

    private String tableName;

    private String poLineItemDescription;

    private String vendorName;

    private String requestedBy;

    /** Matches WFM / DB: createdBy is stored as a string user id. */
    private String createdBy;

    private String itemCategoryCode;

    private int scope;

    private String status;

    private boolean received;

    private LocalDateTime approvedDate;

    @PrePersist
    protected void onCreate() {
        if (this.recordDateTime == null) {
            this.recordDateTime = LocalDateTime.now();
        }
    }

    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public int getAcceptanceRequestRecordNo() {
        return acceptanceRequestRecordNo;
    }

    public void setAcceptanceRequestRecordNo(int acceptanceRequestRecordNo) {
        this.acceptanceRequestRecordNo = acceptanceRequestRecordNo;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPoLineItemDescription() {
        return poLineItemDescription;
    }

    public void setPoLineItemDescription(String poLineItemDescription) {
        this.poLineItemDescription = poLineItemDescription;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getItemCategoryCode() {
        return itemCategoryCode;
    }

    public void setItemCategoryCode(String itemCategoryCode) {
        this.itemCategoryCode = itemCategoryCode;
    }

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isReceived() {
        return received;
    }

    public void setReceived(boolean received) {
        this.received = received;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }

}
