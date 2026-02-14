package com.zain.ksa.alm.acceptance.controller;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zain.ksa.alm.acceptance.dto.ResponseDTO;
import com.zain.ksa.alm.acceptance.service.PurchaseOrderService;

@RestController
@RequestMapping("/po")
public class PurchaseOrderController {

	private static final Logger logger = LogManager.getLogger(PurchaseOrderController.class);

	@Autowired
	private PurchaseOrderService purchaseOrderService;

	@PostMapping("/create")
	@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
	public Map<String, Object> createPurchaseOrder(@RequestBody String req) {
		logger.info("PO CREATE REQUEST | {}", req);

		try {
			JSONArray jsonArray = new JSONArray(req);
			String result = purchaseOrderService.createOrUpdatePurchaseOrder(jsonArray);

			if (result.startsWith("Success")) {
				return ResponseDTO.success("Complete").toMap();
			} else {
				return ResponseDTO.error(result.replace("Error: ", "")).toMap();
			}
		} catch (Exception e) {
			logger.error("PO CREATE EXCEPTION | {}", e.getMessage());
			return ResponseDTO.error(e.getMessage()).toMap();
		}
	}

	@PostMapping("/upl/create")
	@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
	public Map<String, Object> createPurchaseOrderUPL(@RequestBody String req) {
		logger.info("UPL CREATE REQUEST | {}", req);

		try {
			JSONArray jsonArray = new JSONArray(req);
			String result = purchaseOrderService.createOrUpdatePurchaseOrderUPL(jsonArray);

			if (result.startsWith("Success")) {
				return ResponseDTO.success("Complete").toMap();
			} else {
				return ResponseDTO.error(result.replace("Error: ", "")).toMap();
			}
		} catch (Exception e) {
			logger.error("UPL CREATE EXCEPTION | {}", e.getMessage());
			return ResponseDTO.error(e.getMessage()).toMap();
		}
	}
}
