package com.zain.almksazain.controller;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
    public ResponseEntity<?> getAgingReports(@RequestBody String req) {
        logger.info("Received request for agingReport: {}", req);

        JsonObject obj;
        try {
            obj = JsonParser.parseString(req).getAsJsonObject();
        } catch (Exception e) {
            logger.warn("Invalid JSON request body", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON"));
        }

        // Safe extraction of fields (guards against missing keys / null values)
        String supplierId = (obj.has("supplierId") && !obj.get("supplierId").isJsonNull())
                ? obj.get("supplierId").getAsString() : null;
        String columnName = (obj.has("columnName") && !obj.get("columnName").isJsonNull())
                ? obj.get("columnName").getAsString() : "";
        String searchQuery = (obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull())
                ? obj.get("searchQuery").getAsString() : "";
        int page = (obj.has("page") && !obj.get("page").isJsonNull()) ? obj.get("page").getAsInt() : 0;
        int size = (obj.has("size") && !obj.get("size").isJsonNull()) ? obj.get("size").getAsInt() : 100;

        logger.debug("Parsed params - supplierId: {}, columnName: {}, searchQuery: {}, page: {}, size: {}",
                supplierId, columnName, searchQuery, page, size);

        Map<String, Object> response = dccPoCombinedService.getAgingReport(supplierId, columnName, searchQuery, page, size);

        // Compute data size safely (response.get("data") might be null or not a Collection)
        int dataCount = 0;
        Object dataObj = response.get("data");
        if (dataObj instanceof Collection) {
            dataCount = ((Collection<?>) dataObj).size();
        } else if (dataObj instanceof Iterable) {
            // fallback for iterable: attempt to iterate for count (not ideal for very large results)
            int cnt = 0;
            for (Object ignored : (Iterable<?>) dataObj) cnt++;
            dataCount = cnt;
        }

        logger.info("Returning aging report response with {} records (page {}/{})",
                dataCount,
                response.get("currentPage"),
                response.get("totalPages"));

        return ResponseEntity.ok(response);
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
