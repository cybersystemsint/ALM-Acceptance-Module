package com.zain.almksazain.DTO;

import java.math.BigDecimal;

public class DccPOCombinedViewDTO {

    private Long dccRecordNo;
    private String dccPoNumber;
    private String dccVendorName;
    private String dccVendorEmail;
    private String dccProjectName;
    private String newProjectName;
    private String dccAcceptanceType;
    private String dccStatus;
    private String dccCreatedDate;
    private String dateApproved;
    private String vendorComment;
    private String dccId;
    private String dccCurrency;
    private Long lnRecordNo;
    private String lnProductName;
    private String lnProductSerialNo;
    private Double lnDeliveredQty;
    private String lnLocationName;
    private String lnInserviceDate;
    private Double lnUnitPrice;
    private String lnScopeOfWork;
    private String lnRemarks;
    private String lnItemCode;
    private String linkId;
    private String tagNumber;
    private String lineNumber;
    private String actualItemCode;
    private String uplLineNumber;
    private String poId;
    private Double UPLACPTRequestValue; // Renamed from uplACPTRequestValue
//    private Double POAcceptanceQty; // Renamed from poAcceptanceQty
    private Double POLineAcceptanceQty; // Renamed from poLineAcceptanceQty
    private Double poPendingQuantity;
    private String projectName;
    private String supplierId;
    private String vendorNumber;
    private String vendorName;
    private String createdBy;
    private String itemPartNumber;
    private String createdByName;
    private Double poOrderQuantity;
    private String poLineDescription;
    private Double uplLineQuantity;
    private Double poLineQuantity;
    private String uplLineItemCode;
    private String uplLineDescription;
    private String unitOfMeasure;
    private String activeOrPassive;
    private Double uplPendingQuantity;
    private Long approvalCount;
    private String pendingApprovers;
    private String approverComment;
    private String userAging;
    private String totalAging;
    private String itemCode;
    private Double poAcceptanceQty;

    // Getters and Setters
    public Long getDccRecordNo() {
        return dccRecordNo;
    }

    public void setDccRecordNo(Long dccRecordNo) {
        this.dccRecordNo = dccRecordNo;
    }

    public String getDccPoNumber() {
        return dccPoNumber;
    }

    public void setDccPoNumber(String dccPoNumber) {
        this.dccPoNumber = dccPoNumber;
    }

    public String getDccVendorName() {
        return dccVendorName;
    }

    public void setDccVendorName(String dccVendorName) {
        this.dccVendorName = dccVendorName;
    }

    public String getDccVendorEmail() {
        return dccVendorEmail;
    }

    public void setDccVendorEmail(String dccVendorEmail) {
        this.dccVendorEmail = dccVendorEmail;
    }

    public String getDccProjectName() {
        return dccProjectName;
    }

    public void setDccProjectName(String dccProjectName) {
        this.dccProjectName = dccProjectName;
    }

