package com.zain.ksa.alm.acceptance.service;

import java.text.ParseException;

import org.json.JSONArray;

public interface ConfigurationService {
	String createOrUpdateItemCodeSubstitutes(JSONArray jsonArray) throws ParseException;

	String createOrUpdateErrorMessages(JSONArray jsonArray) throws ParseException;

	String createOrUpdateChargeAccounts(JSONArray jsonArray) throws ParseException;

	String deleteChargeAccounts(JSONArray jsonArray);
}