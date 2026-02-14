package com.zain.ksa.alm.acceptance.entity;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "tb_fileRecords")
public class FileRecord implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long recordNo;
	private String fileName;
	private String filePath;
	private String poNumber;
	private Integer dccId;

	public FileRecord() {
	}

	public long getRecordNo() {
		return recordNo;
	}

	public void setRecordNo(long recordNo) {
		this.recordNo = recordNo;
	}

	public Integer getDccId() {
		return dccId;
	}

	public void setDccId(Integer dccId) {
		this.dccId = dccId;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getPoNumber() {
		return poNumber;
	}

	public void setPoNumber(String poNumber) {
		this.poNumber = poNumber;
	}

}
