package com.zain.almksazain.DTO.request;

import java.util.List;

public class DccPORequest {

    // ── Pagination ────────────────────────────────────────────────────────────
    private int page = 1;
    private int size = 100;

    // ── Core filters ──────────────────────────────────────────────────────────
    /** "0" or null = all suppliers */
    private String supplierId;

    private String pendingApprovers;

    // ── Single-column search ──────────────────────────────────────────────────
    private String columnName;
    private String searchQuery;
    /** EQUALS | CONTAINS | STARTS_WITH | ENDS_WITH  (default: CONTAINS) */
    private String operator;

    // ── Multi-column filter array ─────────────────────────────────────────────
    private List<FilterCriteria> filterBy;

    // ── Date range filters ────────────────────────────────────────────────────
    private String createdDateStart;
    private String createdDateEnd;
    private String approvedDateStart;
    private String approvedDateEnd;

    // ── Export format ─────────────────────────────────────────────────────────
    /** "excel" (default) | "csv" */
    private String exportFormat;

    private Boolean exporting;

    // ── Nested filter criteria ────────────────────────────────────────────────

    public static class FilterCriteria {
        private String column;
        private String operator;
        private String value;

        public String getColumn()               { return column; }
        public void   setColumn(String column)  { this.column = column; }
        public String getOperator()             { return operator; }
        public void   setOperator(String op)    { this.operator = op; }
        public String getValue()                { return value; }
        public void   setValue(String value)    { this.value = value; }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getPage()                          { return page; }
    public void   setPage(int page)                  { this.page = page; }
    public int    getSize()                          { return size; }
    public void   setSize(int size)                  { this.size = size; }
    public String getSupplierId()                    { return supplierId; }
    public void   setSupplierId(String s)            { this.supplierId = s; }
    public String getPendingApprovers()              { return pendingApprovers; }
    public void   setPendingApprovers(String s)      { this.pendingApprovers = s; }
    public String getColumnName()                    { return columnName; }
    public void   setColumnName(String s)            { this.columnName = s; }
    public String getSearchQuery()                   { return searchQuery; }
    public void   setSearchQuery(String s)           { this.searchQuery = s; }
    public String getOperator()                      { return operator; }
    public void   setOperator(String s)              { this.operator = s; }
    public List<FilterCriteria> getFilterBy()        { return filterBy; }
    public void   setFilterBy(List<FilterCriteria> f){ this.filterBy = f; }
    public String getCreatedDateStart()              { return createdDateStart; }
    public void   setCreatedDateStart(String s)      { this.createdDateStart = s; }
    public String getCreatedDateEnd()                { return createdDateEnd; }
    public void   setCreatedDateEnd(String s)        { this.createdDateEnd = s; }
    public String getApprovedDateStart()             { return approvedDateStart; }
    public void   setApprovedDateStart(String s)     { this.approvedDateStart = s; }
    public String getApprovedDateEnd()               { return approvedDateEnd; }
    public void   setApprovedDateEnd(String s)       { this.approvedDateEnd = s; }
    public String getExportFormat()                  { return exportFormat; }
    public void   setExportFormat(String s)          { this.exportFormat = s; }
    public Boolean getExporting()                    { return exporting; }
    public void   setExporting(Boolean b)            { this.exporting = b; }
}