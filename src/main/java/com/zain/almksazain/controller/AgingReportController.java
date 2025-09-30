package com.zain.almksazain.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zain.almksazain.dto.AgingReportDTO;
import com.zain.almksazain.dto.AgingReportItemsDTO;
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

     @GetMapping(value = "/agingReport")

    public Page<AgingReportDTO> getAgingReport(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return agingReportService.getAgingReport(page, size);
    }
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
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        logger.debug("Parsed params - supplierId: {}, columnName: {}, searchQuery: {}, page: {}, size: {}", 
            supplierId, columnName, searchQuery, page, size);

        Map<String, Object> response = dccPoCombinedService.getAgingReport(supplierId, columnName, searchQuery, page, size);

        logger.info("Returning aging report response with {} records (page {}/{})", 
            response.get("data") != null ? ((Iterable<?>) response.get("data")).spliterator().getExactSizeIfKnown() : 0,
            response.get("currentPage"), response.get("totalPages"));

        return response;
    }

}
