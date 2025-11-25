package com.zain.almksazain.dto;

import java.util.List;

public class AgingEmailConfigRequest {
    private String jobName;
    private String cronExpression;
    private String time; // optional: HH:mm or HH:mm:ss
    private String timezone;
    private Boolean enabled;
    private String description;
    private String createdBy;
    private String updatedBy;
    private String targetType;

    // New: accept either a single department (legacy) or an array of departments
    private String department;
    private List<String> departments;

    // optional user aging threshold in days (Integer). If null, service default thresholds apply.
    private Integer userAging;

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    // Legacy single department getter/setter (kept for backward compatibility)
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // New list-based departments getter/setter
    public List<String> getDepartments() { return departments; }
    public void setDepartments(List<String> departments) { this.departments = departments; }

    public Integer getUserAging() { return userAging; }
    public void setUserAging(Integer userAging) { this.userAging = userAging; }
}