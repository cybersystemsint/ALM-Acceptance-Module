package com.zain.almksazain.dto;

import java.math.BigDecimal;

public class AgingReportDTO {
    private int recordNo;
    private String poNumber;
    private String vendorName;
    private String status;
    private String createdDate;
    private String createdBy;
    private String supplierId;
    private String vendorNumber;
    private String userAging;
    private String totalAging;
    private long approvalCount;
    private String pendingApprovers;
    private String departmentName;
    private BigDecimal totalDeliveredQty;
    private BigDecimal totalUnitPrice;
    private String currency;
    

    // Constructors, getters and setters

    public AgingReportDTO() {}

    // All args constructor
    public AgingReportDTO(
            int recordNo,
            String poNumber,
            String vendorName,
            String status,
            String createdDate,
            String createdBy,
            String supplierId,
            String vendorNumber,
            String userAging,
            String totalAging,
            long approvalCount,
            String pendingApprovers,
            String departmentName,
            BigDecimal totalDeliveredQty,
            BigDecimal totalUnitPrice,
            String currency
    ) {
        this.recordNo = recordNo;
        this.poNumber = poNumber;
        this.vendorName = vendorName;
        this.status = status;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
        this.supplierId = supplierId;
        this.vendorNumber = vendorNumber;
        this.userAging = userAging;
        this.totalAging = totalAging;
        this.approvalCount = approvalCount;
        this.pendingApprovers = pendingApprovers;
        this.departmentName = departmentName;
        this.totalDeliveredQty = totalDeliveredQty;
        this.totalUnitPrice = totalUnitPrice;
        this.currency = currency;
    }

    // Getters and Setters

    public int getRecordNo() { return recordNo; }
    public void setRecordNo(int recordNo) { this.recordNo = recordNo; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }
    public String getVendorNumber() { return vendorNumber; }
    public void setVendorNumber(String vendorNumber) { this.vendorNumber = vendorNumber; }
    public String getUserAging() { return userAging; }
    public void setUserAging(String userAging) { this.userAging = userAging; }
    public String getTotalAging() { return totalAging; }
    public void setTotalAging(String totalAging) { this.totalAging = totalAging; }
    public long getApprovalCount() { return approvalCount; }
    public void setApprovalCount(long approvalCount) { this.approvalCount = approvalCount; }
    public String getPendingApprovers() { return pendingApprovers; }
    public void setPendingApprovers(String pendingApprovers) { this.pendingApprovers = pendingApprovers; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public BigDecimal getTotalDeliveredQty() {
        return totalDeliveredQty;
    }

    public void setTotalDeliveredQty(BigDecimal totalDeliveredQty) {
        this.totalDeliveredQty = totalDeliveredQty;
    }

    public BigDecimal getTotalUnitPrice() {
        return totalUnitPrice;
    }

    public void setTotalUnitPrice(BigDecimal totalUnitPrice) {
        this.totalUnitPrice = totalUnitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    
}