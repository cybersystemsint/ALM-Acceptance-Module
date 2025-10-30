package com.zain.almksazain.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AgingReportGroupDTO {
    private String department;
    private String departmentStatus;
    private int totalApprovers;
    private int totalRequests;
    private BigDecimal value;
    private Map<String, Integer> buckets; // e.g. {"sameDay": 0, "oneDay": 1, ...}
    private List<AgingReportApproverDTO> approvers;
    public String getDepartment() {
        return department;
    }
    public void setDepartment(String department) {
        this.department = department;
    }
    public String getDepartmentStatus() {
        return departmentStatus;
    }
    public void setDepartmentStatus(String departmentStatus) {
        this.departmentStatus = departmentStatus;
    }
    public int getTotalApprovers() {
        return totalApprovers;
    }
    public void setTotalApprovers(int totalApprovers) {
        this.totalApprovers = totalApprovers;
    }
    public int getTotalRequests() {
        return totalRequests;
    }
    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }
    public BigDecimal getValue() {
        return value;
    }
    public void setValue(BigDecimal value) {
        this.value = value;
    }
    public Map<String, Integer> getBuckets() {
        return buckets;
    }
    public void setBuckets(Map<String, Integer> buckets) {
        this.buckets = buckets;
    }
    public List<AgingReportApproverDTO> getApprovers() {
        return approvers;
    }
    public void setApprovers(List<AgingReportApproverDTO> approvers) {
        this.approvers = approvers;
    }

}
