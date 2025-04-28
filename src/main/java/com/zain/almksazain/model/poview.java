package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "PurchaseOrderView")
public class poview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long hdRecordNo;
    private String hdRecordDatetime;
    private String poId;
    private String poDate;
    private String supplierId;
    private String termsAndConditions;
    private String deliveryLocationId;
    private String createdBy;
    private String approvedBy;
    private String approvalDate;
    private String status;
    private boolean approved;
    private boolean delivered;
    private long lnRecordNo;
    private String lnRecordDatetime;
    private long lineNumber;
    private String itemCode;
    private String UoM;
    private String boqId;
    private int orderQuantity;
    private double unitPrice;
    private double VAT;
    private double linePrice;

    public long getHdRecordNo() {
        return hdRecordNo;
    }

    public String getHdRecordDatetime() {
        return hdRecordDatetime;
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

    public String getStatus() {
        return status;
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public long getLnRecordNo() {
        return lnRecordNo;
    }

    public String getLnRecordDatetime() {
        return lnRecordDatetime;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getUoM() {
        return UoM;
    }

    public String getBoqId() {
        return boqId;
    }

    public int getOrderQuantity() {
        return orderQuantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getVAT() {
        return VAT;
    }

    public double getLinePrice() {
        return linePrice;
    }
}
