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

        AgingReportRequestParser.ParsedRequest parsed = AgingReportRequestParser.parse(obj);
        String supplierId = parsed.supplierId();
        int page = parsed.page();
        int size = parsed.size();
        Map<String, String> filters = parsed.filters();

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

        AgingReportRequestParser.ParsedRequest parsed = AgingReportRequestParser.parse(obj);
        String supplierId = parsed.supplierId();
        int page = parsed.page();
        int size = parsed.size();
        Map<String, String> filters = parsed.filters();

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
