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
@Table(name = "tb_Region")
public class Region implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public Date recordDatetime;
    public String regionName;
    public Integer status;

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

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
