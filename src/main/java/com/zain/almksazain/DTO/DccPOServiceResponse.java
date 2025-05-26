package com.zain.almksazain.DTO;

import java.util.List;

public class DccPOServiceResponse {
    private List<DccPOCombinedViewDTO> data;
    private Long totalRecords; // Unfiltered total in the database
    private Long filteredTotalRecords; // Total after applying filters

    public DccPOServiceResponse(List<DccPOCombinedViewDTO> data, Long totalRecords, Long filteredTotalRecords) {
        this.data = data;
        this.totalRecords = totalRecords;
        this.filteredTotalRecords = filteredTotalRecords;
    }

    // Getters
    public List<DccPOCombinedViewDTO> getData() {
        return data;
    }

    public Long getTotalRecords() {
        return totalRecords;
    }

    public Long getFilteredTotalRecords() {
        return filteredTotalRecords;
    }
}