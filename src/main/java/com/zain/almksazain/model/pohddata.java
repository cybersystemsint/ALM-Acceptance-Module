package com.zain.almksazain.model;

import javax.persistence.*;
import java.sql.Date;

@Entity
@Table(name = "tb_PO_HD")
public class pohddata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public String recordDatetime;
    public String poId;
    public String poDate;
    public String supplierId;
    public String termsAndConditions;
    public String deliveryLocationId;
    public String createdBy;
    public String approvedBy;
    public String approvalDate;
    public String status;
    //NEW

    public String modelNumber;
    public String unitOfMeasure;
    public Integer qtyPerSite;
    public Integer totalNoofSites;
    public Integer totalQty;
    public double accDepreciation;
    public double salvageValue;
    public String newFACategory;
    public String L1;
    public String L2;
    public String L3;
    public String L4;
    public String oldFACategory;
    public String accDepreciationCode;
    public String depreciationCode;
    public Integer lifeYears;
    public String vendorName;
    public String vendorNumber;
    public String projectNumber;
    public Date dateInService;
    public String currency;
    public double unitPrice;
    public String partNumber;
    public String costCenter;
    public boolean approved;
    public boolean delivered;

    //NEW COLUMNS 
    public String typeLookUpCode;
    public String releaseNum;
    public String prNum;
    public String pnSubAllow;
    public String countryOfOrigin;
    public String currencyCode;
    public String subInventoryCode;
    public String receiptRouting;
    public String poClosureStatus;
    public String chargeAccount;
    public String serialControl;

//    //MORE COLUMNS      
//    public double blanketTotalAmount;
//    public Integer poQtyNew;
//    public Integer quantityReceived;
//    public String organizationName;
//    public String organizationCode;
//    public String authorisationStatus;
//    public String departmentName;
//    public String acceptanceType;
    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public String getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(String recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
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

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(String termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public String getDeliveryLocationId() {
        return deliveryLocationId;
    }

    public void setDeliveryLocationId(String deliveryLocationId) {
        this.deliveryLocationId = deliveryLocationId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovalDate() {
        return approvalDate;
    }

    public void setApprovalDate(String approvalDate) {
        this.approvalDate = approvalDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    //new
    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public Integer getQtyPerSite() {
        return qtyPerSite;
    }

    public void setQtyPerSite(Integer qtyPerSite) {
        this.qtyPerSite = qtyPerSite;
    }

    public Integer getTotalNoofSites() {
        return totalNoofSites;
    }

    public void setTotalNoofSites(Integer totalNoofSites) {
        this.totalNoofSites = totalNoofSites;
    }

    public Integer getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(Integer totalQty) {
        this.totalQty = totalQty;
    }

    public double getAccDepreciation() {
        return accDepreciation;
    }

    public void setAccDepreciation(double accDepreciation) {
        this.accDepreciation = accDepreciation;
    }

    public double getSalvageValue() {
        return salvageValue;
    }

    public void setSalvageValue(double salvageValue) {
        this.salvageValue = salvageValue;
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

    public Integer getLifeYears() {
        return lifeYears;
    }

    public void setLifeYears(Integer lifeYears) {
        this.lifeYears = lifeYears;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
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

    public Date getDateInService() {
        return dateInService;
    }

    public void setDateInService(Date dateInService) {
        this.dateInService = dateInService;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
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

    public String getTypeLookUpCode() {
        return typeLookUpCode;
    }

    public void setTypeLookUpCode(String typeLookUpCode) {
        this.typeLookUpCode = typeLookUpCode;
    }

    public String getReleaseNum() {
        return releaseNum;
    }

    public void setReleaseNum(String releaseNum) {
        this.releaseNum = releaseNum;
    }

    public String getPrNum() {
        return prNum;
    }

    public void setPrNum(String prNum) {
        this.prNum = prNum;
    }

    public String getPnSubAllow() {
        return pnSubAllow;
    }

    public void setPnSubAllow(String pnSubAllow) {
        this.pnSubAllow = pnSubAllow;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getSubInventoryCode() {
        return subInventoryCode;
    }

    public void setSubInventoryCode(String subInventoryCode) {
        this.subInventoryCode = subInventoryCode;
    }

    public String getReceiptRouting() {
        return receiptRouting;
    }

    public void setReceiptRouting(String receiptRouting) {
        this.receiptRouting = receiptRouting;
    }

    public String getPoClosureStatus() {
        return poClosureStatus;
    }

    public void setPoClosureStatus(String poClosureStatus) {
        this.poClosureStatus = poClosureStatus;
    }

    public String getChargeAccount() {
        return chargeAccount;
    }

    public void setChargeAccount(String chargeAccount) {
        this.chargeAccount = chargeAccount;
    }

    public String getSerialControl() {
        return serialControl;
    }

    public void setSerialControl(String serialControl) {
        this.serialControl = serialControl;
    }

}
