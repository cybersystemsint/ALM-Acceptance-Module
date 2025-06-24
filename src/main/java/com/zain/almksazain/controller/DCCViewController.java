///*
// * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
// * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
// */
//package com.zain.almksazain.controller;
//
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import com.zain.almksazain.services.DCCViewService;
//import java.util.Map;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.CrossOrigin;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import com.zain.almksazain.model.DCCViewDTO;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// *
// * @author jgithu
// */
//@RestController
//@RequestMapping("/api/dcc-view")
//public class DCCViewController {
//
//    @Autowired
//    private DCCViewService dccViewService;
//
//    @PostMapping(value = "/reports/getNestedDccData", produces = "application/json")
//    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
//    public Map<String, Object> getNestedDccData(@RequestBody String req) {
//        // Parse request parameters
//        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
//        String supplierId = obj.get("supplierId").getAsString();
//        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
//        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
//        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
//        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;
//
//        // Validate pagination parameters
//        page = Math.max(page, 1);
//        size = Math.max(size, 1);
//
//        // Get filtered DCC data
//        List<DCCViewDTO> allDccData = dccViewService.getDCCViewData();
//
//        // Apply filters
//        List<DCCViewDTO> filteredData = allDccData.stream()
//                .filter(dto -> supplierId.equalsIgnoreCase("0") || supplierId.equals(dto.getSupplierId()))
//                .filter(dto -> {
//                    if (columnName.isEmpty() || searchQuery.isEmpty()) {
//                        return true;
//                    }
//                    try {
//                        Field field = DCCViewDTO.class.getDeclaredField(columnName);
//                        field.setAccessible(true);
//                        Object value = field.get(dto);
//                        return value != null && value.toString().toLowerCase().contains(searchQuery.toLowerCase());
//                    } catch (NoSuchFieldException | SecurityException e) {
//                        return false;
//                    }
//                })
//                .collect(Collectors.toList());
//
//        // Get unique DCC record numbers
//        Set<Long> uniqueDccRecordNos = filteredData.stream()
//                .map(DCCViewDTO::getDccRecordNo)
//                .collect(Collectors.toSet());
//
//        // Apply pagination to unique DCC records
//        List<Long> paginatedDccRecordNos = uniqueDccRecordNos.stream()
//                .skip((page - 1) * (long) size)
//                .limit(size)
//                .collect(Collectors.toList());
//
//        // Group line items by DCC record number
//        Map<Long, List<DCCViewDTO>> groupedByDcc = filteredData.stream()
//                .filter(dto -> paginatedDccRecordNos.contains(dto.getDccRecordNo()))
//                .collect(Collectors.groupingBy(DCCViewDTO::getDccRecordNo));
//
//        // Build the nested response
//        List<Map<String, Object>> responseData = new ArrayList<>();
//
//        groupedByDcc.forEach((dccRecordNo, lineItems) -> {
//            if (!lineItems.isEmpty()) {
//                DCCViewDTO firstItem = lineItems.get(0);
//
//                Map<String, Object> dccRecord = new LinkedHashMap<>();
//                // Main DCC fields
//                dccRecord.put("recordNo", firstItem.getDccRecordNo());
//                dccRecord.put("projectName", firstItem.getDccProjectName());
//                dccRecord.put("vendorName", firstItem.getDccVendorName());
//                dccRecord.put("vendorEmail", firstItem.getDccVendorEmail());
//                dccRecord.put("vendorNumber", firstItem.getSupplierId());
//                dccRecord.put("dccCurrency", firstItem.getDccCurrency());
//                dccRecord.put("dccAcceptanceType", firstItem.getDccAcceptanceType());
//                dccRecord.put("dccStatus", firstItem.getDccStatus());
//                dccRecord.put("dccCreatedDate", firstItem.getDccCreatedDate());
//                dccRecord.put("dateApproved", firstItem.getDateApproved());
//                dccRecord.put("vendorComment", firstItem.getVendorComment());
//                dccRecord.put("dccId", firstItem.getDccId());
//                dccRecord.put("createdBy", firstItem.getCreatedBy());
//                dccRecord.put("createdByName", firstItem.getCreatedByName());
//                dccRecord.put("approvalCount", firstItem.getApprovalCount());
//                dccRecord.put("pendingApprovers", firstItem.getPendingApprovers());
//                dccRecord.put("approverComment", firstItem.getApproverComment());
//                dccRecord.put("userAging", firstItem.getUserAging());
//                dccRecord.put("totalAging", firstItem.getTotalAging());
//
//                // Line items
//                List<Map<String, Object>> lineItemsList = new ArrayList<>();
//
//                for (DCCViewDTO lineItem : lineItems) {
//                    Map<String, Object> lineItemMap = new LinkedHashMap<>();
//                    lineItemMap.put("recordNo", lineItem.getLnRecordNo());
//                    lineItemMap.put("lnProductName", lineItem.getLnProductName());
//                    lineItemMap.put("serialNumber", lineItem.getLnProductSerialNo());
//                    lineItemMap.put("deliveredQty", lineItem.getLnDeliveredQty());
//                    lineItemMap.put("locationName", lineItem.getLnLocationName());
//                    lineItemMap.put("dateInService", lineItem.getLnInserviceDate());
//                    lineItemMap.put("lnUnitPrice", lineItem.getLnUnitPrice());
//                    lineItemMap.put("scopeOfWork", lineItem.getLnScopeOfWork());
//                    lineItemMap.put("remarks", lineItem.getLnRemarks());
//                    lineItemMap.put("itemCode", lineItem.getLnItemCode());
//                    lineItemMap.put("linkId", lineItem.getLinkId());
//                    lineItemMap.put("tagNumber", lineItem.getTagNumber());
//                    lineItemMap.put("poLineNumber", lineItem.getLineNumber());
//                    lineItemMap.put("actualItemCode", lineItem.getActualItemCode());
//                    lineItemMap.put("uplLineNumber", lineItem.getUplLineNumber());
//                    lineItemMap.put("currency", lineItem.getDccCurrency());
//                    lineItemMap.put("poId", lineItem.getPoId());
//                    lineItemMap.put("UPLACPTRequestValue", lineItem.getUPLACPTRequestValue());
//                    lineItemMap.put("POAcceptanceQty", lineItem.getPOAcceptanceQty());
//                    lineItemMap.put("POLineAcceptanceQty", lineItem.getPOLineAcceptanceQty());
//                    lineItemMap.put("poPendingQuantity", lineItem.getPoPendingQuantity());
//                    lineItemMap.put("poOrderQuantity", lineItem.getPoOrderQuantity());
//                    lineItemMap.put("itemPartNumber", lineItem.getItemPartNumber());
//                    lineItemMap.put("poLineDescription", lineItem.getPoLineDescription());
//                    lineItemMap.put("uplLineQuantity", lineItem.getUplLineQuantity());
//                    lineItemMap.put("poLineQuantity", lineItem.getPoLineQuantity());
//                    lineItemMap.put("uplLineItemCode", lineItem.getUplLineItemCode());
//                    lineItemMap.put("uplLineDescription", lineItem.getUplLineDescription());
//                    lineItemMap.put("uom", lineItem.getUnitOfMeasure());
//                    lineItemMap.put("activeOrPassive", lineItem.getActiveOrPassive());
//                    lineItemMap.put("uplPendingQuantity", lineItem.getUplPendingQuantity());
//
//                    lineItemsList.add(lineItemMap);
//                }
//
//                dccRecord.put("lineItems", lineItemsList);
//                responseData.add(dccRecord);
//            }
//        });
//
//        // Calculate pagination info
//        int totalRecords = uniqueDccRecordNos.size();
//        int totalPages = (int) Math.ceil((double) totalRecords / size);
//
//        // Build final response
//        Map<String, Object> response = new HashMap<>();
//        response.put("currentPage", page);
//        response.put("pageSize", size);
//        response.put("totalRecords", totalRecords);
//        response.put("totalPages", totalPages);
//        response.put("data", responseData);
//
//        return response;
//    }
//}
