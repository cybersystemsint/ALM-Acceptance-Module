package com.zain.ksa.alm.acceptance.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.acceptance.entity.DCC;
import com.zain.ksa.alm.acceptance.entity.DCCLineItem;
import com.zain.ksa.alm.acceptance.entity.ItemCodeSubstitute;
import com.zain.ksa.alm.acceptance.entity.Node;
import com.zain.ksa.alm.acceptance.entity.PassiveInventory;
import com.zain.ksa.alm.acceptance.entity.PurchaseOrder;
import com.zain.ksa.alm.acceptance.entity.PurchaseOrderUPL;
import com.zain.ksa.alm.acceptance.entity.Scope;
import com.zain.ksa.alm.acceptance.entity.ScopeApprovalLevel;
import com.zain.ksa.alm.acceptance.entity.SerialNumber;
import com.zain.ksa.alm.acceptance.repository.DCCLineItemRepository;
import com.zain.ksa.alm.acceptance.repository.DCCRepository;
import com.zain.ksa.alm.acceptance.repository.ItemCodeSubstituteRepository;
import com.zain.ksa.alm.acceptance.repository.NodeRepository;
import com.zain.ksa.alm.acceptance.repository.PassiveInventoryRepository;
import com.zain.ksa.alm.acceptance.repository.PurchaseOrderRepository;
import com.zain.ksa.alm.acceptance.repository.PurchaseOrderUPLRepository;
import com.zain.ksa.alm.acceptance.repository.ScopeApprovalLevelRepository;
import com.zain.ksa.alm.acceptance.repository.ScopeRepository;
import com.zain.ksa.alm.acceptance.repository.SerialNumberRepository;
import com.zain.ksa.alm.acceptance.service.ValidationService;

@Service
public class ValidationServiceImpl implements ValidationService {

	@Autowired
	private SerialNumberRepository serialNumberRepo;

	@Autowired
	private NodeRepository nodeRepo;

	@Autowired
	private PassiveInventoryRepository passiveRepo;

	@Autowired
	private ItemCodeSubstituteRepository itemCodeSubstituteRepo;

	@Autowired
	private ScopeRepository scopeRepo;

	@Autowired
	private ScopeApprovalLevelRepository scopeApprovalLevelsRepo;

	@Autowired
	private PurchaseOrderRepository purchaseOrderRepo;

	@Autowired
	private PurchaseOrderUPLRepository purchaseOrderUPLRepo;

	@Autowired
	private DCCLineItemRepository dccLineRepo;

	@Autowired
	private DCCRepository dccRepository;

	public boolean isValidSerialNumber(String serialNumber, String itemCode) {
		return !isSerialNumberExists(serialNumber, itemCode);
	}

	public boolean isValidInventoryType(String itemCode, String activeOrPassive) {
		if ("Active".equalsIgnoreCase(activeOrPassive)) {
			return isActiveInventoryValid(itemCode, "");
		} else {
			return isPassiveInventoryValid(itemCode, "");
		}
	}

	public boolean isValidQuantity(double quantity, String itemCode) {
		return quantity > 0;
	}

	public boolean isValidScopeApprovalLevel(String scopeCode, int approvalLevel) {
		return hasApprovalLevelConfiguration(scopeCode);
	}

	public boolean isSerialNumberExists(String serialNumber, String itemCode) {
		List<SerialNumber> existing = serialNumberRepo.findBySerialNumberAndItemCode(serialNumber, itemCode);
		return !existing.isEmpty();
	}

	public boolean isActiveInventoryValid(String itemCode, String serialNumber) {
		List<Node> inventory = nodeRepo.findByPartNumberAndSerialNumber(itemCode, serialNumber);
		return !inventory.isEmpty();
	}

	public boolean isPassiveInventoryValid(String itemCode, String serialNumber) {
		List<PassiveInventory> inventory = passiveRepo.findByItemCodeAndSerialNumber(itemCode, serialNumber);
		return !inventory.isEmpty();
	}

	public boolean isItemCodeSubstituteValid(String itemCode, String actualItemCode) {
		List<ItemCodeSubstitute> substitutes = itemCodeSubstituteRepo.findByItemCodeAndRelatedItemCode(itemCode,
				actualItemCode);
		return !substitutes.isEmpty();
	}

	public boolean hasApprovalLevelConfiguration(String scopeOfWork) {
		Scope scopeRecord = scopeRepo.findByScope(scopeOfWork.trim());
		if (scopeRecord == null)
			return false;

		List<ScopeApprovalLevel> approvalLevels = scopeApprovalLevelsRepo.findByScope((int) scopeRecord.getRecordNo());
		return !approvalLevels.isEmpty();
	}

