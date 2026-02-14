package com.zain.ksa.alm.acceptance.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.acceptance.constant.APIConstants;
import com.zain.ksa.alm.acceptance.entity.ChargeAccount;
import com.zain.ksa.alm.acceptance.entity.ErrorMessage;
import com.zain.ksa.alm.acceptance.entity.ItemCodeSubstitute;
import com.zain.ksa.alm.acceptance.enums.Severity;
import com.zain.ksa.alm.acceptance.repository.ChargeAccountRepository;
import com.zain.ksa.alm.acceptance.repository.ErrorMessageRepository;
import com.zain.ksa.alm.acceptance.repository.ItemCodeSubstituteRepository;
import com.zain.ksa.alm.acceptance.service.ConfigurationService;

@Service
public class ConfigurationServiceImpl implements ConfigurationService {

	private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceImpl.class);

	@Autowired
	private ItemCodeSubstituteRepository itemCodeSubstituteRepo;

	@Autowired
	private ErrorMessageRepository errorMessageRepo;

	@Autowired
	private ChargeAccountRepository chargeAccountRepo;

	public String createOrUpdateItemCodeSubstitutes(JSONArray jsonArray) {
		return processCreateOrUpdate(
				jsonArray,
				itemCodeSubstituteRepo::findByRecordNo,
				() -> new ItemCodeSubstitute(),
				this::populateItemCodeSubstitute,
				this::populateItemCodeSubstituteForCreate,
				itemCodeSubstituteRepo,
				"Item Code Substitute"
		);
	}

	private void populateItemCodeSubstitute(ItemCodeSubstitute entity, JSONObject json) {
		entity.setItemCode(json.getString("itemCode").trim());
		entity.setRelatedItemCode(json.getString("relatedItemCode").trim());
		entity.setReciprocalFlag(json.getString("reciprocalFlag").trim());
		entity.setUpdatedBy(json.getString("updatedBy"));
	}

	private void populateItemCodeSubstituteForCreate(ItemCodeSubstitute entity, JSONObject json) {
		java.sql.Date sqlDate = new java.sql.Date(new Date().getTime());
		entity.setRecordDateTime(sqlDate);
		entity.setCreatedDatetime(sqlDate);
		populateItemCodeSubstitute(entity, json);
		entity.setCreatedBy(json.getString("createdBy"));
	}

	public String createOrUpdateErrorMessages(JSONArray jsonArray) {
		return processCreateOrUpdate(
				jsonArray,
				errorMessageRepo::findByRecordNo,
				() -> new ErrorMessage(),
				this::populateErrorMessage,
				this::populateErrorMessageForCreate,
				errorMessageRepo,
				"Error Message"
		);
	}

	private void populateErrorMessage(ErrorMessage entity, JSONObject json) {
		entity.setModule(json.getString("module").trim());
		entity.setErrorCode(json.getString("errorCode").trim());
		entity.setErrorMessage(json.getString("errorMessage").trim());
		entity.setOperation(json.getString("operation").trim());
		entity.setSeverity(Severity.valueOf(json.getString("severity").trim().toUpperCase(Locale.ROOT)));
		entity.setUpdatedBy(json.getString("updatedBy"));
		entity.setUpdatedDatetime(new java.sql.Date(new Date().getTime()));
	}

	private void populateErrorMessageForCreate(ErrorMessage entity, JSONObject json) {
		entity.setModule(json.getString("module").trim());
		entity.setErrorCode(json.getString("errorCode").trim());
		entity.setErrorMessage(json.getString("errorMessage").trim());
		entity.setOperation(json.getString("operation").trim());
		entity.setSeverity(Severity.valueOf(json.getString("severity").trim().toUpperCase(Locale.ROOT)));
		entity.setCreatedBy(json.getString("createdBy"));
	}

	public String createOrUpdateChargeAccounts(JSONArray jsonArray) {
		return processCreateOrUpdate(
				jsonArray,
				chargeAccountRepo::findByRecordNo,
				() -> new ChargeAccount(),
				this::populateChargeAccount,
				this::populateChargeAccountForCreate,
				chargeAccountRepo,
				"Charge Account"
		);
	}

	private void populateChargeAccount(ChargeAccount entity, JSONObject json) {
		entity.setChargeAccount(json.getString("chargeAccount").trim());
		entity.setOrgCode(json.getString("orgCode").trim());
		entity.setOrgName(json.getString("orgName").trim());
		entity.setSubInventory(json.getString("subInventory").trim());
		entity.setUpdatedBy(json.getString("updatedBy"));
		entity.setUpdatedDate(new java.sql.Date(new Date().getTime()));
	}

	private void populateChargeAccountForCreate(ChargeAccount entity, JSONObject json) {
		java.sql.Date sqlDate = new java.sql.Date(new Date().getTime());
		entity.setRecordDatetime(sqlDate);
		populateChargeAccount(entity, json);
		entity.setCreatedBy(json.getString("createdBy"));
	}

	public String deleteChargeAccounts(JSONArray jsonArray) {
		StringBuilder results = new StringBuilder();
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			Integer recordNo = jsonObject.getInt("recordNo");
			String result = deleteChargeAccount(recordNo);
			results.append(result).append("; ");
		}
		return results.toString();
	}

	private String deleteChargeAccount(Integer recordNo) {
		ChargeAccount existing = chargeAccountRepo.findByRecordNo(recordNo);

		if (existing != null) {
			try {
				chargeAccountRepo.delete(existing);
				return "Record Deleted Success";
			} catch (Exception e) {
				logger.error("Error deleting charge account: {}", e.getMessage());
				return "Charge Account deletion cannot be processed at this time";
			}
		} else {
			return "Record not found";
		}
	}

	public List<ItemCodeSubstitute> getAllItemCodeSubstitutes() {
		return fetchAll(itemCodeSubstituteRepo, "item code substitutes");
	}

	public List<ErrorMessage> getAllErrorMessages() {
		return fetchAll(errorMessageRepo, "error messages");
	}

	public List<ChargeAccount> getAllChargeAccounts() {
		return fetchAll(chargeAccountRepo, "charge accounts");
	}

	private <T> List<T> fetchAll(JpaRepository<T, ?> repository, String entityName) {
		try {
			return repository.findAll();
		} catch (Exception e) {
			logger.error("Error fetching {}: {}", entityName, e.getMessage());
			return new ArrayList<>();
		}
	}

	private <T> String processCreateOrUpdate(
			JSONArray jsonArray,
			Function<Long, T> findByRecordNo,
			Supplier<T> entityConstructor,
			BiConsumer<T, JSONObject> updatePopulator,
			BiConsumer<T, JSONObject> createPopulator,
			JpaRepository<T, ?> repository,
			String entityName) {

		String responseInfo = APIConstants.FAILED_TO_SAVE;

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			long recordNo = jsonObject.getLong("recordNo");

			T existing = findByRecordNo.apply(recordNo);

			if (existing != null) {
				updatePopulator.accept(existing, jsonObject);
				try {
					repository.save(existing);
					responseInfo = APIConstants.RECORD_UPDATED_SUCCESS;
				} catch (Exception e) {
					logger.error("Error updating {}: {}", entityName, e.getMessage());
					return entityName + " update cannot be processed at this time";
				}
			} else {
				T newRecord = entityConstructor.get();
				createPopulator.accept(newRecord, jsonObject);
				try {
					repository.save(newRecord);
					responseInfo = APIConstants.RECORD_CREATED_SUCCESS;
				} catch (Exception e) {
					logger.error("Error creating {}: {}", entityName, e.getMessage());
					return entityName + " creation cannot be processed at this time";
				}
			}
		}

		return responseInfo;
	}
}