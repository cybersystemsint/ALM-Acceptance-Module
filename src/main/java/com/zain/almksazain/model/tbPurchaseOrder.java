/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.model;

import java.io.Serializable;
import javax.persistence.*;
import java.sql.Date;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_PurchaseOrder")
public class tbPurchaseOrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public String poNumber;
    public String typeLookUpCode;
    public double blanketTotalAmount;
    public String releaseNum;
    public Integer lineNumber;
    public String prNum;
    public String projectName;
    public boolean lineCancelFlag;
    public String cancelReason;
    public String itemPartNumber;
    public boolean prSubAllow;
    public String countryOfOrigin;
    public double poOrderQuantity;
    public double poQtyNew;
    public double quantityReceived;
    public double quantityDueOld;
    public double quantityDueNew;
    public double quantityBilled;
    public String currencyCode;
    public double unitPriceInPoCurrency;
    public double unitPriceInSAR;
    public double linePriceInPoCurrency;
    public double linePriceInSAR;
    public double amountReceived;
    public double amountDue;
    public double amountDueNew;
    public double amountBilled;
    public String poLineDescription;
    public String organizationName;
    public String organizationCode;
    public String subInventoryCode;
    public String receiptRouting;
    public String authorisationStatus;
    public String poClosureStatus;
    public String departmentName;
    public String businessOwner;
    public String poLineType;
    public String acceptanceType;
    public String costCenter;
    public String chargeAccount;
    public String serialControl;
    public String vendorSerialNumberYN;
    public String itemType;
    public String itemCategoryInventory;
    public String inventoryCategoryDescription;
    public String itemCategoryFA;
    public String FACategoryDescription;
    public String itemCategoryPurchasing;
    public String PurchasingCategoryDescription;
    public String vendorName;
    public String vendorNumber;
    public Date approvedDate;
    public Date createdDate;
    public Integer createdBy;
    public String createdByName;
    public Double descopedLinePriceInPoCurrency;
    public Double newLinePriceInPoCurrency;

    public Double getNewLinePriceInPoCurrency() {
        return newLinePriceInPoCurrency;
    }

    public void setNewLinePriceInPoCurrency(Double newLinePriceInPoCurrency) {
        this.newLinePriceInPoCurrency = newLinePriceInPoCurrency;
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

    public long getRecordNo() {
        return recordNo;
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

    public String getTypeLookUpCode() {
        return typeLookUpCode;
    }

    public void setTypeLookUpCode(String typeLookUpCode) {
        this.typeLookUpCode = typeLookUpCode;
    }

    public double getBlanketTotalAmount() {
        return blanketTotalAmount;
    }

    public void setBlanketTotalAmount(double blanketTotalAmount) {
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

    public boolean isLineCancelFlag() {
        return lineCancelFlag;
    }

    public void setLineCancelFlag(boolean lineCancelFlag) {
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

    public boolean isPrSubAllow() {
        return prSubAllow;
    }

    public void setPrSubAllow(boolean prSubAllow) {
        this.prSubAllow = prSubAllow;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public double getPoOrderQuantity() {
        return poOrderQuantity;
    }

    public void setPoOrderQuantity(double poOrderQuantity) {
        this.poOrderQuantity = poOrderQuantity;
    }

    public double getPoQtyNew() {
        return poQtyNew;
    }

    public void setPoQtyNew(double poQtyNew) {
        this.poQtyNew = poQtyNew;
    }

    public double getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(double quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public double getQuantityDueOld() {
        return quantityDueOld;
    }

    public void setQuantityDueOld(double quantityDueOld) {
        this.quantityDueOld = quantityDueOld;
    }

    public double getQuantityDueNew() {
        return quantityDueNew;
    }

    public void setQuantityDueNew(double quantityDueNew) {
        this.quantityDueNew = quantityDueNew;
    }

    public double getQuantityBilled() {
        return quantityBilled;
    }

    public void setQuantityBilled(double quantityBilled) {
        this.quantityBilled = quantityBilled;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public double getUnitPriceInPoCurrency() {
        return unitPriceInPoCurrency;
    }

    public void setUnitPriceInPoCurrency(double unitPriceInPoCurrency) {
        this.unitPriceInPoCurrency = unitPriceInPoCurrency;
    }

    public double getUnitPriceInSAR() {
        return unitPriceInSAR;
    }

    public void setUnitPriceInSAR(double unitPriceInSAR) {
        this.unitPriceInSAR = unitPriceInSAR;
    }

    public double getLinePriceInPoCurrency() {
        return linePriceInPoCurrency;
    }

    public void setLinePriceInPoCurrency(double linePriceInPoCurrency) {
        this.linePriceInPoCurrency = linePriceInPoCurrency;
    }

    public double getLinePriceInSAR() {
        return linePriceInSAR;
    }

    public void setLinePriceInSAR(double linePriceInSAR) {
        this.linePriceInSAR = linePriceInSAR;
    }

    public double getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(double amountReceived) {
        this.amountReceived = amountReceived;
    }

    public double getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(double amountDue) {
        this.amountDue = amountDue;
    }

    public double getAmountDueNew() {
        return amountDueNew;
    }

    public void setAmountDueNew(double amountDueNew) {
        this.amountDueNew = amountDueNew;
    }

    public double getAmountBilled() {
        return amountBilled;
    }

    public void setAmountBilled(double amountBilled) {
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

    public void setFACategoryDescription(String FACategoryDescription) {
        this.FACategoryDescription = FACategoryDescription;
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

    public void setPurchasingCategoryDescription(String PurchasingCategoryDescription) {
        this.PurchasingCategoryDescription = PurchasingCategoryDescription;
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

    public double getDescopedLinePriceInPoCurrency() {
        return descopedLinePriceInPoCurrency;
    }

    public void setDescopedLinePriceInPoCurrency(Double descopedLinePriceInPoCurrency) {
        this.descopedLinePriceInPoCurrency = descopedLinePriceInPoCurrency;
    }


}
