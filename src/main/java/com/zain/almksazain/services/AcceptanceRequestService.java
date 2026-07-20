package com.zain.almksazain.services;

import com.zain.almksazain.dto.AcceptanceCreateResult;
import com.zain.almksazain.dto.WorkflowInitResult;
import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.FileRecord;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.model.tb_Region;
import com.zain.almksazain.model.tb_Site;
import com.zain.almksazain.repo.DCCRepository;
import com.zain.almksazain.repo.DccLineRepo;
import com.zain.almksazain.repo.fileRecordRepo;
import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;
import com.zain.almksazain.repo.tbRegionRepo;
import com.zain.almksazain.repo.tbSiteRepo;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transactional orchestrator for /postdcc create path:
 * DCC + line items + file metadata + in-process workflow init.
 */
@Service
public class AcceptanceRequestService {

    private final org.apache.logging.log4j.Logger logger = LogManager.getLogger(AcceptanceRequestService.class);

    @Autowired
    private DCCRepository dccrepo;
    @Autowired
    private DccLineRepo dcclnrepo;
    @Autowired
    private fileRecordRepo fileRepo;
    @Autowired
    private tbPurchaseOrderUPLRepo purchaseOrderUPLRepo;
    @Autowired
    private tbSiteRepo siteRepo;
    @Autowired
    private tbRegionRepo regionRepo;
    @Autowired
    private WorkflowInitializationService workflowInitializationService;

    @Transactional
    public AcceptanceCreateResult createAcceptanceRequest(JSONObject jsonObject, List<MultipartFile> files,
            String uploadDir) {
        long recordNo = Integer.parseInt(jsonObject.getString("recordNo"));
        String poNumber = jsonObject.getString("poNumber");
        String vendorName = jsonObject.getString("vendorName");
        String status = jsonObject.getString("status");
        Integer createdBy = jsonObject.getInt("createdById");
        String createdByName = jsonObject.getString("createdByName");
        JSONArray dccLineData = jsonObject.getJSONArray("lineItems");
        String headerRegion = jsonObject.optString("region", "").trim();

        DCC savedDcc = saveDcc(recordNo, jsonObject);
        long dccId = savedDcc.getRecordNo();
        String newRecordNo = String.valueOf(dccId);

        saveUploadedFiles(files, uploadDir, poNumber, dccId);
        syncLineItems(poNumber, newRecordNo, status, createdBy, createdByName, vendorName, dccLineData);

        AcceptanceCreateResult result = new AcceptanceCreateResult(dccId);
        if (!"incomplete".equalsIgnoreCase(status) && !"request-info".equalsIgnoreCase(status)) {
            List<WorkflowInitResult> initResults = initializeWorkflowForLines(
                    poNumber, newRecordNo, status, createdBy, createdByName, vendorName, headerRegion, dccLineData);
            initResults.forEach(result::addWorkflowInitResult);
        } else {
            logger.info("Skipping workflow init for dccId={} status={}", dccId, status);
        }

        return result;
    }

    private DCC saveDcc(long recordno, JSONObject jsonObject) {
        DCC checkdcc = dccrepo.findByRecordNo(recordno);
        if (checkdcc != null) {
            checkdcc.setCreatedBy(jsonObject.getString("createdByName"));
            checkdcc.setPoNumber(jsonObject.getString("poNumber"));
            checkdcc.setProjectName(jsonObject.getString("projectName"));
            checkdcc.setAcceptanceType(jsonObject.getString("acceptanceType"));
            checkdcc.setStatus(jsonObject.getString("status"));
            if (jsonObject.getString("status").equalsIgnoreCase("request-info")) {
                checkdcc.setStatus("inprocess");
            }
            if (jsonObject.has("vendorComment")) {
                checkdcc.setVendorComment(jsonObject.getString("vendorComment"));
            }
            checkdcc.setVendorName(jsonObject.getString("vendorName"));
            checkdcc.setVendorNumber(jsonObject.getString("vendorNumber"));
            return dccrepo.saveAndFlush(checkdcc);
        }

        DCC nwcheckdcc = new DCC();
        nwcheckdcc.setCreatedBy(jsonObject.getString("createdByName"));
        nwcheckdcc.setPoNumber(jsonObject.getString("poNumber"));
        nwcheckdcc.setProjectName(jsonObject.getString("projectName"));
        nwcheckdcc.setAcceptanceType(jsonObject.getString("acceptanceType"));
        nwcheckdcc.setStatus(jsonObject.getString("status"));
        nwcheckdcc.setVendorName(jsonObject.getString("vendorName"));
        nwcheckdcc.setVendorNumber(jsonObject.getString("vendorNumber"));

        LocalDateTime now = LocalDateTime.now();
        ZoneId eatZone = ZoneId.of("Africa/Nairobi");
        ZonedDateTime eatZonedDateTime = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(eatZone);
        Timestamp timestamp = Timestamp.valueOf(eatZonedDateTime.toLocalDateTime());
        nwcheckdcc.setCreatedDate(timestamp);

        return dccrepo.saveAndFlush(nwcheckdcc);
    }

