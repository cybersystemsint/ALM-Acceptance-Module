package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only mirror of WorkFlow-Management's tb_Workflows — one row per
 * (module, action type) approval workflow, configured through Configurations
 * > Approval Workflow. actionType is stored as plain text ("UPDATE"/"DELETE"/
 * "ADD"/"DECOMMISSION") matching {@link UplActionType#name()} exactly.
 */
@Entity
@Table(name = "tb_Workflows")
public class StdWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer recordNo;

    @Column(name = "moduleId")
    private Integer moduleId;

    private String actionType;

    private Integer status;

    public Integer getRecordNo() {
        return recordNo;
    }

    public Integer getModuleId() {
        return moduleId;
    }

    public String getActionType() {
        return actionType;
    }

    public Integer getStatus() {
        return status;
    }
}
