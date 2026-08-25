package com.zain.almksazain.services;

/** One item's rejection reason within a batch create — the rest of the batch still proceeds. */
public class UplChangeRequestFailure {
    private final Long uplRecordNo;
    private final String reason;

    public UplChangeRequestFailure(Long uplRecordNo, String reason) {
        this.uplRecordNo = uplRecordNo;
        this.reason = reason;
    }

    public Long getUplRecordNo() {
        return uplRecordNo;
    }

    public String getReason() {
        return reason;
    }
}
