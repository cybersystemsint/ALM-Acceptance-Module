package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_Export_Job")
public class ExportJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "jobId", length = 36)
    private String jobId;

    @Column(name = "reportType")
    private String reportType;

    @Column(name = "status")
    private String status;

    @Column(name = "rowsWritten")
    private long rowsWritten;

    @Column(name = "sheetCount")
    private int sheetCount;

    @Column(name = "fileName")
    private String fileName;

    @Column(name = "filePath")
    private String filePath;

    @Lob
    @Column(name = "errorMessage")
    private String errorMessage;

    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "completedAt")
    private LocalDateTime completedAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getRowsWritten() {
        return rowsWritten;
    }

    public void setRowsWritten(long rowsWritten) {
        this.rowsWritten = rowsWritten;
    }

    public int getSheetCount() {
        return sheetCount;
    }

    public void setSheetCount(int sheetCount) {
        this.sheetCount = sheetCount;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
