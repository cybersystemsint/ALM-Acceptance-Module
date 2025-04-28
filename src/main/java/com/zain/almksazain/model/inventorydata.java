package com.zain.almksazain.model;

//package com.telkom.almKSAZain.model;
//
//import javax.persistence.*;
//
//@Entity
//@Table(name = "tb_Inventory")
//public class inventorydata {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    long recordNo;
//    String recordDatetime;
//    String inventoryId;
//    String serialNumber;
//    String captureMethod;
//    String capturedBy;
//    String itemCode;
//    String status;
//    String technology;
//    String  domain;
//    String  manufactureDate;
//    Integer isActive;
//    String PONumber;
//    String locationId;
//    String vendorFamilyType;
//    String tagLine;
//    String lastUpdateDate;
//    String lastUpdateBy;
//    String details;
//    String lastSeenDatetime;
//
//
//    public long getRecordNo() {
//        return recordNo;
//    }
//
//    public void setRecordNo(long recordNo) {
//        this.recordNo = recordNo;
//    }
//
//    public String getRecordDatetime() {
//        return recordDatetime;
//    }
//
//    public void setRecordDatetime(String recordDatetime) {
//        this.recordDatetime = recordDatetime;
//    }
//
//    public String getInventoryId() {
//        return inventoryId;
//    }
//
//    public void setInventoryId(String inventoryId) {
//        this.inventoryId = inventoryId;
//    }
//
//    public String getSerialNumber() {
//        return serialNumber;
//    }
//
//    public void setSerialNumber(String serialNumber) {
//        this.serialNumber = serialNumber;
//    }
//
//    public String getCaptureMethod() {
//        return captureMethod;
//    }
//
//    public void setCaptureMethod(String captureMethod) {
//        this.captureMethod = captureMethod;
//    }
//
//    public String getCapturedBy() {
//        return capturedBy;
//    }
//
//    public void setCapturedBy(String capturedBy) {
//        this.capturedBy = capturedBy;
//    }
//
//    public String getItemCode() {
//        return itemCode;
//    }
//
//    public void setItemCode(String itemCode) {
//        this.itemCode = itemCode;
//    }
//
//    public String getStatus() {
//        return status;
//    }
//
//    public void setStatus(String status) {
//        this.status = status;
//    }
//
//    public String getTechnology() {
//        return technology;
//    }
//
//    public void setTechnology(String technology) {
//        this.technology = technology;
//    }
//
//    public String getDomain() {
//        return domain;
//    }
//
//    public void setDomain(String domain) {
//        this.domain = domain;
//    }
//
//    public String getManufactureDate() {
//        return manufactureDate;
//    }
//
//    public void setManufactureDate(String manufactureDate) {
//        this.manufactureDate = manufactureDate;
//    }
//
//    public Integer getIsActive() {
//        return isActive;
//    }
//
//    public void setIsActive(Integer isActive) {
//        this.isActive = isActive;
//    }
//
//    public String getPONumber() {
//        return PONumber;
//    }
//
//    public void setPONumber(String PONumber) {
//        this.PONumber = PONumber;
//    }
//
//    public String getLocationId() {
//        return locationId;
//    }
//
//    public void setLocationId(String locationId) {
//        this.locationId = locationId;
//    }
//
//    public String getVendorFamilyType() {
//        return vendorFamilyType;
//    }
//
//    public void setVendorFamilyType(String vendorFamilyType) {
//        this.vendorFamilyType = vendorFamilyType;
//    }
//
//    public String getTagLine() {
//        return tagLine;
//    }
//
//    public void setTagLine(String tagLine) {
//        this.tagLine = tagLine;
//    }
//
//    public String getLastUpdateDate() {
//        return lastUpdateDate;
//    }
//
//    public void setLastUpdateDate(String lastUpdateDate) {
//        this.lastUpdateDate = lastUpdateDate;
//    }
//
//    public String getLastUpdateBy() {
//        return lastUpdateBy;
//    }
//
//    public void setLastUpdateBy(String lastUpdateBy) {
//        this.lastUpdateBy = lastUpdateBy;
//    }
//
//    public String getDetails() {
//        return details;
//    }
//
//    public void setDetails(String details) {
//        this.details = details;
//    }
//
//    public String getLastSeenDatetime() {
//        return lastSeenDatetime;
//    }
//
//    public void setLastSeenDatetime(String lastSeenDatetime) {
//        this.lastSeenDatetime = lastSeenDatetime;
//    }
//}
