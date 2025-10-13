package com.zain.almksazain.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOLineItemDTO;
import com.zain.almksazain.DTO.DccPOParentDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.DccPORequestDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.serviceImplementors.DccPOExportService;
import com.zain.almksazain.serviceImplementors.DccPOService;
import com.zain.almksazain.serviceImplementors.DccPOApproverService;
import com.zain.almksazain.serviceImplementors.DccPOServiceV2;
import com.zain.almksazain.serviceImplementors.DccPOService.DccPOFetchResult;
import com.zain.almksazain.serviceImplementors.DccPOServiceV2.DccPOFetchResultV2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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

    @Autowired
    private DccPOApproverService dccPOApproverService;

    @Autowired
    private DccPOExportService dccPOExportService;

    @Autowired
    private DccPOServiceV2 dccPOServiceV2;

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

    // new endpoint
    @PostMapping("/combined-view-approvers")
    public DeferredResult<ResponseEntity<DccPOResponseDTO>> getDccPOCombinedViewApprovers(
            @RequestBody DccPORequestDTO request) {
        DeferredResult<ResponseEntity<DccPOResponseDTO>> deferredResult = new DeferredResult<>(120000L); // 120 seconds timeout

        int page = Math.max(request.getPage(), 1);
        int size = Math.max(request.getSize(), 1);

        CompletableFuture<DccPOApproverService.DccPOFetchResult> future = dccPOApproverService.getDccPOCombinedView(
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

            logger.info("Successfully retrieved DCC PO Combined View for approvers with {} parent records (page: {}, size: {}, supplierId: {}, pendingApprovers: {}, columnName: {}, searchQuery: {})",
                    parentDTOs.size(), page, size, request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery());
            deferredResult.setResult(ResponseEntity.ok(responseDTO));
        }).exceptionally(throwable -> {
            logger.error("Error processing DCC PO Combined View for approvers request", throwable);
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

    @PostMapping("/V2/combined-view")
    public DeferredResult<ResponseEntity<DccPOResponseDTO>> getDccPOCombinedViewV2(
            @RequestBody DccPORequestDTO request) {
        DeferredResult<ResponseEntity<DccPOResponseDTO>> deferredResult = new DeferredResult<>(120000L); // 120 seconds timeout

        int page = Math.max(request.getPage(), 1);
        int size = Math.max(request.getSize(), 1);

        CompletableFuture<DccPOFetchResultV2> future = dccPOServiceV2.getDccPOCombinedView(
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

    //    Export as excel file

    @PostMapping(value = "/V2/export-combined-view", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
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


//    @PostMapping(value = "/export-combined-view", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
//    public DeferredResult<ResponseEntity<byte[]>> exportDccPOCombinedViewToExcelV2(@RequestBody DccPORequestDTO request) {
//        DeferredResult<ResponseEntity<byte[]>> deferredResult = new DeferredResult<>(120000L); // 2 minutes timeout for V2
//
//        // Use new export service
//        CompletableFuture<List<DccPOCombinedViewDTO>> future = dccPOExportService.getAllDccPOForExportV2(
//                request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery(), request.getOperator());
//
//        future.thenAccept(data -> {
//            try {
//                // Group by dccRecordNo to create hierarchical structure
//                Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
//                        .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));
//
//                // Create Excel workbook with streaming for large data
//                SXSSFWorkbook workbook = new SXSSFWorkbook(100); // Keep 100 rows in memory
//                Sheet sheet = workbook.createSheet("DCC PO Data");
//
//                // Create date cell style (reusable) - Use CreationHelper for XSSF/SXSSF
//                CreationHelper createHelper = workbook.getCreationHelper();
//                CellStyle dateStyle = workbook.createCellStyle();
//                dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("MM/dd/yyyy")); // Adjust format as needed (e.g., "dd/MM/yyyy")
//
//                // FIXED: Date formatter for parsing strings (matches DTO format: "d-MMM-yyyy" e.g., "28-Sep-2025")
//                SimpleDateFormat dateFormatter = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH); // Locale.ENGLISH ensures consistent MMM parsing
//
//                CellStyle dateOnlyStyle = workbook.createCellStyle();
//                dateOnlyStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MM-yyyy"));
//                // Create header row
//                Row headerRow = sheet.createRow(0);
//                String[] columnHeaders = {
//                        "Record No", "DCC PO Number", "New Project Name", "Acceptance Type", "Status", "Created Date", "Date Approved",
//                        "Vendor Comment", "DCC ID", "PO ID", "Project Name", "Supplier ID", "Vendor Number", "Vendor Name",
//                        "Created By", "Approval Count", "Pending Approvers", "Approver Comment", "User Aging", "Total Aging",
//                        "Vendor Email", "Currency", "Line Item Record No", "Product Name", "Serial Number", "Delivered Qty",
//                        "Location Name", "In-Service Date", "Unit Price", "Scope of Work", "Remarks", "Item Code", "Link ID",
//                        "Tag Number", "PO Line Number", "Actual Item Code", "UPL Line Number", "PO Acceptance Qty",
//                        "PO Line Acceptance Qty", "PO Pending Quantity", "PO Order Quantity", "Item Part Number",
//                        "PO Line Description", "UPL Line Quantity", "UPL Line Item Code", "UPL Line Description", "UOM",
//                        "Active/Passive", "UPL Pending Quantity"
//                };
//                for (int i = 0; i < columnHeaders.length; i++) {
//                    headerRow.createCell(i).setCellValue(columnHeaders[i]);
//                }
//
//                // Populate data rows - use firstRecord for parent fields, dto for line fields
//                int rowNum = 1;
//                for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : groupedByDccRecordNo.entrySet()) {
//                    DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
//                    for (DccPOCombinedViewDTO dto : entry.getValue()) {
//                        Row row = sheet.createRow(rowNum++);
//                        int col = 0;
//                        // Parent fields from firstRecord
//                        row.createCell(col++).setCellValue(firstRecord.getDccRecordNo() != null ? firstRecord.getDccRecordNo().toString() : "");
//                        row.createCell(col++).setCellValue(firstRecord.getDccPoNumber());
//                        row.createCell(col++).setCellValue(firstRecord.getNewProjectName());
//                        row.createCell(col++).setCellValue(firstRecord.getDccAcceptanceType());
//                        row.createCell(col++).setCellValue(firstRecord.getDccStatus());
//
//                        Cell createdDateCell = row.createCell(col++);
//                        String dccCreatedDateStr = firstRecord.getDccCreatedDate();
//                        if (dccCreatedDateStr != null && !dccCreatedDateStr.isEmpty()) {
//                            try {
//                                Date dccCreatedDate = dateFormatter.parse(dccCreatedDateStr);
//                                createdDateCell.setCellValue(dccCreatedDate);
//                                createdDateCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
//                            } catch (ParseException e) {
//                                logger.warn("Failed to parse Created Date '{}': {}", dccCreatedDateStr, e.getMessage());
//                                createdDateCell.setCellValue(dccCreatedDateStr); // Fallback to string
//                            }
//                        } else {
//                            createdDateCell.setCellValue("");
//                        }
//
//                        // Date: Date Approved (parse from String)
//                        Cell dateApprovedCell = row.createCell(col++);
//                        String dateApprovedStr = firstRecord.getDateApproved();
//                        if (dateApprovedStr != null && !dateApprovedStr.isEmpty()) {
//                            try {
//                                Date dateApproved = dateFormatter.parse(dateApprovedStr);
//                                dateApprovedCell.setCellValue(dateApproved);
//                                dateApprovedCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
//                            } catch (ParseException e) {
//                                logger.warn("Failed to parse Date Approved '{}': {}", dateApprovedStr, e.getMessage());
//                                dateApprovedCell.setCellValue(dateApprovedStr); // Fallback to string
//                            }
//                        } else {
//                            dateApprovedCell.setCellValue("");
//                        }
//
//                        row.createCell(col++).setCellValue(firstRecord.getVendorComment());
//                        row.createCell(col++).setCellValue(firstRecord.getDccId() != null ? firstRecord.getDccId().toString() : "");
//                        row.createCell(col++).setCellValue(firstRecord.getPoId());
//                        row.createCell(col++).setCellValue(firstRecord.getProjectName());
//                        row.createCell(col++).setCellValue(firstRecord.getSupplierId());
//                        row.createCell(col++).setCellValue(firstRecord.getVendorNumber());
//                        row.createCell(col++).setCellValue(firstRecord.getVendorName());
//                        row.createCell(col++).setCellValue(firstRecord.getCreatedBy());
//                        row.createCell(col++).setCellValue(firstRecord.getApprovalCount() != null ? firstRecord.getApprovalCount() : 0);
//                        row.createCell(col++).setCellValue(firstRecord.getPendingApprovers());
//                        row.createCell(col++).setCellValue(firstRecord.getApproverComment());
//                        row.createCell(col++).setCellValue(firstRecord.getUserAging());
//                        row.createCell(col++).setCellValue(firstRecord.getTotalAging());
//                        row.createCell(col++).setCellValue(firstRecord.getDccVendorEmail());
//                        row.createCell(col++).setCellValue(firstRecord.getDccCurrency());
//
//                        // Line fields from dto
//                        row.createCell(col++).setCellValue(dto.getLnRecordNo() != null ? dto.getLnRecordNo().toString() : "");
//                        row.createCell(col++).setCellValue(dto.getLnProductName());
//                        row.createCell(col++).setCellValue(dto.getLnProductSerialNo());
//                        row.createCell(col++).setCellValue(dto.getLnDeliveredQty() != null ? dto.getLnDeliveredQty() : 0);
//                        row.createCell(col++).setCellValue(dto.getLnLocationName());
//
//                        // Date: In-Service Date (parse from String)
//                        Cell inserviceDateCell = row.createCell(col++);
//                        String inserviceDateStr = dto.getLnInserviceDate();
//                        if (inserviceDateStr != null && !inserviceDateStr.isEmpty()) {
//                            try {
//                                Date inserviceDate = dateFormatter.parse(inserviceDateStr);
//                                inserviceDateCell.setCellValue(inserviceDate);
//                                inserviceDateCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
//                            } catch (ParseException e) {
//                                logger.warn("Failed to parse In-Service Date '{}': {}", inserviceDateStr, e.getMessage());
//                                inserviceDateCell.setCellValue(inserviceDateStr); // Fallback to string
//                            }
//                        } else {
//                            inserviceDateCell.setCellValue("");
//                        }
//
//
//                        row.createCell(col++).setCellValue(dto.getLnUnitPrice());
//                        row.createCell(col++).setCellValue(dto.getLnScopeOfWork());
//                        row.createCell(col++).setCellValue(dto.getLnRemarks());
//                        row.createCell(col++).setCellValue(dto.getUplLineItemCode());
//                        row.createCell(col++).setCellValue(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
//                        row.createCell(col++).setCellValue(dto.getTagNumber());
//                        row.createCell(col++).setCellValue(dto.getLineNumber());
//                        row.createCell(col++).setCellValue(dto.getActualItemCode());
//                        row.createCell(col++).setCellValue(dto.getUplLineNumber());
//                        row.createCell(col++).setCellValue(dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0);
//                        row.createCell(col++).setCellValue(dto.getPOLineAcceptanceQty());
//                        row.createCell(col++).setCellValue(dto.getPoPendingQuantity());
//                        row.createCell(col++).setCellValue(dto.getPoOrderQuantity());
//                        row.createCell(col++).setCellValue(dto.getItemPartNumber());
//                        row.createCell(col++).setCellValue(dto.getPoLineDescription());
//                        row.createCell(col++).setCellValue(dto.getUplLineQuantity() != null ? dto.getUplLineQuantity() : 0);
//                        row.createCell(col++).setCellValue(dto.getUplLineItemCode());
//                        row.createCell(col++).setCellValue(dto.getUplLineDescription());
//                        row.createCell(col++).setCellValue(dto.getUnitOfMeasure());
//                        row.createCell(col++).setCellValue(dto.getActiveOrPassive());
//                        row.createCell(col++).setCellValue(dto.getUplPendingQuantity());
//                    }
//                }
//
//                // Write to byte array
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                workbook.write(baos);
//                workbook.dispose(); // Flush and close temp files for SXSSF
//                workbook.close();
//
//                // Set response headers and return
//                HttpHeaders responseHeaders = new HttpHeaders();
//                responseHeaders.add("Content-Disposition", "attachment; filename=dcc_po_export_v2.xlsx");
//                deferredResult.setResult(new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK));
//                logger.info("Successfully exported Excel V2 file with {} parent records and {} total rows", groupedByDccRecordNo.size(), data.size());
//            } catch (Exception ex) {
//                logger.error("Error generating Excel V2 file", ex);
//                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body(("Error generating Excel V2: " + ex.getMessage()).getBytes()));
//            }
//        }).exceptionally(throwable -> {
//            logger.error("Error processing DCC PO export V2 request", throwable);
//            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(("Error V2: " + throwable.getCause().getMessage()).getBytes()));
//            return null;
//        });
//
//        return deferredResult;
//    }

    @PostMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterDccPOCombinedView(
            @RequestBody Map<String, Object> filters,
            @RequestParam(defaultValue = "1") int page,  // Changed default to 1
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "dccRecordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            logger.info("DCC PO Filter request received with {} filters", filters.size());

            // Extract filter parameters
            String supplierId = filters.containsKey("supplierId") ?
                    filters.get("supplierId").toString().trim() : "0";

            String pendingApprovers = filters.containsKey("pendingApprovers") ?
                    filters.get("pendingApprovers").toString().trim() : "";

            String columnName = filters.containsKey("columnName") ?
                    filters.get("columnName").toString().trim() : "";

            String searchQuery = filters.containsKey("searchQuery") ?
                    filters.get("searchQuery").toString().trim() : "";

            String operator = filters.containsKey("operator") ?
                    filters.get("operator").toString().trim() : "AND";

            // Additional filters for specific fields
            Map<String, String> fieldFilters = new HashMap<>();

            // String filters (exact match - case insensitive)
            if (filters.containsKey("dccPoNumber") && !filters.get("dccPoNumber").toString().trim().isEmpty()) {
                fieldFilters.put("dccPoNumber", filters.get("dccPoNumber").toString().trim());
            }

            if (filters.containsKey("newProjectName") && !filters.get("newProjectName").toString().trim().isEmpty()) {
                fieldFilters.put("newProjectName", filters.get("newProjectName").toString().trim());
            }

            if (filters.containsKey("dccStatus") && !filters.get("dccStatus").toString().trim().isEmpty()) {
                fieldFilters.put("dccStatus", filters.get("dccStatus").toString().trim());
            }

            if (filters.containsKey("dccAcceptanceType") && !filters.get("dccAcceptanceType").toString().trim().isEmpty()) {
                fieldFilters.put("dccAcceptanceType", filters.get("dccAcceptanceType").toString().trim());
            }

            if (filters.containsKey("vendorName") && !filters.get("vendorName").toString().trim().isEmpty()) {
                fieldFilters.put("vendorName", filters.get("vendorName").toString().trim());
            }

            if (filters.containsKey("vendorNumber") && !filters.get("vendorNumber").toString().trim().isEmpty()) {
                fieldFilters.put("vendorNumber", filters.get("vendorNumber").toString().trim());
            }

            if (filters.containsKey("createdByName") && !filters.get("createdByName").toString().trim().isEmpty()) {
                fieldFilters.put("createdByName", filters.get("createdByName").toString().trim());
            }

            // Integer filters
            if (filters.containsKey("dccRecordNo") && !filters.get("dccRecordNo").toString().trim().isEmpty()) {
                try {
                    fieldFilters.put("dccRecordNo", filters.get("dccRecordNo").toString().trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid dccRecordNo format: {}", filters.get("dccRecordNo"));
                }
            }

            if (filters.containsKey("approvalCount") && !filters.get("approvalCount").toString().trim().isEmpty()) {
                try {
                    fieldFilters.put("approvalCount", filters.get("approvalCount").toString().trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid approvalCount format: {}", filters.get("approvalCount"));
                }
            }

            // Date range filters
            String createdDateStart = filters.containsKey("createdDateStart") ?
                    filters.get("createdDateStart").toString().trim() : "";
            String createdDateEnd = filters.containsKey("createdDateEnd") ?
                    filters.get("createdDateEnd").toString().trim() : "";
            String approvedDateStart = filters.containsKey("approvedDateStart") ?
                    filters.get("approvedDateStart").toString().trim() : "";
            String approvedDateEnd = filters.containsKey("approvedDateEnd") ?
                    filters.get("approvedDateEnd").toString().trim() : "";

            // Validate and adjust page/size
            page = Math.max(page, 1);
            size = Math.max(size, 1);

            // Call SYNCHRONOUS service method (no CompletableFuture)
            DccPOFetchResult result = dccPOService.getDccPOCombinedViewSync(
                    supplierId,
                    pendingApprovers,
                    page,
                    size,
                    columnName,
                    searchQuery,
                    false, // not exporting
                    operator);

            List<DccPOCombinedViewDTO> data = result.getData();
            Long totalFilteredRecords = result.getTotalFilteredRecords();

            // Apply additional filters in memory
            SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

            List<DccPOCombinedViewDTO> filteredData = data.stream()
                    .filter(dto -> {
                        // Apply field filters
                        for (Map.Entry<String, String> entry : fieldFilters.entrySet()) {
                            String field = entry.getKey();
                            String value = entry.getValue().toLowerCase();

                            switch (field) {
                                case "dccRecordNo":
                                    if (dto.getDccRecordNo() != null &&
                                            !dto.getDccRecordNo().toString().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "dccPoNumber":
                                    if (dto.getDccPoNumber() == null ||
                                            !dto.getDccPoNumber().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "newProjectName":
                                    if (dto.getNewProjectName() == null ||
                                            !dto.getNewProjectName().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "dccStatus":
                                    if (dto.getDccStatus() == null ||
                                            !dto.getDccStatus().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "dccAcceptanceType":
                                    if (dto.getDccAcceptanceType() == null ||
                                            !dto.getDccAcceptanceType().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "vendorName":
                                    if (dto.getVendorName() == null ||
                                            !dto.getVendorName().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "vendorNumber":
                                    if (dto.getVendorNumber() == null ||
                                            !dto.getVendorNumber().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "createdByName":
                                    if (dto.getCreatedByName() == null ||
                                            !dto.getCreatedByName().toLowerCase().equals(value)) {
                                        return false;
                                    }
                                    break;
                                case "approvalCount":
                                    if (dto.getApprovalCount() != null &&
                                            !dto.getApprovalCount().toString().equals(value)) {
                                        return false;
                                    }
                                    break;
                            }
                        }

                        // Apply date range filters
                        try {
                            if (!createdDateStart.isEmpty() || !createdDateEnd.isEmpty()) {
                                if (dto.getDccCreatedDate() != null) {
                                    Date dccDate = sdf.parse(dto.getDccCreatedDate());
                                    if (!createdDateStart.isEmpty()) {
                                        Date startDate = sdf.parse(createdDateStart);
                                        if (dccDate.before(startDate)) return false;
                                    }
                                    if (!createdDateEnd.isEmpty()) {
                                        Date endDate = sdf.parse(createdDateEnd);
                                        if (dccDate.after(endDate)) return false;
                                    }
                                }
                            }

                            if (!approvedDateStart.isEmpty() || !approvedDateEnd.isEmpty()) {
                                if (dto.getDateApproved() != null) {
                                    Date approvedDate = sdf.parse(dto.getDateApproved());
                                    if (!approvedDateStart.isEmpty()) {
                                        Date startDate = sdf.parse(approvedDateStart);
                                        if (approvedDate.before(startDate)) return false;
                                    }
                                    if (!approvedDateEnd.isEmpty()) {
                                        Date endDate = sdf.parse(approvedDateEnd);
                                        if (approvedDate.after(endDate)) return false;
                                    }
                                }
                            }
                        } catch (ParseException e) {
                            logger.warn("Error parsing dates for filtering", e);
                        }

                        return true;
                    })
                    .collect(Collectors.toList());

            // Update total count after additional filtering
            totalFilteredRecords = (long) filteredData.size();

            // Group by dccRecordNo
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = filteredData.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

            // Transform into parent DTOs WITHOUT line items
            List<DccPOParentDTO> parentDTOs = groupedByDccRecordNo.entrySet().stream()
                    .map(entry -> {
                        DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
                        DccPOParentDTO parentDTO = new DccPOParentDTO();

                        // Populate ONLY parent-level fields
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

                        // DO NOT add line items for filter endpoint
                        parentDTO.setLineItems(new ArrayList<>());

                        return parentDTO;
                    })
                    .sorted((a, b) -> b.getRecordNo().compareTo(a.getRecordNo()))
                    .collect(Collectors.toList());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", parentDTOs);
            response.put("currentPage", page);
            response.put("totalItems", totalFilteredRecords);
            response.put("totalPages", (int) Math.ceil((double) totalFilteredRecords / size));
            response.put("first", page == 1);
            response.put("last", page * size >= totalFilteredRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            logger.info("Successfully filtered DCC PO data. {} parent records returned (page: {}, size: {})",
                    parentDTOs.size(), page, size);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error filtering DCC PO Combined View", e);
            String errorMessage = "Error filtering DCC PO data: " + e.getMessage();
            if (e instanceof NumberFormatException) {
                errorMessage = "Invalid number format in filter parameters";
            }
            return new ResponseEntity<>(
                    Collections.singletonMap("message", errorMessage),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

@PostMapping(value = "/export-combined-view", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
public DeferredResult<ResponseEntity<byte[]>> exportDccPOCombinedViewToExcelV2(@RequestBody DccPORequestDTO request) {
    DeferredResult<ResponseEntity<byte[]>> deferredResult = new DeferredResult<>(120000L); // 2 minutes timeout for V2

    // Use new export service
    CompletableFuture<List<DccPOCombinedViewDTO>> future = dccPOExportService.getAllDccPOForExportV2(
            request.getSupplierId(), request.getPendingApprovers(), request.getColumnName(), request.getSearchQuery(), request.getOperator());

    future.thenAccept(data -> {
        try {
            // Group by dccRecordNo to create hierarchical structure
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

            // Sort the entire data list descending by Approval Count, then DccRecordNo (before grouping affects iteration)
            data.sort(Comparator.comparing(DccPOCombinedViewDTO::getApprovalCount, Comparator.reverseOrder())
                    .thenComparing(DccPOCombinedViewDTO::getDccRecordNo, Comparator.reverseOrder())
                    .thenComparing(DccPOCombinedViewDTO::getLineNumber, Comparator.reverseOrder()));

            // Re-group after sorting to preserve order in lists
            groupedByDccRecordNo = data.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo, LinkedHashMap::new, Collectors.toList()));

            // Create Excel workbook with streaming for large data
            SXSSFWorkbook workbook = new SXSSFWorkbook(100); // Keep 100 rows in memory
            Sheet sheet = workbook.createSheet("DCC PO Data");

            // Create date cell style (reusable) - Use CreationHelper for XSSF/SXSSF
            CreationHelper createHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("MM/dd/yyyy")); // Adjust format as needed (e.g., "dd/MM/yyyy")

            // FIXED: Date formatter for parsing strings (matches DTO format: "d-MMM-yyyy" e.g., "28-Sep-2025")
            SimpleDateFormat dateFormatter = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH); // Locale.ENGLISH ensures consistent MMM parsing

            CellStyle dateOnlyStyle = workbook.createCellStyle();
            dateOnlyStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MM-yyyy"));
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] columnHeaders = {
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

            // Populate data rows - use firstRecord for parent fields, dto for line fields
            int rowNum = 1;
            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : groupedByDccRecordNo.entrySet()) {
                DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
                for (DccPOCombinedViewDTO dto : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    int col = 0;
                    // Parent fields from firstRecord
                    row.createCell(col++).setCellValue(firstRecord.getDccRecordNo() != null ? firstRecord.getDccRecordNo().toString() : "");
                    row.createCell(col++).setCellValue(firstRecord.getDccPoNumber());
                    row.createCell(col++).setCellValue(firstRecord.getNewProjectName());
                    row.createCell(col++).setCellValue(firstRecord.getDccAcceptanceType());
                    row.createCell(col++).setCellValue(firstRecord.getDccStatus());

                    Cell createdDateCell = row.createCell(col++);
                    String dccCreatedDateStr = firstRecord.getDccCreatedDate();
                    if (dccCreatedDateStr != null && !dccCreatedDateStr.isEmpty()) {
                        try {
                            Date dccCreatedDate = dateFormatter.parse(dccCreatedDateStr);
                            createdDateCell.setCellValue(dccCreatedDate);
                            createdDateCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
                        } catch (ParseException e) {
                            logger.warn("Failed to parse Created Date '{}': {}", dccCreatedDateStr, e.getMessage());
                            createdDateCell.setCellValue(dccCreatedDateStr); // Fallback to string
                        }
                    } else {
                        createdDateCell.setCellValue("");
                    }

                    // Date: Date Approved (parse from String)
                    Cell dateApprovedCell = row.createCell(col++);
                    String dateApprovedStr = firstRecord.getDateApproved();
                    if (dateApprovedStr != null && !dateApprovedStr.isEmpty()) {
                        try {
                            Date dateApproved = dateFormatter.parse(dateApprovedStr);
                            dateApprovedCell.setCellValue(dateApproved);
                            dateApprovedCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
                        } catch (ParseException e) {
                            logger.warn("Failed to parse Date Approved '{}': {}", dateApprovedStr, e.getMessage());
                            dateApprovedCell.setCellValue(dateApprovedStr); // Fallback to string
                        }
                    } else {
                        dateApprovedCell.setCellValue("");
                    }

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

                    // Line fields from dto
                    row.createCell(col++).setCellValue(dto.getLnRecordNo() != null ? dto.getLnRecordNo().toString() : "");
                    row.createCell(col++).setCellValue(dto.getLnProductName());
                    row.createCell(col++).setCellValue(dto.getLnProductSerialNo());
                    row.createCell(col++).setCellValue(dto.getLnDeliveredQty() != null ? dto.getLnDeliveredQty() : 0);
                    row.createCell(col++).setCellValue(dto.getLnLocationName());

                    // Date: In-Service Date (parse from String)
                    Cell inserviceDateCell = row.createCell(col++);
                    String inserviceDateStr = dto.getLnInserviceDate();
                    if (inserviceDateStr != null && !inserviceDateStr.isEmpty()) {
                        try {
                            Date inserviceDate = dateFormatter.parse(inserviceDateStr);
                            inserviceDateCell.setCellValue(inserviceDate);
                            inserviceDateCell.setCellStyle(dateOnlyStyle); // Use dd-MM-yyyy style
                        } catch (ParseException e) {
                            logger.warn("Failed to parse In-Service Date '{}': {}", inserviceDateStr, e.getMessage());
                            inserviceDateCell.setCellValue(inserviceDateStr); // Fallback to string
                        }
                    } else {
                        inserviceDateCell.setCellValue("");
                    }


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
            workbook.dispose(); // Flush and close temp files for SXSSF
            workbook.close();

            // Set response headers and return
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("Content-Disposition", "attachment; filename=dcc_po_export_v2.xlsx");
            deferredResult.setResult(new ResponseEntity<>(baos.toByteArray(), responseHeaders, HttpStatus.OK));
            logger.info("Successfully exported Excel V2 file with {} parent records and {} total rows", groupedByDccRecordNo.size(), data.size());
        } catch (Exception ex) {
            logger.error("Error generating Excel V2 file", ex);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating Excel V2: " + ex.getMessage()).getBytes()));
        }
    }).exceptionally(throwable -> {
        logger.error("Error processing DCC PO export V2 request", throwable);
        deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error V2: " + throwable.getCause().getMessage()).getBytes()));
        return null;
    });

    return deferredResult;
}
}