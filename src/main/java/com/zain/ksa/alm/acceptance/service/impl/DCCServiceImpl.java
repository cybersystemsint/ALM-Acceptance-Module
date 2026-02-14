package com.zain.ksa.alm.acceptance.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zain.ksa.alm.acceptance.entity.DCC;
import com.zain.ksa.alm.acceptance.entity.DCCLineItem;
import com.zain.ksa.alm.acceptance.entity.DCCStatus;
import com.zain.ksa.alm.acceptance.entity.FileRecord;
import com.zain.ksa.alm.acceptance.entity.PurchaseOrderUPL;
import com.zain.ksa.alm.acceptance.entity.Region;
import com.zain.ksa.alm.acceptance.entity.Site;
import com.zain.ksa.alm.acceptance.repository.DCCLineItemRepository;
import com.zain.ksa.alm.acceptance.repository.DCCRepository;
import com.zain.ksa.alm.acceptance.repository.DCCStatusRepository;
import com.zain.ksa.alm.acceptance.repository.FileRecordRepository;
import com.zain.ksa.alm.acceptance.repository.PurchaseOrderUPLRepository;
import com.zain.ksa.alm.acceptance.repository.RegionRepository;
import com.zain.ksa.alm.acceptance.repository.SiteRepository;
import com.zain.ksa.alm.acceptance.service.DCCService;
import com.zain.ksa.alm.acceptance.service.ValidationService;
import com.zain.ksa.alm.acceptance.util.Helper;
import com.zain.ksa.alm.acceptance.util.Httpcall;

@Service
public class DCCServiceImpl implements DCCService {

	private static final Logger logger = LoggerFactory.getLogger(DCCServiceImpl.class);

	@Autowired
	private DCCRepository dccRepository;

	@Autowired
	private DCCLineItemRepository dccLineRepo;

	@Autowired
	private DCCStatusRepository dccStatusRepo;

	@Autowired
	private FileRecordRepository fileRepo;

	@Autowired
	private PurchaseOrderUPLRepository purchaseOrderUPLRepo;

	@Autowired
	private SiteRepository siteRepo;

	@Autowired
	private RegionRepository regionRepo;

	@Autowired
	private ValidationService validationService;

	private final Httpcall utils = new Httpcall();

	public String createDCCAcceptanceRequest(JSONArray jsonArray) throws ParseException {
		return createDCC(null, jsonArray.toString());
	}

	public String updateDCCStatus(JSONArray jsonArray) throws ParseException {
		return updateDCCStatusInternal(jsonArray.toString());
	}

	public Map<String, Object> handleFileAttachment(MultipartFile file, String dccId, String createdBy) {
		Map<String, Object> response = new HashMap<>();
		try {
			List<MultipartFile> files = Arrays.asList(file);
			handleFileUploads(files, dccId, createdBy);
			response.put("responseCode", "0");
			response.put("responseMessage", "File uploaded successfully");
		} catch (Exception e) {
			response.put("responseCode", "1");
			response.put("responseMessage", "Error: " + e.getMessage());
		}
		return response;
	}

	public String updatePOAcceptanceQuantities(JSONArray jsonArray) throws ParseException {
		updatePoAcceptanceQty();
		return "Success: Complete";
	}

