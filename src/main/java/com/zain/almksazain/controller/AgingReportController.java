package com.zain.almksazain.controller;

import java.util.Collection;
import java.util.HashMap;
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
        int page = (obj.has("page") && !obj.get("page").isJsonNull()) ? obj.get("page").getAsInt() : 1;
        int size = (obj.has("size") && !obj.get("size").isJsonNull()) ? obj.get("size").getAsInt() : 100;

        // Extract filters - support both old format (columnName/searchQuery) and new format (filterBy object)
        Map<String, String> filters = new HashMap<>();

        // Legacy format: single columnName + searchQuery
        if (obj.has("columnName") && !obj.get("columnName").isJsonNull() && !obj.get("columnName").getAsString().isEmpty()) {
            String columnName = obj.get("columnName").getAsString();
            String searchQuery = (obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull())
                    ? obj.get("searchQuery").getAsString() : "";
            if (!searchQuery.isEmpty()) {
                filters.put(columnName, searchQuery);
                logger.debug("Legacy filter format - columnName: {}, searchQuery: {}", columnName, searchQuery);
            }
        }

        // New format: filterBy object with multiple filters
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterByObj = obj.get("filterBy").getAsJsonObject();
            for (String key : filterByObj.keySet()) {
                if (!filterByObj.get(key).isJsonNull()) {
                    String value = filterByObj.get(key).getAsString().trim();
                    if (!value.isEmpty()) {
                        filters.put(key, value);
                        logger.debug("Filter from filterBy - {}: {}", key, value);
                    }
                }
            }
        }

        logger.debug("Parsed params - supplierId: {}, page: {}, size: {}, total filters: {}",
                supplierId, page, size, filters.size());

        // Use multi-filter method if filters exist, otherwise use single-filter method
        Map<String, Object> response;
        if (!filters.isEmpty()) {
            response = dccPoCombinedService.getAgingReportWithMultipleFilters(supplierId, filters, page, size);
        } else {
            response = dccPoCombinedService.getAgingReport(supplierId, "", "", page, size);
        }

        int dataCount = 0;
        Object dataObj = response.get("data");
        if (dataObj instanceof Collection) {
            dataCount = ((Collection<?>) dataObj).size();
        } else if (dataObj instanceof Iterable) {
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

    @PostMapping(value = "/reports/v2/fullAgingReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<?> getFullAgingReports(@RequestBody String req) {
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
        int page = (obj.has("page") && !obj.get("page").isJsonNull()) ? obj.get("page").getAsInt() : 1;
        int size = (obj.has("size") && !obj.get("size").isJsonNull()) ? obj.get("size").getAsInt() : 100;

        // Extract filters - support both old format (columnName/searchQuery) and new format (filterBy object)
        Map<String, String> filters = new HashMap<>();

        // Legacy format: single columnName + searchQuery
        if (obj.has("columnName") && !obj.get("columnName").isJsonNull() && !obj.get("columnName").getAsString().isEmpty()) {
            String columnName = obj.get("columnName").getAsString();
            String searchQuery = (obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull())
                    ? obj.get("searchQuery").getAsString() : "";
            if (!searchQuery.isEmpty()) {
                filters.put(columnName, searchQuery);
                logger.debug("Legacy filter format - columnName: {}, searchQuery: {}", columnName, searchQuery);
            }
        }

        // New format: filterBy object with multiple filters
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterByObj = obj.get("filterBy").getAsJsonObject();
            for (String key : filterByObj.keySet()) {
                if (!filterByObj.get(key).isJsonNull()) {
                    String value = filterByObj.get(key).getAsString().trim();
                    if (!value.isEmpty()) {
                        filters.put(key, value);
                        logger.debug("Filter from filterBy - {}: {}", key, value);
                    }
                }
            }
        }

        logger.debug("Parsed params - supplierId: {}, page: {}, size: {}, total filters: {}",
                supplierId, page, size, filters.size());

        // Use multi-filter method if filters exist, otherwise use single-filter method
        Map<String, Object> response;
        if (!filters.isEmpty()) {
            response = dccPoCombinedService.getFullAgingReportWithMultipleFilters(supplierId, filters, page, size);
        } else {
            response = dccPoCombinedService.getFullAgingReport(supplierId, "", "", page, size);
        }

        int dataCount = 0;
        Object dataObj = response.get("data");
        if (dataObj instanceof Collection) {
            dataCount = ((Collection<?>) dataObj).size();
        } else if (dataObj instanceof Iterable) {
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
   
}
