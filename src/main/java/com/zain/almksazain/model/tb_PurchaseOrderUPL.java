package com.zain.almksazain.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "tb_PurchaseOrderUPL")
public class tb_PurchaseOrderUPL {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordNo")
    private Long recordNo;

    @Column(name = "recordDatetime")
    @Temporal(TemporalType.TIMESTAMP)
    private Date recordDatetime;

    @Column(name = "vendor")
    private String vendor;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "countryOfOrigin")
    private String countryOfOrigin;

    @Column(name = "projectName")
    private String projectName;

    @Column(name = "poType")
    private String poType;

    @Column(name = "releaseNumber")
    private String releaseNumber;

    @Column(name = "poNumber")
    private String poNumber;

    @Column(name = "poLineNumber")
    private String poLineNumber;

    @Column(name = "uplLine")
    private String uplLine;

    @Column(name = "poLineItemType")
    private String poLineItemType;

    @Column(name = "poLineItemCode")
    private String poLineItemCode;

    @Column(name = "poLineDescription")
    private String poLineDescription;

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

    @Column(name = "uom")
    private String uom;

    @Column(name = "currency")
    private String currency;

    @Column(name = "poLineQuantity")
    private Double poLineQuantity;

    @Column(name = "poLineUnitPrice")
    private Double poLineUnitPrice;

    @Column(name = "uplLineQuantity")
    private Double uplLineQuantity;

    @Column(name = "uplLineUnitPrice")
    private Double uplLineUnitPrice;

    @Column(name = "substituteItemCode")
    private String substituteItemCode;

    @Column(name = "remarks")
    private String remarks;

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

    @Column(name = "createdBy")
    private Integer createdBy;

    @Column(name = "createdByName")
    private String createdByName;

    @Column(name = "uplModifiedBy")
    private String uplModifiedBy;

    @Column(name = "uplModifiedDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date uplModifiedDate;

    // Getters and Setters
    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
    }

    public Date getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(Date recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getPoType() {
        return poType;
    }

    public void setPoType(String poType) {
        this.poType = poType;
    }

    public String getReleaseNumber() {
        return releaseNumber;
    }

    public void setReleaseNumber(String releaseNumber) {
        this.releaseNumber = releaseNumber;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getPoLineNumber() {
        return poLineNumber;
    }

    public void setPoLineNumber(String poLineNumber) {
        this.poLineNumber = poLineNumber;
    }

    public String getUplLine() {
        return uplLine;
    }

    public void setUplLine(String uplLine) {
        this.uplLine = uplLine;
    }

    public String getPoLineItemType() {
        return poLineItemType;
    }

    public void setPoLineItemType(String poLineItemType) {
        this.poLineItemType = poLineItemType;
    }

    public String getPoLineItemCode() {
        return poLineItemCode;
    }

    public void setPoLineItemCode(String poLineItemCode) {
        this.poLineItemCode = poLineItemCode;
    }

    public String getPoLineDescription() {
        return poLineDescription;
    }

    public void setPoLineDescription(String poLineDescription) {
        this.poLineDescription = poLineDescription;
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

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getPoLineQuantity() {
        return poLineQuantity;
    }

    public void setPoLineQuantity(Double poLineQuantity) {
        this.poLineQuantity = poLineQuantity;
    }

    public Double getPoLineUnitPrice() {
        return poLineUnitPrice;
    }

    public void setPoLineUnitPrice(Double poLineUnitPrice) {
        this.poLineUnitPrice = poLineUnitPrice;
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

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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

    public String getUplModifiedBy() {
        return uplModifiedBy;
    }

    public void setUplModifiedBy(String uplModifiedBy) {
        this.uplModifiedBy = uplModifiedBy;
    }

    public Date getUplModifiedDate() {
        return uplModifiedDate;
    }

    public void setUplModifiedDate(Date uplModifiedDate) {
        this.uplModifiedDate = uplModifiedDate;
    }
}