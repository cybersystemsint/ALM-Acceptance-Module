package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "tb_UPL")
public class upldata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private String recordDatetime;
    private String projectName;
    private String poId;
    private String customerItemType;
    private String localContent;
    private String scope;
    private String subScope;
    private String poLine;
    private String uplLine;
    private String vendorItemCode;
    private String poLineItemDescription;
    private String erpItemDescription;
    private String zainItemCategory;
    private String serialized;
    private String activePassive;
    private String UOM;
    private double quantity;
    private String unit;
    private String currency;
    private double discount;
    private double unitPriceBeforeDiscount;
    private double poTotalAmtBeforeDiscount;
    private double finalTotalPriceAfterDiscount;
    private String huaweiComments;
    private String amuComments;
    private String procurementComments;
    private String status;
    private String dptApprover1;
    private String dptApprover2;
    private String dptApprover3;
    private String dptApprover4;
    private String regionalApprover;

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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
    }

    public String getCustomerItemType() {
        return customerItemType;
    }

    public void setCustomerItemType(String customerItemType) {
        this.customerItemType = customerItemType;
    }

    public String getLocalContent() {
        return localContent;
    }

    public void setLocalContent(String localContent) {
        this.localContent = localContent;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSubScope() {
        return subScope;
    }

    public void setSubScope(String subScope) {
        this.subScope = subScope;
    }

    public String getPoLine() {
        return poLine;
    }

    public void setPoLine(String poLine) {
        this.poLine = poLine;
    }

    public String getUplLine() {
        return uplLine;
    }

    public void setUplLine(String uplLine) {
        this.uplLine = uplLine;
    }

    public String getVendorItemCode() {
        return vendorItemCode;
    }

    public void setVendorItemCode(String vendorItemCode) {
        this.vendorItemCode = vendorItemCode;
    }

    public String getPoLineItemDescription() {
        return poLineItemDescription;
    }

    public void setPoLineItemDescription(String poLineItemDescription) {
        this.poLineItemDescription = poLineItemDescription;
    }

    public String getErpItemDescription() {
        return erpItemDescription;
    }

    public void setErpItemDescription(String erpItemDescription) {
        this.erpItemDescription = erpItemDescription;
    }

    public String getZainItemCategory() {
        return zainItemCategory;
    }

    public void setZainItemCategory(String zainItemCategory) {
        this.zainItemCategory = zainItemCategory;
    }

    public String getSerialized() {
        return serialized;
    }

    public void setSerialized(String serialized) {
        this.serialized = serialized;
    }

    public String getActivePassive() {
        return activePassive;
    }

    public void setActivePassive(String activePassive) {
        this.activePassive = activePassive;
    }

    public String getUOM() {
        return UOM;
    }

    public void setUOM(String UOM) {
        this.UOM = UOM;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public double getUnitPriceBeforeDiscount() {
        return unitPriceBeforeDiscount;
    }

    public void setUnitPriceBeforeDiscount(double unitPriceBeforeDiscount) {
        this.unitPriceBeforeDiscount = unitPriceBeforeDiscount;
    }

    public double getPoTotalAmtBeforeDiscount() {
        return poTotalAmtBeforeDiscount;
    }

    public void setPoTotalAmtBeforeDiscount(double poTotalAmtBeforeDiscount) {
        this.poTotalAmtBeforeDiscount = poTotalAmtBeforeDiscount;
    }

    public double getFinalTotalPriceAfterDiscount() {
        return finalTotalPriceAfterDiscount;
    }

    public void setFinalTotalPriceAfterDiscount(double finalTotalPriceAfterDiscount) {
        this.finalTotalPriceAfterDiscount = finalTotalPriceAfterDiscount;
    }

    public String getHuaweiComments() {
        return huaweiComments;
    }

    public void setHuaweiComments(String huaweiComments) {
        this.huaweiComments = huaweiComments;
    }

    public String getAmuComments() {
        return amuComments;
    }

    public void setAmuComments(String amuComments) {
        this.amuComments = amuComments;
    }

    public String getProcurementComments() {
        return procurementComments;
    }

    public void setProcurementComments(String procurementComments) {
        this.procurementComments = procurementComments;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

}
