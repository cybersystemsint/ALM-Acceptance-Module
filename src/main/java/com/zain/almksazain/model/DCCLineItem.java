package com.zain.almksazain.model;

import javax.persistence.*;
import java.sql.Date;

@Entity
@Table(name = "tb_DCC_LN")
public class DCCLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private String productName;
    private String productSerialNo;
    private double deliveredQty;
    private String locationName;
    private String inserviceDate;
    private Double unitPrice;
    private String scopeOfWork;
    private String remarks;
    private String dccId;
    private String itemCode;
    private String actualItemCode;
    private String poId;
    private String lineNumber;
    private String UoM;
    private Integer orderQuantity;
    private Double VAT;
    private Double linePrice;
    private String uplLineNumber;
    private String serialNumber;
    private Date dateInService;
    private String uplItemCode;
    private String uplItemDescription;
    private String linkId;
    private String tagNumber;

    // Getters and Setters
    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getActualItemCode() {
        return actualItemCode;
    }

    public void setActualItemCode(String actualItemCode) {
        this.actualItemCode = actualItemCode;
    }

    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getUplItemCode() {
        return uplItemCode;
    }

    public void setUplItemCode(String uplItemCode) {
        this.uplItemCode = uplItemCode;
    }

    public String getUplItemDescription() {
        return uplItemDescription;
    }

    public void setUplItemDescription(String uplItemDescription) {
        this.uplItemDescription = uplItemDescription;
    }

    public String getDccId() {
        return dccId;
    }

    public void setDccId(String dccId) {
        this.dccId = dccId;
    }

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductSerialNo() {
        return productSerialNo;
    }

    public void setProductSerialNo(String productSerialNo) {
        this.productSerialNo = productSerialNo;
    }

    public double getDeliveredQty() {
        return deliveredQty;
    }

    public void setDeliveredQty(double deliveredQty) {
        this.deliveredQty = deliveredQty;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getInserviceDate() {
        return inserviceDate;
    }

    public void setInserviceDate(String inserviceDate) {
        this.inserviceDate = inserviceDate;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getScopeOfWork() {
        return scopeOfWork;
    }

    public void setScopeOfWork(String scopeOfWork) {
        this.scopeOfWork = scopeOfWork;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getPoId() {
        return poId;
    }

    public void setPoId(String poId) {
        this.poId = poId;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getUoM() {
        return UoM;
    }

    public void setUoM(String UoM) {
        this.UoM = UoM;
    }

    public Integer getOrderQuantity() {
        return orderQuantity;
    }

    public void setOrderQuantity(Integer orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

    public Double getVAT() {
        return VAT;
    }

    public void setVAT(Double VAT) {
        this.VAT = VAT;
    }

    public Double getLinePrice() {
        return linePrice;
    }

    public void setLinePrice(Double linePrice) {
        this.linePrice = linePrice;
    }

    public String getUplLineNumber() {
        return uplLineNumber;
    }

    public void setUplLineNumber(String uplLineNumber) {
        this.uplLineNumber = uplLineNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Date getDateInService() {
        return dateInService;
    }

    public void setDateInService(Date dateInService) {
        this.dateInService = dateInService;
    }
}