package com.zain.almksazain.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class CombinedPurchaseOrderRow {

    private Long poRecordNo;
    private String poNumber;
    private String typeLookUpCode;
    private Double blanketTotalAmount;
    private String releaseNum;
    private Integer lineNumber;
    private String prNum;
    private String poProjectName;
    private String newProjectName;
    private String itemPartNumber;
    private Boolean prSubAllow;
    private String poCountryOfOrigin;
    private Double poOrderQuantity;
    private Double uplAcptRequestValue;
    private Double poAcceptanceQty;
    private Double poLineAcceptanceQty;
    private Double poPendingQuantity;
    private Double poQtyNew;
    private Double quantityReceived;
    private String poCurrencyCode;
    private Double unitPriceInPoCurrency;
    private Double unitPriceInSAR;
    private Double linePriceInPoCurrency;
    private Double linePriceInSAR;
    private Double amountReceived;
    private String poLineDescription;
    private String organizationName;
    private String organizationCode;
    private String subInventoryCode;
    private String receiptRouting;
    private String authorisationStatus;
    private String departmentName;
    private String businessOwner;
    private String poLineType;
    private String poAcceptanceType;
    private String costCenter;
    private String chargeAccount;
    private String serialControl;
    private String vendorSerialNumberYN;
    private String itemType;
    private String itemCategoryInventory;
    private String inventoryCategoryDescription;
    private String itemCategoryFA;
    private String faCategoryDescription;
    private String itemCategoryPurchasing;
    private String purchasingCategoryDescription;
    private String poVendorName;
    private String poVendorNumber;
    private java.util.Date poApprovedDate;
    private java.util.Date poCreatedDate;
    private Integer poCreatedBy;
    private String poCreatedByName;
    private Long uplRecordNo;
    private String uplManufacturer;
    private String uplCountryOfOrigin;
    private String uplReleaseNumber;
    private String uplLine;
    private String uplPoLineItemType;
    private String uplPoLineItemCode;
    private String uplPoLineDescription;
    private String uplLineItemType;
    private String uplLineItemCode;
    private String uplLineDescription;
    private String zainItemCategoryCode;
    private String zainItemCategoryDescription;
    private String uplItemSerialized;
    private String activeOrPassive;
    private String uplUom;
    private String uplCurrency;
    private Double uplPoLineQuantity;
    private Double uplPoLineUnitPrice;
    private Double uplLineQuantity;
    private Double uplLineUnitPrice;
    private String substituteItemCode;
    private String uplRemarks;
    private String scopeOfWork;
    private String dptApprover1;
    private String dptApprover2;
    private String dptApprover3;
    private String dptApprover4;
    private String regionalApprover;
    private Integer uplCreatedBy;
    private String uplCreatedByName;
    private String canRaiseAcceptance;
    private Double uplPendingQuantity;
    private String categoryDescription;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("poRecordNo", poRecordNo);
        map.put("poNumber", poNumber);
        map.put("typeLookUpCode", typeLookUpCode);
        map.put("blanketTotalAmount", blanketTotalAmount);
        map.put("releaseNum", releaseNum);
        map.put("lineNumber", lineNumber);
        map.put("prNum", prNum);
        map.put("poProjectName", poProjectName);
        map.put("newProjectName", newProjectName);
        map.put("itemPartNumber", itemPartNumber);
        map.put("prSubAllow", prSubAllow);
        map.put("poCountryOfOrigin", poCountryOfOrigin);
        map.put("poOrderQuantity", poOrderQuantity);
        map.put("UPLACPTRequestValue", uplAcptRequestValue);
        map.put("POAcceptanceQty", poAcceptanceQty);
        map.put("POLineAcceptanceQty", poLineAcceptanceQty);
        map.put("poPendingQuantity", poPendingQuantity);
        map.put("poQtyNew", poQtyNew);
        map.put("quantityReceived", quantityReceived);
        map.put("poCurrencyCode", poCurrencyCode);
        map.put("unitPriceInPoCurrency", unitPriceInPoCurrency);
        map.put("unitPriceInSAR", unitPriceInSAR);
        map.put("linePriceInPoCurrency", linePriceInPoCurrency);
        map.put("linePriceInSAR", linePriceInSAR);
        map.put("amountReceived", amountReceived);
        map.put("poLineDescription", poLineDescription);
        map.put("organizationName", organizationName);
        map.put("organizationCode", organizationCode);
        map.put("subInventoryCode", subInventoryCode);
        map.put("receiptRouting", receiptRouting);
        map.put("authorisationStatus", authorisationStatus);
        map.put("departmentName", departmentName);
        map.put("businessOwner", businessOwner);
        map.put("poLineType", poLineType);
        map.put("poAcceptanceType", poAcceptanceType);
        map.put("costCenter", costCenter);
        map.put("chargeAccount", chargeAccount);
        map.put("serialControl", serialControl);
        map.put("vendorSerialNumberYN", vendorSerialNumberYN);
        map.put("itemType", itemType);
        map.put("itemCategoryInventory", itemCategoryInventory);
        map.put("inventoryCategoryDescription", inventoryCategoryDescription);
        map.put("itemCategoryFA", itemCategoryFA);
        map.put("FACategoryDescription", faCategoryDescription);
        map.put("itemCategoryPurchasing", itemCategoryPurchasing);
        map.put("PurchasingCategoryDescription", purchasingCategoryDescription);
        map.put("poVendorName", poVendorName);
        map.put("poVendorNumber", poVendorNumber);
        map.put("poApprovedDate", poApprovedDate);
        map.put("poCreatedDate", poCreatedDate);
        map.put("poCreatedBy", poCreatedBy);
        map.put("poCreatedByName", poCreatedByName);
        map.put("uplRecordNo", uplRecordNo);
        map.put("uplManufacturer", uplManufacturer);
        map.put("uplCountryOfOrigin", uplCountryOfOrigin);
        map.put("uplReleaseNumber", uplReleaseNumber);
        map.put("uplLine", uplLine);
        map.put("uplPoLineItemType", uplPoLineItemType);
        map.put("uplPoLineItemCode", uplPoLineItemCode);
        map.put("uplPoLineDescription", uplPoLineDescription);
        map.put("uplLineItemType", uplLineItemType);
        map.put("uplLineItemCode", uplLineItemCode);
        map.put("uplLineDescription", uplLineDescription);
        map.put("zainItemCategoryCode", zainItemCategoryCode);
        map.put("zainItemCategoryDescription", zainItemCategoryDescription);
        map.put("uplItemSerialized", uplItemSerialized);
        map.put("activeOrPassive", activeOrPassive);
        map.put("uplUom", uplUom);
        map.put("uplCurrency", uplCurrency);
        map.put("uplPoLineQuantity", uplPoLineQuantity);
        map.put("uplPoLineUnitPrice", uplPoLineUnitPrice);
        map.put("uplLineQuantity", uplLineQuantity);
        map.put("uplLineUnitPrice", uplLineUnitPrice);
        map.put("substituteItemCode", substituteItemCode);
        map.put("uplRemarks", uplRemarks);
        map.put("scopeOfWork", scopeOfWork);
        map.put("dptApprover1", dptApprover1);
        map.put("dptApprover2", dptApprover2);
        map.put("dptApprover3", dptApprover3);
        map.put("dptApprover4", dptApprover4);
        map.put("regionalApprover", regionalApprover);
        map.put("uplCreatedBy", uplCreatedBy);
        map.put("uplCreatedByName", uplCreatedByName);
        map.put("canRaiseAcceptance", canRaiseAcceptance);
        map.put("uplPendingQuantity", uplPendingQuantity);
        map.put("categoryDescription", categoryDescription);
        return map;
    }

    public Long getPoRecordNo() { return poRecordNo; }
    public void setPoRecordNo(Long poRecordNo) { this.poRecordNo = poRecordNo; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public String getTypeLookUpCode() { return typeLookUpCode; }
    public void setTypeLookUpCode(String typeLookUpCode) { this.typeLookUpCode = typeLookUpCode; }
    public Double getBlanketTotalAmount() { return blanketTotalAmount; }
    public void setBlanketTotalAmount(Double blanketTotalAmount) { this.blanketTotalAmount = blanketTotalAmount; }
    public String getReleaseNum() { return releaseNum; }
    public void setReleaseNum(String releaseNum) { this.releaseNum = releaseNum; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getPrNum() { return prNum; }
    public void setPrNum(String prNum) { this.prNum = prNum; }
    public String getPoProjectName() { return poProjectName; }
    public void setPoProjectName(String poProjectName) { this.poProjectName = poProjectName; }
    public String getNewProjectName() { return newProjectName; }
    public void setNewProjectName(String newProjectName) { this.newProjectName = newProjectName; }
    public String getItemPartNumber() { return itemPartNumber; }
    public void setItemPartNumber(String itemPartNumber) { this.itemPartNumber = itemPartNumber; }
    public Boolean getPrSubAllow() { return prSubAllow; }
    public void setPrSubAllow(Boolean prSubAllow) { this.prSubAllow = prSubAllow; }
    public String getPoCountryOfOrigin() { return poCountryOfOrigin; }
    public void setPoCountryOfOrigin(String poCountryOfOrigin) { this.poCountryOfOrigin = poCountryOfOrigin; }
    public Double getPoOrderQuantity() { return poOrderQuantity; }
    public void setPoOrderQuantity(Double poOrderQuantity) { this.poOrderQuantity = poOrderQuantity; }
    public Double getUplAcptRequestValue() { return uplAcptRequestValue; }
    public void setUplAcptRequestValue(Double uplAcptRequestValue) { this.uplAcptRequestValue = uplAcptRequestValue; }
    public Double getPoAcceptanceQty() { return poAcceptanceQty; }
    public void setPoAcceptanceQty(Double poAcceptanceQty) { this.poAcceptanceQty = poAcceptanceQty; }
    public Double getPoLineAcceptanceQty() { return poLineAcceptanceQty; }
    public void setPoLineAcceptanceQty(Double poLineAcceptanceQty) { this.poLineAcceptanceQty = poLineAcceptanceQty; }
    public Double getPoPendingQuantity() { return poPendingQuantity; }
    public void setPoPendingQuantity(Double poPendingQuantity) { this.poPendingQuantity = poPendingQuantity; }
    public Double getPoQtyNew() { return poQtyNew; }
    public void setPoQtyNew(Double poQtyNew) { this.poQtyNew = poQtyNew; }
    public Double getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(Double quantityReceived) { this.quantityReceived = quantityReceived; }
    public String getPoCurrencyCode() { return poCurrencyCode; }
    public void setPoCurrencyCode(String poCurrencyCode) { this.poCurrencyCode = poCurrencyCode; }
    public Double getUnitPriceInPoCurrency() { return unitPriceInPoCurrency; }
    public void setUnitPriceInPoCurrency(Double unitPriceInPoCurrency) { this.unitPriceInPoCurrency = unitPriceInPoCurrency; }
    public Double getUnitPriceInSAR() { return unitPriceInSAR; }
    public void setUnitPriceInSAR(Double unitPriceInSAR) { this.unitPriceInSAR = unitPriceInSAR; }
    public Double getLinePriceInPoCurrency() { return linePriceInPoCurrency; }
    public void setLinePriceInPoCurrency(Double linePriceInPoCurrency) { this.linePriceInPoCurrency = linePriceInPoCurrency; }
    public Double getLinePriceInSAR() { return linePriceInSAR; }
    public void setLinePriceInSAR(Double linePriceInSAR) { this.linePriceInSAR = linePriceInSAR; }
    public Double getAmountReceived() { return amountReceived; }
    public void setAmountReceived(Double amountReceived) { this.amountReceived = amountReceived; }
    public String getPoLineDescription() { return poLineDescription; }
    public void setPoLineDescription(String poLineDescription) { this.poLineDescription = poLineDescription; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }
    public String getSubInventoryCode() { return subInventoryCode; }
    public void setSubInventoryCode(String subInventoryCode) { this.subInventoryCode = subInventoryCode; }
    public String getReceiptRouting() { return receiptRouting; }
    public void setReceiptRouting(String receiptRouting) { this.receiptRouting = receiptRouting; }
    public String getAuthorisationStatus() { return authorisationStatus; }
    public void setAuthorisationStatus(String authorisationStatus) { this.authorisationStatus = authorisationStatus; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getBusinessOwner() { return businessOwner; }
    public void setBusinessOwner(String businessOwner) { this.businessOwner = businessOwner; }
    public String getPoLineType() { return poLineType; }
    public void setPoLineType(String poLineType) { this.poLineType = poLineType; }
    public String getPoAcceptanceType() { return poAcceptanceType; }
    public void setPoAcceptanceType(String poAcceptanceType) { this.poAcceptanceType = poAcceptanceType; }
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
    public String getChargeAccount() { return chargeAccount; }
    public void setChargeAccount(String chargeAccount) { this.chargeAccount = chargeAccount; }
    public String getSerialControl() { return serialControl; }
    public void setSerialControl(String serialControl) { this.serialControl = serialControl; }
    public String getVendorSerialNumberYN() { return vendorSerialNumberYN; }
    public void setVendorSerialNumberYN(String vendorSerialNumberYN) { this.vendorSerialNumberYN = vendorSerialNumberYN; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getItemCategoryInventory() { return itemCategoryInventory; }
    public void setItemCategoryInventory(String itemCategoryInventory) { this.itemCategoryInventory = itemCategoryInventory; }
    public String getInventoryCategoryDescription() { return inventoryCategoryDescription; }
    public void setInventoryCategoryDescription(String inventoryCategoryDescription) { this.inventoryCategoryDescription = inventoryCategoryDescription; }
    public String getItemCategoryFA() { return itemCategoryFA; }
    public void setItemCategoryFA(String itemCategoryFA) { this.itemCategoryFA = itemCategoryFA; }
    public String getFaCategoryDescription() { return faCategoryDescription; }
    public void setFaCategoryDescription(String faCategoryDescription) { this.faCategoryDescription = faCategoryDescription; }
    public String getItemCategoryPurchasing() { return itemCategoryPurchasing; }
    public void setItemCategoryPurchasing(String itemCategoryPurchasing) { this.itemCategoryPurchasing = itemCategoryPurchasing; }
    public String getPurchasingCategoryDescription() { return purchasingCategoryDescription; }
    public void setPurchasingCategoryDescription(String purchasingCategoryDescription) { this.purchasingCategoryDescription = purchasingCategoryDescription; }
    public String getPoVendorName() { return poVendorName; }
    public void setPoVendorName(String poVendorName) { this.poVendorName = poVendorName; }
    public String getPoVendorNumber() { return poVendorNumber; }
    public void setPoVendorNumber(String poVendorNumber) { this.poVendorNumber = poVendorNumber; }
    public java.util.Date getPoApprovedDate() { return poApprovedDate; }
    public void setPoApprovedDate(java.util.Date poApprovedDate) { this.poApprovedDate = poApprovedDate; }
    public java.util.Date getPoCreatedDate() { return poCreatedDate; }
    public void setPoCreatedDate(java.util.Date poCreatedDate) { this.poCreatedDate = poCreatedDate; }
    public Integer getPoCreatedBy() { return poCreatedBy; }
    public void setPoCreatedBy(Integer poCreatedBy) { this.poCreatedBy = poCreatedBy; }
    public String getPoCreatedByName() { return poCreatedByName; }
    public void setPoCreatedByName(String poCreatedByName) { this.poCreatedByName = poCreatedByName; }
    public Long getUplRecordNo() { return uplRecordNo; }
    public void setUplRecordNo(Long uplRecordNo) { this.uplRecordNo = uplRecordNo; }
    public String getUplManufacturer() { return uplManufacturer; }
    public void setUplManufacturer(String uplManufacturer) { this.uplManufacturer = uplManufacturer; }
    public String getUplCountryOfOrigin() { return uplCountryOfOrigin; }
    public void setUplCountryOfOrigin(String uplCountryOfOrigin) { this.uplCountryOfOrigin = uplCountryOfOrigin; }
    public String getUplReleaseNumber() { return uplReleaseNumber; }
    public void setUplReleaseNumber(String uplReleaseNumber) { this.uplReleaseNumber = uplReleaseNumber; }
    public String getUplLine() { return uplLine; }
    public void setUplLine(String uplLine) { this.uplLine = uplLine; }
    public String getUplPoLineItemType() { return uplPoLineItemType; }
    public void setUplPoLineItemType(String uplPoLineItemType) { this.uplPoLineItemType = uplPoLineItemType; }
    public String getUplPoLineItemCode() { return uplPoLineItemCode; }
    public void setUplPoLineItemCode(String uplPoLineItemCode) { this.uplPoLineItemCode = uplPoLineItemCode; }
    public String getUplPoLineDescription() { return uplPoLineDescription; }
    public void setUplPoLineDescription(String uplPoLineDescription) { this.uplPoLineDescription = uplPoLineDescription; }
    public String getUplLineItemType() { return uplLineItemType; }
    public void setUplLineItemType(String uplLineItemType) { this.uplLineItemType = uplLineItemType; }
    public String getUplLineItemCode() { return uplLineItemCode; }
    public void setUplLineItemCode(String uplLineItemCode) { this.uplLineItemCode = uplLineItemCode; }
    public String getUplLineDescription() { return uplLineDescription; }
    public void setUplLineDescription(String uplLineDescription) { this.uplLineDescription = uplLineDescription; }
    public String getZainItemCategoryCode() { return zainItemCategoryCode; }
    public void setZainItemCategoryCode(String zainItemCategoryCode) { this.zainItemCategoryCode = zainItemCategoryCode; }
    public String getZainItemCategoryDescription() { return zainItemCategoryDescription; }
    public void setZainItemCategoryDescription(String zainItemCategoryDescription) { this.zainItemCategoryDescription = zainItemCategoryDescription; }
    public String getUplItemSerialized() { return uplItemSerialized; }
    public void setUplItemSerialized(String uplItemSerialized) { this.uplItemSerialized = uplItemSerialized; }
    public String getActiveOrPassive() { return activeOrPassive; }
    public void setActiveOrPassive(String activeOrPassive) { this.activeOrPassive = activeOrPassive; }
    public String getUplUom() { return uplUom; }
    public void setUplUom(String uplUom) { this.uplUom = uplUom; }
    public String getUplCurrency() { return uplCurrency; }
    public void setUplCurrency(String uplCurrency) { this.uplCurrency = uplCurrency; }
    public Double getUplPoLineQuantity() { return uplPoLineQuantity; }
    public void setUplPoLineQuantity(Double uplPoLineQuantity) { this.uplPoLineQuantity = uplPoLineQuantity; }
    public Double getUplPoLineUnitPrice() { return uplPoLineUnitPrice; }
    public void setUplPoLineUnitPrice(Double uplPoLineUnitPrice) { this.uplPoLineUnitPrice = uplPoLineUnitPrice; }
    public Double getUplLineQuantity() { return uplLineQuantity; }
    public void setUplLineQuantity(Double uplLineQuantity) { this.uplLineQuantity = uplLineQuantity; }
    public Double getUplLineUnitPrice() { return uplLineUnitPrice; }
    public void setUplLineUnitPrice(Double uplLineUnitPrice) { this.uplLineUnitPrice = uplLineUnitPrice; }
    public String getSubstituteItemCode() { return substituteItemCode; }
    public void setSubstituteItemCode(String substituteItemCode) { this.substituteItemCode = substituteItemCode; }
    public String getUplRemarks() { return uplRemarks; }
    public void setUplRemarks(String uplRemarks) { this.uplRemarks = uplRemarks; }
    public String getScopeOfWork() { return scopeOfWork; }
    public void setScopeOfWork(String scopeOfWork) { this.scopeOfWork = scopeOfWork; }
    public String getDptApprover1() { return dptApprover1; }
    public void setDptApprover1(String dptApprover1) { this.dptApprover1 = dptApprover1; }
    public String getDptApprover2() { return dptApprover2; }
    public void setDptApprover2(String dptApprover2) { this.dptApprover2 = dptApprover2; }
    public String getDptApprover3() { return dptApprover3; }
    public void setDptApprover3(String dptApprover3) { this.dptApprover3 = dptApprover3; }
    public String getDptApprover4() { return dptApprover4; }
    public void setDptApprover4(String dptApprover4) { this.dptApprover4 = dptApprover4; }
    public String getRegionalApprover() { return regionalApprover; }
    public void setRegionalApprover(String regionalApprover) { this.regionalApprover = regionalApprover; }
    public Integer getUplCreatedBy() { return uplCreatedBy; }
    public void setUplCreatedBy(Integer uplCreatedBy) { this.uplCreatedBy = uplCreatedBy; }
    public String getUplCreatedByName() { return uplCreatedByName; }
    public void setUplCreatedByName(String uplCreatedByName) { this.uplCreatedByName = uplCreatedByName; }
    public String getCanRaiseAcceptance() { return canRaiseAcceptance; }
    public void setCanRaiseAcceptance(String canRaiseAcceptance) { this.canRaiseAcceptance = canRaiseAcceptance; }
    public Double getUplPendingQuantity() { return uplPendingQuantity; }
    public void setUplPendingQuantity(Double uplPendingQuantity) { this.uplPendingQuantity = uplPendingQuantity; }
    public String getCategoryDescription() { return categoryDescription; }
    public void setCategoryDescription(String categoryDescription) { this.categoryDescription = categoryDescription; }
}
