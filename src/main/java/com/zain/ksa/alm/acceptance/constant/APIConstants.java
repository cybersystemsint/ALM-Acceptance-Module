package com.zain.ksa.alm.acceptance.constant;

import java.util.Arrays;
import java.util.List;

public final class APIConstants {

	// Response Codes
	public static final String SUCCESS_CODE = "0";
	public static final String ERROR_CODE = "1001";

	// Response Messages
	public static final String SUCCESS_MESSAGE = "Complete";
	public static final String RECORD_CREATED_SUCCESS = "Record Created Success";
	public static final String RECORD_UPDATED_SUCCESS = "Record Updated Success";
	public static final String RECORD_DELETED_SUCCESS = "Record Deleted Successfully";
	public static final String FAILED_TO_SAVE = "Failed to save data";

	// File Extensions
	public static final List<String> ALLOWED_FILE_EXTENSIONS = Arrays.asList(".pdf", ".doc", ".csv", ".docx", ".xlsx",
			".jpeg", ".msg", ".jpg", ".png", ".xlsm", ".xls", ".zip", ".rar");

	// File Sizes
	public static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

	// DCC Statuses
	public static final List<String> ALLOWED_DCC_STATUSES = Arrays.asList("approved-received", "inprocess", "approved",
			"returned", "request-info");

	// Date Formats
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	public static final String TIMEZONE = "Africa/Nairobi";

	// Validation Messages
	public static final String DUPLICATE_NOT_ALLOWED = "Duplicates not allowed";
	public static final String RECORD_NOT_FOUND = "Record not found";
	public static final String INVALID_FILE_TYPE = "Invalid file type";
	public static final String FILE_TOO_LARGE = "File exceeds size limit";
	public static final String SERIALIZATION_REQUIRED = "Serial number required for serialized items";

	// Business Rules
	public static final String BLANKET_PO_TYPE = "BLANKET";
	public static final String NO_CONTROL = "NO CONTROL";
	public static final String SERIALIZED_YES = "Yes";
	public static final String ACTIVE_TYPE = "Active";
	public static final String PASSIVE_TYPE = "Passive";

	private APIConstants() {
		// Utility class - prevent instantiation
	}
}
