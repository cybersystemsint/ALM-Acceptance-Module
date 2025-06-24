package com.zain.almksazain.dto;

import java.util.List;

public class AgingReportResponse {
    private Long totalRecords;
    private List<AgingReportItem> data;
    private Integer totalPages;
    private Integer pageSize;
    private Integer currentPage;
    
    // Getters and setters
    public Long getTotalRecords() {
        return totalRecords;
    }
    public void setTotalRecords(Long totalUniqueRecords) {
        this.totalRecords = totalUniqueRecords;
    }
    public List<AgingReportItem> getData() {
        return data;
    }
    public void setData(List<AgingReportItem> data) {
        this.data = data;
    }
    public Integer getTotalPages() {
        return totalPages;
    }
    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
    public Integer getPageSize() {
        return pageSize;
    }
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
    public Integer getCurrentPage() {
        return currentPage;
    }
    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }
}