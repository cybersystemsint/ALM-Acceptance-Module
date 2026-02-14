package com.zain.ksa.alm.acceptance.entity;

import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "tb_Approval_Logs")
public class ApprovalLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public long recordNo;
	public Date recordDatetime;
	public Integer approvalRecordId;
	public String recordType;
	public String PONumber;
	public String status;
	public Integer createdBy;
	public String approver;
	public String region;
	public String itemCategoryCode;

	public long getRecordNo() {
		return recordNo;
	}

	public void setRecordNo(long recordNo) {
		this.recordNo = recordNo;
	}

	public Date getRecordDatetime() {
		return recordDatetime;
	}

	public void setRecordDatetime(Date recordDatetime) {
		this.recordDatetime = recordDatetime;
	}

	public Integer getApprovalRecordId() {
		return approvalRecordId;
	}

	public void setApprovalRecordId(Integer approvalRecordId) {
		this.approvalRecordId = approvalRecordId;
	}

	public String getRecordType() {
		return recordType;
	}

	public void setRecordType(String recordType) {
		this.recordType = recordType;
	}

	public String getPONumber() {
		return PONumber;
	}

	public void setPONumber(String PONumber) {
		this.PONumber = PONumber;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(Integer createdBy) {
		this.createdBy = createdBy;
	}

	public String getApprover() {
		return approver;
	}

	public void setApprover(String approver) {
		this.approver = approver;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getItemCategoryCode() {
		return itemCategoryCode;
	}

	public void setItemCategoryCode(String itemCategoryCode) {
		this.itemCategoryCode = itemCategoryCode;
	}
}
