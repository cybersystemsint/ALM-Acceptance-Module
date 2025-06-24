package com.zain.almksazain.dto;


import java.util.List;



public class PurchaseOrderSummaryDTO {
    private String poNumber;
    private String typeLookUpCode;
    private Double blanketTotalAmount;
    private String releaseNum;
    private String prNum;
    private String projectName;
    private String newProjectName;
    private String lineCancelFlag;
    private String cancelReason;
    private String itemPartNumber;
    private String prSubAllow;
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
    private String vendorNumber;
    private String approvedDate;
    private String createdDate;
    private String createdBy;
    private String createdByName;

    // Totals
    private Double totalPoQtyNew;
    private Double totalQuantityReceived;
    private Double totalQuantityDueOld;
    private Double totalQuantityDueNew;
    private Double totalQuantityBilled;
    private Double totalpoOrderQuantity;
    private Double totalunitPriceInPoCurrency;
    private Double totalunitPriceInSAR;
    private Double totallinePriceInPoCurrency;
    private Double totallinePriceInSAR;
    private Double totalamountReceived;
    private Double totalamountDue;
    private Double totalamountDueNew;
    private Double totalamountBilled;
    private Double totalDescopedLinePriceInPoCurrency;
    private Double totalNewLinePriceInPoCurrency;

    private List<PurchaseOrderLineItemDTO> polineItems;

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

    public String getLineCancelFlag() {
        return lineCancelFlag;
    }

    public void setLineCancelFlag(String lineCancelFlag) {
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

    public String getPrSubAllow() {
        return prSubAllow;
    }

    public void setPrSubAllow(String prSubAllow) {
        this.prSubAllow = prSubAllow;
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

    public Double getTotalPoQtyNew() {
        return totalPoQtyNew;
    }

    public void setTotalPoQtyNew(Double totalPoQtyNew) {
        this.totalPoQtyNew = totalPoQtyNew;
    }

    public Double getTotalQuantityReceived() {
        return totalQuantityReceived;
    }

    public void setTotalQuantityReceived(Double totalQuantityReceived) {
        this.totalQuantityReceived = totalQuantityReceived;
    }

    public Double getTotalQuantityDueOld() {
        return totalQuantityDueOld;
    }

    public void setTotalQuantityDueOld(Double totalQuantityDueOld) {
        this.totalQuantityDueOld = totalQuantityDueOld;
    }

    public Double getTotalQuantityDueNew() {
        return totalQuantityDueNew;
    }

    public void setTotalQuantityDueNew(Double totalQuantityDueNew) {
        this.totalQuantityDueNew = totalQuantityDueNew;
    }

    public Double getTotalQuantityBilled() {
        return totalQuantityBilled;
    }

    public void setTotalQuantityBilled(Double totalQuantityBilled) {
        this.totalQuantityBilled = totalQuantityBilled;
    }

    public Double getTotalpoOrderQuantity() {
        return totalpoOrderQuantity;
    }

    public void setTotalpoOrderQuantity(Double totalpoOrderQuantity) {
        this.totalpoOrderQuantity = totalpoOrderQuantity;
    }

    public Double getTotalunitPriceInPoCurrency() {
        return totalunitPriceInPoCurrency;
    }

    public void setTotalunitPriceInPoCurrency(Double totalunitPriceInPoCurrency) {
        this.totalunitPriceInPoCurrency = totalunitPriceInPoCurrency;
    }

    public Double getTotalunitPriceInSAR() {
        return totalunitPriceInSAR;
    }

    public void setTotalunitPriceInSAR(Double totalunitPriceInSAR) {
        this.totalunitPriceInSAR = totalunitPriceInSAR;
    }

    public Double getTotallinePriceInPoCurrency() {
        return totallinePriceInPoCurrency;
    }

    public void setTotallinePriceInPoCurrency(Double totallinePriceInPoCurrency) {
        this.totallinePriceInPoCurrency = totallinePriceInPoCurrency;
    }

    public Double getTotallinePriceInSAR() {
        return totallinePriceInSAR;
    }

    public void setTotallinePriceInSAR(Double totallinePriceInSAR) {
        this.totallinePriceInSAR = totallinePriceInSAR;
    }

    public Double getTotalamountReceived() {
        return totalamountReceived;
    }

    public void setTotalamountReceived(Double totalamountReceived) {
        this.totalamountReceived = totalamountReceived;
    }

    public Double getTotalamountDue() {
        return totalamountDue;
    }

    public void setTotalamountDue(Double totalamountDue) {
        this.totalamountDue = totalamountDue;
    }

    public Double getTotalamountDueNew() {
        return totalamountDueNew;
    }

    public void setTotalamountDueNew(Double totalamountDueNew) {
        this.totalamountDueNew = totalamountDueNew;
    }

    public Double getTotalamountBilled() {
        return totalamountBilled;
    }

    public void setTotalamountBilled(Double totalamountBilled) {
        this.totalamountBilled = totalamountBilled;
    }

    public Double getTotalDescopedLinePriceInPoCurrency() {
        return totalDescopedLinePriceInPoCurrency;
    }

    public void setTotalDescopedLinePriceInPoCurrency(Double totalDescopedLinePriceInPoCurrency) {
        this.totalDescopedLinePriceInPoCurrency = totalDescopedLinePriceInPoCurrency;
    }

    public Double getTotalNewLinePriceInPoCurrency() {
        return totalNewLinePriceInPoCurrency;
    }

    public void setTotalNewLinePriceInPoCurrency(Double totalNewLinePriceInPoCurrency) {
        this.totalNewLinePriceInPoCurrency = totalNewLinePriceInPoCurrency;
    }



    public String getPurchasingCategoryDescription() {
        return purchasingCategoryDescription;
    }

    public void setPurchasingCategoryDescription(String purchasingCategoryDescription) {
        this.purchasingCategoryDescription = purchasingCategoryDescription;
    }

    public List<PurchaseOrderLineItemDTO> getPolineItems() {
        return polineItems;
    }

    public void setPolineItems(List<PurchaseOrderLineItemDTO> polineItems) {
        this.polineItems = polineItems;
    }


    
}


