package com.zain.almksazain.dto;

import java.util.List;

import com.zain.almksazain.model.CombinedPurchaseOrder;

public class CombinedPurchaseOrderResponse {
    private long totalRecords;
    private List<CombinedPurchaseOrder> data;
    private int totalPages;
    private int pageSize;
    private int currentPage;

    // Constructor, getters, setters
    public CombinedPurchaseOrderResponse(long totalRecords, List<CombinedPurchaseOrder> data, int totalPages, int pageSize, int currentPage) {
        this.totalRecords = totalRecords;
        this.data = data;
        this.totalPages = totalPages;
        this.pageSize = pageSize;
        this.currentPage = currentPage;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public List<CombinedPurchaseOrder> getData() {
        return data;
    }

    public void setData(List<CombinedPurchaseOrder> data) {
        this.data = data;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    // Getters and setters
    
}
