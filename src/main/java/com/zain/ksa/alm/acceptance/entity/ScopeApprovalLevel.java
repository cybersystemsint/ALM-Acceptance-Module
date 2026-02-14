package com.zain.ksa.alm.acceptance.entity;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "tb_Scope_Approval_Levels")
public class ScopeApprovalLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long recordNo;
    private Integer level;
    private Integer scope;
    private String levelName;
    private Integer departmentId;
    private Integer approverUserId;
    private String approverEmail;
    private Integer status;
    private String createdBy;
    private String updatedBy;

    public long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(long recordNo) {
        this.recordNo = recordNo;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getScope() {
        return scope;
    }

    public void setScope(Integer scope) {
        this.scope = scope;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public Integer getApproverUserId() {
        return approverUserId;
    }

    public void setApproverUserId(Integer approverUserId) {
        this.approverUserId = approverUserId;
    }

    public String getApproverEmail() {
        return approverEmail;
    }

    public void setApproverEmail(String approverEmail) {
        this.approverEmail = approverEmail;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
