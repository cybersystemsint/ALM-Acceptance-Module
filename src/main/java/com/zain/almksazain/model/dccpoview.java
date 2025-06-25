package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "dccPOCombinedView")
public class dccpoview {

    private long dccRecordNo;
    private String dccPoNumber;
    private String dccVendorName;
    private String dccVendorEmail;
    private String dccBoqId;
    private String dccProjectName;
    private String dccAcceptanceType;
    private String dccStatus;
    private String dccCreatedDate;
    private String dccId;
    private String dccCurrency;
    private int lnRecordNo;
    private String lnProductName;
    private String lnProductSerialNo;
    private Integer lnDeliveredQty;
    private String lnLocationName;
    private String lnInserviceDate;
    private Double lnUnitPrice;
    private String lnScopeOfWork;
    private String lnRemarks;
    @Id
    private long poRecordNo;
    private String poId;
    private String poDate;
    private String supplierId;
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

    public String getDccPoNumber() {
        return dccPoNumber;
    }

    public String getDccVendorName() {
        return dccVendorName;
    }

    public String getDccVendorEmail() {
        return dccVendorEmail;
    }

    public String getDccBoqId() {
        return dccBoqId;
    }

    public String getDccProjectName() {
        return dccProjectName;
    }

    public String getDccAcceptanceType() {
        return dccAcceptanceType;
    }

    public String getDccStatus() {
        return dccStatus;
    }

    public String getDccCreatedDate() {
        return dccCreatedDate;
    }

    public String getDccId() {
        return dccId;
    }

    public String getDccCurrency() {
        return dccCurrency;
    }

    public int getLnRecordNo() {
        return lnRecordNo;
    }

    public String getLnProductName() {
        return lnProductName;
    }

    public String getLnProductSerialNo() {
        return lnProductSerialNo;
    }

    public Integer getLnDeliveredQty() {
        return lnDeliveredQty;
    }

    public String getLnLocationName() {
        return lnLocationName;
    }

    public String getLnInserviceDate() {
        return lnInserviceDate;
    }

    public Double getLnUnitPrice() {
        return lnUnitPrice;
    }

    public String getLnScopeOfWork() {
        return lnScopeOfWork;
    }

    public String getLnRemarks() {
        return lnRemarks;
    }

    public long getPoRecordNo() {
        return poRecordNo;
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
