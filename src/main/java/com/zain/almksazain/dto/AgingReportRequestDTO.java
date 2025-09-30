package com.zain.almksazain.dto;

public class AgingReportRequestDTO {
    
    private String columnName;
    private String searchQuery;
    private int page;
    private int size;
    // getters and setters
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
