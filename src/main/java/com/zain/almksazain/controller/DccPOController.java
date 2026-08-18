package com.zain.almksazain.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOLineItemDTO;
import com.zain.almksazain.DTO.DccPOParentDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.DccPORequestDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.model.ExportJob;
import com.zain.almksazain.repo.ExportJobRepository;
import com.zain.almksazain.serviceImplementors.*;
import com.zain.almksazain.serviceImplementors.DccPOService.DccPOFetchResult;
import com.zain.almksazain.serviceImplementors.DccPOServiceV2.DccPOFetchResultV2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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


    // Configuration constants for export optimization
    private static final int MAX_EXPORT_RECORDS = 250000; // Configurable limit
    private static final int MAX_CONCURRENT_EXPORTS = 3; // Prevent resource exhaustion
    private static final long EXPORT_TIMEOUT_MS = 120000L; // 2 minutes
    private static final int EXCEL_WINDOW_SIZE = 100; // SXSSF memory window

    // Throttling mechanism
    private final Semaphore exportSemaphore = new Semaphore(MAX_CONCURRENT_EXPORTS);

    @Autowired
    private DccPOService dccPOService;

    @Autowired
    private DccPOApproverService dccPOApproverService;

    @Autowired
    private DccPOExportService dccPOExportService;

    @Autowired
    private DccPOServiceV2 dccPOServiceV2;

    @Autowired
    private DccPOApproverExportService dccPOApproverExportService;

    @Autowired
    private ExportJobRepository exportJobRepository;

    @Value("${app.export.dir:/data/app/logs/ALM/Exports/}")
    private String exportDir;

    private static final int MAX_ROWS_PER_SHEET = 1_000_000;
    private static final long PROGRESS_UPDATE_EVERY_N_ROWS = 5_000;

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
                                        lineItem.setRegion(dto.getRegion());
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
                                        lineItem.setRegion(dto.getRegion());
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "dccRecordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            logger.info("DCC PO Filter request received with {} filters", filters.size());

            // Extract basic filter parameters
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

            // Build field filters map
            Map<String, String> fieldFilters = new HashMap<>();
            String[] filterableFields = {
                    "dccPoNumber", "newProjectName", "dccStatus", "dccAcceptanceType",
                    "vendorName", "vendorNumber", "createdByName", "createdBy",
                    "recordNo", "dccRecordNo", "approvalCount"
            };

            for (String field : filterableFields) {
                if (filters.containsKey(field) && !filters.get(field).toString().trim().isEmpty()) {
                    String value = filters.get(field).toString().trim();

                    // Skip supplierId if it's "0"
                    if ("supplierId".equals(field) && "0".equals(value)) {
                        continue;
                    }

                    // Map recordNo to dccRecordNo for consistency
                    if ("recordNo".equals(field)) {
                        fieldFilters.put("dccRecordNo", value);
                        logger.info("RecordNo filter: {}", value);
                    } else if ("createdBy".equals(field)) {
                        fieldFilters.put("createdByName", value);
                    } else {
                        fieldFilters.put(field, value);
                    }
                }
            }

            // Extract date range filters
            String createdDateStart = filters.containsKey("createdDateStart") ?
                    filters.get("createdDateStart").toString().trim() : "";
            String createdDateEnd = filters.containsKey("createdDateEnd") ?
                    filters.get("createdDateEnd").toString().trim() : "";
            String approvedDateStart = filters.containsKey("approvedDateStart") ?
                    filters.get("approvedDateStart").toString().trim() : "";
            String approvedDateEnd = filters.containsKey("approvedDateEnd") ?
                    filters.get("approvedDateEnd").toString().trim() : "";

            // Validate page/size
            page = Math.max(page, 1);
            size = Math.max(size, 1);

            logger.info("Calling service with fieldFilters: {}, dateRanges: [{} to {}, {} to {}]",
                    fieldFilters, createdDateStart, createdDateEnd, approvedDateStart, approvedDateEnd);

            // Call ENHANCED service method with all filters
            DccPOFetchResult result = dccPOService.getDccPOCombinedViewSyncWithFilters(
                    supplierId,
                    pendingApprovers,
                    page,
                    size,
                    columnName,
                    searchQuery,
                    false, // not exporting
                    operator,
                    fieldFilters,
                    createdDateStart,
                    createdDateEnd,
                    approvedDateStart,
                    approvedDateEnd);

            List<DccPOCombinedViewDTO> data = result.getData();
            Long totalFilteredRecords = result.getTotalFilteredRecords();

            logger.info("Service returned {} records, total filtered: {}", data.size(), totalFilteredRecords);

