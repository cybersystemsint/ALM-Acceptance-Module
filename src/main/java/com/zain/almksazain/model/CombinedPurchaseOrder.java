package com.zain.almksazain.model;


import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "combinedPurchaseOrderView")
public class CombinedPurchaseOrder {
    @Id
    @Column(name = "poRecordNo")
    private Long poRecordNo;

    @Column(name = "poNumber")
    private String poNumber;
    @Column(name = "typeLookUpCode")
    private String typeLookUpCode;
    @Column(name = "blanketTotalAmount")
    private BigDecimal blanketTotalAmount;
    @Column(name = "releaseNum")
    private String releaseNum;
    @Column(name = "lineNumber")
    private Integer lineNumber;
    @Column(name = "prNum")
    private String prNum;
    @Column(name = "poProjectName")
    private String poProjectName;
    @Column(name = "newProjectName")
    private String newProjectName;
    @Column(name = "itemPartNumber")
    private String itemPartNumber;
    @Column(name = "prSubAllow")
    private Boolean prSubAllow;
    @Column(name = "poCountryOfOrigin")
    private String poCountryOfOrigin;
    @Column(name = "poOrderQuantity")
    private Double poOrderQuantity;
    @Column(name = "UPLACPTRequestValue")
    private Double UPLACPTRequestValue;
    @Column(name = "POAcceptanceQty")
    private Double POAcceptanceQty;
    @Column(name = "POLineAcceptanceQty")
    private Double POLineAcceptanceQty;
    @Column(name = "poPendingQuantity")
    private Double poPendingQuantity;
    @Column(name = "poQtyNew")
    private Double poQtyNew;
    @Column(name = "quantityReceived")
    private Double quantityReceived;
    @Column(name = "poCurrencyCode")
    private String poCurrencyCode;
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
    @Column(name = "poLineDescription", columnDefinition = "longtext")
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
    private String authorisationStatus;
    @Column(name = "departmentName")
    private String departmentName;
    @Column(name = "businessOwner")
    private String businessOwner;
    @Column(name = "poLineType")
    private String poLineType;
    @Column(name = "poAcceptanceType")
    private String poAcceptanceType;
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
    private String FACategoryDescription;
    @Column(name = "itemCategoryPurchasing")
    private String itemCategoryPurchasing;
    @Column(name = "PurchasingCategoryDescription")
    private String PurchasingCategoryDescription;
    @Column(name = "poVendorName")
    private String poVendorName;
    @Column(name = "poVendorNumber")
    private String poVendorNumber;
    @Column(name = "poApprovedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date poApprovedDate;
    @Column(name = "poCreatedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date poCreatedDate;
    @Column(name = "poCreatedBy")
    private Integer poCreatedBy;
    @Column(name = "poCreatedByName")
    private String poCreatedByName;
    @Column(name = "uplRecordNo")
    private Integer uplRecordNo;
    @Column(name = "uplManufacturer")
    private String uplManufacturer;
    @Column(name = "uplCountryOfOrigin")
    private String uplCountryOfOrigin;
    @Column(name = "uplReleaseNumber")
    private String uplReleaseNumber;
    @Column(name = "uplLine")
    private String uplLine;
    @Column(name = "uplPoLineItemType")
    private String uplPoLineItemType;
    @Column(name = "uplPoLineItemCode")
    private String uplPoLineItemCode;
    @Column(name = "uplPoLineDescription")
    private String uplPoLineDescription;
    @Column(name = "uplLineItemType")
    private String uplLineItemType;
    @Column(name = "uplLineItemCode")
    private String uplLineItemCode;
    @Column(name = "uplLineDescription")
    private String uplLineDescription;
    @Column(name = "zainItemCategoryCode")
    private String zainItemCategoryCode;
    @Column(name = "zainItemCategoryDescription")
    private String zainItemCategoryDescription;
    @Column(name = "uplItemSerialized")
    private String uplItemSerialized;
    @Column(name = "activeOrPassive")
    private String activeOrPassive;
    @Column(name = "uplUom")
    private String uplUom;
    @Column(name = "uplCurrency")
    private String uplCurrency;
    @Column(name = "uplPoLineQuantity")
    private Double uplPoLineQuantity;
    @Column(name = "uplPoLineUnitPrice")
    private Double uplPoLineUnitPrice;
    @Column(name = "uplLineQuantity")
    private Double uplLineQuantity;
    @Column(name = "uplLineUnitPrice")
    private Double uplLineUnitPrice;
    @Column(name = "substituteItemCode")
    private String substituteItemCode;
    @Column(name = "uplRemarks")
    private String uplRemarks;
    @Column(name = "scopeOfWork")
    private String scopeOfWork;
    @Column(name = "dptApprover1")
    private String dptApprover1;
    @Column(name = "dptApprover2")
    private String dptApprover2;
    @Column(name = "dptApprover3")
    private String dptApprover3;
    @Column(name = "dptApprover4")
    private String dptApprover4;
    @Column(name = "regionalApprover")
    private String regionalApprover;
    @Column(name = "uplCreatedBy")
    private Integer uplCreatedBy;
    @Column(name = "uplCreatedByName")
    private String uplCreatedByName;
    @Column(name = "canRaiseAcceptance")
    private String canRaiseAcceptance;
    @Column(name = "uplPendingQuantity")
    private Double uplPendingQuantity;
    @Column(name = "categoryDescription")
    private String categoryDescription;
    public Long getPoRecordNo() {
        return poRecordNo;
    }
    public void setPoRecordNo(Long poRecordNo) {
        this.poRecordNo = poRecordNo;
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
    public BigDecimal getBlanketTotalAmount() {
        return blanketTotalAmount;
    }
    public void setBlanketTotalAmount(BigDecimal blanketTotalAmount) {
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
    public String getPoProjectName() {
        return poProjectName;
    }
    public void setPoProjectName(String poProjectName) {
        this.poProjectName = poProjectName;
    }
    public String getNewProjectName() {
        return newProjectName;
    }
    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName;
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
    public String getPoCountryOfOrigin() {
        return poCountryOfOrigin;
    }
    public void setPoCountryOfOrigin(String poCountryOfOrigin) {
        this.poCountryOfOrigin = poCountryOfOrigin;
    }
    public Double getPoOrderQuantity() {
        return poOrderQuantity;
    }
    public void setPoOrderQuantity(Double poOrderQuantity) {
        this.poOrderQuantity = poOrderQuantity;
    }
    public Double getUPLACPTRequestValue() {
        return UPLACPTRequestValue;
    }
    public void setUPLACPTRequestValue(Double uPLACPTRequestValue) {
        UPLACPTRequestValue = uPLACPTRequestValue;
    }
    public Double getPOAcceptanceQty() {
        return POAcceptanceQty;
    }
    public void setPOAcceptanceQty(Double pOAcceptanceQty) {
        POAcceptanceQty = pOAcceptanceQty;
    }
    public Double getPOLineAcceptanceQty() {
        return POLineAcceptanceQty;
    }
    public void setPOLineAcceptanceQty(Double pOLineAcceptanceQty) {
        POLineAcceptanceQty = pOLineAcceptanceQty;
    }
    public Double getPoPendingQuantity() {
        return poPendingQuantity;
    }
    public void setPoPendingQuantity(Double poPendingQuantity) {
        this.poPendingQuantity = poPendingQuantity;
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
    public String getPoCurrencyCode() {
        return poCurrencyCode;
    }
    public void setPoCurrencyCode(String poCurrencyCode) {
        this.poCurrencyCode = poCurrencyCode;
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
    public String getAuthorisationStatus() {
        return authorisationStatus;
    }
    public void setAuthorisationStatus(String authorisationStatus) {
        this.authorisationStatus = authorisationStatus;
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
    public String getPoAcceptanceType() {
        return poAcceptanceType;
    }
    public void setPoAcceptanceType(String poAcceptanceType) {
        this.poAcceptanceType = poAcceptanceType;
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
    public String getFACategoryDescription() {
        return FACategoryDescription;
    }
    public void setFACategoryDescription(String fACategoryDescription) {
        FACategoryDescription = fACategoryDescription;
    }
    public String getItemCategoryPurchasing() {
        return itemCategoryPurchasing;
    }
    public void setItemCategoryPurchasing(String itemCategoryPurchasing) {
        this.itemCategoryPurchasing = itemCategoryPurchasing;
    }
    public String getPurchasingCategoryDescription() {
        return PurchasingCategoryDescription;
    }
    public void setPurchasingCategoryDescription(String purchasingCategoryDescription) {
        PurchasingCategoryDescription = purchasingCategoryDescription;
    }
    public String getPoVendorName() {
        return poVendorName;
    }
    public void setPoVendorName(String poVendorName) {
        this.poVendorName = poVendorName;
    }
    public String getPoVendorNumber() {
        return poVendorNumber;
    }
    public void setPoVendorNumber(String poVendorNumber) {
        this.poVendorNumber = poVendorNumber;
    }
    public Date getPoApprovedDate() {
        return poApprovedDate;
    }
    public void setPoApprovedDate(Date poApprovedDate) {
        this.poApprovedDate = poApprovedDate;
    }
    public Date getPoCreatedDate() {
        return poCreatedDate;
    }
    public void setPoCreatedDate(Date poCreatedDate) {
        this.poCreatedDate = poCreatedDate;
    }
    public Integer getPoCreatedBy() {
        return poCreatedBy;
    }
    public void setPoCreatedBy(Integer poCreatedBy) {
        this.poCreatedBy = poCreatedBy;
    }
    public String getPoCreatedByName() {
        return poCreatedByName;
    }
    public void setPoCreatedByName(String poCreatedByName) {
        this.poCreatedByName = poCreatedByName;
    }
    public Integer getUplRecordNo() {
        return uplRecordNo;
    }
    public void setUplRecordNo(Integer uplRecordNo) {
        this.uplRecordNo = uplRecordNo;
    }
    public String getUplManufacturer() {
        return uplManufacturer;
    }
    public void setUplManufacturer(String uplManufacturer) {
        this.uplManufacturer = uplManufacturer;
    }
    public String getUplCountryOfOrigin() {
        return uplCountryOfOrigin;
    }
    public void setUplCountryOfOrigin(String uplCountryOfOrigin) {
        this.uplCountryOfOrigin = uplCountryOfOrigin;
    }
    public String getUplReleaseNumber() {
        return uplReleaseNumber;
    }
    public void setUplReleaseNumber(String uplReleaseNumber) {
        this.uplReleaseNumber = uplReleaseNumber;
    }
    public String getUplLine() {
        return uplLine;
    }
    public void setUplLine(String uplLine) {
        this.uplLine = uplLine;
    }
    public String getUplPoLineItemType() {
        return uplPoLineItemType;
    }
    public void setUplPoLineItemType(String uplPoLineItemType) {
        this.uplPoLineItemType = uplPoLineItemType;
    }
    public String getUplPoLineItemCode() {
        return uplPoLineItemCode;
    }
    public void setUplPoLineItemCode(String uplPoLineItemCode) {
        this.uplPoLineItemCode = uplPoLineItemCode;
    }
    public String getUplPoLineDescription() {
        return uplPoLineDescription;
    }
    public void setUplPoLineDescription(String uplPoLineDescription) {
        this.uplPoLineDescription = uplPoLineDescription;
    }
    public String getUplLineItemType() {
        return uplLineItemType;
    }
    public void setUplLineItemType(String uplLineItemType) {
        this.uplLineItemType = uplLineItemType;
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
    public String getZainItemCategoryCode() {
        return zainItemCategoryCode;
    }
    public void setZainItemCategoryCode(String zainItemCategoryCode) {
        this.zainItemCategoryCode = zainItemCategoryCode;
    }
    public String getZainItemCategoryDescription() {
        return zainItemCategoryDescription;
    }
    public void setZainItemCategoryDescription(String zainItemCategoryDescription) {
        this.zainItemCategoryDescription = zainItemCategoryDescription;
    }
    public String getUplItemSerialized() {
        return uplItemSerialized;
    }
    public void setUplItemSerialized(String uplItemSerialized) {
        this.uplItemSerialized = uplItemSerialized;
    }
    public String getActiveOrPassive() {
        return activeOrPassive;
    }
    public void setActiveOrPassive(String activeOrPassive) {
        this.activeOrPassive = activeOrPassive;
    }
    public String getUplUom() {
        return uplUom;
    }
    public void setUplUom(String uplUom) {
        this.uplUom = uplUom;
    }
    public String getUplCurrency() {
        return uplCurrency;
    }
    public void setUplCurrency(String uplCurrency) {
        this.uplCurrency = uplCurrency;
    }
    public Double getUplPoLineQuantity() {
        return uplPoLineQuantity;
    }
    public void setUplPoLineQuantity(Double uplPoLineQuantity) {
        this.uplPoLineQuantity = uplPoLineQuantity;
    }
    public Double getUplPoLineUnitPrice() {
        return uplPoLineUnitPrice;
    }
    public void setUplPoLineUnitPrice(Double uplPoLineUnitPrice) {
        this.uplPoLineUnitPrice = uplPoLineUnitPrice;
    }
    public Double getUplLineQuantity() {
        return uplLineQuantity;
    }
    public void setUplLineQuantity(Double uplLineQuantity) {
        this.uplLineQuantity = uplLineQuantity;
    }
    public Double getUplLineUnitPrice() {
        return uplLineUnitPrice;
    }
    public void setUplLineUnitPrice(Double uplLineUnitPrice) {
        this.uplLineUnitPrice = uplLineUnitPrice;
    }
    public String getSubstituteItemCode() {
        return substituteItemCode;
    }
    public void setSubstituteItemCode(String substituteItemCode) {
        this.substituteItemCode = substituteItemCode;
    }
    public String getUplRemarks() {
        return uplRemarks;
    }
    public void setUplRemarks(String uplRemarks) {
        this.uplRemarks = uplRemarks;
    }
    public String getScopeOfWork() {
        return scopeOfWork;
    }
    public void setScopeOfWork(String scopeOfWork) {
        this.scopeOfWork = scopeOfWork;
    }
    public String getDptApprover1() {
        return dptApprover1;
    }
    public void setDptApprover1(String dptApprover1) {
        this.dptApprover1 = dptApprover1;
    }
    public String getDptApprover2() {
        return dptApprover2;
    }
    public void setDptApprover2(String dptApprover2) {
        this.dptApprover2 = dptApprover2;
    }
    public String getDptApprover3() {
        return dptApprover3;
    }
    public void setDptApprover3(String dptApprover3) {
        this.dptApprover3 = dptApprover3;
    }
    public String getDptApprover4() {
        return dptApprover4;
    }
    public void setDptApprover4(String dptApprover4) {
        this.dptApprover4 = dptApprover4;
    }
    public String getRegionalApprover() {
        return regionalApprover;
    }
    public void setRegionalApprover(String regionalApprover) {
        this.regionalApprover = regionalApprover;
    }
    public Integer getUplCreatedBy() {
        return uplCreatedBy;
    }
    public void setUplCreatedBy(Integer uplCreatedBy) {
        this.uplCreatedBy = uplCreatedBy;
    }
    public String getUplCreatedByName() {
        return uplCreatedByName;
    }
    public void setUplCreatedByName(String uplCreatedByName) {
        this.uplCreatedByName = uplCreatedByName;
    }
    public String getCanRaiseAcceptance() {
        return canRaiseAcceptance;
    }
    public void setCanRaiseAcceptance(String canRaiseAcceptance) {
        this.canRaiseAcceptance = canRaiseAcceptance;
    }
    public Double getUplPendingQuantity() {
        return uplPendingQuantity;
    }
    public void setUplPendingQuantity(Double uplPendingQuantity) {
        this.uplPendingQuantity = uplPendingQuantity;
    }
    public String getCategoryDescription() {
        return categoryDescription;
    }
    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
    }


}

