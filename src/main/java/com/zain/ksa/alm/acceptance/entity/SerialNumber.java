package com.zain.ksa.alm.acceptance.entity;

import java.io.Serializable;
import java.sql.Date;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "tb_SerialNumber")
public class SerialNumber implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public Date recordDatetime;
    public String serialNumber;
    public String itemCode;
    public String location;
    public Date InServiceDate;
    public String createdBy;

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public Date getRecordDatetime() {
        return recordDatetime;
    }

    public void setRecordDatetime(Date recordDatetime) {
        this.recordDatetime = recordDatetime;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Date getInServiceDate() {
        return InServiceDate;
    }

    public void setInServiceDate(Date InServiceDate) {
        this.InServiceDate = InServiceDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

}
