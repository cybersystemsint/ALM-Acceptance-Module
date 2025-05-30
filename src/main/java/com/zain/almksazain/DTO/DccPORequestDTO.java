package com.zain.almksazain.DTO;

public class DccPORequestDTO {
    private String supplierId;
    private int page;
    private int size;
    private String pendingApprovers;
    private Long recordNo;
    private String columnName;
    private String searchQuery;

    // Getters and setters
    public String getSupplierId() {
        return supplierId;
    }
    public String getPendingApprovers() { return pendingApprovers; }
    public void setPendingApprovers(String pendingApprovers) { this.pendingApprovers = pendingApprovers; }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public int getPage() {
        return page;
    }
    public Long getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(Long recordNo) {
        this.recordNo = recordNo;
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

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }
}