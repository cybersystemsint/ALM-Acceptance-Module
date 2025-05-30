package com.zain.almksazain.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "tb_DCC")
public class DCC {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "vendorName")
    private String vendorName;

    @Column(name = "vendorEmail")
    private String vendorEmail;

    @Column(name = "boqId")
    private String boqId;

    @Column(name = "projectName")
    private String projectName;

    @Column(name = "newProjectName")
    private String newProjectName;

    @Column(name = "acceptanceType")
    private String acceptanceType;

    @Column(name = "status")
    private String status;

    @Column(name = "createdDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "dccId")
    private String dccId;

    @Column(name = "currency")
    private String currency;

    @Column(name = "poDate")
    private String poDate;

    @Column(name = "supplierId")
    private String supplierId;

    @Column(name = "totalQty")
    private Integer totalQty;

    @Column(name = "totalNoofSites")
    private Integer totalNoofSites;

    @Column(name = "newFACategory")
    private String newFACategory;

    @Column(name = "L1")
    private String l1;

    @Column(name = "L2")
    private String l2;

    @Column(name = "L3")
    private String l3;

    @Column(name = "L4")
    private String l4;

    @Column(name = "oldFACategory")
    private String oldFACategory;

    @Column(name = "accDepreciationCode")
    private String accDepreciationCode;

    @Column(name = "depreciationCode")
    private String depreciationCode;

    @Column(name = "vendorNumber")
    private String vendorNumber;

    @Column(name = "projectNumber")
    private String projectNumber;

    @Column(name = "partNumber")
    private String partNumber;

    @Column(name = "costCenter")
    private String costCenter;

    @Column(name = "termsAndConditions")
    private String termsAndConditions;

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "approvedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date approvedDate;

    @Column(name = "vendorComment")
    private String vendorComment;

    // Getters and Setters
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

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorEmail() {
        return vendorEmail;
    }

    public void setVendorEmail(String vendorEmail) {
        this.vendorEmail = vendorEmail;
    }

    public String getBoqId() {
        return boqId;
    }

    public void setBoqId(String boqId) {
        this.boqId = boqId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getNewProjectName() {
        return newProjectName;
    }

    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName;
    }

    public String getAcceptanceType() {
        return acceptanceType;
    }

    public void setAcceptanceType(String acceptanceType) {
        this.acceptanceType = acceptanceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getDccId() {
        return dccId;
    }

    public void setDccId(String dccId) {
        this.dccId = dccId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPoDate() {
        return poDate;
    }

    public void setPoDate(String poDate) {
        this.poDate = poDate;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public Integer getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(Integer totalQty) {
        this.totalQty = totalQty;
    }

    public Integer getTotalNoofSites() {
        return totalNoofSites;
    }

    public void setTotalNoofSites(Integer totalNoofSites) {
        this.totalNoofSites = totalNoofSites;
    }

    public String getNewFACategory() {
        return newFACategory;
    }

    public void setNewFACategory(String newFACategory) {
        this.newFACategory = newFACategory;
    }

    public String getL1() {
        return l1;
    }

    public void setL1(String l1) {
        this.l1 = l1;
    }

    public String getL2() {
        return l2;
    }

    public void setL2(String l2) {
        this.l2 = l2;
    }

    public String getL3() {
        return l3;
    }

    public void setL3(String l3) {
        this.l3 = l3;
    }

    public String getL4() {
        return l4;
    }

    public void setL4(String l4) {
        this.l4 = l4;
    }

    public String getOldFACategory() {
        return oldFACategory;
    }

    public void setOldFACategory(String oldFACategory) {
        this.oldFACategory = oldFACategory;
    }

    public String getAccDepreciationCode() {
        return accDepreciationCode;
    }

    public void setAccDepreciationCode(String accDepreciationCode) {
        this.accDepreciationCode = accDepreciationCode;
    }

    public String getDepreciationCode() {
        return depreciationCode;
    }

    public void setDepreciationCode(String depreciationCode) {
        this.depreciationCode = depreciationCode;
    }

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getProjectNumber() {
        return projectNumber;
    }

    public void setProjectNumber(String projectNumber) {
        this.projectNumber = projectNumber;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(String termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(Date approvedDate) {
        this.approvedDate = approvedDate;
    }

    public String getVendorComment() {
        return vendorComment;
    }

    public void setVendorComment(String vendorComment) {
        this.vendorComment = vendorComment;
    }
}