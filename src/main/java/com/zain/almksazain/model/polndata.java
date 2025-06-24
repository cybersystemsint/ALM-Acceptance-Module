package com.zain.almksazain.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "tb_PO_LN")
public class polndata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public String recordDatetime;
    public String poId;
    public long lineNumber;
    public String itemCode;
    public String UoM;
    public String boqId;
    public int orderQuantity;
    public BigDecimal unitPrice;
    public BigDecimal VAT;
    public BigDecimal linePrice;
    //NEW COLUMNS     
    public String quantityDueOld;
    public String quantityDueNew;
    public String quantityBilled;
    public BigDecimal unitPriceInSAR;
    public BigDecimal linePriceInPoCurrency;
    public BigDecimal linePriceInSAR;
    public BigDecimal amountReceived;
    public BigDecimal amountDue;
    public BigDecimal amountDueNew;
    public BigDecimal amountBilled;
    public String poLineType;
    public String itemType;
    public String itemCategoryInventory;
    public String categoryDescription;
    public String itemCategoryFA;
    public String FACategoryDescription;
    public String itemCategoryPurchasing;
    public String PurchasingCategoryDescription;

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public String getRecordDatetime() {
        return recordDatetime;
    }

    public String getQuantityDueOld() {
        return quantityDueOld;
    }

    public void setQuantityDueOld(String quantityDueOld) {
        this.quantityDueOld = quantityDueOld;
    }

    public String getQuantityDueNew() {
        return quantityDueNew;
    }

    public void setQuantityDueNew(String quantityDueNew) {
        this.quantityDueNew = quantityDueNew;
    }

    public String getQuantityBilled() {
        return quantityBilled;
    }

    public void setQuantityBilled(String quantityBilled) {
        this.quantityBilled = quantityBilled;
    }

    public BigDecimal getUnitPriceInSAR() {
        return unitPriceInSAR;
    }

    public void setUnitPriceInSAR(BigDecimal unitPriceInSAR) {
        this.unitPriceInSAR = unitPriceInSAR;
    }

    public BigDecimal getLinePriceInPoCurrency() {
        return linePriceInPoCurrency;
    }

    public void setLinePriceInPoCurrency(BigDecimal linePriceInPoCurrency) {
        this.linePriceInPoCurrency = linePriceInPoCurrency;
    }

    public BigDecimal getLinePriceInSAR() {
        return linePriceInSAR;
    }

    public void setLinePriceInSAR(BigDecimal linePriceInSAR) {
        this.linePriceInSAR = linePriceInSAR;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public void setAmountReceived(BigDecimal amountReceived) {
        this.amountReceived = amountReceived;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public BigDecimal getAmountDueNew() {
        return amountDueNew;
    }

    public void setAmountDueNew(BigDecimal amountDueNew) {
        this.amountDueNew = amountDueNew;
    }

    public BigDecimal getAmountBilled() {
        return amountBilled;
    }

    public void setAmountBilled(BigDecimal amountBilled) {
        this.amountBilled = amountBilled;
    }

    public String getPoLineType() {
        return poLineType;
    }

    public void setPoLineType(String poLineType) {
        this.poLineType = poLineType;
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

    public String getCategoryDescription() {
        return categoryDescription;
    }

    public void setCategoryDescription(String categoryDescription) {
        this.categoryDescription = categoryDescription;
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

    public void setRecordDatetime(String recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(long lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getUoM() {
        return UoM;
    }

    public void setUoM(String uoM) {
        UoM = uoM;
    }

    public String getBoqId() {
        return boqId;
    }

    public void setBoqId(String boqId) {
        this.boqId = boqId;
    }

    public int getOrderQuantity() {
        return orderQuantity;
    }

    public void setOrderQuantity(int orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getVAT() {
        return VAT;
    }

    public void setVAT(BigDecimal VAT) {
        this.VAT = VAT;
    }

    public BigDecimal getLinePrice() {
        return linePrice;
    }

    public void setLinePrice(BigDecimal linePrice) {
        this.linePrice = linePrice;
    }
}
