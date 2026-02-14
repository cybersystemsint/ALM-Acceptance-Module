package com.zain.ksa.alm.acceptance.service;

public interface ValidationService {
	boolean isDuplicateUPLLine(String poNumber, String lineNumber, String uplLineNumber);

	boolean hasExistingAcceptance(String poNumber, String lineNumber, String uplLineNumber);

	boolean isPOExists(String poNumber);

	boolean isValidSerialNumber(String serialNumber, String itemCode);

	boolean isValidInventoryType(String itemCode, String activeOrPassive);

	boolean isValidQuantity(double quantity, String itemCode);

	boolean isValidScopeApprovalLevel(String scopeCode, int approvalLevel);

	// Additional methods used by DCCService
	boolean isSerialNumberExists(String serialNumber, String itemCode);

	boolean isItemCodeSubstituteValid(String itemCode, String actualItemCode);

	boolean hasApprovalLevelConfiguration(String scopeOfWork);

	boolean isDateInServiceValid(String dateInService);

	String validateUPLItemSerialization(String poNumber, String lineNumber, String uplLineNumber, String serialNumber);

	String validatePOSerialization(String poNumber, String lineNumber, String serialNumber);

	boolean isDCCAlreadyRaised(String serialNumber, String itemCode);
}