    public String getNewProjectName() {
        return newProjectName;
    }

    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName;
    }

    public String getDccAcceptanceType() {
        return dccAcceptanceType;
    }

    public void setDccAcceptanceType(String dccAcceptanceType) {
        this.dccAcceptanceType = dccAcceptanceType;
    }

    public String getDccStatus() {
        return dccStatus;
    }

    public void setDccStatus(String dccStatus) {
        this.dccStatus = dccStatus;
    }

    public String getDccCreatedDate() {
        return dccCreatedDate;
    }

    public void setDccCreatedDate(String dccCreatedDate) {
        this.dccCreatedDate = dccCreatedDate;
    }

    public String getDateApproved() {
        return dateApproved;
    }

    public void setDateApproved(String dateApproved) {
        this.dateApproved = dateApproved;
    }

    public String getVendorComment() {
        return vendorComment;
    }

    public void setVendorComment(String vendorComment) {
        this.vendorComment = vendorComment;
    }

    public String getDccId() {
        return dccId;
    }

    public void setDccId(String dccId) {
        this.dccId = dccId;
    }

    public String getDccCurrency() {
        return dccCurrency;
    }

    public void setDccCurrency(String dccCurrency) {
        this.dccCurrency = dccCurrency;
    }

    public Long getLnRecordNo() {
        return lnRecordNo;
    }

    public void setLnRecordNo(Long lnRecordNo) {
        this.lnRecordNo = lnRecordNo;
    }

    public String getLnProductName() {
        return lnProductName;
    }

    public void setLnProductName(String lnProductName) {
        this.lnProductName = lnProductName;
    }

    public String getLnProductSerialNo() {
        return lnProductSerialNo;
    }

    public void setLnProductSerialNo(String lnProductSerialNo) {
        this.lnProductSerialNo = lnProductSerialNo;
    }

    public Double getLnDeliveredQty() {
        return lnDeliveredQty;
    }

    public void setLnDeliveredQty(Double lnDeliveredQty) {
        this.lnDeliveredQty = lnDeliveredQty;
    }

    public String getLnLocationName() {
        return lnLocationName;
    }

    public void setLnLocationName(String lnLocationName) {
        this.lnLocationName = lnLocationName;
    }

    public String getLnInserviceDate() {
        return lnInserviceDate;
    }

    public void setLnInserviceDate(String lnInserviceDate) {
        this.lnInserviceDate = lnInserviceDate;
    }

    public Double getLnUnitPrice() {
        return lnUnitPrice;
    }

    public void setLnUnitPrice(Double lnUnitPrice) {
        this.lnUnitPrice = lnUnitPrice;
    }

    public String getLnScopeOfWork() {
        return lnScopeOfWork;
    }

    public void setLnScopeOfWork(String lnScopeOfWork) {
        this.lnScopeOfWork = lnScopeOfWork;
    }

    public String getLnRemarks() {
        return lnRemarks;
    }

    public void setLnRemarks(String lnRemarks) {
        this.lnRemarks = lnRemarks;
    }

    public String getLnItemCode() {
        return lnItemCode;
    }

    public void setLnItemCode(String lnItemCode) {
        this.lnItemCode = lnItemCode;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getActualItemCode() {
        return actualItemCode;
    }

    public void setActualItemCode(String actualItemCode) {
        this.actualItemCode = actualItemCode;
    }

    public String getUplLineNumber() {
        return uplLineNumber;
    }

    public void setUplLineNumber(String uplLineNumber) {
        this.uplLineNumber = uplLineNumber;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
    }

    public Double getUPLACPTRequestValue() {
        return UPLACPTRequestValue;
    }

    public void setUPLACPTRequestValue(Double UPLACPTRequestValue) {
        this.UPLACPTRequestValue = UPLACPTRequestValue;
    }

    public Double getpoAcceptanceQty() {
        return poAcceptanceQty;
    }

    public void setpoAcceptanceQty(Double poAcceptanceQty) {
        this.poAcceptanceQty = poAcceptanceQty;
    }

    public Double getPOLineAcceptanceQty() {
        return POLineAcceptanceQty;
    }

    public void setPOLineAcceptanceQty(Double POLineAcceptanceQty) {
        this.POLineAcceptanceQty = POLineAcceptanceQty;
    }

    public Double getPoPendingQuantity() {
        return poPendingQuantity;
    }

    public void setPoPendingQuantity(Double poPendingQuantity) {
        this.poPendingQuantity = poPendingQuantity;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getItemPartNumber() {
        return itemPartNumber;
    }

    public void setItemPartNumber(String itemPartNumber) {
        this.itemPartNumber = itemPartNumber;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Double getPoOrderQuantity() {
        return poOrderQuantity;
    }

    public void setPoOrderQuantity(Double poOrderQuantity) {
        this.poOrderQuantity = poOrderQuantity;
    }

    public String getPoLineDescription() {
        return poLineDescription;
    }

    public void setPoLineDescription(String poLineDescription) {
        this.poLineDescription = poLineDescription;
    }

    public Double getUplLineQuantity() {
        return uplLineQuantity;
    }

    public void setUplLineQuantity(Double uplLineQuantity) {
        this.uplLineQuantity = uplLineQuantity;
    }

    public Double getPoLineQuantity() {
        return poLineQuantity;
    }

    public void setPoLineQuantity(Double poLineQuantity) {
        this.poLineQuantity = poLineQuantity;
    }

    public String getUplLineItemCode() {
        return uplLineItemCode;
    }

    public void setUplLineItemCode(String uplLineItemCode) {
        this.uplLineItemCode = uplLineItemCode;
    }

    public String getUplLineDescription() {
        return uplLineDescription;
    }

    public void setUplLineDescription(String uplLineDescription) {
        this.uplLineDescription = uplLineDescription;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public String getActiveOrPassive() {
        return activeOrPassive;
    }

    public void setActiveOrPassive(String activeOrPassive) {
        this.activeOrPassive = activeOrPassive;
    }

    public Double getUplPendingQuantity() {
        return uplPendingQuantity;
    }

    public void setUplPendingQuantity(Double uplPendingQuantity) {
        this.uplPendingQuantity = uplPendingQuantity;
    }

    public Long getApprovalCount() {
        return approvalCount;
    }

    public void setApprovalCount(Long approvalCount) {
        this.approvalCount = approvalCount;
    }

    public String getPendingApprovers() {
        return pendingApprovers;
    }

    public void setPendingApprovers(String pendingApprovers) {
        this.pendingApprovers = pendingApprovers;
    }

    public String getApproverComment() {
        return approverComment;
    }

    public void setApproverComment(String approverComment) {
        this.approverComment = approverComment;
    }

    public String getUserAging() {
        return userAging;
    }

    public void setUserAging(String userAging) {
        this.userAging = userAging;
    }

    public String getTotalAging() {
        return totalAging;
    }

    public void setTotalAging(String totalAging) {
        this.totalAging = totalAging;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }


//    public void setpoAcceptanceQty(Double poAcceptanceQty) {
//        this.poAcceptanceQty = poAcceptanceQty;
//    }
//
//    public Double getpoAcceptanceQty() {
//        return poAcceptanceQty;
//    }
}