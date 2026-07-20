package com.zain.almksazain.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "tb_Scope_Approval_Suppliers")
public class tbScopeApprovalSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approvalLevelId", nullable = false)
    private tbScopeApprovalLevels scopeApprovalLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplierId", nullable = false)
    private supplierdata supplier;

    public Integer getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Integer recordNo) {
        this.recordNo = recordNo;
    }

    public tbScopeApprovalLevels getScopeApprovalLevel() {
        return scopeApprovalLevel;
    }

    public void setScopeApprovalLevel(tbScopeApprovalLevels scopeApprovalLevel) {
        this.scopeApprovalLevel = scopeApprovalLevel;
    }

    public supplierdata getSupplier() {
        return supplier;
    }

    public void setSupplier(supplierdata supplier) {
        this.supplier = supplier;
    }
}