// NEW: Apply columnName/searchQuery filter for calculated fields (approvalCount, pendingApprovers)
            if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
                String columnLower = columnName.toLowerCase();

                // Check if filtering by calculated fields that aren't in database
                if (columnLower.equals("approvalcount") || columnLower.equals("approvalsrequired") ||
                        columnLower.equals("pendingapprover") || columnLower.equals("pendingapprovers")) {

                    logger.info("Applying in-memory filter for calculated field: {}", columnName);

                    data = data.stream()
                            .filter(dto -> {
                                String fieldValue = null;

                                // Get the field value based on column name
                                if (columnLower.equals("approvalcount") || columnLower.equals("approvalsrequired")) {
                                    fieldValue = dto.getApprovalCount() != null ? dto.getApprovalCount().toString() : null;
                                } else if (columnLower.equals("pendingapprover") || columnLower.equals("pendingapprovers")) {
                                    fieldValue = dto.getPendingApprovers();
                                }

                                // Apply operator-based matching
                                if (fieldValue == null) return false;

                                return matchesOperator(fieldValue, searchQuery, operator);
                            })
                            .collect(Collectors.toList());

                    logger.info("After calculated field filter: {} records", data.size());

                    // Recalculate total for pagination
                    totalFilteredRecords = (long) data.size();
                }
            }

            // Group by dccRecordNo
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

            // Transform into parent DTOs
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

            logger.info("Successfully filtered DCC PO data. {} parent records returned (page: {}/{}, total: {})",
                    parentDTOs.size(), page, (int) Math.ceil((double) totalFilteredRecords / size), totalFilteredRecords);

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
    @PostMapping(value = "/filter-approvers", produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterDccPOApprovers(
            @RequestBody Map<String, Object> filters,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "dccRecordNo") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            logger.info("DCC PO Approver Filter request received with {} filters", filters.size());

            // REQUIRED: Approver ID to fetch data for
            String approverIdParam = filters.containsKey("approverId") ?
                    filters.get("approverId").toString().trim() : "";

            if (approverIdParam.isEmpty()) {
                return new ResponseEntity<>(
                        Collections.singletonMap("message", "approverId is required"),
                        HttpStatus.BAD_REQUEST
                );
            }

            String supplierId = filters.containsKey("supplierId") ?
                    filters.get("supplierId").toString().trim() : "0";
            String operator = filters.containsKey("operator") ?
                    filters.get("operator").toString().trim() : "AND";

            Map<String, String> fieldFilters = new HashMap<>();
            String[] filterFields = {
                    "recordNo", "dccRecordNo", "dccId", "poId", "dccPoNumber", "poNumber",
                    "projectName", "newProjectName", "dccAcceptanceType", "acceptanceType",
                    "dccStatus", "status", "vendorName", "vendorNumber", "vendorEmail",
                    "dccEmail", "createdBy", "createdByName", "vendorComment", "vendorComments",
                    "approverComment", "approvalCount", "dccCurrency", "currency"
                    // NOTE: pendingApprovers is NOT in this array - handled separately below
            };

            for (String field : filterFields) {
                if (filters.containsKey(field) && !filters.get(field).toString().trim().isEmpty()) {
                    if ("supplierId".equals(field) && "0".equals(filters.get(field).toString().trim())) {
                        continue;
                    }
                    fieldFilters.put(field, filters.get(field).toString().trim());
                }
            }

            page = Math.max(page, 1);
            size = Math.max(size, 1);

            // Call service with approverIdParam (the ID, not the name)
            CompletableFuture<List<DccPOCombinedViewDTO>> future =
                    dccPOApproverExportService.getAllDccPOForApproverExportWithDirectFilters(
                            supplierId, approverIdParam, fieldFilters, operator);

            List<DccPOCombinedViewDTO> allData = future.get(120, TimeUnit.SECONDS);

            if (allData.isEmpty()) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("reports", Collections.emptyList());
                emptyResponse.put("currentPage", page);
                emptyResponse.put("totalItems", 0L);
                emptyResponse.put("totalPages", 0);
                emptyResponse.put("first", true);
                emptyResponse.put("last", true);
                emptyResponse.put("size", size);
                emptyResponse.put("sort", sortBy + "," + sortDir);
                return new ResponseEntity<>(emptyResponse, HttpStatus.OK);
            }

            List<DccPOCombinedViewDTO> filteredData = applyApproverPostFetchFilters(allData, filters);

            // Group first, then count parents
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = filteredData.stream()
                    .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

            // Count unique parents (not line items)
            Long totalFilteredRecords = (long) groupedByDccRecordNo.size();

            // Paginate at parent level
            List<Long> sortedRecordNos = groupedByDccRecordNo.keySet().stream()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            int offset = (page - 1) * size;
            int toIndex = Math.min(offset + size, sortedRecordNos.size());
            List<Long> paginatedRecordNos = offset < sortedRecordNos.size() ?
                    sortedRecordNos.subList(offset, toIndex) : Collections.emptyList();

            Map<Long, List<DccPOCombinedViewDTO>> paginatedGrouped = new LinkedHashMap<>();
            for (Long recordNo : paginatedRecordNos) {
                paginatedGrouped.put(recordNo, groupedByDccRecordNo.get(recordNo));
            }
            List<DccPOParentDTO> parentDTOs = paginatedGrouped.entrySet().stream()
                    .map(entry -> {
                        DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);
                        DccPOParentDTO parentDTO = new DccPOParentDTO();

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

                        parentDTO.setLineItems(new ArrayList<>());

                        return parentDTO;
                    })
