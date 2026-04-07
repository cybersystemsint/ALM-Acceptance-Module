package com.zain.almksazain.dto;

import java.util.Map;


public class AgingReportRequestDTO {
    private String supplierId;
    private String columnName;
    private String searchQuery;
    private Integer page;
    private Integer size;
    private String searchOperator;
    private Map<String, FilterRequestDto.FilterDto> filterBy;

    // Getters and Setters

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
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

    public String getSearchOperator() {
        return searchOperator;
    }

    public void setSearchOperator(String searchOperator) {
        this.searchOperator = searchOperator;
    }

    public Map<String, FilterRequestDto.FilterDto> getFilterBy() {
        return filterBy;
    }

    public void setFilterBy(Map<String, FilterRequestDto.FilterDto> filterBy) {
        this.filterBy = filterBy;
    }
}