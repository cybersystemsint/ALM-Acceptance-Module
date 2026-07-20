package com.zain.almksazain.dto;

public class WorkflowInitResult {

    private final int dccId;
    private final int approvalRequestRecordNo;
    private final String poNumber;
    private final String vendorName;
    private final String requestedBy;
    private final String scopeName;
    private final String firstApproverEmail;
    private final String firstApproverName;

    public WorkflowInitResult(int dccId, int approvalRequestRecordNo, String poNumber, String vendorName,
            String requestedBy, String scopeName, String firstApproverEmail, String firstApproverName) {
        this.dccId = dccId;
        this.approvalRequestRecordNo = approvalRequestRecordNo;
        this.poNumber = poNumber;
        this.vendorName = vendorName;
        this.requestedBy = requestedBy;
        this.scopeName = scopeName;
        this.firstApproverEmail = firstApproverEmail;
        this.firstApproverName = firstApproverName;
    }

    public int getDccId() {
        return dccId;
    }

    public int getApprovalRequestRecordNo() {
        return approvalRequestRecordNo;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getScopeName() {
        return scopeName;
    }

    public String getFirstApproverEmail() {
        return firstApproverEmail;
    }

    public String getFirstApproverName() {
        return firstApproverName;
    }
}
