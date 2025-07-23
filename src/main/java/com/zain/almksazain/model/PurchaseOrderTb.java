package com.zain.almksazain.model;

import javax.persistence.*;
@Entity
@Table(name = "tb_PurchaseOrder")
public class PurchaseOrderTb {
    
    @Id
    private String recordNo;
    private String poNumber;
    private String vendorNumber;
    private Integer lineNumber;
    private String itemPartNumber;
    private String countryOfOrigin;
    private Double poOrderQuantity;
    private Double poQtyNew;
    private Double quantityReceived;
    private Double quantityDueOld;
    private Double quantityDueNew;
    private Double quantityBilled;
    private Double unitPriceInPoCurrency;
    private Double unitPriceInSAR;
    private Double linePriceInPoCurrency;
    private Double linePriceInSAR;
    private Double amountReceived;
    private Double amountDue;
    private Double amountDueNew;
    private Double amountBilled;
    private String poLineDescription;
    private String  vendorSerialNumberYN;
    private String itemCategoryInventory;
    private String inventoryCategoryDescription;
    private String itemCategoryFA;
    private String FACategoryDescription;
    private String  lineCancelFlag;
    private String  prSubAllow;
    private Double descopedLinePriceInPoCurrency;
    private Double newLinePriceInPoCurrency;
    
