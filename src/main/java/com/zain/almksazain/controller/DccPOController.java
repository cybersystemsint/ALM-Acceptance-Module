package com.zain.almksazain.controller;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOLineItemDTO;
import com.zain.almksazain.DTO.DccPOParentDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.DccPORequestDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.serviceImplementors.DccPOService;
import com.zain.almksazain.serviceImplementors.DccPOService.DccPOFetchResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling DCC PO Combined View requests.
 * Provides an endpoint to fetch paginated DCC records with related Purchase Order, UPL, Line Item, and Approval data.
 */
@RestController
@RequestMapping("/dcc-po")
public class DccPOController {

    private static final Logger logger = LogManager.getLogger(DccPOController.class);

    @Autowired
    private DccPOService dccPOService;

    /**
     * Endpoint to retrieve paginated DCC PO Combined View data.
     * Accepts a request body with supplierId, pendingApprovers, pagination parameters, and optional search filters.
     * Returns a DeferredResult containing a hierarchical response with parent and line item data.
     *
     * @param request The request DTO containing supplierId, pendingApprovers, page, size, columnName, and searchQuery.
     * @return DeferredResult containing the ResponseEntity with DccPOResponseDTO or an error message.
     */

    @PostMapping("/combined-view")
    public DeferredResult<ResponseEntity<DccPOResponseDTO>> getDccPOCombinedView(
            @RequestBody DccPORequestDTO request) {
        DeferredResult<ResponseEntity<DccPOResponseDTO>> deferredResult = new DeferredResult<>(120000L); // 120 seconds timeout

        int page = Math.max(request.getPage(), 1);
        int size = Math.max(request.getSize(), 1);

        CompletableFuture<DccPOFetchResult> future = dccPOService.getDccPOCombinedView(
                request.getSupplierId(),
                request.getPendingApprovers(),
                page,
                size,
                request.getColumnName(),
                request.getSearchQuery(),
                request.getExporting() != null ? request.getExporting() : false,
                request.getOperator());

        future.thenAccept(result -> {
            List<DccPOCombinedViewDTO> data = result.getData();
            Long totalFilteredRecords = result.getTotalFilteredRecords();

            // Group by dccRecordNo to create hierarchical structure
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

            // Transform into hierarchical structure
            List<DccPOParentDTO> parentDTOs = groupedByDccRecordNo.entrySet().stream()
                    .map(entry -> {
                        DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
                        DccPOParentDTO parentDTO = new DccPOParentDTO();
                        // Populate parent-level fields
                        parentDTO.setRecordNo(firstRecord.getDccRecordNo());
                        parentDTO.setDccPoNumber(firstRecord.getDccPoNumber());
                        parentDTO.setNewProjectName(firstRecord.getNewProjectName());
                        parentDTO.setDccAcceptanceType(firstRecord.getDccAcceptanceType());
                        parentDTO.setDccStatus(firstRecord.getDccStatus());
                        parentDTO.setDccCreatedDate(firstRecord.getDccCreatedDate());
                        parentDTO.setDateApproved(firstRecord.getDateApproved());
                        parentDTO.setVendorComment(firstRecord.getVendorComment());
                        parentDTO.setDccId(firstRecord.getDccId());
                        parentDTO.setPoId(firstRecord.getPoId());
                        parentDTO.setProjectName(firstRecord.getProjectName());
                        parentDTO.setSupplierId(firstRecord.getSupplierId());
                        parentDTO.setVendorNumber(firstRecord.getVendorNumber());
                        parentDTO.setVendorName(firstRecord.getVendorName());
                        parentDTO.setCreatedBy(firstRecord.getCreatedBy());
                        parentDTO.setCreatedByName(firstRecord.getCreatedByName());
                        parentDTO.setApprovalCount(firstRecord.getApprovalCount());
                        parentDTO.setPendingApprovers(firstRecord.getPendingApprovers());
                        parentDTO.setApproverComment(firstRecord.getApproverComment());
                        parentDTO.setUserAging(firstRecord.getUserAging());
                        parentDTO.setTotalAging(firstRecord.getTotalAging());
                        parentDTO.setVendorEmail(firstRecord.getDccVendorEmail());
                        parentDTO.setDccCurrency(firstRecord.getDccCurrency());

                        // Add line items only if child data exists
                        List<DccPOLineItemDTO> lineItems = new ArrayList<>();
                        if (firstRecord.getLnRecordNo() != null) { // Check if child data is present
                            lineItems = entry.getValue().stream()
                                    .map(dto -> {
                                        DccPOLineItemDTO lineItem = new DccPOLineItemDTO();
                                        lineItem.setRecordNo(dto.getLnRecordNo());
                                        lineItem.setLnProductName(dto.getLnProductName());
                                        lineItem.setSerialNumber(dto.getLnProductSerialNo());
                                        lineItem.setDeliveredQty(dto.getLnDeliveredQty());
                                        lineItem.setLocationName(dto.getLnLocationName());
                                        lineItem.setDateInService(dto.getLnInserviceDate());
                                        lineItem.setLnUnitPrice(dto.getLnUnitPrice());
                                        lineItem.setScopeOfWork(dto.getLnScopeOfWork());
                                        lineItem.setRemarks(dto.getLnRemarks());
                                        lineItem.setItemCode(dto.getUplLineItemCode());
                                        lineItem.setLinkId(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
                                        lineItem.setTagNumber(dto.getTagNumber());
                                        lineItem.setPoLineNumber(dto.getLineNumber());
                                        lineItem.setActualItemCode(dto.getActualItemCode());
                                        lineItem.setUplLineNumber(dto.getUplLineNumber());
                                        lineItem.setCurrency(dto.getDccCurrency());
                                        lineItem.setPoId(dto.getPoId());
                                        lineItem.setUPLACPTRequestValue(dto.getUPLACPTRequestValue());
//                                        lineItem.setPOAcceptanceQty(dto.getPOAcceptanceQty());
                                        lineItem.setpoAcceptanceQty(dto.getpoAcceptanceQty());
                                        lineItem.setPOLineAcceptanceQty(dto.getPOLineAcceptanceQty());
                                        lineItem.setPoPendingQuantity(dto.getPoPendingQuantity());
                                        lineItem.setPoOrderQuantity(dto.getPoOrderQuantity());
                                        lineItem.setItemPartNumber(dto.getItemPartNumber());
                                        lineItem.setPoLineDescription(dto.getPoLineDescription());
                                        lineItem.setUplLineQuantity(dto.getUplLineQuantity());
                                        lineItem.setPoLineQuantity(dto.getPoLineQuantity());
                                        lineItem.setUplLineItemCode(dto.getUplLineItemCode());
                                        lineItem.setUplLineDescription(dto.getUplLineDescription());
                                        lineItem.setUom(dto.getUnitOfMeasure());
                                        lineItem.setActiveOrPassive(dto.getActiveOrPassive());
                                        lineItem.setUplPendingQuantity(dto.getUplPendingQuantity());
                                        return lineItem;
                                    })
                                    .collect(Collectors.toList());
                        }
                        parentDTO.setLineItems(lineItems);
                        return parentDTO;
                    })
                    .sorted((a, b) -> b.getRecordNo().compareTo(a.getRecordNo())) // Sort by recordNo descending
                    .collect(Collectors.toList());

            // Build response
            DccPOResponseDTO responseDTO = new DccPOResponseDTO();
            responseDTO.setTotalRecords(totalFilteredRecords);
            responseDTO.setData(parentDTOs);
            responseDTO.setTotalPages((int) Math.ceil((double) totalFilteredRecords / size));
            responseDTO.setPageSize(size);
            responseDTO.setCurrentPage(page);

            logger.info("Successfully retrieved DCC PO Combined View with {} parent records (page: {}, size: {}, supplierId: {}, pendingApprovers: {}, columnName: {}, searchQuery: {})",
                    parentDTOs.size(), page, size, request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery());
            deferredResult.setResult(ResponseEntity.ok(responseDTO));
        }).exceptionally(throwable -> {
            logger.error("Error processing DCC PO Combined View request", throwable);
            if (throwable.getCause() instanceof DccPOProcessingException) {
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error: " + throwable.getCause().getMessage()));
            } else {
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Unexpected error occurred"));
            }
            return null;
        });

        return deferredResult;
    }

    /**
     * Exception handler for DccPOProcessingException.
     *
     * @param ex The DccPOProcessingException thrown during processing.
     * @return ResponseEntity with error message and 500 status.
     */
    @ExceptionHandler(DccPOProcessingException.class)
    public ResponseEntity<String> handleDccPOProcessingException(DccPOProcessingException ex) {
        logger.error("DCC PO Processing Exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + ex.getMessage());
    }

    /**
     * Exception handler for general exceptions.
     *
     * @param ex The Exception thrown during processing.
     * @return ResponseEntity with error message and 500 status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        logger.error("Unexpected error in DCC PO Controller", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error occurred: " + ex.getMessage());
    }


    // changes to endpoint to  optimize export

//    @PostMapping("/export-combined-view")
//    public DeferredResult<ResponseEntity<DccPOResponseDTO>> exportDccPOCombinedView(
//            @RequestBody DccPORequestDTO request) {
//        DeferredResult<ResponseEntity<DccPOResponseDTO>> deferredResult = new DeferredResult<>(600000L); // 10 minutes timeout for export
//
//        CompletableFuture<List<DccPOCombinedViewDTO>> future = dccPOService.getAllDccPOForExport(
//                request.getSupplierId(),
//                request.getPendingApprovers(),
//                request.getColumnName(),
//                request.getSearchQuery(),
//                request.getOperator());
//
//        future.thenAccept(data -> {
//            Long totalFilteredRecords = (long) data.size();
//
//            // Group by dccRecordNo to create hierarchical structure (same as in the paginated endpoint)
//            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
//                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));
//
//            List<DccPOParentDTO> parentDTOs = groupedByDccRecordNo.entrySet().stream()
//                    .map(entry -> {
//                        DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
//                        DccPOParentDTO parentDTO = new DccPOParentDTO();
//                        // Populate parent-level fields
//                        parentDTO.setRecordNo(firstRecord.getDccRecordNo());
//                        parentDTO.setDccPoNumber(firstRecord.getDccPoNumber());
//                        parentDTO.setNewProjectName(firstRecord.getNewProjectName());
//                        parentDTO.setDccAcceptanceType(firstRecord.getDccAcceptanceType());
//                        parentDTO.setDccStatus(firstRecord.getDccStatus());
//                        parentDTO.setDccCreatedDate(firstRecord.getDccCreatedDate());
//                        parentDTO.setDateApproved(firstRecord.getDateApproved());
//                        parentDTO.setVendorComment(firstRecord.getVendorComment());
//                        parentDTO.setDccId(firstRecord.getDccId());
//                        parentDTO.setPoId(firstRecord.getPoId());
//                        parentDTO.setProjectName(firstRecord.getProjectName());
//                        parentDTO.setSupplierId(firstRecord.getSupplierId());
//                        parentDTO.setVendorNumber(firstRecord.getVendorNumber());
//                        parentDTO.setVendorName(firstRecord.getVendorName());
//                        parentDTO.setCreatedBy(firstRecord.getCreatedBy());
//                        parentDTO.setCreatedByName(firstRecord.getCreatedByName());
//                        parentDTO.setApprovalCount(firstRecord.getApprovalCount());
//                        parentDTO.setPendingApprovers(firstRecord.getPendingApprovers());
//                        parentDTO.setApproverComment(firstRecord.getApproverComment());
//                        parentDTO.setUserAging(firstRecord.getUserAging());
//                        parentDTO.setTotalAging(firstRecord.getTotalAging());
//                        parentDTO.setVendorEmail(firstRecord.getDccVendorEmail());
//                        parentDTO.setDccCurrency(firstRecord.getDccCurrency());
//
//                        // Add line items
//                        List<DccPOLineItemDTO> lineItems = entry.getValue().stream()
//                                .map(dto -> {
//                                    DccPOLineItemDTO lineItem = new DccPOLineItemDTO();
//                                    lineItem.setRecordNo(dto.getLnRecordNo());
//                                    lineItem.setLnProductName(dto.getLnProductName());
//                                    lineItem.setSerialNumber(dto.getLnProductSerialNo());
//                                    lineItem.setDeliveredQty(dto.getLnDeliveredQty());
//                                    lineItem.setLocationName(dto.getLnLocationName());
//                                    lineItem.setDateInService(dto.getLnInserviceDate());
//                                    lineItem.setLnUnitPrice(dto.getLnUnitPrice());
//                                    lineItem.setScopeOfWork(dto.getLnScopeOfWork());
//                                    lineItem.setRemarks(dto.getLnRemarks());
//                                    lineItem.setItemCode(dto.getUplLineItemCode());
//                                    lineItem.setLinkId(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
//                                    lineItem.setTagNumber(dto.getTagNumber());
//                                    lineItem.setPoLineNumber(dto.getLineNumber());
//                                    lineItem.setActualItemCode(dto.getActualItemCode());
//                                    lineItem.setUplLineNumber(dto.getUplLineNumber());
//                                    lineItem.setCurrency(dto.getDccCurrency());
//                                    lineItem.setPoId(dto.getPoId());
//                                    lineItem.setUPLACPTRequestValue(dto.getUPLACPTRequestValue());
//                                    lineItem.setpoAcceptanceQty(dto.getpoAcceptanceQty());
//                                    lineItem.setPOLineAcceptanceQty(dto.getPOLineAcceptanceQty());
//                                    lineItem.setPoPendingQuantity(dto.getPoPendingQuantity());
//                                    lineItem.setPoOrderQuantity(dto.getPoOrderQuantity());
//                                    lineItem.setItemPartNumber(dto.getItemPartNumber());
//                                    lineItem.setPoLineDescription(dto.getPoLineDescription());
//                                    lineItem.setUplLineQuantity(dto.getUplLineQuantity());
//                                    lineItem.setPoLineQuantity(dto.getPoLineQuantity());
//                                    lineItem.setUplLineItemCode(dto.getUplLineItemCode());
//                                    lineItem.setUplLineDescription(dto.getUplLineDescription());
//                                    lineItem.setUom(dto.getUnitOfMeasure());
//                                    lineItem.setActiveOrPassive(dto.getActiveOrPassive());
//                                    lineItem.setUplPendingQuantity(dto.getUplPendingQuantity());
//                                    return lineItem;
//                                })
//                                .collect(Collectors.toList());
//                        parentDTO.setLineItems(lineItems);
//                        return parentDTO;
//                    })
//                    .sorted((a, b) -> b.getRecordNo().compareTo(a.getRecordNo())) // Sort by recordNo descending
//                    .collect(Collectors.toList());
//
//            // Build response (similar to paginated, but with all data and no pagination info)
//            DccPOResponseDTO responseDTO = new DccPOResponseDTO();
//            responseDTO.setTotalRecords(totalFilteredRecords);
//            responseDTO.setData(parentDTOs);
//            // For export, set pageSize to total, currentPage to 1, totalPages to 1
//            responseDTO.setTotalPages(1);
//            responseDTO.setPageSize(totalFilteredRecords.intValue());
//            responseDTO.setCurrentPage(1);
//
//            logger.info("Successfully exported DCC PO Combined View with {} parent records (supplierId: {}, pendingApprovers: {}, columnName: {}, searchQuery: {})",
//                    parentDTOs.size(), request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery());
//            deferredResult.setResult(ResponseEntity.ok(responseDTO));
//        }).exceptionally(throwable -> {
//            logger.error("Error processing DCC PO export request", throwable);
//            if (throwable.getCause() instanceof DccPOProcessingException) {
//                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body("Error: " + throwable.getCause().getMessage()));
//            } else {
//                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body("Unexpected error occurred"));
//            }
//            return null;
//        });
//
//        return deferredResult;
//    }


    //    Export as excel file

    @PostMapping(value = "/export-combined-view", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public DeferredResult<ResponseEntity<byte[]>> exportDccPOCombinedViewToExcel(@RequestBody DccPORequestDTO request) {
        DeferredResult<ResponseEntity<byte[]>> deferredResult = new DeferredResult<>(600000L); // 10 minutes timeout

        CompletableFuture<List<DccPOCombinedViewDTO>> future = dccPOService.getAllDccPOForExport(
                request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery(), request.getOperator());

        future.thenAccept(data -> {
            try {
                // Group by dccRecordNo to create hierarchical structure
                Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                        .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

                // Create Excel workbook
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("DCC PO Data");

                // Create header row
                Row headerRow = sheet.createRow(0);
                String[] columnHeaders = { // Renamed to avoid any potential conflict
                        "Record No", "DCC PO Number", "New Project Name", "Acceptance Type", "Status", "Created Date", "Date Approved",
                        "Vendor Comment", "DCC ID", "PO ID", "Project Name", "Supplier ID", "Vendor Number", "Vendor Name",
                        "Created By", "Approval Count", "Pending Approvers", "Approver Comment", "User Aging", "Total Aging",
                        "Vendor Email", "Currency", "Line Item Record No", "Product Name", "Serial Number", "Delivered Qty",
                        "Location Name", "In-Service Date", "Unit Price", "Scope of Work", "Remarks", "Item Code", "Link ID",
                        "Tag Number", "PO Line Number", "Actual Item Code", "UPL Line Number", "PO Acceptance Qty",
                        "PO Line Acceptance Qty", "PO Pending Quantity", "PO Order Quantity", "Item Part Number",
                        "PO Line Description", "UPL Line Quantity", "UPL Line Item Code", "UPL Line Description", "UOM",
                        "Active/Passive", "UPL Pending Quantity"
                };
                for (int i = 0; i < columnHeaders.length; i++) {
                    headerRow.createCell(i).setCellValue(columnHeaders[i]);
                }

                // Populate data rows
                int rowNum = 1;
                for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : groupedByDccRecordNo.entrySet()) {
                    DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
                    for (DccPOCombinedViewDTO dto : entry.getValue()) {
                        Row row = sheet.createRow(rowNum++);
                        int col = 0;
                        row.createCell(col++).setCellValue(firstRecord.getDccRecordNo() != null ? firstRecord.getDccRecordNo().toString() : "");
                        row.createCell(col++).setCellValue(firstRecord.getDccPoNumber());
                        row.createCell(col++).setCellValue(firstRecord.getNewProjectName());
                        row.createCell(col++).setCellValue(firstRecord.getDccAcceptanceType());
                        row.createCell(col++).setCellValue(firstRecord.getDccStatus());
                        row.createCell(col++).setCellValue(firstRecord.getDccCreatedDate());
                        row.createCell(col++).setCellValue(firstRecord.getDateApproved());
                        row.createCell(col++).setCellValue(firstRecord.getVendorComment());
                        row.createCell(col++).setCellValue(firstRecord.getDccId() != null ? firstRecord.getDccId().toString() : "");
                        row.createCell(col++).setCellValue(firstRecord.getPoId());
                        row.createCell(col++).setCellValue(firstRecord.getProjectName());
                        row.createCell(col++).setCellValue(firstRecord.getSupplierId());
                        row.createCell(col++).setCellValue(firstRecord.getVendorNumber());
                        row.createCell(col++).setCellValue(firstRecord.getVendorName());
                        row.createCell(col++).setCellValue(firstRecord.getCreatedBy());
                        row.createCell(col++).setCellValue(firstRecord.getApprovalCount() != null ? firstRecord.getApprovalCount() : 0);
                        row.createCell(col++).setCellValue(firstRecord.getPendingApprovers());
                        row.createCell(col++).setCellValue(firstRecord.getApproverComment());
                        row.createCell(col++).setCellValue(firstRecord.getUserAging());
                        row.createCell(col++).setCellValue(firstRecord.getTotalAging());
                        row.createCell(col++).setCellValue(firstRecord.getDccVendorEmail());
                        row.createCell(col++).setCellValue(firstRecord.getDccCurrency());
                        row.createCell(col++).setCellValue(dto.getLnRecordNo() != null ? dto.getLnRecordNo().toString() : "");
                        row.createCell(col++).setCellValue(dto.getLnProductName());
                        row.createCell(col++).setCellValue(dto.getLnProductSerialNo());
                        row.createCell(col++).setCellValue(dto.getLnDeliveredQty() != null ? dto.getLnDeliveredQty() : 0);
                        row.createCell(col++).setCellValue(dto.getLnLocationName());
                        row.createCell(col++).setCellValue(dto.getLnInserviceDate());
                        row.createCell(col++).setCellValue(dto.getLnUnitPrice());
                        row.createCell(col++).setCellValue(dto.getLnScopeOfWork());
                        row.createCell(col++).setCellValue(dto.getLnRemarks());
                        row.createCell(col++).setCellValue(dto.getUplLineItemCode());
                        row.createCell(col++).setCellValue(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
                        row.createCell(col++).setCellValue(dto.getTagNumber());
                        row.createCell(col++).setCellValue(dto.getLineNumber());
                        row.createCell(col++).setCellValue(dto.getActualItemCode());
                        row.createCell(col++).setCellValue(dto.getUplLineNumber());
                        row.createCell(col++).setCellValue(dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0);
                        row.createCell(col++).setCellValue(dto.getPOLineAcceptanceQty());
                        row.createCell(col++).setCellValue(dto.getPoPendingQuantity());
                        row.createCell(col++).setCellValue(dto.getPoOrderQuantity());
                        row.createCell(col++).setCellValue(dto.getItemPartNumber());
                        row.createCell(col++).setCellValue(dto.getPoLineDescription());
                        row.createCell(col++).setCellValue(dto.getUplLineQuantity() != null ? dto.getUplLineQuantity() : 0);
                        row.createCell(col++).setCellValue(dto.getUplLineItemCode());
                        row.createCell(col++).setCellValue(dto.getUplLineDescription());
                        row.createCell(col++).setCellValue(dto.getUnitOfMeasure());
                        row.createCell(col++).setCellValue(dto.getActiveOrPassive());
                        row.createCell(col++).setCellValue(dto.getUplPendingQuantity());
                    }
                }

                // Write to byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                workbook.close();

                // Set response headers and return
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.add("Content-Disposition", "attachment; filename=dcc_po_export.xlsx");
                deferredResult.setResult(new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK));
                logger.info("Successfully exported Excel file with {} parent records", groupedByDccRecordNo.size());
            } catch (Exception ex) {
                logger.error("Error generating Excel file", ex);
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("Error generating Excel: " + ex.getMessage()).getBytes()));
            }
        }).exceptionally(throwable -> {
            logger.error("Error processing DCC PO export request", throwable);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error: " + throwable.getCause().getMessage()).getBytes()));
            return null;
        });

        return deferredResult;
    }

}