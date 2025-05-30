package com.zain.almksazain.DTO;

import java.util.Date;
import java.time.LocalDateTime;

public class DccPOProjection {
    // DCC fields
    private Long dccRecordNo;
    private String dccPoNumber;
    private String dccVendorName;
    private String dccVendorEmail;
    private String dccProjectName;
    private String newProjectName;
    private String dccAcceptanceType;
    private String dccStatus;
    private Date dccCreatedDate;
    private String vendorComment;
    private String dccId;
    private String dccCurrency;
    private String createdBy;

    // DCCLineItem fields
    private Long lnRecordNo;
    private String lnProductName;
    private String lnProductSerialNo;
    private Double lnDeliveredQty;
    private String lnLocationName;
    private Date lnInserviceDate;
    private Double lnUnitPrice;
    private String lnScopeOfWork;
    private String lnRemarks;
    private String linkId;
    private String tagNumber;
    private String lineNumber;
    private String actualItemCode;
    private String uplLineNumber;
    private String poId;

    // PurchaseOrder fields
    private String projectName;
    private String supplierId;
    private String vendorName;
    private String itemPartNumber;
    private Double poOrderQuantity;
    private String poLineDescription;
    private Double poQtyNew;

    // PurchaseOrderUPL fields
    private Double uplLineQuantity;
    private String uplLineItemCode;
    private String uplLineDescription;
    private String unitOfMeasure;
    private String activeOrPassive;
    private String poLineNumber;
    private Double uplPoLineUnitPrice;
    private Double poLineQuantity;

    // Approval Request fields
    private Long approvalRequestRecordNo;
    private LocalDateTime approvalApprovedDate;
    private String approvalRequestStatus;

