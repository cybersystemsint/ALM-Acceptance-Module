/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.model;

import java.io.Serializable;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@Table(name = "tb_Category_Approvals")
public class tbCategoryApprovals implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int approvalId;

    private int approvalRecordId;

    private int approvalLevelId;

    // Snapshot of the referenced approval level's own number/department at
    // the moment this row was created - approvalLevelId above is still kept
    // (useful for diagnostics), but nothing should resolve a level's number
    // or department by following that FK, since a later edit to that
    // tb_Scope_Approval_Levels row (or the hierarchy-adjustment feature
    // renumbering it) must not retroactively change what an already-created
    // approval record shows. approverName below already followed this same
    // copy-don't-reference pattern.
    private Integer levelNumber;

    private Integer departmentId;

    private int approverId;

    private String approverName;

    private int regionId;

    private String status = "Pending";

    private String approvalStatus = "pending";

    private String comments;

    private Integer approvedBy;

    private Integer actionTypeId;

    private LocalDateTime approvedDate;

    // private LocalDate recordDateTime;
    private LocalDateTime recordDateTime;

    private boolean display;

    public int getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(int approvalId) {
        this.approvalId = approvalId;
    }

    public int getApprovalRecordId() {
        return approvalRecordId;
    }

    public void setApprovalRecordId(int approvalRecordId) {
        this.approvalRecordId = approvalRecordId;
    }

    public int getApprovalLevelId() {
        return approvalLevelId;
    }

    public void setApprovalLevelId(int approvalLevelId) {
        this.approvalLevelId = approvalLevelId;
    }

    public Integer getLevelNumber() {
        return levelNumber;
    }

    public void setLevelNumber(Integer levelNumber) {
        this.levelNumber = levelNumber;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public int getApproverId() {
        return approverId;
    }

    public void setApproverId(int approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Integer getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Integer approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Integer getActionTypeId() {
        return actionTypeId;
    }

    public void setActionTypeId(Integer actionTypeId) {
        this.actionTypeId = actionTypeId;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }

    public LocalDateTime  getRecordDateTime() {
        return recordDateTime;
    }

    public void setRecordDateTime(LocalDateTime  recordDateTime) {
        this.recordDateTime = recordDateTime;
    }

   
    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        this.display = display;
    }

}