	public String createDCC(List<MultipartFile> files, String req) {
		logger.info("CREATE ACCEPTANCE REQUEST: {}", req);

		JSONArray jsonArray = new JSONArray(req);

		if (jsonArray.length() == 0) {
			return "Error: No data provided";
		}

		JSONObject firstRecord = jsonArray.getJSONObject(0);
		String poNumber = firstRecord.getString("poNumber");

		String fileValidationResult = validateFiles(files);
		if (fileValidationResult != null) {
			return fileValidationResult;
		}

		String validationResult = validateDCCRequest(jsonArray);
		if (validationResult != null) {
			return validationResult;
		}

		try {
			String batchfilename = getbatchfilename("DCC_BATCHUPLOAD");
			Helper.logBatchFile(req, true, batchfilename);

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				long recordNo = jsonObject.getLong("recordNo");

				String result = addEditDCC(recordNo, jsonObject);

				if (result.contains("Success")) {
					DCC topRecord = dccRepository.findTopByPoNumber(poNumber);
					String recordId = topRecord != null ? String.valueOf(topRecord.getRecordNo()) : "";

					String newRecordNo = "";
					DCC checkdcc = dccRepository.findByRecordNo(recordNo);
					if (checkdcc != null) {
						newRecordNo = String.valueOf(recordNo);
					} else {
						newRecordNo = recordId;
					}

					if (files != null) {
						handleFileUploads(files, poNumber, newRecordNo);
					}

					JSONArray dccLineData = jsonObject.getJSONArray("lineItems");
					String status = jsonObject.getString("status");
					Integer createdBy = jsonObject.getInt("createdById");
					String createdByName = jsonObject.getString("createdByName");
					String vendorName = jsonObject.getString("vendorName");

					postdccln(poNumber, newRecordNo, status, createdBy, createdByName, vendorName,
							dccLineData.toString());
				}
			}

			return "Success: Complete";

		} catch (Exception e) {
			logger.error("Error creating DCC: {}", e.getMessage());
			return "Error: " + e.getMessage();
		}
	}

	public String updateDCCStatusInternal(String req) {
		logger.info("DCC Status Update Request: {}", req);

		try {
			JSONArray jsonArray = new JSONArray(req);

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				long recordNo = jsonObject.getLong("recordNo");
				long dccRecordNo = jsonObject.getLong("ddcRecordNo");
				String dccId = jsonObject.getString("dccId");
				String userId = jsonObject.getString("userId");
				String status = jsonObject.getString("status");
				int lnRecordNo = jsonObject.getInt("lnRecordNo");

				String result = addEditDccStatus(recordNo, status, userId, dccId, lnRecordNo);
				updateDccData(dccId, dccRecordNo, status);

				if (!result.contains("Success")) {
					return "Error: " + result;
				}
			}

			return "Success: Complete";

		} catch (Exception e) {
			logger.error("Error updating DCC status: {}", e.getMessage());
			return "Error: " + e.getMessage();
		}
	}

	public ResponseEntity<Map<String, Object>> getAttachments(Map<String, Object> requestBody) {
		Map<String, Object> response = new HashMap<>();

		String poNumber = (String) requestBody.get("poNumber");
		Integer dccId = (Integer) requestBody.get("dccId");

		logger.info("Fetching files for poNumber: {} and dccId: {}", poNumber, dccId);

		if (poNumber == null || dccId == null) {
			response.put("responseCode", "1");
			response.put("responseDesc", "poNumber and dccId are required");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		List<FileRecord> fileRecords = fileRepo.findByPoNumberAndDccId(poNumber, dccId);

		if (fileRecords.isEmpty()) {
			response.put("responseCode", "1");
			response.put("responseDesc", "No files found for the given poNumber and dccId");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
		}

		List<Map<String, String>> files = fileRecords.stream().map(file -> {
			Map<String, String> fileInfo = new HashMap<>();
			fileInfo.put("fileName", file.getFileName());
			fileInfo.put("filePath", "/files/" + file.getFileName());
			return fileInfo;
		}).collect(Collectors.toList());

		response.put("responseCode", "0");
		response.put("responseDesc", "Record Fetched Success");
		response.put("files", files);

		return ResponseEntity.ok(response);
	}

	public void updatePoAcceptanceQty() {
		logger.info("Updating PO acceptance quantities");
	}

	private String validateFiles(List<MultipartFile> files) {
		if (files == null)
			return null;

		List<String> allowedExtensions = Arrays.asList(".pdf", ".doc", ".csv", ".docx", ".xlsx", ".jpeg", ".msg",
				".jpg", ".png", ".xlsm", ".xls", ".zip", ".rar");
		long maxFileSize = 100 * 1024 * 1024;

		for (MultipartFile file : files) {
			String originalFileName = file.getOriginalFilename();
			long fileSize = file.getSize();

			if (fileSize > maxFileSize) {
				return "Error: File too large: " + originalFileName + " exceeds the 100MB limit.";
			}

			String fileExtension = "";
			if (originalFileName != null) {
				int dotIndex = originalFileName.lastIndexOf('.');
				if (dotIndex > 0) {
					fileExtension = originalFileName.substring(dotIndex).toLowerCase();
				}

				if (!allowedExtensions.contains(fileExtension)) {
					return "Error: Invalid file type: " + fileExtension;
				}

				if (".zip".equals(fileExtension)) {
					String zipValidation = validateZipContents(file);
					if (zipValidation != null)
						return zipValidation;
				}

				if (".rar".equals(fileExtension)) {
					try {
						boolean isValid = validateRarContents(file.getInputStream(), originalFileName);
						if (!isValid) {
							return "Error: Invalid file inside rar archive";
						}
					} catch (Exception e) {
						logger.error("Failed to validate rar file: {}", originalFileName, e);
						return "Error: Failed to process rar file.";
					}
				}
			}
		}
		return null;
	}

	private String validateZipContents(MultipartFile file) {
		List<String> allowedExtensions = Arrays.asList(".pdf", ".doc", ".csv", ".docx", ".xlsx", ".jpeg", ".msg",
				".jpg", ".png", ".xlsm", ".xls", ".zip", ".rar");

		try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					String entryName = entry.getName();
					int dotIndex = entryName.lastIndexOf('.');
					String entryExtension = "";
					if (dotIndex > 0) {
						entryExtension = entryName.substring(dotIndex).toLowerCase();
					}
					if (!allowedExtensions.contains(entryExtension)) {
						return "Error: Invalid file inside zip: " + entryName;
					}
				}
			}
		} catch (IOException e) {
			logger.error("Failed to read zip file", e);
			return "Error: Failed to process zip file.";
		}
		return null;
	}

	private boolean validateRarContents(InputStream rarInputStream, String originalFileName)
			throws IOException, InterruptedException {
		List<String> allowedExtensions = Arrays.asList(".pdf", ".doc", ".csv", ".docx", ".xlsx", ".jpeg", ".msg",
				".jpg", ".png", ".xlsm", ".xls", ".zip", ".rar");

		File tempRarFile = File.createTempFile("upload-", ".rar");
		try (OutputStream outStream = new FileOutputStream(tempRarFile)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = rarInputStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, bytesRead);
			}
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("unrar", "lb", tempRarFile.getAbsolutePath());
			pb.redirectErrorStream(true);
			Process process = pb.start();

			List<String> entries = new ArrayList<>();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (!line.isEmpty()) {
						entries.add(line);
					}
				}
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				logger.info("Unrar command failed with exit code: {}", exitCode);
				return false;
			}

			for (String entryName : entries) {
				if (entryName.endsWith("/") || !entryName.contains(".")) {
					continue;
				}

				int dotIndex = entryName.lastIndexOf('.');
				String ext = (dotIndex > 0) ? entryName.substring(dotIndex).toLowerCase() : "";

				if (!allowedExtensions.contains(ext)) {
					logger.info("Invalid file inside rar: {}", entryName);
					return false;
				}
			}

			return true;

		} finally {
			if (!tempRarFile.delete()) {
				logger.warn("Warning: Temp file delete failed: {}", tempRarFile.getAbsolutePath());
			}
		}
	}

	private String validateDCCRequest(JSONArray jsonArray) {
		List<String> errorMessages = new ArrayList<>();

		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			JSONArray lineItems = jsonObject.getJSONArray("lineItems");

			for (int j = 0; j < lineItems.length(); j++) {
				JSONObject lineItem = lineItems.getJSONObject(j);
				String serialNumber = lineItem.getString("serialNumber").trim();
				String itemCode = lineItem.getString("itemCode").trim();
				String actualItemCode = lineItem.optString("actualItemCode", "").trim();
				String scopeOfWork = lineItem.getString("scopeOfWork");
				String dateInService = lineItem.getString("dateInService");
				String uplLineNumber = lineItem.getString("uplLineNumber");
				String poLineNumber = lineItem.getString("poLineNumber");
				String poNumber = jsonObject.getString("poNumber");

				if (!serialNumber.isEmpty() && !itemCode.isEmpty()) {
					if (validationService.isSerialNumberExists(serialNumber, itemCode)) {
						errorMessages.add(
								"Serial number " + serialNumber + " with item code " + itemCode + " already exists");
					}
				}

				if (!actualItemCode.isEmpty()
						&& !validationService.isItemCodeSubstituteValid(itemCode, actualItemCode)) {
					errorMessages.add("Invalid actual item code: " + actualItemCode);
				}

				if (!validationService.hasApprovalLevelConfiguration(scopeOfWork)) {
					errorMessages.add("Missing approval level for scope: " + scopeOfWork);
				}

				if (!validationService.isDateInServiceValid(dateInService)) {
					errorMessages.add("Date in service is more than 6 months old for UPL line: " + uplLineNumber);
				}

				if (!uplLineNumber.isEmpty()) {
					String uplValidation = validationService.validateUPLItemSerialization(poNumber, poLineNumber,
							uplLineNumber, serialNumber);
					if (uplValidation != null) {
						errorMessages.add(uplValidation);
					}
				} else {
					String poValidation = validationService.validatePOSerialization(poNumber, poLineNumber,
							serialNumber);
					if (poValidation != null) {
						errorMessages.add(poValidation);
					}
				}

				if (validationService.isDCCAlreadyRaised(serialNumber, itemCode)) {
					errorMessages.add("DCC already raised for serial number: " + serialNumber);
				}
			}
		}

		return errorMessages.isEmpty() ? null : String.join(" | ", errorMessages);
	}

	private void handleFileUploads(List<MultipartFile> files, String poNumber, String recordId) throws IOException {
		String uploadDir = "/data/app/logs/ALM/POUPL/";

		for (MultipartFile file : files) {
			String originalFileName = file.getOriginalFilename();
			String fileExtension = "";
			if (originalFileName != null) {
				int dotIndex = originalFileName.lastIndexOf('.');
				if (dotIndex > 0) {
					fileExtension = originalFileName.substring(dotIndex);
				}
			}

			String newFileName = originalFileName + "_" + System.currentTimeMillis() + fileExtension;
			File destinationFile = new File(uploadDir + newFileName);

			Files.copy(file.getInputStream(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			FileRecord fileRecord = new FileRecord();
			fileRecord.setFileName(newFileName);
			fileRecord.setPoNumber(poNumber);
			fileRecord.setFilePath(destinationFile.getAbsolutePath());
			fileRecord.setDccId(Integer.parseInt(recordId));
			fileRepo.save(fileRecord);
		}
	}

	private String addEditDCC(long recordno, JSONObject jsonObject) {
		String dataadded = "Failed to save";

		try {
			DCC checkdcc = dccRepository.findByRecordNo(recordno);
			if (checkdcc != null) {
				updateDCCEntity(checkdcc, jsonObject);
				dccRepository.save(checkdcc);
				dataadded = "Data updated Success";
			} else {
				DCC newDcc = new DCC();
				updateDCCEntity(newDcc, jsonObject);

				LocalDateTime now = LocalDateTime.now();
				ZoneId eatZone = ZoneId.of("Africa/Nairobi");
				ZonedDateTime eatZonedDateTime = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(eatZone);
				LocalDateTime eatLocalDateTime = eatZonedDateTime.toLocalDateTime();
				Timestamp timestamp = Timestamp.valueOf(eatLocalDateTime);
				newDcc.setCreatedDate(timestamp);

				dccRepository.save(newDcc);
				dataadded = "Data updated Success";
			}
		} catch (Exception e) {
			logger.error("Error in addEditDCC: {}", e.getMessage());
			dataadded = "Error: " + e.getMessage();
		}

		return dataadded;
	}

	private void updateDCCEntity(DCC dcc, JSONObject jsonObject) {
		dcc.setCreatedBy(jsonObject.getString("createdByName"));
		dcc.setPoNumber(jsonObject.getString("poNumber"));
		dcc.setProjectName(jsonObject.getString("projectName"));
		dcc.setAcceptanceType(jsonObject.getString("acceptanceType"));
		dcc.setStatus(jsonObject.getString("status"));
		dcc.setVendorName(jsonObject.getString("vendorName"));
		dcc.setVendorNumber(jsonObject.getString("vendorNumber"));

		if (jsonObject.has("vendorComment")) {
			dcc.setVendorComment(jsonObject.getString("vendorComment"));
		}

		if (jsonObject.getString("status").equalsIgnoreCase("request-info")) {
			dcc.setStatus("inprocess");
		}
	}

	private String postdccln(String poNumber, String recordId, String status, Integer createdBy, String createdByName,
			String vendorName, String req) {
		try {
			JSONArray jsonArray = new JSONArray(req);
			List<String> itemCategoryCodes = new ArrayList<>();

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				long recordNo = jsonObject.getLong("recordNo");
				String scopeOfWork = jsonObject.getString("scopeOfWork");

				if (scopeOfWork.length() > 1) {
					itemCategoryCodes.add(scopeOfWork);
				}

				String result = addDCCLineItem(poNumber, recordNo, recordId, status, createdBy, createdByName,
						vendorName, jsonObject);
				if (!result.contains("Success")) {
					return result;
				}
			}

			initializeWorkflow(poNumber, recordId, status, createdBy, createdByName, vendorName, itemCategoryCodes);

			return "Complete";

		} catch (Exception e) {
			logger.error("Error in postdccln: {}", e.getMessage());
			return "Error: " + e.getMessage();
		}
	}

	private String addDCCLineItem(String poNumber, long recordno, String recordId, String status, Integer createdBy,
			String createdByName, String vendorName, JSONObject jsonObject) {
		String result = "Failed to save";

		try {
			DCCLineItem dccLineItem = dccLineRepo.findByRecordNo(recordno);
			if (dccLineItem != null) {
				updateDCCLineItem(dccLineItem, jsonObject, poNumber);
				dccLineRepo.save(dccLineItem);
				result = "Record update Success";
			} else {
				DCCLineItem newDccLineItem = new DCCLineItem();
				newDccLineItem.setDccId(recordId);
				updateDCCLineItem(newDccLineItem, jsonObject, poNumber);
				dccLineRepo.save(newDccLineItem);
				result = "Record add Success";
			}
		} catch (Exception e) {
			logger.error("Error in addDCCLineItem: {}", e.getMessage());
			result = "Error: " + e.getMessage();
		}

		return result;
	}

	private void updateDCCLineItem(DCCLineItem dccLineItem, JSONObject jsonObject, String poNumber)
			throws ParseException {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		dccLineItem.setLineNumber(jsonObject.getString("poLineNumber"));
		dccLineItem.setUplLineNumber(jsonObject.getString("uplLineNumber"));
		dccLineItem.setItemCode(jsonObject.getString("itemCode"));
		dccLineItem.setSerialNumber(jsonObject.getString("serialNumber"));
		dccLineItem.setPoId(poNumber);

		Double delivered = Double.parseDouble(jsonObject.getString("deliveredQty"));
		dccLineItem.setDeliveredQty(delivered);

		if (jsonObject.has("actualItemCode")) {
			dccLineItem.setActualItemCode(jsonObject.getString("actualItemCode"));
		}

		if (jsonObject.getString("uplLineNumber").length() != 0) {
			PurchaseOrderUPL topRecord = purchaseOrderUPLRepo.findTopByPoNumberAndPoLineNumberAndUplLine(poNumber,
					jsonObject.getString("poLineNumber"), jsonObject.getString("uplLineNumber"));

			if (topRecord != null) {
				String unitOfMeasure = topRecord.getUom();
				dccLineItem.setUoM(unitOfMeasure);

				double uplLineUnitPrice = topRecord.getUplLineUnitPrice();
				double poLineUnitPriceCalc = topRecord.getPoLineUnitPrice();
				double lineTotal = uplLineUnitPrice * delivered;

				if (poLineUnitPriceCalc != 0) {
					BigDecimal bdLineTotal = BigDecimal.valueOf(lineTotal);
					BigDecimal bdPoLineUnitPrice = BigDecimal.valueOf(poLineUnitPriceCalc);
					double poacceptanceQty = bdLineTotal.divide(bdPoLineUnitPrice, 20, RoundingMode.HALF_UP)
							.doubleValue();
					dccLineItem.setPoAcceptanceQty(poacceptanceQty);
				}
			}
		} else {
			dccLineItem.setUoM("Each");
		}

		dccLineItem.setLocationName(jsonObject.getString("locationName"));

		String dateInServiceString = jsonObject.getString("dateInService");
		java.util.Date parsedDate = dateFormat.parse(dateInServiceString);
		java.sql.Date sqlDate = new java.sql.Date(parsedDate.getTime());
		dccLineItem.setDateInService(sqlDate);

		dccLineItem.setScopeOfWork(jsonObject.getString("scopeOfWork"));
		dccLineItem.setRemarks(jsonObject.getString("remarks"));
		dccLineItem.setLinkId(jsonObject.getString("linkId"));
		dccLineItem.setTagNumber(jsonObject.getString("tagNumber"));

		if (jsonObject.has("uplLineItemCode")) {
			dccLineItem.setUplItemCode(jsonObject.getString("uplLineItemCode"));
		}
		if (jsonObject.has("uplLineDescription")) {
			dccLineItem.setUplItemDescription(jsonObject.getString("uplLineDescription"));
		}
	}

	private void initializeWorkflow(String poNumber, String recordId, String status, Integer createdBy,
			String createdByName, String vendorName, List<String> itemCategoryCodes) {
		try {
			Site topRecord = siteRepo.findFirstBySiteId(itemCategoryCodes.get(0));
			Integer regionrecordId = topRecord != null ? topRecord.getRegionId() : null;
			Region regionRecord = regionRepo.findByRecordNo(regionrecordId);

			Set<String> uniqueItemCategoryCodes = new LinkedHashSet<>(itemCategoryCodes);
			JSONArray jsonArraynew = new JSONArray();

			for (String itemCategoryCode : uniqueItemCategoryCodes) {
				JSONObject params = new JSONObject();
				params.put("acceptanceRequestRecordNo", recordId);
				params.put("tableName", "tb_DCC");
				params.put("poNumber", poNumber);
				params.put("scope", itemCategoryCode);
				params.put("requestedBy", createdByName);
				params.put("vendorName", vendorName);
				params.put("createdBy", createdBy.toString());
				params.put("regions", regionRecord != null ? regionRecord.getRegionName() : "");
				params.put("status", status.equalsIgnoreCase("request-info") ? "request-info" : "");
				jsonArraynew.put(params);
			}

			logger.info("POST WORKFLOW REQUEST: {}", jsonArraynew.toString());

			CompletableFuture.runAsync(() -> {
				try {
					if (!status.equalsIgnoreCase("incomplete")) {
						String ipaddress = getIPAddress();
						HashMap<String, Object> requestMap = utils.httpPOST(jsonArraynew.toString(),
								"http://" + ipaddress + ":8080/alm-zain-ksa/workflow/initialize-approval");
						logger.info("POST WORKFLOW RESPONSE: {}", requestMap);
					}
				} catch (Exception ex) {
					logger.error("POST WORKFLOW EXCEPTION: {}", ex.toString());
				}
			});

		} catch (Exception e) {
			logger.error("Error initializing workflow: {}", e.getMessage());
		}
	}

	private String addEditDccStatus(long recordNo, String status, String userId, String dccId, int lnRecordNo) {
		String dataadded = "Failed to save";

		try {
			DCCStatus checkdcc = dccStatusRepo.findByRecordNo(recordNo);
			if (checkdcc != null) {
				checkdcc.setDccId(dccId);
				checkdcc.setStatus(status);
				checkdcc.setUserId(userId);
				checkdcc.setLnRecordNo(lnRecordNo);
				dccStatusRepo.save(checkdcc);
				dataadded = "Data updated Success";
			} else {
				DCCStatus newDccStatus = new DCCStatus();
				newDccStatus.setDccId(dccId);
				newDccStatus.setStatus(status);
				newDccStatus.setUserId(userId);
				newDccStatus.setStatusDate(Helper.getKenyaDateTimeString());
				newDccStatus.setLnRecordNo(lnRecordNo);
				dccStatusRepo.save(newDccStatus);
				dataadded = "Data updated Success";
			}
		} catch (Exception e) {
			logger.error("Error in addEditDccStatus: {}", e.getMessage());
			dataadded = "Error: " + e.getMessage();
		}

		return dataadded;
	}

	private void updateDccData(String dccid, long recordno, String status) {
		try {
			DCC checkdcc = dccRepository.findByRecordNo(recordno);
			if (checkdcc != null) {
				checkdcc.setStatus(status);
				dccRepository.save(checkdcc);
			}
		} catch (Exception e) {
			logger.error("Error updating DCC data: {}", e.getMessage());
		}
	}

	private String getIPAddress() {
		try {
			InetAddress inetAddress = InetAddress.getLocalHost();
			return inetAddress.getHostAddress();
		} catch (UnknownHostException ex) {
			logger.error("Error getting IP address: {}", ex.getMessage());
			return "localhost";
		}
	}

	private String getbatchfilename(String filetype) {
		return filetype + "_" + System.currentTimeMillis() + ".json";
	}
}