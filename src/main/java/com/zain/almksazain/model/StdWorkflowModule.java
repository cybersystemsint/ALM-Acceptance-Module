package com.zain.almksazain.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only mirror of WorkFlow-Management's tb_Modules (its own {@code Module}
 * entity lives in {@code com.bushnet.AlmWorkflowManagement.models}). Same
 * physical table, different service — configuration happens through that
 * service's existing screens (Configurations > Approval Workflow), this side
 * only ever reads it to resolve which workflow governs UPL update/delete.
 */
@Entity
@Table(name = "tb_Modules")
public class StdWorkflowModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    private String moduleName;
    private Integer status;
    private String tableName;

    public Integer getRecordNo() {
        return recordNo;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Integer getStatus() {
        return status;
    }

    public String getTableName() {
        return tableName;
    }
}
