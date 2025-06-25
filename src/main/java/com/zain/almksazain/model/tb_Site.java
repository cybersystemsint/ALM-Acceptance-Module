/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 *
 * @author jgithu
 */

@Entity
@Table(name = "tb_Site")
public class tb_Site implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long recordNo;
    public String siteId;
    public String longitude;
    public String latitude;
    public Integer regionId;
    public Integer siteTypeId;
    public String siteStatus;
    public String leaseStatus;
    public String GLISites;

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

 

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public Integer getRegionId() {
        return regionId;
    }

    public void setRegionId(Integer regionId) {
        this.regionId = regionId;
    }

    public Integer getSiteTypeId() {
        return siteTypeId;
    }

    public void setSiteTypeId(Integer siteTypeId) {
        this.siteTypeId = siteTypeId;
    }

    public String getSiteStatus() {
        return siteStatus;
    }

    public void setSiteStatus(String siteStatus) {
        this.siteStatus = siteStatus;
    }

    public String getLeaseStatus() {
        return leaseStatus;
    }

    public void setLeaseStatus(String leaseStatus) {
        this.leaseStatus = leaseStatus;
    }

    public String getGLISites() {
        return GLISites;
    }

    public void setGLISites(String GLISites) {
        this.GLISites = GLISites;
    }
    
    
}
