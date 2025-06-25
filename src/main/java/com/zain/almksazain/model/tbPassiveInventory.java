/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.model;

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.*;

/**
 *
 * @author jgithu
 */
@Entity
@Table(name = "tb_Passive_Inventory")
public class tbPassiveInventory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;
    private Timestamp recordDateTime;
    private String siteId;
    private String objectId;
    private String parentName;
    private int status;
    private String actualLatitude;
    private String actualLongitude;
    private String auditDate;
    private String auditUser;

    private String entryDate;
    private String entryUser;
    private String internalReference;
    private String itemBarCode;
    private String itemCapacity;
    private String itemClassification;
    private String itemClassification2;
    private String itemCode;
    private String itemMake;
    private String itemStatus;
    private String itemSupplier;
    private String lastModifiedDate;
    private String lastModifiedUser;
    private String locationAddress;
    private String locationClassification;
    private String locationSubType;
    private String model;
    private String note;
    private String part;
    private String serialNumber;
    private String shelterVendor;
    private String shelterRoomId;
    private String UOM;
    private String categoryInNEP;
    private String ownership;
    private String scrapStatus;
    private String scrapUser;
    private String scrapDate;
    private String lastPVStatus;
    private String lastPVUser;
    private String lastPVDate;
    private String PRPONo;
    private int inventoryType;
    private String changedBy;
    private Timestamp changedDate;
    
      
    private Boolean isMapped;

    public Boolean getIsMapped() {
        return isMapped;
    }

    public void setIsMapped(Boolean isMapped) {
        this.isMapped = isMapped;
    }


    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public Timestamp getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(Timestamp recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getActualLatitude() {
        return actualLatitude;
    }

    public void setActualLatitude(String actualLatitude) {
        this.actualLatitude = actualLatitude;
    }

    public String getActualLongitude() {
        return actualLongitude;
    }

    public void setActualLongitude(String actualLongitude) {
        this.actualLongitude = actualLongitude;
    }

    public String getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(String auditDate) {
        this.auditDate = auditDate;
    }

    public String getAuditUser() {
        return auditUser;
    }

    public void setAuditUser(String auditUser) {
        this.auditUser = auditUser;
    }

 

    public String getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }

    public String getEntryUser() {
        return entryUser;
    }

    public void setEntryUser(String entryUser) {
        this.entryUser = entryUser;
    }

    public String getInternalReference() {
        return internalReference;
    }

    public void setInternalReference(String internalReference) {
        this.internalReference = internalReference;
    }

    public String getItemBarCode() {
        return itemBarCode;
    }

    public void setItemBarCode(String itemBarCode) {
        this.itemBarCode = itemBarCode;
    }

    public String getItemCapacity() {
        return itemCapacity;
    }

    public void setItemCapacity(String itemCapacity) {
        this.itemCapacity = itemCapacity;
    }

    public String getItemClassification() {
        return itemClassification;
    }

    public void setItemClassification(String itemClassification) {
        this.itemClassification = itemClassification;
    }

    public String getItemClassification2() {
        return itemClassification2;
    }

    public void setItemClassification2(String itemClassification2) {
        this.itemClassification2 = itemClassification2;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemMake() {
        return itemMake;
    }

    public void setItemMake(String itemMake) {
        this.itemMake = itemMake;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getItemSupplier() {
        return itemSupplier;
    }

    public void setItemSupplier(String itemSupplier) {
        this.itemSupplier = itemSupplier;
    }

    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedUser() {
        return lastModifiedUser;
    }

    public void setLastModifiedUser(String lastModifiedUser) {
        this.lastModifiedUser = lastModifiedUser;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public String getLocationClassification() {
        return locationClassification;
    }

    public void setLocationClassification(String locationClassification) {
        this.locationClassification = locationClassification;
    }

    public String getLocationSubType() {
        return locationSubType;
    }

    public void setLocationSubType(String locationSubType) {
        this.locationSubType = locationSubType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getShelterVendor() {
        return shelterVendor;
    }

    public void setShelterVendor(String shelterVendor) {
        this.shelterVendor = shelterVendor;
    }

    public String getShelterRoomId() {
        return shelterRoomId;
    }

    public void setShelterRoomId(String shelterRoomId) {
        this.shelterRoomId = shelterRoomId;
    }

    public String getUOM() {
        return UOM;
    }

    public void setUOM(String UOM) {
        this.UOM = UOM;
    }

    public String getCategoryInNEP() {
        return categoryInNEP;
    }

    public void setCategoryInNEP(String categoryInNEP) {
        this.categoryInNEP = categoryInNEP;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getScrapStatus() {
        return scrapStatus;
    }

    public void setScrapStatus(String scrapStatus) {
        this.scrapStatus = scrapStatus;
    }

    public String getScrapUser() {
        return scrapUser;
    }

    public void setScrapUser(String scrapUser) {
        this.scrapUser = scrapUser;
    }

    public String getScrapDate() {
        return scrapDate;
    }

    public void setScrapDate(String scrapDate) {
        this.scrapDate = scrapDate;
    }

    public String getLastPVStatus() {
        return lastPVStatus;
    }

    public void setLastPVStatus(String lastPVStatus) {
        this.lastPVStatus = lastPVStatus;
    }

    public String getLastPVUser() {
        return lastPVUser;
    }

    public void setLastPVUser(String lastPVUser) {
        this.lastPVUser = lastPVUser;
    }

    public String getLastPVDate() {
        return lastPVDate;
    }

    public void setLastPVDate(String lastPVDate) {
        this.lastPVDate = lastPVDate;
    }

    public String getPRPONo() {
        return PRPONo;
    }

    public void setPRPONo(String PRPONo) {
        this.PRPONo = PRPONo;
    }

    public int getInventoryType() {
        return inventoryType;
    }

    public void setInventoryType(int inventoryType) {
        this.inventoryType = inventoryType;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Timestamp getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(Timestamp changedDate) {
        this.changedDate = changedDate;
    }

}
