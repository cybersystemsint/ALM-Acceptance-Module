package com.zain.ksa.alm.acceptance.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrder;
import com.zain.ksa.alm.acceptance.entity.PurchaseOrderUPL;
import com.zain.ksa.alm.acceptance.repository.PurchaseOrderRepository;
import com.zain.ksa.alm.acceptance.repository.PurchaseOrderUPLRepository;
import com.zain.ksa.alm.acceptance.service.PurchaseOrderService;
import com.zain.ksa.alm.acceptance.service.ValidationService;

@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

	private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

	@Autowired
	private PurchaseOrderRepository purchaseOrderRepo;

	@Autowired
	private PurchaseOrderUPLRepository purchaseOrderUPLRepo;

	@Autowired
	private ValidationService validationService;

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public String createOrUpdatePurchaseOrder(JSONArray jsonArray) throws ParseException {
		List<String> validationErrors = new ArrayList<>();
		List<String> blanketErrors = new ArrayList<>();
		String responseInfo = "Failed to save data";

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			long recordNo = jsonObject.getLong("recordNo");

			PurchaseOrder existingPO = purchaseOrderRepo.findByRecordNo(recordNo);

			if (existingPO != null) {
				responseInfo = updatePurchaseOrder(existingPO, jsonObject);
			} else {
				String typeLookup = jsonObject.getString("typeLookUpCode").trim();
				String releaseNum = jsonObject.getString("releaseNum").trim();

				if (typeLookup.equalsIgnoreCase("BLANKET") && releaseNum.equalsIgnoreCase("0")) {
					blanketErrors.add(jsonObject.getString("poNumber"));
					continue;
				}

				PurchaseOrder topRecord = purchaseOrderRepo.findTopByPoNumberAndLineNumberAndReleaseNum(
						jsonObject.getString("poNumber"), String.valueOf(jsonObject.getInt("lineNumber")), releaseNum);

				if (topRecord == null) {
					responseInfo = createPurchaseOrder(jsonObject, typeLookup, releaseNum);
				} else {
					validationErrors.add(jsonObject.getString("poNumber") + " " + jsonObject.getInt("lineNumber"));
				}
			}
		}

		if (!validationErrors.isEmpty()) {
			return "Error: PO numbers and Line Items: " + String.join(", ", validationErrors)
					+ " are already uploaded. Duplicates not allowed";
		}

		return responseInfo.contains("Success") ? "Success: Complete" : "Error: " + responseInfo;
	}

	private String updatePurchaseOrder(PurchaseOrder po, JSONObject json) throws ParseException {
		mapJsonToPO(po, json);
		try {
			purchaseOrderRepo.save(po);
			return "Record Updated Success";
		} catch (Exception e) {
			logger.error("Error updating PO: {}", e.getMessage());
			return e.toString();
		}
	}

	private String createPurchaseOrder(JSONObject json, String typeLookup, String releaseNum) throws ParseException {
		PurchaseOrder newPO = new PurchaseOrder();

		if (typeLookup.equalsIgnoreCase("BLANKET")) {
			newPO.setPoNumber(json.getString("poNumber").trim() + "-" + releaseNum);
		} else {
			newPO.setPoNumber(json.getString("poNumber").trim());
		}

		mapJsonToPO(newPO, json);

		try {
			purchaseOrderRepo.save(newPO);
			return "Record Created Success";
		} catch (Exception e) {
			logger.error("Error creating PO: {}", e.getMessage());
			return e.toString();
		}
	}

	private void mapJsonToPO(PurchaseOrder po, JSONObject json) throws ParseException {
		po.setTypeLookUpCode(json.getString("typeLookUpCode").trim());
		po.setBlanketTotalAmount(json.getDouble("blanketTotalAmount"));
		po.setReleaseNum(json.getString("releaseNum").trim());
		po.setLineNumber(json.getInt("lineNumber"));
		po.setPrNum(json.getString("prNum").trim());
		po.setProjectName(json.getString("projectName").trim());
		po.setLineCancelFlag(json.getBoolean("lineCancelFlag"));
		po.setCancelReason(json.getString("cancelReason").trim());
		po.setItemPartNumber(json.getString("itemPartNumber").trim());
		po.setPrSubAllow(json.getBoolean("prSubAllow"));
		po.setCountryOfOrigin(json.getString("countryOfOrigin").trim());
		po.setPoOrderQuantity(json.getDouble("poOrderQuantity"));
		po.setPoQtyNew(json.getDouble("poQtyNew"));
		po.setQuantityReceived(json.getDouble("quantityReceived"));
		po.setQuantityDueOld(json.getDouble("quantityDueOld"));
		po.setQuantityDueNew(json.getDouble("quantityDueNew"));
		po.setQuantityBilled(json.getDouble("quantityBilled"));
		po.setCurrencyCode(json.getString("currencyCode").trim());
		po.setUnitPriceInPoCurrency(json.getDouble("unitPriceInPoCurrency"));
		po.setUnitPriceInSAR(json.getDouble("unitPriceInSAR"));
		po.setLinePriceInPoCurrency(json.getDouble("linePriceInPoCurrency"));
		po.setLinePriceInSAR(json.getDouble("linePriceInSAR"));
		po.setAmountReceived(json.getDouble("amountReceived"));
		po.setAmountDue(json.getDouble("amountDue"));
		po.setAmountDueNew(json.getDouble("amountDueNew"));
		po.setAmountBilled(json.getDouble("amountBilled"));
		po.setPoLineDescription(json.getString("poLineDescription").trim());
		po.setOrganizationName(json.getString("organizationName").trim());
		po.setOrganizationCode(json.getString("organizationCode").trim());
		po.setSubInventoryCode(json.getString("subInventoryCode").trim());
		po.setReceiptRouting(json.getString("receiptRouting").trim());
		po.setAuthorisationStatus(json.getString("authorisationStatus").trim());
		po.setPoClosureStatus(json.getString("poClosureStatus").trim());
		po.setDepartmentName(json.getString("departmentName").trim());
		po.setPoLineType(json.getString("poLineType").trim());
		po.setAcceptanceType(json.getString("acceptanceType").trim());
		po.setCostCenter(json.getString("costCenter").trim());
		po.setSerialControl(json.getString("serialControl").trim());
		po.setVendorSerialNumberYN(json.getString("vendorSerialNumberYN").trim());
		po.setItemType(json.getString("itemType").trim());
		po.setItemCategoryInventory(json.getString("itemCategoryInventory").trim());
		po.setInventoryCategoryDescription(json.getString("inventoryCategoryDescription").trim());
		po.setItemCategoryFA(json.getString("itemCategoryFA").trim());
		po.setFACategoryDescription(json.getString("FACategoryDescription").trim());
		po.setItemCategoryPurchasing(json.getString("itemCategoryPurchasing").trim());
		po.setPurchasingCategoryDescription(json.getString("purchasingCategoryDescription").trim());
		po.setVendorName(json.getString("vendorName").trim());
		po.setVendorNumber(json.getString("vendorNumber").trim());
		po.setCreatedBy(json.getInt("createdById"));
		po.setCreatedByName(json.getString("createdByName").trim());

		Double unitPrice = json.optDouble("unitPriceInPoCurrency", 0.0);
		Double poQtyNew = json.optDouble("poQtyNew", 0.0);
		Double poOrderQuantity = json.optDouble("poOrderQuantity", 0.0);

		if (poQtyNew > 0) {
			Double quantityDiff = poOrderQuantity - poQtyNew;
			po.setDescopedLinePriceInPoCurrency(quantityDiff * unitPrice);
			po.setNewLinePriceInPoCurrency(poQtyNew * unitPrice);
		}

		java.util.Date approvedDate = dateFormat.parse(json.getString("approvedDate"));
		java.util.Date createdDate = dateFormat.parse(json.getString("createdDate"));
		po.setApprovedDate(new java.sql.Date(approvedDate.getTime()));
		po.setCreatedDate(new java.sql.Date(createdDate.getTime()));
	}

	public String createOrUpdatePurchaseOrderUPL(JSONArray jsonArray) throws ParseException {
		List<String> missingPoNumbers = new ArrayList<>();
		List<String> emptyNumbers = new ArrayList<>();
		List<String> duplicateLines = new ArrayList<>();
		String responseInfo = "Failed to save data";

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			long recordNo = jsonObject.getLong("recordNo");

			PurchaseOrderUPL existingUPL = purchaseOrderUPLRepo.findByRecordNo(recordNo);

			if (existingUPL != null) {
				responseInfo = updatePurchaseOrderUPL(existingUPL, jsonObject);
			} else {
				String poNumber = jsonObject.getString("poNumber").trim();
				String poLineNumber = jsonObject.getString("poLineNumber").trim();
				String uplLine = jsonObject.getString("uplLine").trim();

				if (poNumber.isEmpty()) {
					emptyNumbers.add("Record " + (i + 1));
					continue;
				}

				if (!validationService.isPOExists(poNumber)) {
					missingPoNumbers.add(poNumber);
					continue;
				}

				if (validationService.isDuplicateUPLLine(poNumber, poLineNumber, uplLine)) {
					duplicateLines.add(poNumber + "-" + poLineNumber + "-" + uplLine);
					continue;
				}

				responseInfo = createPurchaseOrderUPL(jsonObject);
			}
		}

		if (!missingPoNumbers.isEmpty()) {
			return "Error: PO numbers not found: " + String.join(", ", missingPoNumbers);
		}

		if (!emptyNumbers.isEmpty()) {
			return "Error: Empty PO numbers in records: " + String.join(", ", emptyNumbers);
		}

		if (!duplicateLines.isEmpty()) {
			return "Error: Duplicate UPL lines: " + String.join(", ", duplicateLines);
		}

		return responseInfo.contains("Success") ? "Success: Complete" : "Error: " + responseInfo;
	}

	private String updatePurchaseOrderUPL(PurchaseOrderUPL upl, JSONObject json) throws ParseException {
		mapJsonToUPL(upl, json);
		try {
			purchaseOrderUPLRepo.save(upl);
			return "Record Updated Success";
		} catch (Exception e) {
			logger.error("Error updating UPL: {}", e.getMessage());
			return e.toString();
		}
	}

	private String createPurchaseOrderUPL(JSONObject json) throws ParseException {
		PurchaseOrderUPL newUPL = new PurchaseOrderUPL();
		mapJsonToUPL(newUPL, json);

		try {
			purchaseOrderUPLRepo.save(newUPL);
			return "Record Created Success";
		} catch (Exception e) {
			logger.error("Error creating UPL: {}", e.getMessage());
			return e.toString();
		}
	}

	private void mapJsonToUPL(PurchaseOrderUPL upl, JSONObject json) throws ParseException {
		upl.setVendor(json.getString("vendor").trim());
		upl.setManufacturer(json.getString("manufacturer").trim());
		upl.setCountryOfOrigin(json.getString("countryOfOrigin").trim());
		upl.setProjectName(json.getString("projectName").trim());
		upl.setPoType(json.getString("poType").trim());
		upl.setReleaseNumber(json.getString("releaseNumber").trim());
		upl.setPoNumber(json.getString("poNumber").trim());
		upl.setPoLineNumber(json.getString("poLineNumber").trim());
		upl.setUplLine(json.getString("uplLine").trim());
		upl.setPoLineItemType(json.getString("poLineItemType").trim());
		upl.setPoLineItemCode(json.getString("poLineItemCode").trim());
		upl.setPoLineDescription(json.getString("poLineDescription").trim());
		upl.setUplLineItemType(json.getString("uplLineItemType").trim());
		upl.setUplLineItemCode(json.getString("uplLineItemCode").trim());
		upl.setUplLineDescription(json.getString("uplLineDescription").trim());
		upl.setZainItemCategoryCode(json.getString("zainItemCategoryCode").trim());
		upl.setZainItemCategoryDescription(json.getString("zainItemCategoryDescription").trim());
		upl.setUplItemSerialized(json.getString("uplItemSerialized").trim());
		upl.setActiveOrPassive(json.getString("activeOrPassive").trim());
		upl.setUom(json.getString("uom").trim());
		upl.setCurrency(json.getString("currency").trim());
		upl.setPoLineQuantity(json.getDouble("poLineQuantity"));
		upl.setPoLineUnitPrice(json.getDouble("poLineUnitPrice"));
		upl.setUplLineQuantity(json.getDouble("uplLineQuantity"));
		upl.setUplLineUnitPrice(json.getDouble("uplLineUnitPrice"));
		upl.setSubstituteItemCode(json.getString("substituteItemCode").trim());
		upl.setRemarks(json.getString("remarks").trim());
		upl.setCreatedBy(json.getInt("createdById"));
		upl.setCreatedByName(json.getString("createdByName").trim());
		upl.setUplModifiedBy(json.getString("uplModifiedBy").trim());

		java.util.Date recordDate = new java.util.Date();
		upl.setRecordDatetime(new java.sql.Date(recordDate.getTime()));
		upl.setUplModifiedDate(new java.sql.Date(recordDate.getTime()));
	}
}
