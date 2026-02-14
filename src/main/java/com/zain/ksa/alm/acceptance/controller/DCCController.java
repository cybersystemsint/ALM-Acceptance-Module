package com.zain.ksa.alm.acceptance.controller;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zain.ksa.alm.acceptance.dto.ResponseDTO;
import com.zain.ksa.alm.acceptance.service.DCCService;

@RestController
@RequestMapping("/dcc")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class DCCController {

    private static final Logger logger = LoggerFactory.getLogger(DCCController.class);

    @Autowired
    private DCCService dccService;

    @PostMapping("/create")
    public Map<String, Object> createDCC(
            @RequestPart(value = "file", required = false) List<MultipartFile> files,
            @RequestPart("data") String req) {
        
        logger.info("DCC Create Request: {}", req);
        
        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = dccService.createDCCAcceptanceRequest(jsonArray);
            return result.startsWith("Success") ? 
                ResponseDTO.success("Complete").toMap() : 
                ResponseDTO.error(result).toMap();
        } catch (Exception e) {
            logger.error("Error creating DCC: {}", e.getMessage());
            return ResponseDTO.error("Failed to create DCC: " + e.getMessage()).toMap();
        }
    }

    @PostMapping("/status")
    public Map<String, Object> updateDCCStatus(@RequestBody String req) {
        logger.info("DCC Status Update Request: {}", req);
        
        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = dccService.updateDCCStatus(jsonArray);
            return result.startsWith("Success") ? 
                ResponseDTO.success("Complete").toMap() : 
                ResponseDTO.error(result).toMap();
        } catch (Exception e) {
            logger.error("Error updating DCC status: {}", e.getMessage());
            return ResponseDTO.error("Failed to update DCC status: " + e.getMessage()).toMap();
        }
    }

    @PostMapping("/attachments")
    public Map<String, Object> handleFileAttachment(
            @RequestPart("file") MultipartFile file,
            @RequestParam("dccId") String dccId,
            @RequestParam("createdBy") String createdBy) {
        logger.info("File Attachment Request - DCC ID: {}, Created By: {}", dccId, createdBy);
        
        try {
            return dccService.handleFileAttachment(file, dccId, createdBy);
        } catch (Exception e) {
            logger.error("Error handling file attachment: {}", e.getMessage());
            return ResponseDTO.error("Failed to handle file attachment: " + e.getMessage()).toMap();
        }
    }

    @PostMapping("/update-po-acceptance-quantities")
    public Map<String, Object> updatePoAcceptanceQuantities(@RequestBody String req) {
        try {
            JSONArray jsonArray = new JSONArray(req);
            String result = dccService.updatePOAcceptanceQuantities(jsonArray);
            return result.startsWith("Success") ? 
                ResponseDTO.success("Complete").toMap() : 
                ResponseDTO.error(result).toMap();
        } catch (Exception e) {
            logger.error("Error updating PO acceptance quantities: {}", e.getMessage());
            return ResponseDTO.error("Failed to update PO acceptance quantities: " + e.getMessage()).toMap();
        }
    }
}