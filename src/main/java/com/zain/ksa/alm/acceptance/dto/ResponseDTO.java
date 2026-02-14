package com.zain.ksa.alm.acceptance.dto;

import java.util.HashMap;
import java.util.Map;

import com.zain.ksa.alm.acceptance.constant.APIConstants;

public class ResponseDTO {
	private String responseCode;
	private String responseMessage;
	private Object data;

	public ResponseDTO() {
	}

	public ResponseDTO(String responseCode, String responseMessage) {
		this.responseCode = responseCode;
		this.responseMessage = responseMessage;
	}

	public ResponseDTO(String responseCode, String responseMessage, Object data) {
		this.responseCode = responseCode;
		this.responseMessage = responseMessage;
		this.data = data;
	}

	public static ResponseDTO success(String message) {
		return new ResponseDTO(APIConstants.SUCCESS_CODE, message);
	}

	public static ResponseDTO success(String message, Object data) {
		return new ResponseDTO(APIConstants.SUCCESS_CODE, message, data);
	}

	public static ResponseDTO error(String message) {
		return new ResponseDTO(APIConstants.ERROR_CODE, message);
	}

	public static ResponseDTO error(String message, Object data) {
		return new ResponseDTO(APIConstants.ERROR_CODE, message, data);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("responseCode", responseCode);
		map.put("responseMessage", responseMessage);
		if (data != null) {
			map.put("data", data);
		}
		return map;
	}

	// Getters and Setters
	public String getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}

	public String getResponseMessage() {
		return responseMessage;
	}

	public void setResponseMessage(String responseMessage) {
		this.responseMessage = responseMessage;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
}