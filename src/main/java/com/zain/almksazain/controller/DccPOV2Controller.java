package com.zain.almksazain.controller;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.request.DccPORequest;
import com.zain.almksazain.model.ExportJob;
import com.zain.almksazain.repo.ExportJobRepository;
import com.zain.almksazain.service.DccPOV2Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

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
import java.util.stream.Collectors;

/**
 * Unified DCC PO controller.
 *
 * POST /dcc-po/v2/combined-view          — paginated view (all filters)
 * POST /dcc-po/v2/export-combined-view   — file download (excel | csv)
 *
 * ─── PAYLOAD EXAMPLES ────────────────────────────────────────────────────────
 
 * 1. Combined — pending approver + multi-column + date range:
 * {
 *   "supplierId": "0",
 *   "page": 1,
 *   "size": 100,
 *   "pendingApprovers": "Jayakrishnan.Kappat",
 *   "filterBy": [
 *     { "column": "status", "operator": "EQUALS", "value": "pending" }
 *   ],
 *   "createdDateStart": "1-Jan-2025",
 *   "createdDateEnd":   "31-Dec-2025"
 * }
 *
 * 2. Export as Excel (default):
 * {
 *   "supplierId": "0",
 *   "pendingApprovers": "Jayakrishnan.Kappat",
 *   "exportFormat": "excel"
 * }
 *
 * 7. Export as CSV:
 * {
 *   "supplierId": "0",
 *   "filterBy": [
 *     { "column": "status", "operator": "EQUALS", "value": "pending" }
 *   ],
 *   "exportFormat": "csv"
 * }
 * ─────────────────────────────────────────────────────────────────────────────
 */
@RestController
@RequestMapping("/dcc-po/v2/")
public class DccPOV2Controller {

    private static final Logger logger = LogManager.getLogger(DccPOV2Controller.class);

    private static final long TIMEOUT_MS         = 120_000L;
    private static final long EXPORT_TIMEOUT_MS  = 300_000L;
    private static final int  MAX_CONCURRENT_EXPORTS = 3;
    private static final int  EXCEL_WINDOW_SIZE  = 100;
    private static final int  MAX_ROWS_PER_SHEET = 1_000_000;
    private static final long PROGRESS_UPDATE_EVERY_N_ROWS = 5_000;

    // Column headers shared by Excel and CSV builders
    private static final String[] HEADERS = {
        "Request No", "PO Number", "Project Name", "Acceptance Type", "Status",
        "Created Date", "Approval Date", "Vendor", "Created By", "Approval Count",
        "Pending Approvers", "User Aging", "Total Aging", "Vendor Comment",
        "Last Approver Comment", "PO Line Number", "UPL Line Number", "Serial Number",
        "PO Item Code", "Actual Item Code", "UPL Item Code", "PO Acceptance Qty",
        "PO Line Description", "UPL Line Description", "PO Pending Qty",
        "Acceptance Qty", "Location", "Scope of Work", "In Service Date",
        "Link ID", "TAG Number", "Remarks"
    };

    private final Semaphore exportSemaphore = new Semaphore(MAX_CONCURRENT_EXPORTS);

    @Autowired private DccPOV2Service service;
    @Autowired private ExportJobRepository exportJobRepository;

    @Value("${app.export.dir:/data/app/logs/ALM/Exports/}")
    private String exportDir;

    // ─── /combined-view ───────────────────────────────────────────────────────

