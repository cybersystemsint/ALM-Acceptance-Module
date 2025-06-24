package com.zain.almksazain.dto;

import java.util.List;

public class PurchaseOrderResponse {

      private Long totalRecords;
    private List<PurchaseOrderSummaryDTO> data;
    private Integer totalPages;
    private Integer pageSize;
    private Integer currentPage;
    
    public Long getTotalRecords() {
        return totalRecords;
    }
    public void setTotalRecords(Long totalRecords) {
        this.totalRecords = totalRecords;
    }
    public List<PurchaseOrderSummaryDTO> getData() {
        return data;
    }
    public void setData(List<PurchaseOrderSummaryDTO> data) {
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
