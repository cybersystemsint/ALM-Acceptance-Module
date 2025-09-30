package com.zain.almksazain.dto;


import java.math.BigDecimal;

public class AgingReportItemsDTO {
    private int recordNo; 
    private String poNumber; 
    private String newProjectName;
    private String dccAcceptanceType; 
    private String dccStatus;
    private String dccCreatedDate;
    private String dateApproved;
    private String vendorComment;
    private String dccId;
    private String lnLocationName;
    private String lnInserviceDate; 
    private String lnScopeOfWork;
    private String poId; 
    private Double uplacptRequestValue;
    private String projectName;
    private String supplierId;
    private String vendorName; 
    private String createdBy; 
    private Integer approvalCount;
    private String pendingApprovers;
    private String approverComment;
    private String userAging;
    private String totalAging;
    private String vendorEmail;
    private String vendorNumber;
    private String departmentName;
    private Integer userAgingInDays;
    private Integer totalAgingInDays;
    private BigDecimal totalDeliveredQty;
    private BigDecimal totalUnitPrice;
    private String currency;

    // Constructors, getters, setters

    public AgingReportItemsDTO() {}

    // All args constructor (add all fields)
    public AgingReportItemsDTO(
            int recordNo,
            String poNumber,
            String newProjectName,
            String dccAcceptanceType,
            String dccStatus,
            String dccCreatedDate,
            String dateApproved,
            String vendorComment,
            String dccId,
            String lnLocationName,
            String lnInserviceDate,
            String lnScopeOfWork,
            String poId,
            Double uplacptRequestValue,
            String projectName,
            String supplierId,
            String vendorName,
            String createdBy,
            String createdByName,
            Integer approvalCount,
            String pendingApprovers,
            String approverComment,
            String userAging,
            String totalAging,
            String vendorEmail,
            String vendorNumber,
            String departmentName,
            Integer userAgingInDays,
            Integer totalAgingInDays,
            BigDecimal totalDeliveredQty,
            BigDecimal totalUnitPrice,
            String currency
    ) {
        this.recordNo = recordNo;
        this.poNumber = poNumber;
        this.newProjectName = newProjectName;
        this.dccAcceptanceType = dccAcceptanceType;
        this.dccStatus = dccStatus;
        this.dccCreatedDate = dccCreatedDate;
        this.dateApproved = dateApproved;
        this.vendorComment = vendorComment;
        this.dccId = dccId;
        this.lnLocationName = lnLocationName;
        this.lnInserviceDate = lnInserviceDate;
        this.lnScopeOfWork = lnScopeOfWork;
        this.poId = poId;
        this.uplacptRequestValue = uplacptRequestValue;
        this.projectName = projectName;
        this.supplierId = supplierId;
        this.vendorName = vendorName;
        this.createdBy = createdBy;
        this.approvalCount = approvalCount;
        this.pendingApprovers = pendingApprovers;
        this.approverComment = approverComment;
        this.userAging = userAging;
        this.totalAging = totalAging;
        this.vendorEmail = vendorEmail;
        this.vendorNumber = vendorNumber;
        this.departmentName = departmentName;
        this.userAgingInDays = userAgingInDays;
        this.totalAgingInDays = totalAgingInDays;
        this.totalDeliveredQty = totalDeliveredQty;
        this.totalUnitPrice = totalUnitPrice;
        this.currency = currency;
    }

    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
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

    public String getLnScopeOfWork() {
        return lnScopeOfWork;
    }

    public void setLnScopeOfWork(String lnScopeOfWork) {
        this.lnScopeOfWork = lnScopeOfWork;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
    }

    public Double getUplacptRequestValue() {
        return uplacptRequestValue;
    }

    public void setUplacptRequestValue(Double uplacptRequestValue) {
        this.uplacptRequestValue = uplacptRequestValue;
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

    public Integer getApprovalCount() {
        return approvalCount;
    }

    public void setApprovalCount(Integer approvalCount) {
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

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Integer getUserAgingInDays() {
        return userAgingInDays;
    }

    public void setUserAgingInDays(Integer userAgingInDays) {
        this.userAgingInDays = userAgingInDays;
    }

    public Integer getTotalAgingInDays() {
        return totalAgingInDays;
    }

    public void setTotalAgingInDays(Integer totalAgingInDays) {
        this.totalAgingInDays = totalAgingInDays;
    }

    public BigDecimal getTotalDeliveredQty() {
        return totalDeliveredQty;
    }

    public void setTotalDeliveredQty(BigDecimal totalDeliveredQty) {
        this.totalDeliveredQty = totalDeliveredQty;
    }

    public BigDecimal getTotalUnitPrice() {
        return totalUnitPrice;
    }

    public void setTotalUnitPrice(BigDecimal totalUnitPrice) {
        this.totalUnitPrice = totalUnitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }


    
}
