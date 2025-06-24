package com.zain.almksazain.dto;

public class PurchaseOrderRequest {
    private String supplierId;
    private String poNumber;
    private String columnName;
    private String searchQuery;
    private String dateFrom;
    private String dateTo;
    private Integer page;
    private Integer size;

    public PurchaseOrderRequest(String columnName, String dateFrom, String dateTo, Integer page, String poNumber, String searchQuery, Integer size, String supplierId) {
        this.columnName = columnName;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.page = page;
        this.poNumber = poNumber;
        this.searchQuery = searchQuery;
        this.size = size;
        this.supplierId = supplierId;
    }

    //getters and setters
    public String getSupplierId() {
        return supplierId;
    }
    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }
    public String getPoNumber() {
        return poNumber;
    }
    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
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
    public String getDateFrom() {
        return dateFrom;
    }
    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }
    public String getDateTo() {
        return dateTo;
    }
    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }
    public Integer getPage() {
        return page;
    }
    public void setPage(Integer page) {
        this.page = page;
    }
    public Integer getSize() {
        return size;
    }
    public void setSize(Integer size) {
        this.size = size;
    }

    
}
