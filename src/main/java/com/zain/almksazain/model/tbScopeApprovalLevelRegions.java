package com.zain.almksazain.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Local mapping onto the same physical table WorkFlow-Management's
 * ScopeApprovalRegion entity uses (tb_Scope_Approval_Level_Regions) - which
 * region(s) a given approval level applies to.
 */
@Entity
@Table(name = "tb_Scope_Approval_Level_Regions")
public class tbScopeApprovalLevelRegions implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;

    private int approvalLevelId;
    private int regionId;
    private int scopeId;

    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public int getApprovalLevelId() {
        return approvalLevelId;
    }

    public void setApprovalLevelId(int approvalLevelId) {
        this.approvalLevelId = approvalLevelId;
    }

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }

    public int getScopeId() {
        return scopeId;
    }

    public void setScopeId(int scopeId) {
        this.scopeId = scopeId;
    }
}