    // Getters and Setters
    public Long getDccRecordNo() { return dccRecordNo; }
    public void setDccRecordNo(Long dccRecordNo) { this.dccRecordNo = dccRecordNo; }
    public String getDccPoNumber() { return dccPoNumber; }
    public void setDccPoNumber(String dccPoNumber) { this.dccPoNumber = dccPoNumber; }
    public String getDccVendorName() { return dccVendorName; }
    public void setDccVendorName(String dccVendorName) { this.dccVendorName = dccVendorName; }
    public String getDccVendorEmail() { return dccVendorEmail; }
    public void setDccVendorEmail(String dccVendorEmail) { this.dccVendorEmail = dccVendorEmail; }
    public String getDccProjectName() { return dccProjectName; }
    public void setDccProjectName(String dccProjectName) { this.dccProjectName = dccProjectName; }
    public String getNewProjectName() { return newProjectName; }
    public void setNewProjectName(String newProjectName) { this.newProjectName = newProjectName; }
    public String getDccAcceptanceType() { return dccAcceptanceType; }
    public void setDccAcceptanceType(String dccAcceptanceType) { this.dccAcceptanceType = dccAcceptanceType; }
    public String getDccStatus() { return dccStatus; }
    public void setDccStatus(String dccStatus) { this.dccStatus = dccStatus; }
    public Date getDccCreatedDate() { return dccCreatedDate; }
    public void setDccCreatedDate(Date dccCreatedDate) { this.dccCreatedDate = dccCreatedDate; }
    public String getVendorComment() { return vendorComment; }
    public void setVendorComment(String vendorComment) { this.vendorComment = vendorComment; }
    public String getDccId() { return dccId; }
    public void setDccId(String dccId) { this.dccId = dccId; }
    public String getDccCurrency() { return dccCurrency; }
    public void setDccCurrency(String dccCurrency) { this.dccCurrency = dccCurrency; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getLnRecordNo() { return lnRecordNo; }
    public void setLnRecordNo(Long lnRecordNo) { this.lnRecordNo = lnRecordNo; }
    public String getLnProductName() { return lnProductName; }
    public void setLnProductName(String lnProductName) { this.lnProductName = lnProductName; }
    public String getLnProductSerialNo() { return lnProductSerialNo; }
    public void setLnProductSerialNo(String lnProductSerialNo) { this.lnProductSerialNo = lnProductSerialNo; }
    public Double getLnDeliveredQty() { return lnDeliveredQty; }
    public void setLnDeliveredQty(Double lnDeliveredQty) { this.lnDeliveredQty = lnDeliveredQty; }
    public String getLnLocationName() { return lnLocationName; }
    public void setLnLocationName(String lnLocationName) { this.lnLocationName = lnLocationName; }
    public Date getLnInserviceDate() { return lnInserviceDate; }
    public void setLnInserviceDate(Date lnInserviceDate) { this.lnInserviceDate = lnInserviceDate; }
    public Double getLnUnitPrice() { return lnUnitPrice; }
    public void setLnUnitPrice(Double lnUnitPrice) { this.lnUnitPrice = lnUnitPrice; }
    public String getLnScopeOfWork() { return lnScopeOfWork; }
    public void setLnScopeOfWork(String lnScopeOfWork) { this.lnScopeOfWork = lnScopeOfWork; }
    public String getLnRemarks() { return lnRemarks; }
    public void setLnRemarks(String lnRemarks) { this.lnRemarks = lnRemarks; }
    public String getLinkId() { return linkId; }
    public void setLinkId(String linkId) { this.linkId = linkId; }
    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }
    public String getLineNumber() { return lineNumber; }
    public void setLineNumber(String lineNumber) { this.lineNumber = lineNumber; }
    public String getActualItemCode() { return actualItemCode; }
    public void setActualItemCode(String actualItemCode) { this.actualItemCode = actualItemCode; }
    public String getUplLineNumber() { return uplLineNumber; }
    public void setUplLineNumber(String uplLineNumber) { this.uplLineNumber = uplLineNumber; }
    public String getPoId() { return poId; }
    public void setPoId(String poId) { this.poId = poId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getItemPartNumber() { return itemPartNumber; }
    public void setItemPartNumber(String itemPartNumber) { this.itemPartNumber = itemPartNumber; }
    public Double getPoOrderQuantity() { return poOrderQuantity; }
    public void setPoOrderQuantity(Double poOrderQuantity) { this.poOrderQuantity = poOrderQuantity; }
    public String getPoLineDescription() { return poLineDescription; }
    public void setPoLineDescription(String poLineDescription) { this.poLineDescription = poLineDescription; }
    public Double getPoQtyNew() { return poQtyNew; }
    public void setPoQtyNew(Double poQtyNew) { this.poQtyNew = poQtyNew; }
    public Double getUplLineQuantity() { return uplLineQuantity; }
    public void setUplLineQuantity(Double uplLineQuantity) { this.uplLineQuantity = uplLineQuantity; }
    public String getUplLineItemCode() { return uplLineItemCode; }
    public void setUplLineItemCode(String uplLineItemCode) { this.uplLineItemCode = uplLineItemCode; }
    public String getUplLineDescription() { return uplLineDescription; }
    public void setUplLineDescription(String uplLineDescription) { this.uplLineDescription = uplLineDescription; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    public String getActiveOrPassive() { return activeOrPassive; }
    public void setActiveOrPassive(String activeOrPassive) { this.activeOrPassive = activeOrPassive; }
    public String getPoLineNumber() { return poLineNumber; }
    public void setPoLineNumber(String poLineNumber) { this.poLineNumber = poLineNumber; }
    public Double getUplPoLineUnitPrice() { return uplPoLineUnitPrice; }
    public void setUplPoLineUnitPrice(Double uplPoLineUnitPrice) { this.uplPoLineUnitPrice = uplPoLineUnitPrice; }
    public Double getPoLineQuantity() { return poLineQuantity; }
    public void setPoLineQuantity(Double poLineQuantity) { this.poLineQuantity = poLineQuantity; }
    public Long getApprovalRequestRecordNo() { return approvalRequestRecordNo; }
    public void setApprovalRequestRecordNo(Long approvalRequestRecordNo) { this.approvalRequestRecordNo = approvalRequestRecordNo; }
    public LocalDateTime getApprovalApprovedDate() { return approvalApprovedDate; }
    public void setApprovalApprovedDate(LocalDateTime approvalApprovedDate) { this.approvalApprovedDate = approvalApprovedDate; }
    public String getApprovalRequestStatus() { return approvalRequestStatus; }
    public void setApprovalRequestStatus(String approvalRequestStatus) { this.approvalRequestStatus = approvalRequestStatus; }
}