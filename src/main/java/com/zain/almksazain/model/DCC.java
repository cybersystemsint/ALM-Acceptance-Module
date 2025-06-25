package com.zain.almksazain.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "tb_DCC")
public class DCC {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private String poNumber;
    private String vendorName;
    private String vendorEmail;
    private String boqId;
    private String projectName;
    private String acceptanceType;
    private String status;
    private Date createdDate;
    private String dccId;
    private String currency;

    private String poDate;
    private String supplierId;
    private Integer totalQty;
    private Integer totalNoofSites;
    private String newFACategory;
    private String L1;
    private String L2;
    private String L3;
    private String L4;
    private String oldFACategory;
    private String accDepreciationCode;
    private String depreciationCode;
    private String vendorNumber;
    private String projectNumber;
    private String partNumber;
    private String costCenter;
    private String termsAndConditions;
    private String createdBy;
    private String vendorComment;

    // Constructors, getters, and setters
    // Constructor
    public DCC() {
    }

    public long getRecordNo() {
        return recordNo;
    }

    public String getVendorComment() {
        return vendorComment;
    }

    public void setVendorComment(String vendorComment) {
        this.vendorComment = vendorComment;
    }

    public void setRecordNo(long recordNo) {
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
        return L1;
    }

    public void setL1(String L1) {
        this.L1 = L1;
    }

    public String getL2() {
        return L2;
    }

    public void setL2(String L2) {
        this.L2 = L2;
    }

    public String getL3() {
        return L3;
    }

    public void setL3(String L3) {
        this.L3 = L3;
    }

    public String getL4() {
        return L4;
    }

    public void setL4(String L4) {
        this.L4 = L4;
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

}
