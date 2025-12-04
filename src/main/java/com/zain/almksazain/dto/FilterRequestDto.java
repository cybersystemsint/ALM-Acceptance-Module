package com.zain.almksazain.dto;

import java.util.List;
import java.util.Map;



public class FilterRequestDto {

    private Integer page = 0;
    private Integer size = 100;
    private String columnName;
    private String searchQuery;
    private String searchOperator;
    private Map<String, FilterDto> filterBy;

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

    public String getSearchOperator() {
        return searchOperator;
    }

    public void setSearchOperator(String searchOperator) {
        this.searchOperator = searchOperator;
    }

    public Map<String, FilterDto> getFilterBy() {
        return filterBy;
    }

    public void setFilterBy(Map<String, FilterDto> filterBy) {
        this.filterBy = filterBy;
    }

    public static class FilterDto {
        // operator: contains|equals|startsWith|endsWith|isEmpty|isNotEmpty|isAnyOf
        private String operator;
        private Object value;

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}