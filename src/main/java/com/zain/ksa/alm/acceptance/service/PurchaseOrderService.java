package com.zain.ksa.alm.acceptance.service;

import java.text.ParseException;

import org.json.JSONArray;

public interface PurchaseOrderService {
	String createOrUpdatePurchaseOrder(JSONArray jsonArray) throws ParseException;

	String createOrUpdatePurchaseOrderUPL(JSONArray jsonArray) throws ParseException;
}
