package com.zain.almksazain.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_Category_Approval_Requests")
public class TbCategoryApprovalRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "recordDateTime")
    private LocalDateTime recordDateTime;

    @Column(name = "acceptanceRequestRecordNo")
    private Long acceptanceRequestRecordNo;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "tableName")
    private String tableName;

    @Column(name = "poLineItemDescription")
    private String poLineItemDescription;

    @Column(name = "vendorName")
    private String vendorName;

    @Column(name = "requestedBy")
    private String requestedBy;

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "itemCategoryCode")
    private String itemCategoryCode;

    @Column(name = "scope")
    private String scope;

    @Column(name = "status")
    private String status;

    @Column(name = "received")
    private Boolean received;

    @Column(name = "approvedDate")
    private LocalDateTime approvedDate;

    // Getters and Setters
    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public LocalDateTime getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public Long getAcceptanceRequestRecordNo() {
        return acceptanceRequestRecordNo;
    }

    public void setAcceptanceRequestRecordNo(Long acceptanceRequestRecordNo) {
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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getReceived() {
        return received;
    }

    public void setReceived(Boolean received) {
        this.received = received;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }
}