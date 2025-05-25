package com.zain.almksazain.DTO;

import java.util.List;

public class DccPOParentDTO {
    private Long recordNo;
    private String dccPoNumber;
    private String newProjectName;
    private String dccAcceptanceType;
    private String dccStatus;
    private String dccCreatedDate;
    private String dateApproved;
    private String vendorComment;
    private String dccId;
    private String poId;
    private String projectName;
    private String supplierId;
    private String vendorNumber;
    private String vendorName;
    private String createdBy;
    private String createdByName;
    private Long approvalCount;
    private String pendingApprovers;
    private String approverComment;
    private String userAging;
    private String totalAging;
    private String vendorEmail;
    private String dccCurrency;
    private List<DccPOLineItemDTO> lineItems;

    // Getters and setters
    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public String getDccPoNumber() {
        return dccPoNumber;
    }

    public void setDccPoNumber(String dccPoNumber) {
        this.dccPoNumber = dccPoNumber;
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

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
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

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
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

    public String getVendorEmail() {
        return vendorEmail;
    }

    public void setVendorEmail(String vendorEmail) {
        this.vendorEmail = vendorEmail;
    }

    public String getDccCurrency() {
        return dccCurrency;
    }

    public void setDccCurrency(String dccCurrency) {
        this.dccCurrency = dccCurrency;
    }

    public List<DccPOLineItemDTO> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<DccPOLineItemDTO> lineItems) {
        this.lineItems = lineItems;
    }
}