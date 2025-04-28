package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "DCCView")
public class vwdcc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long dccRecordNo;
    private String poNumber;
    private String vendorName;
    private String vendorEmail;
    private String boqId;
    private String projectName;
    private String acceptanceType;
    private String dccStatus;
    private String createdDate;
    private String dccId;
    private String currency;
    private int lnRecordNo;
    private String productName;
    private String productSerialNo;
    private Integer deliveredQty;
    private String locationName;
    private String inserviceDate;
    private Double unitPrice;
    private String scopeOfWork;
    private String remarks;

    public long getDccRecordNo() {
        return dccRecordNo;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getVendorEmail() {
        return vendorEmail;
    }

    public String getBoqId() {
        return boqId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getAcceptanceType() {
        return acceptanceType;
    }

    public String getDccStatus() {
        return dccStatus;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getDccId() {
        return dccId;
    }

    public String getCurrency() {
        return currency;
    }

    public int getLnRecordNo() {
        return lnRecordNo;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductSerialNo() {
        return productSerialNo;
    }

    public Integer getDeliveredQty() {
        return deliveredQty;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getInserviceDate() {
        return inserviceDate;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public String getScopeOfWork() {
        return scopeOfWork;
    }

    public String getRemarks() {
        return remarks;
    }
}
