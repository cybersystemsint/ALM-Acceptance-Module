package com.zain.almksazain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "tb_Aging_Email_config")
public class AgingEmailConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "description")
    private String description;

    @Column(name = "last_run_time")
    private LocalDateTime lastRunTime;

    @Column(name = "next_run_time")
    private LocalDateTime nextRunTime;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "department")
    private String department;

    @Column(name = "user_aging")
    private Integer userAging;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(LocalDateTime lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public LocalDateTime getNextRunTime() {
        return nextRunTime;
    }

    public void setNextRunTime(LocalDateTime nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }


    public String getDepartment() {
        return department;
    }


    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getUserAging() {
        return userAging;
    }

    public void setUserAging(Integer userAging) {
        this.userAging = userAging;
    }

    public void setDepartments(List<String> depts) {
        if (depts == null || depts.isEmpty()) {
            this.department = null;
            return;
        }
        // if "all" present -> explicit ALL
        for (String d : depts) {
            if (d != null && "all".equalsIgnoreCase(d.trim())) {
                this.department = "ALL";
                return;
            }
        }
        try {
            ObjectMapper om = new ObjectMapper();
            this.department = om.writeValueAsString(depts);
        } catch (Exception e) {
            // fallback to a comma-joined string if JSON writing fails
            this.department = String.join(",", depts);
        }
    }


    public List<String> getDepartmentsList() {
        if (this.department == null) return Collections.emptyList();
        String raw = this.department.trim();
        if (raw.isEmpty()) return Collections.emptyList();
        if ("ALL".equalsIgnoreCase(raw)) {
            List<String> all = new ArrayList<>(1);
            all.add("ALL");
            return all;
        }
        // try JSON parse
        if (raw.startsWith("[")) {
            try {
                ObjectMapper om = new ObjectMapper();
                List<String> list = om.readValue(raw, new TypeReference<List<String>>() {});
                if (list == null) return Collections.emptyList();
                return list;
            } catch (Exception ignored) { }
        }
        // fallback: single string or comma-separated legacy format
        if (raw.contains(",")) {
            String[] parts = raw.split("\\s*,\\s*");
            List<String> l = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (p != null && !p.isBlank()) l.add(p.trim());
            }
            return l;
        }
        List<String> single = new ArrayList<>(1);
        single.add(raw);
        return single;
    }
}