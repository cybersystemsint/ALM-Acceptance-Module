package com.zain.almksazain.dto;

import java.util.List;
import java.util.Map;

public class AgingReportPagedResponseDTO {
    private List<AgingReportGroupDTO> data;
    private long totalValue;
    private long totalPendingApprovers;
    private long totalPendingRequests;
    private Map<String, Long> dailyCounts; // e.g. bucketName->count
    private long totalRecords;
    private int page;
    private int size;
    // getters and setters
    public List<AgingReportGroupDTO> getData() {
        return data;
    }
    public void setData(List<AgingReportGroupDTO> data) {
        this.data = data;
    }
    public long getTotalValue() {
        return totalValue;
    }
    public void setTotalValue(long totalValue) {
        this.totalValue = totalValue;
    }
    public long getTotalPendingApprovers() {
        return totalPendingApprovers;
    }
    public void setTotalPendingApprovers(long totalPendingApprovers) {
        this.totalPendingApprovers = totalPendingApprovers;
    }
    public long getTotalPendingRequests() {
        return totalPendingRequests;
    }
    public void setTotalPendingRequests(long totalPendingRequests) {
        this.totalPendingRequests = totalPendingRequests;
    }
    public Map<String, Long> getDailyCounts() {
        return dailyCounts;
    }
    public void setDailyCounts(Map<String, Long> dailyCounts) {
        this.dailyCounts = dailyCounts;
    }
    public long getTotalRecords() {
        return totalRecords;
    }
    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }
    public int getPage() {
        return page;
    }
    public void setPage(int page) {
        this.page = page;
    }
    public int getSize() {
        return size;
    }
    public void setSize(int size) {
        this.size = size;
    }


}
