package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "DccPoStatusCombinedView")
public class DccPoCombinedView {
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
    private int deliveredQty;
    private String locationName;
    private String inserviceDate;
    private double unitPrice;
    private String scopeOfWork;
    private String remarks;
    private String userId;
    private String dccStatusState;
    private String statusDate;
    private int poRecordNo;
    private String recordDatetime;
    private String poId;
    private String poDate;
    private String supplierId;
    private String itemCode;
    private String termsAndConditions;
    private String deliveryLocationId;
    private String createdBy;
    private String approvedBy;
    private String approvalDate;
    private String poStatus;
    private boolean approved;
    private boolean delivered;

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

    public int getDeliveredQty() {
        return deliveredQty;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getInserviceDate() {
        return inserviceDate;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public String getScopeOfWork() {
        return scopeOfWork;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getUserId() {
        return userId;
    }

    public String getDccStatusState() {
        return dccStatusState;
    }

    public String getStatusDate() {
        return statusDate;
    }

    public int getPoRecordNo() {
        return poRecordNo;
    }

    public String getRecordDatetime() {
        return recordDatetime;
    }

    public String getPoId() {
        return poId;
    }

    public String getPoDate() {
        return poDate;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public String getDeliveryLocationId() {
        return deliveryLocationId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public String getApprovalDate() {
        return approvalDate;
    }

    public String getPoStatus() {
        return poStatus;
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isDelivered() {
        return delivered;
    }
}