    private void saveUploadedFiles(List<MultipartFile> files, String uploadDir, String poNumber, long dccId) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            String originalFileName = file.getOriginalFilename();
            logger.info("Received file: " + originalFileName);
            String fileExtension = "";
            if (originalFileName != null) {
                int dotIndex = originalFileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileExtension = originalFileName.substring(dotIndex);
                }
            }
            String newFileName = originalFileName + "_" + System.currentTimeMillis() + fileExtension;
            File destinationFile = new File(uploadDir + newFileName);
            if (destinationFile.exists()) {
                throw new RuntimeException("File already exists: " + newFileName);
            }
            try {
                Files.copy(file.getInputStream(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                FileRecord fileRecord = new FileRecord();
                fileRecord.setFileName(newFileName);
                fileRecord.setPoNumber(poNumber);
                fileRecord.setFilePath(destinationFile.getAbsolutePath());
                fileRecord.setDccId((int) dccId);
                fileRepo.save(fileRecord);
            } catch (IOException e) {
                throw new RuntimeException("File upload failed: " + e.getMessage(), e);
            }
        }
    }

    private void syncLineItems(String poNumber, String recordId, String status, Integer createdBy,
            String createdByName, String vendorName, JSONArray dccLineData) {
        Set<Long> incomingRecordNos = new HashSet<>();
        for (int x = 0; x < dccLineData.length(); x++) {
            JSONObject lineObj = dccLineData.getJSONObject(x);
            long lineRecordNo = 0L;
            try {
                String rn = lineObj.optString("recordNo", "0");
                if (rn != null && !rn.isBlank()) {
                    lineRecordNo = Long.parseLong(rn);
                }
            } catch (Exception ignored) {
            }
            if (lineRecordNo > 0L) {
                incomingRecordNos.add(lineRecordNo);
            }
        }

        List<DCCLineItem> existingLines = dcclnrepo.findByDccId(recordId);
        Set<Long> existingRecordNos = existingLines.stream()
                .map(DCCLineItem::getRecordNo)
                .collect(Collectors.toSet());
        existingRecordNos.removeAll(incomingRecordNos);
        if (!existingRecordNos.isEmpty()) {
            dcclnrepo.deleteByRecordNoIn(new ArrayList<>(existingRecordNos));
        }

        for (int i = 0; i < dccLineData.length(); i++) {
            JSONObject jsonObject = dccLineData.getJSONObject(i);
            long lineRecordNo = Integer.parseInt(jsonObject.getString("recordNo"));
            String jsresp = addDCCLineItem(poNumber, lineRecordNo, recordId, status, createdBy, createdByName,
                    vendorName, jsonObject);
            if (!jsresp.contains("Success")) {
                throw new RuntimeException("Failed to save DCC line item recordNo=" + lineRecordNo + ": " + jsresp);
            }
        }
    }

    private List<WorkflowInitResult> initializeWorkflowForLines(String poNumber, String recordId, String status,
            Integer createdBy, String createdByName, String vendorName, String headerRegion, JSONArray dccLineData) {

        List<String> itemCategoryCodes = new ArrayList<>();
        String poLineDescription = "";
        String locationName = "";

        for (int i = 0; i < dccLineData.length(); i++) {
            JSONObject jsonObject = dccLineData.getJSONObject(i);
            poLineDescription = jsonObject.getString("poLineItemDescription");
            locationName = jsonObject.getString("locationName");
            String scopeOfWork = jsonObject.getString("scopeOfWork");
            if (scopeOfWork != null && scopeOfWork.length() > 1) {
                itemCategoryCodes.add(scopeOfWork);
            }
        }

        String workflowRegion;
        if (headerRegion != null && !headerRegion.isEmpty()) {
            workflowRegion = headerRegion;
        } else {
            tb_Site topRecord = siteRepo.findFirstBySiteId(locationName);
            Integer regionRecordId = topRecord != null ? topRecord.getRegionId() : null;
            tb_Region regionRecord = regionRecordId != null ? regionRepo.findByRecordNo(regionRecordId) : null;
            workflowRegion = regionRecord != null ? regionRecord.getRegionName() : "";
        }

        List<String> regions = Arrays.stream(workflowRegion.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList());

        Set<String> uniqueScopes = new LinkedHashSet<>(itemCategoryCodes);
        List<WorkflowInitResult> results = new ArrayList<>();
        Integer acceptanceRequestRecordNo = Integer.parseInt(recordId);

        for (String scopeName : uniqueScopes) {
            logger.info("Initializing in-process workflow for dccId={}, scope={}, regions={}",
                    recordId, scopeName, regions);
            WorkflowInitResult initResult = workflowInitializationService.initializeApproval(
                    acceptanceRequestRecordNo,
                    vendorName,
                    createdByName,
                    scopeName,
                    createdBy != null ? createdBy.toString() : null,
                    regions,
                    "tb_DCC",
                    poLineDescription,
                    poNumber);
            results.add(initResult);
        }

        return results;
    }

    public String addDCCLineItem(String poNumber, long recordno, String recordId, String status, Integer createdBy,
            String createdByName, String vendorName, JSONObject jsonObject) {

        logger.info("| DCC Line " + jsonObject.toString());

        String result = "Failed to save";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        LocalDateTime now = LocalDateTime.now();

        DCCLineItem eddccLineItem = dcclnrepo.findByRecordNo(recordno);
        if (eddccLineItem != null) {
            eddccLineItem.setLineNumber(jsonObject.getString("poLineNumber"));
            eddccLineItem.setUplLineNumber(jsonObject.getString("uplLineNumber"));
            eddccLineItem.setItemCode(jsonObject.getString("itemCode"));
            eddccLineItem.setSerialNumber(jsonObject.getString("serialNumber"));
            Double delivered = Double.parseDouble(jsonObject.getString("deliveredQty"));
            if (jsonObject.has("actualItemCode")) {
                eddccLineItem.setActualItemCode(jsonObject.getString("actualItemCode"));
            }

            if (jsonObject.getString("uplLineNumber").length() != 0) {
                tb_PurchaseOrderUPL topRecord = purchaseOrderUPLRepo.findTopByPoNumberAndPoLineNumberAndUplLine(
                        poNumber, jsonObject.getString("poLineNumber"), jsonObject.getString("uplLineNumber"));
                String unitOfMeasure = topRecord != null ? String.valueOf(topRecord.getUom()) : "";
                eddccLineItem.setUoM(unitOfMeasure);

                double uplLineUnitPrice = topRecord != null ? topRecord.getUplLineUnitPrice() : 0;
                double poLineUnitPriceCalc = topRecord != null ? topRecord.getPoLineUnitPrice() : 0;
                double lineTotal = uplLineUnitPrice * delivered;
                double poacceptanceQty = 0;
                if (poLineUnitPriceCalc != 0) {
                    poacceptanceQty = BigDecimal.valueOf(lineTotal)
                            .divide(BigDecimal.valueOf(poLineUnitPriceCalc), 20, RoundingMode.HALF_UP)
                            .doubleValue();
                }
                eddccLineItem.setPoAcceptanceQty(poacceptanceQty);
            } else {
                eddccLineItem.setUoM("Each");
            }

            eddccLineItem.setPoId(poNumber);
            eddccLineItem.setDeliveredQty(delivered);
            eddccLineItem.setLocationName(jsonObject.getString("locationName"));
            String dateInServiceString = jsonObject.getString("dateInService");
            if (jsonObject.has("uplLineItemCode")) {
                eddccLineItem.setUplItemCode(jsonObject.getString("uplLineItemCode"));
            }
            if (jsonObject.has("uplLineDescription")) {
                eddccLineItem.setUplItemDescription(jsonObject.getString("uplLineDescription"));
            }
            try {
                java.util.Date parsedDate = dateFormat.parse(dateInServiceString);
                eddccLineItem.setDateInService(new java.sql.Date(parsedDate.getTime()));
            } catch (ParseException ex) {
                logger.info("Exception " + ex.getMessage());
            }
            eddccLineItem.setScopeOfWork(jsonObject.getString("scopeOfWork"));
            eddccLineItem.setRemarks(jsonObject.getString("remarks"));
            eddccLineItem.setLinkId(jsonObject.getString("linkId"));
            eddccLineItem.setTagNumber(jsonObject.getString("tagNumber"));

            try {
                dcclnrepo.save(eddccLineItem);
                result = "Record update Success";
            } catch (Exception ex) {
                logger.info("Exception " + ex.getMessage());
                result = ex.getCause() != null ? ex.getCause().toString() : ex.getMessage();
            }
        } else {
            DCCLineItem dccLineItem = new DCCLineItem();
            dccLineItem.setDccId(recordId);
            dccLineItem.setLineNumber(jsonObject.getString("poLineNumber"));
            dccLineItem.setUplLineNumber(jsonObject.getString("uplLineNumber"));
            dccLineItem.setItemCode(jsonObject.getString("itemCode"));
            dccLineItem.setSerialNumber(jsonObject.getString("serialNumber"));
            dccLineItem.setLocationName(jsonObject.getString("locationName"));
            String dateInServiceString = jsonObject.getString("dateInService");
            Double delivered = Double.parseDouble(jsonObject.getString("deliveredQty"));
            if (jsonObject.has("actualItemCode")) {
                dccLineItem.setActualItemCode(jsonObject.getString("actualItemCode"));
            }

            if (jsonObject.getString("uplLineNumber").length() != 0) {
                tb_PurchaseOrderUPL topRecord = purchaseOrderUPLRepo.findTopByPoNumberAndPoLineNumberAndUplLine(
                        poNumber, jsonObject.getString("poLineNumber"), jsonObject.getString("uplLineNumber"));
                String unitOfMeasure = topRecord != null ? String.valueOf(topRecord.getUom()) : "";
                dccLineItem.setUoM(unitOfMeasure);

                double uplLineUnitPrice = topRecord != null ? topRecord.getUplLineUnitPrice() : 0;
                double poLineUnitPriceCalc = topRecord != null ? topRecord.getPoLineUnitPrice() : 0;
                double lineTotal = uplLineUnitPrice * delivered;
                double poacceptanceQty = 0;
                if (poLineUnitPriceCalc != 0) {
                    poacceptanceQty = BigDecimal.valueOf(lineTotal)
                            .divide(BigDecimal.valueOf(poLineUnitPriceCalc), 20, RoundingMode.HALF_UP)
                            .doubleValue();
                }
                dccLineItem.setPoAcceptanceQty(poacceptanceQty);
            }

            if (jsonObject.has("uplLineItemCode")) {
                dccLineItem.setUplItemCode(jsonObject.getString("uplLineItemCode"));
            }
            if (jsonObject.has("uplLineDescription")) {
                dccLineItem.setUplItemDescription(jsonObject.getString("uplLineDescription"));
            }
            dccLineItem.setPoId(poNumber);
            dccLineItem.setDeliveredQty(delivered);
            try {
                java.util.Date parsedDate = dateFormat.parse(dateInServiceString);
                dccLineItem.setDateInService(new java.sql.Date(parsedDate.getTime()));
            } catch (ParseException ex) {
                logger.info("Exception " + ex.getMessage());
            }
            dccLineItem.setScopeOfWork(jsonObject.getString("scopeOfWork"));
            dccLineItem.setRemarks(jsonObject.getString("remarks"));
            dccLineItem.setLinkId(jsonObject.getString("linkId"));
            dccLineItem.setTagNumber(jsonObject.getString("tagNumber"));

            try {
                dcclnrepo.save(dccLineItem);
                result = "Record add Success";
            } catch (Exception exc) {
                logger.info("POST DCC EXCEPTION" + exc);
                result = exc.getCause() != null ? exc.getCause().toString() : exc.getMessage();
            }
        }
        return result;
    }
}
