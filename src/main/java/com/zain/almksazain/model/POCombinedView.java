package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
//@Table(name = "dccPOCombinedView")
 @Table(name = "dccPOCombinedCache")
public class POCombinedView {
    
   @Id
    @Column(name = "dccRecordNo")
    private Long recordNo;

    @Column(name = "dccPoNumber")
    private String poNumber;

    @Column(name = "newProjectName")
    private String newProjectName;

    @Column(name = "dccAcceptanceType")
    private String dccAcceptanceType;

    @Column(name = "dccStatus")
    private String dccStatus;

    @Column(name = "dccCreatedDate")
    private String dccCreatedDate;

    @Column(name = "dateApproved")
    private String dateApproved;

    @Column(name = "vendorComment")
    private String vendorComment;

    @Column(name = "dccId")
    private String dccId;

    @Column(name = "lnLocationName")
    private String lnLocationName;

    @Column(name = "lnInserviceDate")
    private String lnInserviceDate;

    @Column(name = "lnScopeOfWork")
    private String lnScopeOfWork;

    @Column(name = "poId")
    private String poId;

    @Column(name = "UPLACPTRequestValue")
    private Double uplacptRequestValue;

    @Column(name = "dccProjectName")
    private String projectName;

    @Column(name = "supplierId")
    private String supplierId;

    @Column(name = "dccVendorName")
    private String vendorName;

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "createdByName")
    private String createdByName;

    @Column(name = "approvalCount")
    private Integer approvalCount;

    @Column(name = "pendingApprovers")
    private String pendingApprovers;

    @Column(name = "approverComment", columnDefinition = "mediumtext")
    private String approverComment;

    @Column(name = "userAging")
    private String userAging;

    @Column(name = "totalAging")
    private String totalAging;

    @Column(name = "dccVendorEmail")
    private String vendorEmail;


    // @Column(name = "departmentName")
    // private String departmentName;



    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
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

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
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


}