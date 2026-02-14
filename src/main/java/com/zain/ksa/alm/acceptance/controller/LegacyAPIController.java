package com.zain.ksa.alm.acceptance.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zain.ksa.alm.acceptance.constant.APIConstants;
import com.zain.ksa.alm.acceptance.service.ConfigurationService;
import com.zain.ksa.alm.acceptance.service.DCCService;
import com.zain.ksa.alm.acceptance.service.PurchaseOrderService;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class LegacyAPIController {

	private static final Logger logger = LoggerFactory.getLogger(LegacyAPIController.class);

	@Autowired
	private PurchaseOrderService purchaseOrderService;

	@Autowired
	private ConfigurationService configurationService;

	@Autowired
	private DCCService dccService;

	// Legacy PO endpoints - redirect to new services
	@Deprecated
	@PostMapping("/createpo")
	public Map<String, String> createpo(@RequestBody String req) {
		logger.warn("DEPRECATED: /createpo endpoint used. Please migrate to /po/create");
		try {
			String result = purchaseOrderService.createOrUpdatePurchaseOrder(new org.json.JSONArray(req));
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/createpoupl")
	public Map<String, String> createpoupl(@RequestBody String req) {
		logger.warn("DEPRECATED: /createpoupl endpoint used. Please migrate to /po/upl/create");
		try {
			String result = purchaseOrderService.createOrUpdatePurchaseOrderUPL(new org.json.JSONArray(req));
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	// Legacy Configuration endpoints - redirect to new services
	@Deprecated
	@PostMapping("/createItemCodeSubstitutes")
	public Map<String, String> createItemCodeSubstitutes(@RequestBody String req) {
		logger.warn(
				"DEPRECATED: /createItemCodeSubstitutes endpoint used. Please migrate to /config/item-code-substitutes");
		try {
			String result = configurationService.createOrUpdateItemCodeSubstitutes(new org.json.JSONArray(req));
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/createErrorMessage")
	public Map<String, String> createErrorMessage(@RequestBody String req) {
		logger.warn("DEPRECATED: /createErrorMessage endpoint used. Please migrate to /config/error-messages");
		try {
			String result = configurationService.createOrUpdateErrorMessages(new org.json.JSONArray(req));
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/createChargeAccount")
	public Map<String, String> createChargeAccount(@RequestBody String req) {
		logger.warn("DEPRECATED: /createChargeAccount endpoint used. Please migrate to /config/charge-accounts");
		try {
			String result = configurationService.createOrUpdateChargeAccounts(new org.json.JSONArray(req));
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/deleteChargeAccount")
	public Map<String, String> deleteChargeAccount(@RequestBody String req) {
		logger.warn("DEPRECATED: /deleteChargeAccount endpoint used. Please migrate to /config/charge-accounts/delete");
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray("[" + req + "]");
			String result = configurationService.deleteChargeAccounts(jsonArray);
			return response(result.startsWith("Success") ? "Success" : "Error", result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	// Legacy DCC endpoints - redirect to new services
	@Deprecated
	@PostMapping("/postdcc")
	public Map<String, String> postdcc(@RequestPart(value = "file", required = false) List<MultipartFile> files,
			@RequestPart("data") String req) {
		logger.warn("DEPRECATED: /postdcc endpoint used. Please migrate to /dcc/create");
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			String result = dccService.createDCCAcceptanceRequest(jsonArray);
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/postdccstatus")
	public Map<String, String> postdccstatus(@RequestBody String req) {
		logger.warn("DEPRECATED: /postdccstatus endpoint used. Please migrate to /dcc/status");
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			String result = dccService.updateDCCStatus(jsonArray);
			return response(result.startsWith("Success") ? "Success" : "Error",
					result.startsWith("Success") ? "Complete" : result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	@Deprecated
	@PostMapping("/getAttachments")
	public Map<String, String> getAttachments(@RequestBody Map<String, Object> requestBody) {
		logger.warn("DEPRECATED: /getAttachments endpoint used. Please migrate to /dcc/attachments");
		// Legacy endpoint - simplified response
		return response("Success", "Please use new /dcc/attachments endpoint");
	}

	@Deprecated
	@PostMapping("/update-po-acceptance-quantities")
	public Map<String, String> updatePoAcceptanceQuantities(@RequestBody String req) {
		logger.warn(
				"DEPRECATED: /update-po-acceptance-quantities endpoint used. Please migrate to /dcc/update-po-acceptance-quantities");
		try {
			org.json.JSONArray jsonArray = new org.json.JSONArray(req);
			String result = dccService.updatePOAcceptanceQuantities(jsonArray);
			return response(result.startsWith("Success") ? "Success" : "Error", result);
		} catch (Exception e) {
			return response("Error", e.getMessage());
		}
	}

	private Map<String, String> response(String result, String msg) {
		java.util.HashMap<String, String> map = new java.util.HashMap<>();
		map.put("responseCode",
				result.equalsIgnoreCase("success") ? APIConstants.SUCCESS_CODE : APIConstants.ERROR_CODE);
		map.put("responseMessage", msg);
		return map;
	}
}