	public boolean isDateInServiceValid(String dateInServiceString) {
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
			LocalDateTime dateInService = LocalDateTime.parse(dateInServiceString, formatter);
			LocalDateTime sixMonthsAgo = LocalDateTime.now().minus(6, ChronoUnit.MONTHS);
			return !dateInService.isBefore(sixMonthsAgo);
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isPOExists(String poNumber) {
		List<PurchaseOrder> poList = purchaseOrderRepo.findByPoNumber(poNumber);
		return !poList.isEmpty();
	}

	public boolean isDuplicateUPLLine(String poNumber, String lineNumber, String uplLine) {
		List<PurchaseOrderUPL> existing = purchaseOrderUPLRepo.findByPoNumberAndPoLineNumberAndUplLine(poNumber,
				lineNumber, uplLine);
		return !existing.isEmpty();
	}

	public boolean hasExistingAcceptance(String poNumber, String lineNumber, String uplLine) {
		List<DCCLineItem> existing = dccLineRepo.findByPoIdAndLineNumberAndUplLineNumber(poNumber, lineNumber, uplLine);
		return existing.isEmpty();
	}

	public boolean isDCCAlreadyRaised(String serialNumber, String itemCode) {
		List<DCCLineItem> existing = dccLineRepo.findBySerialNumberAndItemCode(serialNumber, itemCode);
		if (existing.isEmpty())
			return false;

		DCCLineItem topRecord = dccLineRepo.findTopBySerialNumberAndItemCode(serialNumber, itemCode);
		if (topRecord == null)
			return false;

		DCC dccRecord = dccRepository.findTopByRecordNo(Integer.parseInt(topRecord.getDccId()));
		if (dccRecord == null)
			return false;

		String status = dccRecord.getStatus();
		return status.equalsIgnoreCase("approved-received") || status.equalsIgnoreCase("inprocess")
				|| status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("returned")
				|| status.equalsIgnoreCase("request-info");
	}

	public boolean isQuantityExceeded(String poNumber, String lineNumber, double requestedQty) {
		PurchaseOrder poDetails = purchaseOrderRepo.findTopByPoNumberAndLineNumber(poNumber, lineNumber);
		if (poDetails == null)
			return false;

		Double poQtyNew = poDetails.getPoQtyNew();
		Double quantityDueNew = poDetails.getQuantityDueNew();
		Double quantityDueOld = poDetails.getQuantityDueOld();

		List<String> allowedStatuses = Arrays.asList("approved-received", "inprocess", "approved", "returned",
				"request-info");
		List<Integer> validRecordNos = dccRepository.findByPoNumberAndStatus(poNumber, allowedStatuses);

		if (!validRecordNos.isEmpty()) {
			List<String> dccIdStrings = validRecordNos.stream().map(String::valueOf).collect(Collectors.toList());
			Double totalDeliveredQty = dccLineRepo.sumDeliveredQtyByDccIdsAndPoLineInfo(dccIdStrings, poNumber,
					lineNumber, "");

			if (poQtyNew > 0) {
				return (requestedQty + totalDeliveredQty) > quantityDueNew;
			} else {
				return (requestedQty + totalDeliveredQty) > quantityDueOld;
			}
		}

		return false;
	}

	public String validateUPLItemSerialization(String poNumber, String lineNumber, String uplLine,
			String serialNumber) {
		PurchaseOrderUPL uplRecord = purchaseOrderUPLRepo.findTopByPoNumberAndPoLineNumberAndUplLine(poNumber,
				lineNumber, uplLine);
		if (uplRecord == null)
			return null;

		String itemSerialized = uplRecord.getUplItemSerialized();
		String activeOrPassive = uplRecord.getActiveOrPassive();

		if ("Yes".equalsIgnoreCase(itemSerialized) && serialNumber.trim().isEmpty()) {
			return "UPL line Item " + uplLine + " is serialized and requires a serial number";
		}

		return null;
	}

	public String validatePOSerialization(String poNumber, String lineNumber, String serialNumber) {
		PurchaseOrder poDetails = purchaseOrderRepo.findTopByPoNumberAndLineNumber(poNumber, lineNumber);
		if (poDetails == null)
			return null;

		String serialControl = poDetails.getSerialControl();
		if (!"NO CONTROL".equalsIgnoreCase(serialControl) && serialNumber.trim().isEmpty()) {
			return "PO line Item " + lineNumber + " is serialized and requires a serial number";
		}

		return null;
	}
}