package com.zain.almksazain.model;

import javax.persistence.*;

@Entity
@Table(name = "tb_Department")
public class departmentsdata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;

    @Column(name = "departmentName")
    private String deptName;

    @Column(name = "status") 
    private boolean sysStatus;

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public boolean isSysStatus() {
        return sysStatus;
    }

    public void setSysStatus(boolean sysStatus) {
        this.sysStatus = sysStatus;
    }
}