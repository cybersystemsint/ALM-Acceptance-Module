package com.zain.almksazain.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Local mapping onto the same physical table WorkFlow-Management's
 * ScopeApprovalSupplier entity uses (tb_Scope_Approval_Suppliers) - a
 * supplier-specific approver override for a given approval level.
 */
@Entity
@Table(name = "tb_Scope_Approval_Suppliers")
public class tbScopeApprovalSuppliers implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;

    private int approvalLevelId;
    private int supplierId;

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

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }
}
