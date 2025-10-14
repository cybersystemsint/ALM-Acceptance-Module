package com.zain.almksazain.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zain.almksazain.dto.AgingReportPagedResponseDTO;
import com.zain.almksazain.dto.AgingReportRequestDTO;
import com.zain.almksazain.services.AgingReportService;
import com.zain.almksazain.services.DccPoCombinedService;

@RestController
public class AgingReportController {
      private static final Logger logger = LogManager.getLogger(AgingReportController.class);

    @Autowired
    private AgingReportService agingReportService;
    @Autowired
    private DccPoCombinedService dccPoCombinedService;
    
    @PostMapping(value = "/aging-report")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public AgingReportPagedResponseDTO getGroupedAgingReport(@RequestBody AgingReportRequestDTO request) {
        return agingReportService.getGroupedAgingReport(request);
    }

    @PostMapping(value = "/reports/v2/agingReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
        public Map<String, Object> getAgingReports(@RequestBody String req) {
        logger.info("Received request for /api/reports/agingReport: {}", req);
        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 100;

        logger.debug("Parsed params - supplierId: {}, columnName: {}, searchQuery: {}, page: {}, size: {}", 
            supplierId, columnName, searchQuery, page, size);

        Map<String, Object> response = dccPoCombinedService.getAgingReport(supplierId, columnName, searchQuery, page, size);

        logger.info("Returning aging report response with {} records (page {}/{})", 
            response.get("data") != null ? ((Iterable<?>) response.get("data")).spliterator().getExactSizeIfKnown() : 0,
            response.get("currentPage"), response.get("totalPages"));

        return response;
    }


    
    // New multiple filter endpoint
@PostMapping("/reports/v2/agingReport/filter")
public ResponseEntity<Map<String, Object>> getAgingReportWithMultipleFilters(
        @RequestParam(required = false) String supplierId,
        @RequestBody Map<String, String> filters, 
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "100") int size) {
    
    
    // Clean and validate filters
    Map<String, String> cleanedFilters = filters.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().trim().isEmpty())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().trim()
            ));
    
    Map<String, Object> result = dccPoCombinedService.getAgingReportWithMultipleFilters(
        supplierId, cleanedFilters, page, size);
    return ResponseEntity.ok(result);
}

}
