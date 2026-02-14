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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zain.ksa.alm.acceptance.dto.ResponseDTO;
import com.zain.ksa.alm.acceptance.service.ConfigurationService;

@RestController
@RequestMapping("/config")
public class ConfigurationController {

    private static final Logger logger = LogManager.getLogger(ConfigurationController.class);

    @Autowired
    private ConfigurationService configurationService;

    @PostMapping("/item-code-substitutes")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> createItemCodeSubstitutes(@RequestBody String req) {
        logger.info("createItemCodeSubstitutes Request | {}", req);

        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = configurationService.createOrUpdateItemCodeSubstitutes(jsonArray);

            if (result.contains("Success")) {
                return ResponseDTO.success("Complete").toMap();
            } else {
                return ResponseDTO.error(result).toMap();
            }
        } catch (Exception e) {
            logger.error("createItemCodeSubstitutes Exception | {}", e.getMessage());
            return ResponseDTO.error(e.getMessage()).toMap();
        }
    }

    @PostMapping("/error-messages")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> createErrorMessage(@RequestBody String req) {
        logger.info("createErrorMessage Request | {}", req);

        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = configurationService.createOrUpdateErrorMessages(jsonArray);

            if (result.contains("Success")) {
                return ResponseDTO.success("Complete").toMap();
            } else {
                return ResponseDTO.error(result).toMap();
            }
        } catch (Exception e) {
            logger.error("createErrorMessage Exception | {}", e.getMessage());
            return ResponseDTO.error(e.getMessage()).toMap();
        }
    }

    @PostMapping("/charge-accounts")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> createChargeAccount(@RequestBody String req) {
        logger.info("ChargeAccountRequest | {}", req);

        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = configurationService.createOrUpdateChargeAccounts(jsonArray);

            if (result.contains("Success")) {
                return ResponseDTO.success("Complete").toMap();
            } else {
                return ResponseDTO.error(result).toMap();
            }
        } catch (Exception e) {
            logger.error("createChargeAccount Exception | {}", e.getMessage());
            return ResponseDTO.error(e.getMessage()).toMap();
        }
    }

    @PostMapping("/charge-accounts/delete")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> deleteChargeAccount(@RequestBody String req) {
        logger.info("Delete Charge Account Request | {}", req);

        try {
            JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
            Integer recordNo = obj.get("recordNo").getAsInt();
            JSONArray jsonArray = new JSONArray("[{\"recordNo\":" + recordNo + "}]");
            String result = configurationService.deleteChargeAccounts(jsonArray);

            if (result.startsWith("Success")) {
                return ResponseDTO.success(result.replace("Success: ", "")).toMap();
            } else {
                return ResponseDTO.error(result.replace("Error: ", "")).toMap();
            }
        } catch (Exception e) {
            logger.error("deleteChargeAccount Exception | {}", e.getMessage());
            return ResponseDTO.error("An error occurred while processing your request").toMap();
        }
    }
}