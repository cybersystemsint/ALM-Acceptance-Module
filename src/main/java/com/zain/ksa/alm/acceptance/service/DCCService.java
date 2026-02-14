package com.zain.ksa.alm.acceptance.service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface DCCService {
	String createDCCAcceptanceRequest(JSONArray jsonArray) throws ParseException;

	String updateDCCStatus(JSONArray jsonArray) throws ParseException;

	Map<String, Object> handleFileAttachment(MultipartFile file, String dccId, String createdBy);

	String updatePOAcceptanceQuantities(JSONArray jsonArray) throws ParseException;

	String createDCC(List<MultipartFile> files, String req);

	ResponseEntity<Map<String, Object>> getAttachments(Map<String, Object> requestBody);

	void updatePoAcceptanceQty();
}