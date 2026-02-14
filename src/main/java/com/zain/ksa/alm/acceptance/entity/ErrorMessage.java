package com.zain.ksa.alm.acceptance.entity;

import java.io.Serializable;
import java.sql.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.zain.ksa.alm.acceptance.enums.Severity;

@Entity
@Table(name = "tb_ErrorMessages")
public class ErrorMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long recordNo;

	@Column(nullable = false)
	private String module;

	@Column(nullable = false)
	private String errorCode;

	@Column(nullable = false)
	private String errorMessage;

	@Column(nullable = false)
	private String operation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Severity severity;

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false, updatable = false)
	private Date createdDatetime;

	@Column
	private String updatedBy;

	@Column
	private Date updatedDatetime;

	public Long getRecordNo() {
		return recordNo;
	}

	public void setRecordNo(Long recordNo) {
		this.recordNo = recordNo;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

	public Severity getSeverity() {
		return severity;
	}

	public void setSeverity(Severity severity) {
		this.severity = severity;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public Date getCreatedDatetime() {
		return createdDatetime;
	}

	public void setCreatedDatetime(Date createdDatetime) {
		this.createdDatetime = createdDatetime;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public Date getUpdatedDatetime() {
		return updatedDatetime;
	}

	public void setUpdatedDatetime(Date updatedDatetime) {
		this.updatedDatetime = updatedDatetime;
	}
}
