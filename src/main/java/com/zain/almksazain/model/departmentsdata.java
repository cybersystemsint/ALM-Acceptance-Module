package com.zain.almksazain.model;

import javax.persistence.*;



@Entity
@Table(name = "tb_Departments")
public class departmentsdata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long recordNo;
    String deptName;
    boolean sysStatus;

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