//                    .sorted((a, b) -> b.getRecordNo().compareTo(a.getRecordNo()))
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("reports", parentDTOs);
            response.put("currentPage", page);
            response.put("totalItems", totalFilteredRecords);
            response.put("totalPages", (int) Math.ceil((double) totalFilteredRecords / size));
            response.put("first", page == 1);
            response.put("last", page * size >= totalFilteredRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            logger.info("Successfully filtered DCC PO approver data. {} parent records returned", parentDTOs.size());

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (TimeoutException e) {
            logger.error("Timeout filtering DCC PO approver data", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Request timeout - query took too long"),
                    HttpStatus.REQUEST_TIMEOUT
            );
        } catch (Exception e) {
            logger.error("Error filtering DCC PO approver data", e);
            return new ResponseEntity<>(
                    Collections.singletonMap("message", "Error filtering data: " + e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
    @PostMapping(value = "/export-combined-view", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public DeferredResult<ResponseEntity<byte[]>> exportDccPOCombinedViewToExcelV2(@RequestBody Map<String, Object> request) {
        DeferredResult<ResponseEntity<byte[]>> deferredResult = new DeferredResult<>(EXPORT_TIMEOUT_MS);

        // Check concurrent export limit
        if (!exportSemaphore.tryAcquire()) {
            logger.warn("Export request rejected - too many concurrent exports");
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many concurrent exports in progress. Please try again in a moment.".getBytes()));
            return deferredResult;
        }

        try {
            // Extract and validate parameters
            ExportParameters params = extractParameters(request);

            logger.info("Starting export with filters: {}", params.fieldFilters.keySet());

            // Fetch data with database-level filtering
            CompletableFuture<List<DccPOCombinedViewDTO>> future =
                    dccPOExportService.getAllDccPOForExportV2(
                            params.supplierId,
                            params.pendingApprovers,
                            params.columnName,
                            params.searchQuery,
                            params.operator,
                            params.fieldFilters,
                            params.createdDateStart,
                            params.createdDateEnd,
                            params.approvedDateStart,
                            params.approvedDateEnd,
                            MAX_EXPORT_RECORDS // Pass limit to service
                    );

            future.thenAccept(data -> {
                try {
                    processExportData(data, params, deferredResult);
                } finally {
                    exportSemaphore.release(); // Always release permit
                }
            }).exceptionally(throwable -> {
                exportSemaphore.release(); // Release on error
                handleExportError(throwable, deferredResult);
                return null;
            });

        } catch (Exception e) {
            exportSemaphore.release(); // Release on validation error
            logger.error("Error validating export request", e);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(("Invalid request: " + e.getMessage()).getBytes()));
        }

        return deferredResult;
    }

    // Extract and validate request parameters
    private ExportParameters extractParameters(Map<String, Object> request) {
        ExportParameters params = new ExportParameters();

        // Basic parameters
        params.supplierId = getStringParam(request, "supplierId", "0");
        params.pendingApprovers = getStringParam(request, "pendingApprovers", null);
        params.columnName = getStringParam(request, "columnName", null);
        params.searchQuery = getStringParam(request, "searchQuery", null);
        params.operator = getStringParam(request, "operator", null);

        // Build field filters
        params.fieldFilters = buildFieldFilters(request);

        // Date range filters (extract once, parse later if needed)
        params.createdDateStart = getStringParam(request, "createdDateStart", "");
        params.createdDateEnd = getStringParam(request, "createdDateEnd", "");
        params.approvedDateStart = getStringParam(request, "approvedDateStart", "");
        params.approvedDateEnd = getStringParam(request, "approvedDateEnd", "");

        return params;
    }

    // Build field filters from request
    private Map<String, String> buildFieldFilters(Map<String, Object> request) {
        Map<String, String> filters = new HashMap<>();

        String[] filterFields = {
                "dccPoNumber", "newProjectName", "dccStatus", "dccAcceptanceType",
                "vendorName", "vendorNumber", "createdByName", "createdBy",
                "recordNo", "dccRecordNo", "approvalCount"
        };

        for (String field : filterFields) {
            String value = getStringParam(request, field, null);
            if (value != null && !value.isEmpty()) {
                // Normalize field names
                if ("recordNo".equals(field)) {
                    filters.put("dccRecordNo", value);
                } else if ("createdBy".equals(field)) {
                    filters.put("createdByName", value);
                } else {
                    filters.put(field, value);
                }
            }
        }

        return filters;
    }

    // Process and export data
    private void processExportData(List<DccPOCombinedViewDTO> data,
                                   ExportParameters params,
                                   DeferredResult<ResponseEntity<byte[]>> deferredResult) {
        try {
            // Validate data size
            if (data.isEmpty()) {
                logger.warn("No data found for export with given filters");
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body("No data found matching the specified filters".getBytes()));
                return;
            }

            if (data.size() > MAX_EXPORT_RECORDS) {
                logger.warn("Export would return {} records, exceeding limit of {}",
                        data.size(), MAX_EXPORT_RECORDS);
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(String.format("Export would return %d records. Maximum allowed is %d. Please add more filters.",
                                data.size(), MAX_EXPORT_RECORDS).getBytes()));
                return;
            }

            logger.info("Processing {} records for export", data.size());

            // Single-pass sort and group (data already filtered by database)
            List<DccPOCombinedViewDTO> sortedData = data.stream()
                    .sorted(Comparator
                            .comparing(DccPOCombinedViewDTO::getDccRecordNo,
                                    Comparator.nullsLast(Comparator.reverseOrder())) // Request No DESC
                            .thenComparing(DccPOCombinedViewDTO::getApprovalCount,
                                    Comparator.nullsLast(Comparator.reverseOrder())) // Approval Count DESC
                            .thenComparing(DccPOCombinedViewDTO::getLineNumber,
                                    Comparator.nullsLast(Comparator.reverseOrder())) // Line Number DESC
                    )
                    .collect(Collectors.toList());


            // Group while preserving sort order
            Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = sortedData.stream()
                    .collect(Collectors.groupingBy(
                            DccPOCombinedViewDTO::getDccRecordNo,
                            LinkedHashMap::new,
                            Collectors.toList()));

            logger.info("Grouped into {} unique DCC records", groupedByDccRecordNo.size());

            // Generate Excel file
            byte[] excelBytes = generateExcelFile(groupedByDccRecordNo, sortedData.size());

            // Return response
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("Content-Disposition", "attachment; filename=dcc_po_export_v2.xlsx");
            responseHeaders.add("X-Total-Records", String.valueOf(sortedData.size()));
            responseHeaders.add("X-Total-Groups", String.valueOf(groupedByDccRecordNo.size()));

            deferredResult.setResult(new ResponseEntity<>(excelBytes, responseHeaders, HttpStatus.OK));

            logger.info("Successfully exported {} records in {} groups",
                    sortedData.size(), groupedByDccRecordNo.size());

        } catch (Exception ex) {
            logger.error("Error processing export data", ex);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating Excel: " + ex.getMessage()).getBytes()));
        }
    }

    // Generate Excel file
    private byte[] generateExcelFile(Map<Long, List<DccPOCombinedViewDTO>> groupedData,
                                     int totalRows) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(EXCEL_WINDOW_SIZE);
        try {
            Sheet sheet = workbook.createSheet("DCC PO Data");

            // Create reusable styles
            ExcelStyles styles = createExcelStyles(workbook);

            // Date formatter (reused throughout)
            SimpleDateFormat dateFormatter = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

            // Create header row
            createHeaderRow(sheet, styles);

            // Populate data rows
            int rowNum = 1;
            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : groupedData.entrySet()) {
                DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);

                for (DccPOCombinedViewDTO dto : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    populateDataRow(row, firstRecord, dto, styles, dateFormatter);
                }
            }

            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();

        } finally {
            workbook.dispose(); // Clean up temp files
            workbook.close();
        }
    }

    // Create Excel styles (reusable)
    private ExcelStyles createExcelStyles(SXSSFWorkbook workbook) {
        CreationHelper createHelper = workbook.getCreationHelper();

        // Header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.BLACK.getIndex());
        headerStyle.setFont(headerFont);

        // Date style
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MM-yyyy"));

        return new ExcelStyles(headerStyle, dateStyle);
    }

    // Create header row
    private void createHeaderRow(Sheet sheet, ExcelStyles styles) {
        Row headerRow = sheet.createRow(0);
        String[] columnHeaders = {
                "Request No", "PO Number", "Project Name", "Acceptance Type", "Status",
                "Created Date", "Approval Date", "Vendor", "Created By", "Approval Count",
                "Pending Approvers", "User Aging", "Total Aging", "Vendor Comment",
                "Last Approver Comment", "PO Line Number", "UPL Line Number", "Serial Number",
                "PO Item Code", "Actual Item Code", "UPL Item Code", "PO Acceptance Qty",
                "PO Line Description", "UPL Line Description", "PO Pending Qty",
                "Acceptance Qty", "Location", "Scope of Work", "In Service Date",
                "Link ID", "TAG Number", "Remarks"
        };

        for (int i = 0; i < columnHeaders.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnHeaders[i]);
            cell.setCellStyle(styles.headerStyle);
        }
    }

    // Populate a data row
    private void populateDataRow(Row row,
                                 DccPOCombinedViewDTO firstRecord,
                                 DccPOCombinedViewDTO dto,
                                 ExcelStyles styles,
                                 SimpleDateFormat dateFormatter) {
        int col = 0;

        // Parent fields from firstRecord
        setCellValue(row, col++, firstRecord.getDccRecordNo());
        setCellValue(row, col++, firstRecord.getDccPoNumber());
        setCellValue(row, col++, firstRecord.getProjectName());
        setCellValue(row, col++, firstRecord.getDccAcceptanceType());
        setCellValue(row, col++, firstRecord.getDccStatus());

        // Date fields
        col = setDateCell(row, col, firstRecord.getDccCreatedDate(), styles.dateStyle, dateFormatter);
        col = setDateCell(row, col, firstRecord.getDateApproved(), styles.dateStyle, dateFormatter);

        // More parent fields
        setCellValue(row, col++, firstRecord.getVendorName());
        setCellValue(row, col++, firstRecord.getCreatedBy());
        setCellValue(row, col++, firstRecord.getApprovalCount() != null ? firstRecord.getApprovalCount() : 0);
        setCellValue(row, col++, firstRecord.getPendingApprovers());
        setCellValue(row, col++, firstRecord.getUserAging());
        setCellValue(row, col++, firstRecord.getTotalAging());
        setCellValue(row, col++, firstRecord.getVendorComment());
        setCellValue(row, col++, firstRecord.getApproverComment());

        // Line item fields from dto
        setCellValue(row, col++, dto.getLineNumber());
        setCellValue(row, col++, dto.getUplLineNumber());
        setCellValue(row, col++, dto.getLnProductSerialNo());
        setCellValue(row, col++, dto.getItemPartNumber());
        setCellValue(row, col++, dto.getActualItemCode());
        setCellValue(row, col++, dto.getUplLineItemCode());
        setCellValue(row, col++, dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0);
        setCellValue(row, col++, dto.getPoLineDescription());
        setCellValue(row, col++, dto.getUplLineDescription());
        setCellValue(row, col++, dto.getPoPendingQuantity());
        setCellValue(row, col++, dto.getLnDeliveredQty());
        setCellValue(row, col++, dto.getLnLocationName());
        setCellValue(row, col++, dto.getLnScopeOfWork());

        // In-service date
        col = setDateCell(row, col, dto.getLnInserviceDate(), styles.dateStyle, dateFormatter);

        // Final fields
        setCellValue(row, col++, dto.getLinkId());
        setCellValue(row, col++, dto.getTagNumber());
        setCellValue(row, col++, dto.getLnRemarks());
    }

    // Helper: Set cell value (handles nulls and types)
    private void setCellValue(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    // Helper: Set date cell (parse and format)
    private int setDateCell(Row row, int col, String dateStr,
                            CellStyle dateStyle, SimpleDateFormat dateFormatter) {
        Cell cell = row.createCell(col);
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                Date date = dateFormatter.parse(dateStr);
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            } catch (ParseException e) {
                logger.warn("Failed to parse date '{}': {}", dateStr, e.getMessage());
                cell.setCellValue(dateStr); // Fallback to string
            }
        } else {
            cell.setCellValue("");
        }
        return col + 1;
    }

    // Helper: Get string parameter
    private String getStringParam(Map<String, Object> request, String key, String defaultValue) {
        Object value = request.get(key);
        if (value == null) return defaultValue;
        String str = value.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    // Handle export errors
    private void handleExportError(Throwable throwable,
                                   DeferredResult<ResponseEntity<byte[]>> deferredResult) {
        logger.error("Error processing export request", throwable);
        String errorMessage = throwable.getCause() != null ?
                throwable.getCause().getMessage() : throwable.getMessage();
        deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Export failed: " + errorMessage).getBytes()));
    }

    // Helper classes
    private static class ExportParameters {
        String supplierId;
        String pendingApprovers;
        String columnName;
        String searchQuery;
        String operator;
        Map<String, String> fieldFilters;
        String createdDateStart;
        String createdDateEnd;
        String approvedDateStart;
        String approvedDateEnd;
    }

    private static class ExcelStyles {
        final CellStyle headerStyle;
        final CellStyle dateStyle;

        ExcelStyles(CellStyle headerStyle, CellStyle dateStyle) {
            this.headerStyle = headerStyle;
            this.dateStyle = dateStyle;
        }
    }

    // ─── /export-combined-view-approvers — job-based (start/status/download) ──
    // Mirrors DccPOV2Controller's /combined-view/export pattern: returns a jobId
    // immediately, builds the workbook on a background thread (surviving the
    // originating tab), and persists the finished file to disk keyed by jobId.

    private static final String[] APPROVER_EXPORT_HEADERS = {
            "Request No", "PO Number", "Project Name", "Acceptance Type", "Status",
            "Created Date", "Approval Date", "Vendor", "Created By", "Approval Count",
            "Pending Approvers", "User Aging", "Total Aging", "Vendor Comment",
            "Last Approver Comment", "PO Line Number", "UPL Line Number", "Serial Number",
            "PO Item Code", "Actual Item Code", "UPL Item Code", "PO Acceptance Qty",
            "PO Line Description", "UPL Line Description", "PO Pending Qty",
            "Acceptance Qty", "Location", "Scope of Work", "In Service Date",
            "Link ID", "TAG Number", "Remarks"
    };

    @PostMapping("/export-combined-view-approvers")
    public ResponseEntity<?> startExportDccPOCombinedViewApproversToExcel(@RequestBody Map<String, Object> request) {
        // Prefer "approverId" (the key used by this tab's other two endpoints,
        // /combined-view-approvers and /filter-approvers) for the REQUIRED id of whose
        // action-history queue this export is for. Falls back to "pendingApprovers" for
        // backward compatibility, but "approverId" must win when both are present: the
        // dialog's "Pending Approvers" column filter is also named "pendingApprovers" and
        // gets spread into this same payload, so that key can legitimately hold a search
        // string (an approver's name) rather than the requesting user's id.
        Object approverIdObj = request.get("approverId") != null
                ? request.get("approverId") : request.get("pendingApprovers");
        String pendingApprovers = (approverIdObj != null)
                ? approverIdObj.toString().trim()
                : "";

        if (pendingApprovers.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Approver ID is required");
        }

        if (!exportSemaphore.tryAcquire()) {
            logger.warn("Approver export rejected — semaphore full");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many concurrent exports. Please try again shortly.");
        }

        String jobId = UUID.randomUUID().toString();
        ExportJob job = new ExportJob();
        job.setJobId(jobId);
        job.setReportType("actionedRequests");
        job.setStatus(ExportJob.STATUS_PENDING);
        job.setRowsWritten(0);
        job.setSheetCount(0);
        job.setCreatedAt(LocalDateTime.now());
        exportJobRepository.save(job);

        CompletableFuture.runAsync(() -> runApproverExportJob(jobId, request, pendingApprovers));

        Map<String, String> resp = new HashMap<>();
        resp.put("jobId", jobId);
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/export-combined-view-approvers/{jobId}/status")
    public ResponseEntity<?> getExportDccPOCombinedViewApproversStatus(@PathVariable String jobId) {
        Optional<ExportJob> jobOpt = exportJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExportJob job = jobOpt.get();
        Map<String, Object> resp = new HashMap<>();
        resp.put("jobId", job.getJobId());
        resp.put("status", job.getStatus());
        resp.put("rowsWritten", job.getRowsWritten());
        resp.put("sheetCount", job.getSheetCount());
        resp.put("fileName", job.getFileName());
        resp.put("errorMessage", job.getErrorMessage());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/export-combined-view-approvers/{jobId}/download")
    public ResponseEntity<?> downloadExportDccPOCombinedViewApprovers(@PathVariable String jobId) {
        Optional<ExportJob> jobOpt = exportJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExportJob job = jobOpt.get();
        if (!ExportJob.STATUS_DONE.equals(job.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Export not ready yet, status=" + job.getStatus());
        }
        File file = new File(job.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.GONE).body("Export file no longer available");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.add("Content-Disposition", "attachment; filename=" + job.getFileName());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
    }

    /**
     * Post-fetch filters shared by /filter-approvers (grid) and the approver export job.
     * These fields aren't DCC-table columns (pendingApprovers/userAging/totalAging are
     * computed, not persisted) so they can't go through the fieldFilters DB-level map above -
     * they're applied in memory here instead. Kept as one shared method specifically so the
     * grid's filtered view and its export can never drift apart on what a filter means.
     */
    private List<DccPOCombinedViewDTO> applyApproverPostFetchFilters(
            List<DccPOCombinedViewDTO> allData, Map<String, Object> filters) {

        String pendingApproversFilter = filters.containsKey("pendingApprovers") ?
                filters.get("pendingApprovers").toString().trim() : "";
        String dateApproved = filters.containsKey("dateApproved") ?
                filters.get("dateApproved").toString().trim() : "";
        String dccCreatedDate = filters.containsKey("dccCreatedDate") ?
                filters.get("dccCreatedDate").toString().trim() : "";
        String createdDateStart = filters.containsKey("createdDateStart") ?
                filters.get("createdDateStart").toString().trim() : "";
        String createdDateEnd = filters.containsKey("createdDateEnd") ?
                filters.get("createdDateEnd").toString().trim() : "";
        String approvedDateStart = filters.containsKey("approvedDateStart") ?
                filters.get("approvedDateStart").toString().trim() : "";
        String approvedDateEnd = filters.containsKey("approvedDateEnd") ?
                filters.get("approvedDateEnd").toString().trim() : "";
        String userAging = filters.containsKey("userAging") ?
                filters.get("userAging").toString().trim() : "";
        String totalAging = filters.containsKey("totalAging") ?
                filters.get("totalAging").toString().trim() : "";

        SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

        return allData.stream()
                .filter(dto -> {
                    try {
                        // Filter by pendingApprovers NAME (if provided)
                        if (!pendingApproversFilter.isEmpty()) {
                            if (dto.getPendingApprovers() == null ||
                                    !dto.getPendingApprovers().toLowerCase()
                                            .contains(pendingApproversFilter.toLowerCase())) {
                                return false;
                            }
                        }

                        if (!dateApproved.isEmpty()) {
                            if (dto.getDateApproved() == null ||
                                    !dto.getDateApproved().equals(dateApproved)) {
                                return false;
                            }
                        }

                        if (!dccCreatedDate.isEmpty()) {
                            if (dto.getDccCreatedDate() == null ||
                                    !dto.getDccCreatedDate().equals(dccCreatedDate)) {
                                return false;
                            }
                        }

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

                        if (!userAging.isEmpty()) {
                            if (dto.getUserAging() == null ||
                                    !dto.getUserAging().equals(userAging)) {
                                return false;
                            }
                        }

                        if (!totalAging.isEmpty()) {
                            if (dto.getTotalAging() == null ||
                                    !dto.getTotalAging().equals(totalAging)) {
                                return false;
                            }
                        }

                        return true;
                    } catch (ParseException e) {
                        logger.warn("Error parsing dates for filtering", e);
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }

    /** True if the request narrows the result set beyond the approver's default action-history
     *  scope (approverId itself is implicit, not a filter the user chose to apply). */
    private boolean approverExportHasFilters(Map<String, Object> request,
                                             Map<String, String> fieldFilters, String supplierId) {
        if (!fieldFilters.isEmpty()) return true;
        if (supplierId != null && !supplierId.trim().isEmpty() && !"0".equals(supplierId.trim())) return true;
        String[] postFetchFilterKeys = {
                "pendingApprovers", "dateApproved", "dccCreatedDate",
                "createdDateStart", "createdDateEnd", "approvedDateStart", "approvedDateEnd",
                "userAging", "totalAging"
        };
        for (String key : postFetchFilterKeys) {
            if (request.containsKey(key) && request.get(key) != null
                    && !request.get(key).toString().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void runApproverExportJob(String jobId, Map<String, Object> request, String pendingApprovers) {
        ExportJob job = exportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            logger.error("Approver export job {} disappeared before it could start", jobId);
            exportSemaphore.release();
            return;
        }
        job.setStatus(ExportJob.STATUS_RUNNING);
        exportJobRepository.save(job);

        try {
            Object supplierIdObj = request.get("supplierId");
            String supplierId = (supplierIdObj != null) ? supplierIdObj.toString().trim() : "0";
            Object operatorObj = request.get("operator");
            String operator = (operatorObj != null) ? operatorObj.toString().trim() : "AND";

            Map<String, String> fieldFilters = new HashMap<>();
            String[] filterFields = {
                    "recordNo", "dccRecordNo", "dccId", "poId", "dccPoNumber", "poNumber",
                    "projectName", "newProjectName", "dccAcceptanceType", "acceptanceType",
                    "dccStatus", "status", "vendorName", "vendorNumber", "vendorEmail",
                    "dccEmail", "createdBy", "createdByName", "vendorComment", "vendorComments",
                    "approverComment", "approvalCount", "dccCurrency", "currency"
            };
            for (String field : filterFields) {
                if (request.containsKey(field) && !request.get(field).toString().trim().isEmpty()) {
                    fieldFilters.put(field, request.get(field).toString().trim());
                }
            }

            if (request.containsKey("columnName") && request.containsKey("searchQuery")) {
                Object columnNameObj = request.get("columnName");
                Object searchQueryObj = request.get("searchQuery");
                if (columnNameObj != null && searchQueryObj != null) {
                    String columnName = columnNameObj.toString().trim();
                    String searchQuery = searchQueryObj.toString().trim();
                    if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
                        String[] columns = columnName.split(",");
                        String[] values = searchQuery.split(",");
                        int minLength = Math.min(columns.length, values.length);
                        for (int i = 0; i < minLength; i++) {
                            if (!columns[i].trim().isEmpty() && !values[i].trim().isEmpty()) {
                                fieldFilters.put(columns[i].trim(), values[i].trim());
                            }
                        }
                    }
                }
            }

            logger.info("Approver export job {} filters - approverId: {}, fieldFilters: {}, operator: {}",
                    jobId, pendingApprovers, fieldFilters, operator);

            List<DccPOCombinedViewDTO> data = dccPOApproverExportService
                    .getAllDccPOForApproverExportWithDirectFilters(supplierId, pendingApprovers, fieldFilters, operator)
                    .get(EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Same post-fetch filters (pendingApprovers name-contains, date ranges, exact-match
            // aging strings) that /filter-approvers applies to the grid's filtered view - these
            // aren't DCC-table columns so they can't go through fieldFilters above. Without this,
            // filters visible in the on-screen grid would silently not apply to the export.
            data = applyApproverPostFetchFilters(data, request);

            if (data.isEmpty()) {
                logger.warn("No data found for approver export job {} (approver {})", jobId, pendingApprovers);
                job.setStatus(ExportJob.STATUS_FAILED);
                job.setErrorMessage("No data found for the specified approver with given filters.");
                job.setCompletedAt(LocalDateTime.now());
                exportJobRepository.save(job);
                return;
            }

            File dir = new File(exportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String storedFileName = "dcc_po_approver_export_" + pendingApprovers + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + "_" + jobId.substring(0, 8) + ".xlsx";
            File outFile = new File(dir, storedFileName);

            int sheetCount = buildApproverExcelToFile(data, outFile, job);

            LocalDateTime completedAt = LocalDateTime.now();
            job.setStatus(ExportJob.STATUS_DONE);
            String filterTag = approverExportHasFilters(request, fieldFilters, supplierId) ? "_FILTERED" : "";
            job.setFileName("ACTIONED_REQUESTS" + filterTag + "_"
                    + completedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".xlsx");
            job.setFilePath(outFile.getAbsolutePath());
            job.setRowsWritten(data.size());
            job.setSheetCount(sheetCount);
            job.setCompletedAt(completedAt);
            exportJobRepository.save(job);
            logger.info("Approver export job {} complete — {} rows, {} sheet(s)", jobId, data.size(), sheetCount);

        } catch (Exception ex) {
            logger.error("Approver export job {} failed", jobId, ex);
            job.setStatus(ExportJob.STATUS_FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
        } finally {
            exportSemaphore.release();
        }
    }

    private String approverSheetName(int sheetNumber) {
        return sheetNumber == 1 ? "DCC PO Approver Data" : "DCC PO Approver Data (" + sheetNumber + ")";
    }

    private void writeApproverHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < APPROVER_EXPORT_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(APPROVER_EXPORT_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /** Writes the workbook straight to disk and reports progress on the given job as it goes,
     *  rolling over to a new sheet every MAX_ROWS_PER_SHEET rows. Returns the final sheet count. */
    private int buildApproverExcelToFile(List<DccPOCombinedViewDTO> data, File outFile, ExportJob job) throws Exception {
        // Data is already sorted by recordNo DESC from service
        Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo,
                        LinkedHashMap::new, Collectors.toList()));

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(EXCEL_WINDOW_SIZE)) {
            CreationHelper createHelper = workbook.getCreationHelper();
            SimpleDateFormat dateFormatter = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

            CellStyle dateOnlyStyle = workbook.createCellStyle();
            dateOnlyStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MM-yyyy"));

            // Excel's default "General" format only displays ~11 significant digits, silently
            // rounding near-whole values (e.g. a corrupted 4.000000000000003 delivered qty) to
            // a clean "4" on screen even though the stored cell value is unchanged. "#"
            // placeholders (vs "0") don't force trailing zeros, so genuine whole numbers still
            // show cleanly (80 not 80.0000...) while real fractional precision shows in full.
            CellStyle preciseQtyStyle = workbook.createCellStyle();
            preciseQtyStyle.setDataFormat(createHelper.createDataFormat().getFormat("0.####################"));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.BLACK.getIndex());
            headerStyle.setFont(headerFont);

            int sheetCount = 1;
            Sheet sheet = workbook.createSheet(approverSheetName(sheetCount));
            writeApproverHeaderRow(sheet, headerStyle);

            int rowNum = 1;
            long written = 0;
            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : groupedByDccRecordNo.entrySet()) {
                DccPOCombinedViewDTO firstRecord = entry.getValue().get(0);

                for (DccPOCombinedViewDTO dto : entry.getValue()) {
                    if (rowNum > MAX_ROWS_PER_SHEET) {
                        sheetCount++;
                        sheet = workbook.createSheet(approverSheetName(sheetCount));
                        writeApproverHeaderRow(sheet, headerStyle);
                        rowNum = 1;
                    }

                    Row row = sheet.createRow(rowNum++);
                    int col = 0;

                    row.createCell(col++).setCellValue(firstRecord.getDccRecordNo() != null ?
                            firstRecord.getDccRecordNo().toString() : "");
                    row.createCell(col++).setCellValue(firstRecord.getDccPoNumber() != null ?
                            firstRecord.getDccPoNumber() : "");
                    row.createCell(col++).setCellValue(firstRecord.getProjectName() != null ?
                            firstRecord.getProjectName() : "");
                    row.createCell(col++).setCellValue(firstRecord.getDccAcceptanceType() != null ?
                            firstRecord.getDccAcceptanceType() : "");
                    row.createCell(col++).setCellValue(firstRecord.getDccStatus() != null ?
                            firstRecord.getDccStatus() : "");

                    Cell createdDateCell = row.createCell(col++);
                    String dccCreatedDateStr = firstRecord.getDccCreatedDate();
                    if (dccCreatedDateStr != null && !dccCreatedDateStr.isEmpty()) {
                        try {
                            Date dccCreatedDate = dateFormatter.parse(dccCreatedDateStr);
                            createdDateCell.setCellValue(dccCreatedDate);
                            createdDateCell.setCellStyle(dateOnlyStyle);
                        } catch (ParseException e) {
                            createdDateCell.setCellValue(dccCreatedDateStr);
                        }
                    } else {
                        createdDateCell.setCellValue("");
                    }

                    Cell dateApprovedCell = row.createCell(col++);
                    String dateApprovedStr = firstRecord.getDateApproved();
                    if (dateApprovedStr != null && !dateApprovedStr.isEmpty()) {
                        try {
                            Date dateApproved = dateFormatter.parse(dateApprovedStr);
                            dateApprovedCell.setCellValue(dateApproved);
                            dateApprovedCell.setCellStyle(dateOnlyStyle);
                        } catch (ParseException e) {
                            dateApprovedCell.setCellValue(dateApprovedStr);
                        }
                    } else {
                        dateApprovedCell.setCellValue("");
                    }

                    row.createCell(col++).setCellValue(firstRecord.getVendorName() != null ?
                            firstRecord.getVendorName() : "");
                    row.createCell(col++).setCellValue(firstRecord.getCreatedBy() != null ?
                            firstRecord.getCreatedBy() : "");
                    row.createCell(col++).setCellValue(firstRecord.getApprovalCount() != null ?
                            firstRecord.getApprovalCount() : 0);
                    row.createCell(col++).setCellValue(firstRecord.getPendingApprovers() != null ?
                            firstRecord.getPendingApprovers() : "");
                    row.createCell(col++).setCellValue(firstRecord.getUserAging() != null ?
                            firstRecord.getUserAging() : "0 days 0 hrs 0 mins");
                    row.createCell(col++).setCellValue(firstRecord.getTotalAging() != null ?
                            firstRecord.getTotalAging() : "0 days 0 hrs 0 mins");
                    row.createCell(col++).setCellValue(firstRecord.getVendorComment() != null ?
                            firstRecord.getVendorComment() : "");
                    row.createCell(col++).setCellValue(firstRecord.getApproverComment() != null ?
                            firstRecord.getApproverComment() : "");

                    row.createCell(col++).setCellValue(dto.getLineNumber() != null ? dto.getLineNumber() : "");
                    row.createCell(col++).setCellValue(dto.getUplLineNumber() != null ? dto.getUplLineNumber() : "");
                    row.createCell(col++).setCellValue(dto.getLnProductSerialNo() != null ? dto.getLnProductSerialNo() : "");
                    row.createCell(col++).setCellValue(dto.getItemPartNumber() != null ? dto.getItemPartNumber() : "");
                    row.createCell(col++).setCellValue(dto.getActualItemCode() != null ? dto.getActualItemCode() : "");
                    row.createCell(col++).setCellValue(dto.getUplLineItemCode() != null ? dto.getUplLineItemCode() : "");
                    Cell poAcceptanceQtyCell = row.createCell(col++);
                    poAcceptanceQtyCell.setCellValue(dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0);
                    poAcceptanceQtyCell.setCellStyle(preciseQtyStyle);

                    row.createCell(col++).setCellValue(dto.getPoLineDescription() != null ? dto.getPoLineDescription() : "");
                    row.createCell(col++).setCellValue(dto.getUplLineDescription() != null ? dto.getUplLineDescription() : "");

                    Cell poPendingQtyCell = row.createCell(col++);
                    poPendingQtyCell.setCellValue(dto.getPoPendingQuantity() != null ? dto.getPoPendingQuantity() : 0.0);
                    poPendingQtyCell.setCellStyle(preciseQtyStyle);

                    // "Acceptance Qty" is the per-line delivered quantity (getLnDeliveredQty), matching
                    // the correct Requests-tab exporter (DccPOV2Controller.buildExcelToFile) — not the
                    // PO/UPL line's ordered quantity, which is constant across every line item sharing
                    // that PO Line + UPL Line.
                    Cell acceptanceQtyCell = row.createCell(col++);
                    acceptanceQtyCell.setCellValue(dto.getLnDeliveredQty() != null ? dto.getLnDeliveredQty() : 0.0);
                    acceptanceQtyCell.setCellStyle(preciseQtyStyle);
                    row.createCell(col++).setCellValue(dto.getLnLocationName() != null ? dto.getLnLocationName() : "");
                    row.createCell(col++).setCellValue(dto.getLnScopeOfWork() != null ? dto.getLnScopeOfWork() : "");

                    Cell inserviceDateCell = row.createCell(col++);
                    String inserviceDateStr = dto.getLnInserviceDate();
                    if (inserviceDateStr != null && !inserviceDateStr.isEmpty()) {
                        try {
                            Date inserviceDate = dateFormatter.parse(inserviceDateStr);
                            inserviceDateCell.setCellValue(inserviceDate);
                            inserviceDateCell.setCellStyle(dateOnlyStyle);
                        } catch (ParseException e) {
                            inserviceDateCell.setCellValue(inserviceDateStr);
                        }
                    } else {
                        inserviceDateCell.setCellValue("");
                    }

                    row.createCell(col++).setCellValue(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
                    row.createCell(col++).setCellValue(dto.getTagNumber() != null ? dto.getTagNumber() : "");
                    row.createCell(col++).setCellValue(dto.getLnRemarks() != null ? dto.getLnRemarks() : "");

                    written++;
                    if (written % PROGRESS_UPDATE_EVERY_N_ROWS == 0) {
                        job.setRowsWritten(written);
                        job.setSheetCount(sheetCount);
                        exportJobRepository.save(job);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                workbook.write(fos);
            }
            workbook.dispose();
            return sheetCount;
        }
    }
    private boolean matchesOperator(String fieldValue, String searchQuery, String operator) {
        if (fieldValue == null || searchQuery == null) return false;

        String fieldLower = fieldValue.toLowerCase();
        String searchLower = searchQuery.toLowerCase();

        switch (operator != null ? operator.toLowerCase() : "contains") {
            case "equals":
                return fieldLower.equals(searchLower);
            case "startswith":
                return fieldLower.startsWith(searchLower);
            case "endswith":
                return fieldLower.endsWith(searchLower);
            case "contains":
            default:
                return fieldLower.contains(searchLower);
        }
    }
}