    @PostMapping("/combined-view")
    public DeferredResult<ResponseEntity<DccPOResponseDTO>> combinedView(
            @RequestBody DccPORequest request) {

        DeferredResult<ResponseEntity<DccPOResponseDTO>> result =
                new DeferredResult<>(TIMEOUT_MS);

        service.getCombinedView(request)
                .thenAccept(response -> {
                    logger.info("combined-view — {} parents (page={}, size={})",
                            response.getData() != null ? response.getData().size() : 0,
                            request.getPage(), request.getSize());
                    result.setResult(ResponseEntity.ok(response));
                })
                .exceptionally(ex -> {
                    logger.error("combined-view error", ex);
                    result.setErrorResult(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                    return null;
                });

        return result;
    }

    // ─── /export-combined-view — job-based (start/status/download) ────────────
    // Mirrors the acceptance-report export pattern: the request returns a jobId
    // immediately, the actual fetch+build+write happens in the background and
    // survives the originating browser tab, and the finished file is persisted
    // to disk keyed by jobId so it can be downloaded whenever the user comes
    // back for it. CSV support is dropped here - Excel only, per the Requests
    // tab now always requesting exportFormat "xlsx" from the frontend.

    @PostMapping("/combined-view/export")
    public ResponseEntity<?> startExportCombinedView(@RequestBody DccPORequest request) {
        if (!exportSemaphore.tryAcquire()) {
            logger.warn("Export rejected — semaphore full");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many concurrent exports. Please try again shortly.");
        }

        String jobId = UUID.randomUUID().toString();
        ExportJob job = new ExportJob();
        job.setJobId(jobId);
        job.setReportType("acceptanceRequests");
        job.setStatus(ExportJob.STATUS_PENDING);
        job.setRowsWritten(0);
        job.setSheetCount(0);
        job.setCreatedAt(LocalDateTime.now());
        exportJobRepository.save(job);

        CompletableFuture.runAsync(() -> runCombinedViewExportJob(jobId, request));

        Map<String, String> resp = new HashMap<>();
        resp.put("jobId", jobId);
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/combined-view/export/{jobId}/status")
    public ResponseEntity<?> getExportCombinedViewStatus(@PathVariable String jobId) {
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

    @GetMapping("/combined-view/export/{jobId}/download")
    public ResponseEntity<?> downloadExportCombinedView(@PathVariable String jobId) {
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

    private void runCombinedViewExportJob(String jobId, DccPORequest request) {
        ExportJob job = exportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            logger.error("Export job {} disappeared before it could start", jobId);
            exportSemaphore.release();
            return;
        }
        job.setStatus(ExportJob.STATUS_RUNNING);
        exportJobRepository.save(job);

        try {
            List<DccPOCombinedViewDTO> rows =
                    service.getExportData(request).get(EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (rows.isEmpty()) {
                job.setStatus(ExportJob.STATUS_FAILED);
                job.setErrorMessage("No data found for the given filters.");
                job.setCompletedAt(LocalDateTime.now());
                exportJobRepository.save(job);
                return;
            }

            File dir = new File(exportDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String storedFileName = "dcc_po_export_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + "_" + jobId.substring(0, 8) + ".xlsx";
            File outFile = new File(dir, storedFileName);

            int sheetCount = buildExcelToFile(rows, outFile, job);

            LocalDateTime completedAt = LocalDateTime.now();
            job.setStatus(ExportJob.STATUS_DONE);
            String filterTag = combinedViewHasFilters(request) ? "_FILTERED" : "";
            job.setFileName("ACCEPTANCE_REQUESTS" + filterTag + "_"
                    + completedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".xlsx");
            job.setFilePath(outFile.getAbsolutePath());
            job.setRowsWritten(rows.size());
            job.setSheetCount(sheetCount);
            job.setCompletedAt(completedAt);
            exportJobRepository.save(job);
            logger.info("Export job {} complete — {} rows, {} sheet(s)", jobId, rows.size(), sheetCount);

        } catch (Exception ex) {
            logger.error("Export job {} failed", jobId, ex);
            job.setStatus(ExportJob.STATUS_FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
        } finally {
            exportSemaphore.release();
        }
    }

    /** True if the request narrows the result set beyond the current user's default scope
     *  (approver/supplier restriction is implicit, not a filter the user chose to apply). */
    private boolean combinedViewHasFilters(DccPORequest request) {
        if (request.getFilterBy() != null && !request.getFilterBy().isEmpty()) return true;
        if (hasValue(request.getColumnName()) && hasValue(request.getSearchQuery())) return true;
        if (hasValue(request.getCreatedDateStart()) || hasValue(request.getCreatedDateEnd())) return true;
        if (hasValue(request.getApprovedDateStart()) || hasValue(request.getApprovedDateEnd())) return true;
        return hasValue(request.getSupplierId()) && !"0".equals(request.getSupplierId().trim());
    }

    private boolean hasValue(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ─── EXCEL BUILDER ────────────────────────────────────────────────────────

    private String sheetName(int sheetNumber) {
        return sheetNumber == 1 ? "DCC PO Data" : "DCC PO Data (" + sheetNumber + ")";
    }

    /** Writes the workbook straight to disk and reports progress on the given job as it goes,
     *  rolling over to a new sheet every MAX_ROWS_PER_SHEET rows. Returns the final sheet count. */
    private int buildExcelToFile(List<DccPOCombinedViewDTO> rows, File outFile, ExportJob job) throws Exception {
        List<DccPOCombinedViewDTO> sorted = rows.stream()
                .sorted(Comparator.comparing(DccPOCombinedViewDTO::getDccRecordNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<Long, List<DccPOCombinedViewDTO>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo,
                        LinkedHashMap::new, Collectors.toList()));

        SimpleDateFormat dateFmt = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(EXCEL_WINDOW_SIZE)) {
            CreationHelper ch = wb.getCreationHelper();

            CellStyle hdrStyle = wb.createCellStyle();
            Font hdrFont = wb.createFont();
            hdrFont.setBold(true);
            hdrStyle.setFont(hdrFont);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(ch.createDataFormat().getFormat("dd-MM-yyyy"));

            // Excel's default "General" format only displays ~11 significant digits, silently
            // rounding near-whole values (e.g. a corrupted 4.000000000000003 delivered qty) to a
            // clean "4" on screen even though the stored cell value is unchanged. "#" placeholders
            // (vs "0") don't force trailing zeros, so genuine whole numbers still show cleanly
            // (80 not 80.0000...) while values with real fractional precision show it in full.
            CellStyle preciseQtyStyle = wb.createCellStyle();
            preciseQtyStyle.setDataFormat(ch.createDataFormat().getFormat("0.####################"));

            int sheetCount = 1;
            Sheet sheet = wb.createSheet(sheetName(sheetCount));
            writeHeaderRow(sheet, hdrStyle);

            int rowNum = 1;
            long written = 0;
            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : grouped.entrySet()) {
                DccPOCombinedViewDTO first = entry.getValue().get(0);
                for (DccPOCombinedViewDTO dto : entry.getValue()) {
                    if (rowNum > MAX_ROWS_PER_SHEET) {
                        sheetCount++;
                        sheet = wb.createSheet(sheetName(sheetCount));
                        writeHeaderRow(sheet, hdrStyle);
                        rowNum = 1;
                    }

                    Row row = sheet.createRow(rowNum++);
                    int col = 0;

                    // Parent-level
                    setCell(row, col++, first.getDccRecordNo());
                    setCell(row, col++, first.getDccPoNumber());
                    setCell(row, col++, first.getProjectName());
                    setCell(row, col++, first.getDccAcceptanceType());
                    setCell(row, col++, first.getDccStatus());
                    col = setDateCell(row, col, first.getDccCreatedDate(), dateStyle, dateFmt);
                    col = setDateCell(row, col, first.getDateApproved(),   dateStyle, dateFmt);
                    setCell(row, col++, first.getVendorName());
                    setCell(row, col++, first.getCreatedBy());
                    setCell(row, col++, first.getApprovalCount() != null ? first.getApprovalCount() : 0);
                    setCell(row, col++, first.getPendingApprovers());
                    setCell(row, col++, first.getUserAging());
                    setCell(row, col++, first.getTotalAging());
                    setCell(row, col++, first.getVendorComment());
                    setCell(row, col++, first.getApproverComment());

                    // Line-level
                    setCell(row, col++, dto.getLineNumber());
                    setCell(row, col++, dto.getUplLineNumber());
                    setCell(row, col++, dto.getLnProductSerialNo());
                    setCell(row, col++, dto.getItemPartNumber());
                    setCell(row, col++, dto.getActualItemCode());
                    setCell(row, col++, dto.getUplLineItemCode());
                    setCell(row, col++, dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0, preciseQtyStyle);
                    setCell(row, col++, dto.getPoLineDescription());
                    setCell(row, col++, dto.getUplLineDescription());
                    setCell(row, col++, dto.getPoPendingQuantity(), preciseQtyStyle);
                    setCell(row, col++, dto.getLnDeliveredQty(), preciseQtyStyle);
                    setCell(row, col++, dto.getLnLocationName());
                    setCell(row, col++, dto.getLnScopeOfWork());
                    col = setDateCell(row, col, dto.getLnInserviceDate(), dateStyle, dateFmt);
                    setCell(row, col++, dto.getLinkId());
                    setCell(row, col++, dto.getTagNumber());
                    setCell(row, col++, dto.getLnRemarks());

                    written++;
                    if (written % PROGRESS_UPDATE_EVERY_N_ROWS == 0) {
                        job.setRowsWritten(written);
                        job.setSheetCount(sheetCount);
                        exportJobRepository.save(job);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            wb.dispose();
            return sheetCount;
        }
    }

    private void writeHeaderRow(Sheet sheet, CellStyle hdrStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(hdrStyle);
        }
    }

    // ─── CELL HELPERS ─────────────────────────────────────────────────────────

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null)                cell.setCellValue("");
        else if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else                              cell.setCellValue(value.toString());
    }

    private void setCell(Row row, int col, Object value, CellStyle style) {
        setCell(row, col, value);
        row.getCell(col).setCellStyle(style);
    }

    private int setDateCell(Row row, int col, String dateStr,
                            CellStyle style, SimpleDateFormat fmt) {
        Cell cell = row.createCell(col);
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                cell.setCellValue(fmt.parse(dateStr));
                cell.setCellStyle(style);
            } catch (ParseException e) {
                cell.setCellValue(dateStr);
            }
        } else {
            cell.setCellValue("");
        }
        return col + 1;
    }
}