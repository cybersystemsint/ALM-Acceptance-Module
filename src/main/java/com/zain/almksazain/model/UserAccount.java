package com.zain.almksazain.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = " tb_SystemUsers")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private Integer userId;

    @Column(name = "username")
    private String username;

    @Column(name = "userpassword")
    private String userPassword;

    @Column(name = "dateadded")
    private LocalDateTime dateAdded;

    @Column(name = "addedby")
    private Integer addedBy;

    @Column(name = "updatedBy")
    private Integer updatedBy;

    @Column(name = "updatedDateTime")
    private LocalDateTime updatedDateTime;

    @Column(name = "SysStatus")
    private Boolean sysStatus = false;

    @Column(name = "status")
    private String status = "pending";

    @Column(name = "userposition")
    private String userPosition;

    @Column(name = "phonenumber")
    private String phoneNumber;

    @Column(name = "emailaddress")
    private String emailAddress;

    @Column(name = "passchange")
    private Boolean passChange;

    @Column(name = "lastPasswordChangeDate")
    private LocalDateTime lastPasswordChangeDate;

    @Column(name = "fullName")
    private String fullName;

    @Column(name = "canApprove")
    private Boolean canApprove;

    @Column(name = "canEdit")
    private Boolean canEdit;

    @Column(name = "canReceiveAccptncRqst")
    private Boolean canReceiveAccptncRqst = false;

    @Column(name = "roleId")
    private Integer roleId;

    @Column(name = "supplierId")
    private String supplierId;

    @Column(name = "approvalLevel")
    private String approvalLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departmentId")
    private tb_department department;

    // Getters and Setters

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

    public Boolean getPassChange() {
        return passChange;
    }

    public void setPassChange(Boolean passChange) {
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

    public Boolean getCanReceiveAccptncRqst() {
        return canReceiveAccptncRqst;
    }

    public void setCanReceiveAccptncRqst(Boolean canReceiveAccptncRqst) {
        this.canReceiveAccptncRqst = canReceiveAccptncRqst;
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

    public tb_department getDepartment() {
        return department;
    }

    public void setDepartment(tb_department department) {
        this.department = department;
    }
}