package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "tb_DCC_Status")
public class dccstatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private String dccId;
    private String userId;
    private String status;
    private String statusDate;
    int lnRecordNo;

    public int getLnRecordNo() {
        return lnRecordNo;
    }

    public void setLnRecordNo(int lnRecordNo) {
        this.lnRecordNo = lnRecordNo;
    }

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public String getDccId() {
        return dccId;
    }

    public void setDccId(String dccId) {
        this.dccId = dccId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDate() {
        return statusDate;
    }

    public void setStatusDate(String statusDate) {
        this.statusDate = statusDate;
    }
}
