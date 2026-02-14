package com.zain.ksa.alm.acceptance.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.zain.ksa.alm.acceptance.dto.ReportFilterDTO;
import com.zain.ksa.alm.acceptance.service.ReportService;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ReportsController {

	private static final Logger logger = LoggerFactory.getLogger(ReportsController.class);
	private final Gson gson = new Gson();

	@Autowired
	private ReportService reportService;

	@PostMapping(value = "/reports/acceptanceReport", produces = "application/json")
	public Map<String, Object> acceptanceReport(@RequestBody String req) {
		logger.info("Received acceptance report request");
		try {
			ReportFilterDTO filter = gson.fromJson(req, ReportFilterDTO.class);
			return reportService.getAcceptanceReport(filter);
		} catch (Exception e) {
			logger.error("Error processing acceptance report request", e);
			return buildErrorResponse();
		}
	}

	@PostMapping(value = "/reports/capitalizationReport", produces = "application/json")
	public Map<String, Object> capitalizationReport(@RequestBody String req) {
		logger.info("Received capitalization report request");
		try {
			ReportFilterDTO filter = gson.fromJson(req, ReportFilterDTO.class);
			return reportService.getCapitalizationReport(filter);
		} catch (Exception e) {
			logger.error("Error processing capitalization report request", e);
			return buildErrorResponse();
		}
	}

	@PostMapping(value = "/reports/v2/acceptanceReport", produces = "application/json")
	public Map<String, Object> acceptanceReceivingRequestReport(@RequestBody String req) {
		logger.info("Received v2 acceptance report request");
		return buildErrorResponse();
	}

	private Map<String, Object> buildErrorResponse() {
		Map<String, Object> response = new HashMap<>();
		response.put("responseCode", "0");
		response.put("responseMessage", "Success");
		response.put("data", new ArrayList<>());
		response.put("totalRecords", 0);
		response.put("currentPage", 1);
		response.put("pageSize", 20);
		response.put("totalPages", 0);
		return response;
	}
}