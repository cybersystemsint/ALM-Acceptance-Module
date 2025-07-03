package com.zain.almksazain.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "tb_DCC_LN")
public class DCCLineItem {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "dccId")
    private String dccId;

    @Column(name = "productName")
    private String productName;

    @Column(name = "productSerialNo")
    private String productSerialNo;

    @Column(name = "serialNumber")
    private String serialNumber;

    @Column(name = "deliveredQty")
    private Double deliveredQty;

    @Column(name = "locationName")
    private String locationName;

    @Column(name = "inserviceDate")
    private String inserviceDate;

    @Column(name = "dateInService")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateInService;

    @Column(name = "unitPrice")
    private Double unitPrice;

    @Column(name = "scopeOfWork")
    private String scopeOfWork;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "itemCode")
    private String itemCode;

    @Column(name = "actualItemCode")
    private String actualItemCode;

    @Column(name = "poId")
    private String poId;

    @Column(name = "lineNumber")
    private String lineNumber;

    @Column(name = "UoM")
    private String uom;

    @Column(name = "orderQuantity")
    private Integer orderQuantity;

    @Column(name = "VAT")
    private Double vat;

    @Column(name = "linePrice")
    private Double linePrice;

    @Column(name = "uplLineNumber")
    private String uplLineNumber;

    @Column(name = "uplItemCode")
    private String uplItemCode;

    @Column(name = "uplItemDescription")
    private String uplItemDescription;

    @Column(name = "linkId")
    private String linkId;

    @Column(name = "tagNumber")
    private String tagNumber;

    @Column(name = "poAcceptanceQty")
    private Double poAcceptanceQty;

    // Getters and Setters
    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public String getDccId() {
        return dccId;
    }

    public void setDccId(String dccId) {
        this.dccId = dccId;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Double getDeliveredQty() {
        return deliveredQty;
    }

    public void setDeliveredQty(Double deliveredQty) {
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

    public Date getDateInService() {
        return dateInService;
    }

    public void setDateInService(Date dateInService) {
        this.dateInService = dateInService;
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

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public Integer getOrderQuantity() {
        return orderQuantity;
    }

    public void setOrderQuantity(Integer orderQuantity) {
        this.orderQuantity = orderQuantity;
    }

    public Double getVat() {
        return vat;
    }

    public void setVat(Double vat) {
        this.vat = vat;
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

    public double getpoAcceptanceQty(){
        return poAcceptanceQty;
    };

}