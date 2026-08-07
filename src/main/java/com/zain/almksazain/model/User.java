package com.zain.almksazain.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "tb_SystemUsers")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private Integer userId;
    private String username;
    private String userPassword;
    private LocalDateTime dateAdded;
    private Integer addedBy;
    private Integer updatedBy;
    private LocalDateTime updatedDateTime;
    private Boolean sysStatus;
    private String status;
    private String userPosition;
    private String phoneNumber;
    private String emailAddress;
    private String lineManager;
    private String escalationManager;
    private String passChange;
    private LocalDateTime lastPasswordChangeDate;
    private String fullName;
    private Boolean canApprove;
    private Boolean canEdit;
    @Column(name = "canReceiveAccptncRqst")
    private Boolean canReceiveAcceptanceRequest;
    private Integer roleId;
    private String supplierId;
    private String approvalLevel;
    private Integer departmentId;

    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getUserPassword() {
        return userPassword;
    }
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }
    public LocalDateTime getDateAdded() {
        return dateAdded;
    }
    public void setDateAdded(LocalDateTime dateAdded) {
        this.dateAdded = dateAdded;
    }
    public Integer getAddedBy() {
        return addedBy;
    }
    public void setAddedBy(Integer addedBy) {
        this.addedBy = addedBy;
    }
    public Integer getUpdatedBy() {
        return updatedBy;
    }
    public void setUpdatedBy(Integer updatedBy) {
        this.updatedBy = updatedBy;
    }
    public LocalDateTime getUpdatedDateTime() {
        return updatedDateTime;
    }
    public void setUpdatedDateTime(LocalDateTime updatedDateTime) {
        this.updatedDateTime = updatedDateTime;
    }
    public Boolean getSysStatus() {
        return sysStatus;
    }
    public void setSysStatus(Boolean sysStatus) {
        this.sysStatus = sysStatus;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getUserPosition() {
        return userPosition;
    }
    public void setUserPosition(String userPosition) {
        this.userPosition = userPosition;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getEmailAddress() {
        return emailAddress;
    }
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    public String getPassChange() {
        return passChange;
    }
    public void setPassChange(String passChange) {
        this.passChange = passChange;
    }
    public LocalDateTime getLastPasswordChangeDate() {
        return lastPasswordChangeDate;
    }
    public void setLastPasswordChangeDate(LocalDateTime lastPasswordChangeDate) {
        this.lastPasswordChangeDate = lastPasswordChangeDate;
    }
    public String getFullName() {
        return fullName;
    }
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    public Boolean getCanApprove() {
        return canApprove;
    }
    public void setCanApprove(Boolean canApprove) {
        this.canApprove = canApprove;
    }
    public Boolean getCanEdit() {
        return canEdit;
    }
    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }
    public Boolean getCanReceiveAcceptanceRequest() {
        return canReceiveAcceptanceRequest;
    }
    public void setCanReceiveAcceptanceRequest(Boolean canReceiveAcceptanceRequest) {
        this.canReceiveAcceptanceRequest = canReceiveAcceptanceRequest;
    }
    public Integer getRoleId() {
        return roleId;
    }
    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }
    public String getSupplierId() {
        return supplierId;
    }
    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }
    public String getApprovalLevel() {
        return approvalLevel;
    }
    public void setApprovalLevel(String approvalLevel) {
        this.approvalLevel = approvalLevel;
    }
    public Integer getDepartmentId() {
        return departmentId;
    }
    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getLineManager() {
        return lineManager;
    }

    public void setLineManager(String lineManager) {
        this.lineManager = lineManager;
    }

    public String getEscalationManager() {
        return escalationManager;
    }

    public void setEscalationManager(String escalationManager) {
        this.escalationManager = escalationManager;
    }

    
}

