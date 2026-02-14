package com.zain.ksa.alm.acceptance.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "alm")
@Component
public class ALMProperties {

	private String uploadPath = "/data/app/logs/ALM/POUPL/";
	private String logPath = "/data/app/logs/ALM/Configuration/configuration-management.log";
	private String batchFilePath = "/home/app/logs/ALM/BatchFiles/";

	private long maxFileSize = 100 * 1024 * 1024; // 100MB
	private int maxValidationMonths = 6;

	private List<String> allowedFileExtensions = Arrays.asList(".pdf", ".doc", ".csv", ".docx", ".xlsx", ".jpeg",
			".msg", ".jpg", ".png", ".xlsm", ".xls", ".zip", ".rar");

	private List<String> allowedDccStatuses = Arrays.asList("approved-received", "inprocess", "approved", "returned",
			"request-info");

	private String workflowUrl = "http://localhost:8080/alm-zain-ksa/workflow/initialize-approval";
	private String timezone = "Africa/Nairobi";

	// Getters and Setters
	public String getUploadPath() {
		return uploadPath;
	}

	public void setUploadPath(String uploadPath) {
		this.uploadPath = uploadPath;
	}

	public String getLogPath() {
		return logPath;
	}

	public void setLogPath(String logPath) {
		this.logPath = logPath;
	}

	public String getBatchFilePath() {
		return batchFilePath;
	}

	public void setBatchFilePath(String batchFilePath) {
		this.batchFilePath = batchFilePath;
	}

	public long getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	public int getMaxValidationMonths() {
		return maxValidationMonths;
	}

	public void setMaxValidationMonths(int maxValidationMonths) {
		this.maxValidationMonths = maxValidationMonths;
	}

	public List<String> getAllowedFileExtensions() {
		return allowedFileExtensions;
	}

	public void setAllowedFileExtensions(List<String> allowedFileExtensions) {
		this.allowedFileExtensions = allowedFileExtensions;
	}

	public List<String> getAllowedDccStatuses() {
		return allowedDccStatuses;
	}

	public void setAllowedDccStatuses(List<String> allowedDccStatuses) {
		this.allowedDccStatuses = allowedDccStatuses;
	}

	public String getWorkflowUrl() {
		return workflowUrl;
	}

	public void setWorkflowUrl(String workflowUrl) {
		this.workflowUrl = workflowUrl;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}
}