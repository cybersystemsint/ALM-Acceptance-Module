package com.zain.almksazain.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgingReportApproverDTO {
    private String name;
    private String title;
    private String status;
    private Map<String, Integer> buckets;
    private int total;
    private BigDecimal value;
        private List<AgingReportDTO> dtos = new ArrayList<>();



    // getters and setters
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Map<String, Integer> getBuckets() {
        return buckets;
    }
    public void setBuckets(Map<String, Integer> buckets) {
        this.buckets = buckets;
    }
    public int getTotal() {
        return total;
    }
    public void setTotal(int total) {
        this.total = total;
    }
    public BigDecimal getValue() {
        return value;
    }
    public void setValue(BigDecimal value) {
        this.value = value;
    }
      public List<AgingReportDTO> getDtos() {
        return dtos;
    }

    public void setDtos(List<AgingReportDTO> dtos) {
        this.dtos = dtos;
    }
}
