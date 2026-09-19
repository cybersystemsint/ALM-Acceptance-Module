package com.zain.almksazain.controller;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.ExportPageResult;
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
    // Per-page fetch timeout - the export loop calls getExportDataPage once per chunk now
    // rather than once for the whole result set, so this bounds a single chunk's fetch time,
    // not the overall job (a large export naturally takes longer in wall-clock time, but each
    // individual page fetch is expected to complete well within this).
    private static final long EXPORT_TIMEOUT_MS  = 300_000L;
    private static final int  MAX_CONCURRENT_EXPORTS = 3;
    private static final int  EXCEL_WINDOW_SIZE  = 100;
    private static final int  MAX_ROWS_PER_SHEET = 1_000_000;

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

    // Chunk size for the export's DCC fetch - the whole point of paging here is
    // to bound how many DCCs (and their related PO/UPL/line-item/approval data)
    // are in memory at once, so this must stay well below "the whole dataset"
    // regardless of how large the underlying table grows.
    private static final int EXPORT_CHUNK_SIZE = 500;

    private void runCombinedViewExportJob(String jobId, DccPORequest request) {
        ExportJob job = exportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            logger.error("Export job {} disappeared before it could start", jobId);
            exportSemaphore.release();
            return;
        }
        job.setStatus(ExportJob.STATUS_RUNNING);
        exportJobRepository.save(job);

        WorkbookState state = null;
        try {
            state = openWorkbookState();

            long totalWritten = 0;
            boolean anyRows = false;
            int page = 1;
            while (true) {
                ExportPageResult pageResult = service.getExportDataPage(request, page, EXPORT_CHUNK_SIZE)
                        .get(EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                List<DccPOCombinedViewDTO> chunkRows = pageResult.getRows();
                if (!chunkRows.isEmpty()) {
                    anyRows = true;
                    totalWritten += writeChunkToWorkbook(state, chunkRows);
                    job.setRowsWritten(totalWritten);
                    job.setSheetCount(state.sheetCount);
                    exportJobRepository.save(job);
                    logger.info("Export job {} — page {} written, {} rows so far", jobId, page, totalWritten);
                }

                if (!pageResult.hasMore()) {
                    break;
                }
                page++;
            }

            if (!anyRows) {
                state.workbook.dispose();
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

            finalizeWorkbook(state, outFile);

            LocalDateTime completedAt = LocalDateTime.now();
            job.setStatus(ExportJob.STATUS_DONE);
            String filterTag = combinedViewHasFilters(request) ? "_FILTERED" : "";
            job.setFileName("ACCEPTANCE_REQUESTS" + filterTag + "_"
                    + completedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".xlsx");
            job.setFilePath(outFile.getAbsolutePath());
            job.setRowsWritten(totalWritten);
            job.setSheetCount(state.sheetCount);
            job.setCompletedAt(completedAt);
            exportJobRepository.save(job);
            logger.info("Export job {} complete — {} rows, {} sheet(s)", jobId, totalWritten, state.sheetCount);

        } catch (Exception ex) {
            logger.error("Export job {} failed", jobId, ex);
            if (state != null) {
                try {
                    state.workbook.dispose();
                } catch (Exception disposeEx) {
                    logger.warn("Failed to dispose workbook for failed export job {}", jobId, disposeEx);
                }
            }
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

    /** Holds an open, in-progress workbook plus the running position within it, so a large
     *  export can be written page-by-page (see {@link #writeChunkToWorkbook}) without ever
     *  holding more than one page's worth of row data in memory at a time - only the
     *  SXSSFWorkbook's own small on-disk-backed row window lives for the life of the job. */
    private static final class WorkbookState {
        final SXSSFWorkbook workbook;
        final CellStyle hdrStyle;
        final CellStyle dateStyle;
        final CellStyle preciseQtyStyle;
        final CellStyle wholeQtyStyle;
        final SimpleDateFormat dateFmt;
        Sheet sheet;
        int sheetCount;
        int rowNum;

        WorkbookState(SXSSFWorkbook workbook, CellStyle hdrStyle, CellStyle dateStyle,
                CellStyle preciseQtyStyle, CellStyle wholeQtyStyle, SimpleDateFormat dateFmt, Sheet sheet) {
            this.workbook = workbook;
            this.hdrStyle = hdrStyle;
            this.dateStyle = dateStyle;
            this.preciseQtyStyle = preciseQtyStyle;
            this.wholeQtyStyle = wholeQtyStyle;
            this.dateFmt = dateFmt;
            this.sheet = sheet;
            this.sheetCount = 1;
            this.rowNum = 1;
        }
    }

    private WorkbookState openWorkbookState() {
        SXSSFWorkbook wb = new SXSSFWorkbook(EXCEL_WINDOW_SIZE);
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
        // Excel format strings can't conditionally hide a literal character - the "." here
        // always renders even when every "#" after it is empty - so this format alone would
        // print whole numbers as "80." with a trailing dot. wholeQtyStyle (plain "0", no
        // decimal point at all) is used instead whenever a value has no fractional part;
        // see setQtyCell.
        CellStyle preciseQtyStyle = wb.createCellStyle();
        preciseQtyStyle.setDataFormat(ch.createDataFormat().getFormat("0.####################"));

        CellStyle wholeQtyStyle = wb.createCellStyle();
        wholeQtyStyle.setDataFormat(ch.createDataFormat().getFormat("0"));

        SimpleDateFormat dateFmt = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

        Sheet sheet = wb.createSheet(sheetName(1));
        WorkbookState state = new WorkbookState(wb, hdrStyle, dateStyle, preciseQtyStyle, wholeQtyStyle, dateFmt, sheet);
        writeHeaderRow(state.sheet, state.hdrStyle);
        return state;
    }

    /** Writes one page's worth of rows into the already-open workbook, rolling over to a new
     *  sheet every MAX_ROWS_PER_SHEET rows. Only this one page's DTOs are ever in memory at
     *  once - the caller discards {@code chunkRows} after this returns and fetches the next
     *  page, so peak memory no longer grows with the total export size. Returns the number of
     *  rows written from this chunk. */
    private long writeChunkToWorkbook(WorkbookState state, List<DccPOCombinedViewDTO> chunkRows) {
        // Each page is already fetched in descending-recordNo order, so sorting/grouping here
        // only needs to hold this page's rows, not the whole export - it keeps each DCC's line
        // items written together contiguously, exactly as the pre-chunking version did.
        List<DccPOCombinedViewDTO> sorted = chunkRows.stream()
                .sorted(Comparator.comparing(DccPOCombinedViewDTO::getDccRecordNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<Long, List<DccPOCombinedViewDTO>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo,
                        LinkedHashMap::new, Collectors.toList()));

        long written = 0;
        for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : grouped.entrySet()) {
            DccPOCombinedViewDTO first = entry.getValue().get(0);
            for (DccPOCombinedViewDTO dto : entry.getValue()) {
                if (state.rowNum > MAX_ROWS_PER_SHEET) {
                    state.sheetCount++;
                    state.sheet = state.workbook.createSheet(sheetName(state.sheetCount));
                    writeHeaderRow(state.sheet, state.hdrStyle);
                    state.rowNum = 1;
                }

                Row row = state.sheet.createRow(state.rowNum++);
                int col = 0;

                // Parent-level
                setCell(row, col++, first.getDccRecordNo());
                setCell(row, col++, first.getDccPoNumber());
                setCell(row, col++, first.getProjectName());
                setCell(row, col++, first.getDccAcceptanceType());
                setCell(row, col++, first.getDccStatus());
                col = setDateCell(row, col, first.getDccCreatedDate(), state.dateStyle, state.dateFmt);
                col = setDateCell(row, col, first.getDateApproved(),   state.dateStyle, state.dateFmt);
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
                setQtyCell(row, col++, dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0.0, state);
                setCell(row, col++, dto.getPoLineDescription());
                setCell(row, col++, dto.getUplLineDescription());
                setQtyCell(row, col++, dto.getPoPendingQuantity(), state);
                setQtyCell(row, col++, dto.getLnDeliveredQty(), state);
                setCell(row, col++, dto.getLnLocationName());
                setCell(row, col++, dto.getLnScopeOfWork());
                col = setDateCell(row, col, dto.getLnInserviceDate(), state.dateStyle, state.dateFmt);
                setCell(row, col++, dto.getLinkId());
                setCell(row, col++, dto.getTagNumber());
                setCell(row, col++, dto.getLnRemarks());

                written++;
            }
        }
        return written;
    }

    /** Flushes the finished workbook to disk and releases its temp resources. */
    private void finalizeWorkbook(WorkbookState state, File outFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            state.workbook.write(fos);
        }
        state.workbook.dispose();
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

    /** Writes a quantity value at full precision without ever showing a trailing decimal
     *  point on whole numbers. Excel format strings render literal characters unconditionally,
     *  so a single format like "0.####################" always prints the "." even when every
     *  "#" after it is empty (e.g. 1.0 -&gt; "1." instead of "1") - there is no format-string-only
     *  way to hide it. Deciding the style per value in code sidesteps that: whole numbers get a
     *  plain "0" format (no decimal point at all), and genuinely fractional values get the
     *  full-precision format, so the displayed value always matches what's stored - an integer
     *  shows as an integer, and something like 2.000000000043 shows in full, unrounded. */
    private void setQtyCell(Row row, int col, Double value, WorkbookState state) {
        if (value == null) {
            setCell(row, col, null);
            return;
        }
        boolean isWhole = !value.isNaN() && !value.isInfinite() && value == Math.rint(value);
        setCell(row, col, value, isWhole ? state.wholeQtyStyle : state.preciseQtyStyle);
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