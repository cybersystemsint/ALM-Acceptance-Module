package com.zain.almksazain.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "tb_Scope_Approval_Level_Regions")
public class tbScopeApprovalLevelRegion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approvalLevelId", nullable = false)
    private tbScopeApprovalLevels scopeApprovalLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regionId", nullable = false)
    private tb_Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scopeId", nullable = false)
    private tbScope scope;

    public tbScopeApprovalLevelRegion() {
    }

    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public tbScopeApprovalLevels getScopeApprovalLevel() {
        return scopeApprovalLevel;
    }

    public void setScopeApprovalLevel(tbScopeApprovalLevels scopeApprovalLevel) {
        this.scopeApprovalLevel = scopeApprovalLevel;
    }

    public tb_Region getRegion() {
        return region;
    }

    public void setRegion(tb_Region region) {
        this.region = region;
    }

    public tbScope getScope() {
        return scope;
    }

    public void setScope(tbScope scope) {
        this.scope = scope;
    }
}