    // New fields from DTO
    private String typeLookUpCode;
    private Double blanketTotalAmount;
    private String releaseNum;
    private String prNum;
    private String projectName;
    private String newProjectName;
    private String cancelReason;
    private String currencyCode;
    private String organizationName;
    private String organizationCode;
    private String subInventoryCode;
    private String receiptRouting;
    private String authorisationStatus;
    private String poClosureStatus;
    private String departmentName;
    private String businessOwner;
    private String poLineType;
    private String acceptanceType;
    private String costCenter;
    private String chargeAccount;
    private String serialControl;
    private String itemType;
    private String itemCategoryPurchasing;
    private String purchasingCategoryDescription;
    private String vendorName;
    private String approvedDate;
    private String createdDate;
    private String createdBy;
    private String createdByName;
    public String getRecordNo() {
        return recordNo;
    }
    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }
    public String getPoNumber() {
        return poNumber;
    }
    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }
    public String getVendorNumber() {
        return vendorNumber;
    }
    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }
    public Integer getLineNumber() {
        return lineNumber;
    }
    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
    public String getItemPartNumber() {
        return itemPartNumber;
    }
    public void setItemPartNumber(String itemPartNumber) {
        this.itemPartNumber = itemPartNumber;
    }
    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }
    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }
    public Double getPoOrderQuantity() {
        return poOrderQuantity;
    }
    public void setPoOrderQuantity(Double poOrderQuantity) {
        this.poOrderQuantity = poOrderQuantity;
    }
    public Double getPoQtyNew() {
        return poQtyNew;
    }
    public void setPoQtyNew(Double poQtyNew) {
        this.poQtyNew = poQtyNew;
    }
    public Double getQuantityReceived() {
        return quantityReceived;
    }
    public void setQuantityReceived(Double quantityReceived) {
        this.quantityReceived = quantityReceived;
    }
    public Double getQuantityDueOld() {
        return quantityDueOld;
    }
    public void setQuantityDueOld(Double quantityDueOld) {
        this.quantityDueOld = quantityDueOld;
    }
    public Double getQuantityDueNew() {
        return quantityDueNew;
    }
    public void setQuantityDueNew(Double quantityDueNew) {
        this.quantityDueNew = quantityDueNew;
    }
    public Double getQuantityBilled() {
        return quantityBilled;
    }
    public void setQuantityBilled(Double quantityBilled) {
        this.quantityBilled = quantityBilled;
    }
    public Double getUnitPriceInPoCurrency() {
        return unitPriceInPoCurrency;
    }
    public void setUnitPriceInPoCurrency(Double unitPriceInPoCurrency) {
        this.unitPriceInPoCurrency = unitPriceInPoCurrency;
    }
    public Double getUnitPriceInSAR() {
        return unitPriceInSAR;
    }
    public void setUnitPriceInSAR(Double unitPriceInSAR) {
        this.unitPriceInSAR = unitPriceInSAR;
    }
    public Double getLinePriceInPoCurrency() {
        return linePriceInPoCurrency;
    }
    public void setLinePriceInPoCurrency(Double linePriceInPoCurrency) {
        this.linePriceInPoCurrency = linePriceInPoCurrency;
    }
    public Double getLinePriceInSAR() {
        return linePriceInSAR;
    }
    public void setLinePriceInSAR(Double linePriceInSAR) {
        this.linePriceInSAR = linePriceInSAR;
    }
    public Double getAmountReceived() {
        return amountReceived;
    }
    public void setAmountReceived(Double amountReceived) {
        this.amountReceived = amountReceived;
    }
    public Double getAmountDue() {
        return amountDue;
    }
    public void setAmountDue(Double amountDue) {
        this.amountDue = amountDue;
    }
    public Double getAmountDueNew() {
        return amountDueNew;
    }
    public void setAmountDueNew(Double amountDueNew) {
        this.amountDueNew = amountDueNew;
    }
    public Double getAmountBilled() {
        return amountBilled;
    }
    public void setAmountBilled(Double amountBilled) {
        this.amountBilled = amountBilled;
    }
    public String getPoLineDescription() {
        return poLineDescription;
    }
    public void setPoLineDescription(String poLineDescription) {
        this.poLineDescription = poLineDescription;
    }
    public String  getVendorSerialNumberYN() {
        return vendorSerialNumberYN;
    }
    public void setVendorSerialNumberYN(String  vendorSerialNumberYN) {
        this.vendorSerialNumberYN = vendorSerialNumberYN;
    }
    public String getItemCategoryInventory() {
        return itemCategoryInventory;
    }
    public void setItemCategoryInventory(String itemCategoryInventory) {
        this.itemCategoryInventory = itemCategoryInventory;
    }
    public String getInventoryCategoryDescription() {
        return inventoryCategoryDescription;
    }
    public void setInventoryCategoryDescription(String inventoryCategoryDescription) {
        this.inventoryCategoryDescription = inventoryCategoryDescription;
    }
    public String getItemCategoryFA() {
        return itemCategoryFA;
    }
    public void setItemCategoryFA(String itemCategoryFA) {
        this.itemCategoryFA = itemCategoryFA;
    }
    public String getFACategoryDescription() {
        return FACategoryDescription;
    }
    public void setFACategoryDescription(String fACategoryDescription) {
        FACategoryDescription = fACategoryDescription;
    }
    public String  getLineCancelFlag() {
        return lineCancelFlag;
    }
    public void setLineCancelFlag(String  lineCancelFlag) {
        this.lineCancelFlag = lineCancelFlag;
    }
    public String  getPrSubAllow() {
        return prSubAllow;
    }
    public void setPrSubAllow(String  prSubAllow) {
        this.prSubAllow = prSubAllow;
    }
    public Double getDescopedLinePriceInPoCurrency() {
        return descopedLinePriceInPoCurrency;
    }
    public void setDescopedLinePriceInPoCurrency(Double descopedLinePriceInPoCurrency) {
        this.descopedLinePriceInPoCurrency = descopedLinePriceInPoCurrency;
    }
    public Double getNewLinePriceInPoCurrency() {
        return newLinePriceInPoCurrency;
    }
    public void setNewLinePriceInPoCurrency(Double newLinePriceInPoCurrency) {
        this.newLinePriceInPoCurrency = newLinePriceInPoCurrency;
    }
    public String getTypeLookUpCode() {
        return typeLookUpCode;
    }
    public void setTypeLookUpCode(String typeLookUpCode) {
        this.typeLookUpCode = typeLookUpCode;
    }
    public Double getBlanketTotalAmount() {
        return blanketTotalAmount;
    }
    public void setBlanketTotalAmount(Double blanketTotalAmount) {
        this.blanketTotalAmount = blanketTotalAmount;
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
    public String getCancelReason() {
        return cancelReason;
    }
    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
    public String getCurrencyCode() {
        return currencyCode;
    }
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }
    public String getOrganizationName() {
        return organizationName;
    }
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }
    public String getOrganizationCode() {
        return organizationCode;
    }
    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
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
    public String getAuthorisationStatus() {
        return authorisationStatus;
    }
    public void setAuthorisationStatus(String authorisationStatus) {
        this.authorisationStatus = authorisationStatus;
    }
    public String getPoClosureStatus() {
        return poClosureStatus;
    }
    public void setPoClosureStatus(String poClosureStatus) {
        this.poClosureStatus = poClosureStatus;
    }
    public String getDepartmentName() {
        return departmentName;
    }
    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
    public String getBusinessOwner() {
        return businessOwner;
    }
    public void setBusinessOwner(String businessOwner) {
        this.businessOwner = businessOwner;
    }
    public String getPoLineType() {
        return poLineType;
    }
    public void setPoLineType(String poLineType) {
        this.poLineType = poLineType;
    }
    public String getAcceptanceType() {
        return acceptanceType;
    }
    public void setAcceptanceType(String acceptanceType) {
        this.acceptanceType = acceptanceType;
    }
    public String getCostCenter() {
        return costCenter;
    }
    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
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
    public String getItemType() {
        return itemType;
    }
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    public String getItemCategoryPurchasing() {
        return itemCategoryPurchasing;
    }
    public void setItemCategoryPurchasing(String itemCategoryPurchasing) {
        this.itemCategoryPurchasing = itemCategoryPurchasing;
    }
    public String getPurchasingCategoryDescription() {
        return purchasingCategoryDescription;
    }
    public void setPurchasingCategoryDescription(String purchasingCategoryDescription) {
        this.purchasingCategoryDescription = purchasingCategoryDescription;
    }
    public String getVendorName() {
        return vendorName;
    }
    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
    public String getApprovedDate() {
        return approvedDate;
    }
    public void setApprovedDate(String approvedDate) {
        this.approvedDate = approvedDate;
    }
    public String getCreatedDate() {
        return createdDate;
    }
    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
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

   
    
}
