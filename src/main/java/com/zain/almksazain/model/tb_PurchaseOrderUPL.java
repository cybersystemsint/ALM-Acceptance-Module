package com.zain.almksazain.model;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Date;

@Entity
@Table(name = "tb_PurchaseOrderUPL")
public class tb_PurchaseOrderUPL{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private Date recordDatetime;
    private String vendor;
    private String manufacturer;
    private String countryOfOrigin;
    private String projectName;
    private String poType;
    private String releaseNumber;
    private String poNumber;
    private String poLineNumber;
    private String uplLine; // Changed from uplLineNumber
    private String poLineItemType;
    private String poLineItemCode;
    private String poLineDescription;
    private String uplLineItemType;
    private String uplLineItemCode;
    private String uplLineDescription;
    private String zainItemCategoryCode;
    private String zainItemCategoryDescription;
    private String uplItemSerialized;
    private String activeOrPassive;
    private String uom;
    private String currency;
    private double poLineQuantity;
    private double poLineUnitPrice;
    private double uplLineQuantity;
    private double uplLineUnitPrice;
    private String substituteItemCode;
    private String remarks;
    private String dptApprover1;
    private String dptApprover2;
    private String dptApprover3;
    private String dptApprover4;
    private String regionalApprover;
    private Integer createdBy;
    private String createdByName;
    private String uplModifiedBy;
    private Date uplModifiedDate;

    // Getters and setters
    public long getRecordNo() { return recordNo; }
    public void setRecordNo(long recordNo) { this.recordNo = recordNo; }

    public Date getRecordDatetime() { return recordDatetime; }
    public void setRecordDatetime(Date recordDatetime) { this.recordDatetime = recordDatetime; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCountryOfOrigin() { return countryOfOrigin; }
    public void setCountryOfOrigin(String countryOfOrigin) { this.countryOfOrigin = countryOfOrigin; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getPoType() { return poType; }
    public void setPoType(String poType) { this.poType = poType; }

    public String getReleaseNumber() { return releaseNumber; }
    public void setReleaseNumber(String releaseNumber) { this.releaseNumber = releaseNumber; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getPoLineNumber() { return poLineNumber; }
    public void setPoLineNumber(String poLineNumber) { this.poLineNumber = poLineNumber; }

    public String getUplLine() { return uplLine; }
    public void setUplLine(String uplLine) { this.uplLine = uplLine; }

    public String getPoLineItemType() { return poLineItemType; }
    public void setPoLineItemType(String poLineItemType) { this.poLineItemType = poLineItemType; }

    public String getPoLineItemCode() { return poLineItemCode; }
    public void setPoLineItemCode(String poLineItemCode) { this.poLineItemCode = poLineItemCode; }

    public String getPoLineDescription() { return poLineDescription; }
    public void setPoLineDescription(String poLineDescription) { this.poLineDescription = poLineDescription; }

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

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public double getPoLineQuantity() { return poLineQuantity; }
    public void setPoLineQuantity(double poLineQuantity) { this.poLineQuantity = poLineQuantity; }

    public double getPoLineUnitPrice() { return poLineUnitPrice; }
    public void setPoLineUnitPrice(double poLineUnitPrice) { this.poLineUnitPrice = poLineUnitPrice; }

    public double getUplLineQuantity() { return uplLineQuantity; }
    public void setUplLineQuantity(double uplLineQuantity) { this.uplLineQuantity = uplLineQuantity; }

    public double getUplLineUnitPrice() { return uplLineUnitPrice; }
    public void setUplLineUnitPrice(double uplLineUnitPrice) { this.uplLineUnitPrice = uplLineUnitPrice; }

    public String getSubstituteItemCode() { return substituteItemCode; }
    public void setSubstituteItemCode(String substituteItemCode) { this.substituteItemCode = substituteItemCode; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

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

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public String getUplModifiedBy() { return uplModifiedBy; }
    public void setUplModifiedBy(String uplModifiedBy) { this.uplModifiedBy = uplModifiedBy; }

    public Date getUplModifiedDate() { return uplModifiedDate; }
    public void setUplModifiedDate(Date uplModifiedDate) { this.uplModifiedDate = uplModifiedDate; }

    public double getUnitPrice() { return this.poLineUnitPrice; }
    public void setUnitPrice(double unitPrice) { this.poLineUnitPrice = unitPrice; }
}