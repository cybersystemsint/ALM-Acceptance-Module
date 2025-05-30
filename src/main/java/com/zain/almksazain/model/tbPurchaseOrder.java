package com.zain.almksazain.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "tb_PurchaseOrder")
public class tbPurchaseOrder {

    @Id
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "typeLookUpCode")
    private String typeLookUpCode;

    @Column(name = "blanketTotalAmount")
    private Double blanketTotalAmount;

    @Column(name = "releaseNum")
    private String releaseNum;

    @Column(name = "lineNumber")
    private Integer lineNumber;

    @Column(name = "prNum")
    private String prNum;

    @Column(name = "projectName")
    private String projectName;

    @Column(name = "newProjectName")
    private String newProjectName;

    @Column(name = "lineCancelFlag")
    private Boolean lineCancelFlag;

    @Column(name = "cancelReason")
    private String cancelReason;

    @Column(name = "itemPartNumber")
    private String itemPartNumber;

    @Column(name = "prSubAllow")
    private Boolean prSubAllow;

    @Column(name = "countryOfOrigin")
    private String countryOfOrigin;

    @Column(name = "poOrderQuantity")
    private Double amountDueLine;

    @Column(name = "poQtyNew")
    private Double poQtyNew;

    @Column(name = "quantityReceived")
    private Double quantityReceived;

    @Column(name = "quantityDueOld")
    private Double quantityDueOld;

    @Column(name = "quantityDueNew")
    private Double quantityDueNew;

    @Column(name = "quantityBilled")
    private Double quantityBilled;

    @Column(name = "currencyCode")
    private String currencyCode;

    @Column(name = "unitPriceInPoCurrency")
    private Double unitPriceInPoCurrency;

    @Column(name = "unitPriceInSAR")
    private Double unitPriceInSAR;

    @Column(name = "linePriceInPoCurrency")
    private Double linePriceInPoCurrency;

    @Column(name = "linePriceInSAR")
    private Double linePriceInSAR;

    @Column(name = "amountReceived")
    private Double amountReceived;

    @Column(name = "amountDue")
    private Double amountDue;

    @Column(name = "amountDueNew")
    private Double amountDueNew;

    @Column(name = "amountBilled")
    private Double amountBilled;

    @Column(name = "poLineDescription")
    private String poLineDescription;

    @Column(name = "organizationName")
    private String organizationName;

    @Column(name = "organizationCode")
    private String organizationCode;

    @Column(name = "subInventoryCode")
    private String subInventoryCode;

    @Column(name = "receiptRouting")
    private String receiptRouting;

    @Column(name = "authorisationStatus")
    private String authorizationStatus;

    @Column(name = "poClosureStatus")
    private String poClosureStatus;

    @Column(name = "departmentName")
    private String departmentName;

    @Column(name = "businessOwner")
    private String businessOwner;

    @Column(name = "poLineType")
    private String poLineType;

    @Column(name = "acceptanceType")
    private String acceptanceType;

    @Column(name = "costCenter")
    private String costCenter;

    @Column(name = "chargeAccount")
    private String chargeAccount;

    @Column(name = "serialControl")
    private String serialControl;

    @Column(name = "vendorSerialNumberYN")
    private String vendorSerialNumberYN;

    @Column(name = "itemType")
    private String itemType;

    @Column(name = "itemCategoryInventory")
    private String itemCategoryInventory;

    @Column(name = "inventoryCategoryDescription")
    private String inventoryCategoryDescription;

    @Column(name = "itemCategoryFA")
    private String itemCategoryFA;

    @Column(name = "FACategoryDescription")
    private String faCategoryDescription;

    @Column(name = "itemCategoryPurchasing")
    private String itemCategoryPurchasing;

    @Column(name = "PurchasingCategoryDescription")
    private String purchasingCategoryDescription;

    @Column(name = "vendorName")
    private String vendorName;

    @Column(name = "vendorNumber")
    private String vendorNumber;

    @Column(name = "approvedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date approvedDate;

    @Column(name = "createdDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(name = "createdBy")
    private Integer createdBy;

    @Column(name = "createdByName")
    private String createdByName;

    @Column(name = "descopedLinePriceInPoCurrency")
    private Double descopedLinePriceInPoCurrency;

    @Column(name = "newLinePriceInPoCurrency")
    private Double newLinePriceInPoCurrency;

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

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
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

    public Boolean getLineCancelFlag() {
        return lineCancelFlag;
    }

    public void setLineCancelFlag(Boolean lineCancelFlag) {
        this.lineCancelFlag = lineCancelFlag;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getItemPartNumber() {
        return itemPartNumber;
    }

    public void setItemPartNumber(String itemPartNumber) {
        this.itemPartNumber = itemPartNumber;
    }

    public Boolean getPrSubAllow() {
        return prSubAllow;
    }

    public void setPrSubAllow(Boolean prSubAllow) {
        this.prSubAllow = prSubAllow;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public Double getAmountDueLine() {
        return amountDueLine;
    }

    public void setAmountDueLine(Double amountDueLine) {
        this.amountDueLine = amountDueLine;
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

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
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

    public String getAuthorizationStatus() {
        return authorizationStatus;
    }

    public void setAuthorizationStatus(String authorizationStatus) {
        this.authorizationStatus = authorizationStatus;
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

    public String getVendorSerialNumberYN() {
        return vendorSerialNumberYN;
    }

    public void setVendorSerialNumberYN(String vendorSerialNumberYN) {
        this.vendorSerialNumberYN = vendorSerialNumberYN;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
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

    public String getFaCategoryDescription() {
        return faCategoryDescription;
    }

    public void setFaCategoryDescription(String faCategoryDescription) {
        this.faCategoryDescription = faCategoryDescription;
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

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public Date getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(Date approvedDate) {
        this.approvedDate = approvedDate;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
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
}