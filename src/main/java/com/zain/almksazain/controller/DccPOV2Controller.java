package com.zain.almksazain.controller;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.request.DccPORequest;
import com.zain.almksazain.service.DccPOV2Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
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

    // ─── /export-combined-view ────────────────────────────────────────────────

    @PostMapping("/combined-view/export")
    public DeferredResult<ResponseEntity<byte[]>> exportCombinedView(
            @RequestBody DccPORequest request) {

        boolean isCsv = "csv".equalsIgnoreCase(request.getExportFormat());
        String  mime  = isCsv
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        DeferredResult<ResponseEntity<byte[]>> result =
                new DeferredResult<>(EXPORT_TIMEOUT_MS);

        if (!exportSemaphore.tryAcquire()) {
            logger.warn("Export rejected — semaphore full");
            result.setErrorResult(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many concurrent exports. Please try again shortly.".getBytes()));
            return result;
        }

        service.getExportData(request)
                .thenAccept(rows -> {
                    try {
                        if (rows.isEmpty()) {
                            exportSemaphore.release();
                            result.setErrorResult(ResponseEntity.status(HttpStatus.NO_CONTENT)
                                    .body("No data found for the given filters.".getBytes()));
                            return;
                        }

                        String ts       = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        String ext      = isCsv ? "csv" : "xlsx";
                        String filename = "dcc_po_export_" + ts + "." + ext;

                        byte[] content = isCsv ? buildCsv(rows) : buildExcel(rows);

                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.parseMediaType(mime));
                        headers.add("Content-Disposition", "attachment; filename=" + filename);
                        headers.add("X-Total-Records", String.valueOf(rows.size()));

                        result.setResult(new ResponseEntity<>(content, headers, HttpStatus.OK));
                        logger.info("Export ({}) complete — {} rows, file={}", ext, rows.size(), filename);
                    } catch (Exception ex) {
                        logger.error("Export generation error", ex);
                        result.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(("Export failed: " + ex.getMessage()).getBytes()));
                    } finally {
                        exportSemaphore.release();
                    }
                })
                .exceptionally(ex -> {
                    exportSemaphore.release();
                    logger.error("Export async error", ex);
                    result.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(("Export failed: " + ex.getMessage()).getBytes()));
                    return null;
                });

        return result;
    }

    // ─── EXCEL BUILDER ────────────────────────────────────────────────────────

    private byte[] buildExcel(List<DccPOCombinedViewDTO> rows) throws Exception {
        List<DccPOCombinedViewDTO> sorted = rows.stream()
                .sorted(Comparator.comparing(DccPOCombinedViewDTO::getDccRecordNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<Long, List<DccPOCombinedViewDTO>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo,
                        LinkedHashMap::new, Collectors.toList()));

        SimpleDateFormat dateFmt = new SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(EXCEL_WINDOW_SIZE)) {
            Sheet sheet = wb.createSheet("DCC PO Data");
            CreationHelper ch = wb.getCreationHelper();

            CellStyle hdrStyle = wb.createCellStyle();
            Font hdrFont = wb.createFont();
            hdrFont.setBold(true);
            hdrStyle.setFont(hdrFont);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(ch.createDataFormat().getFormat("dd-MM-yyyy"));

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(hdrStyle);
            }

            int rowNum = 1;
            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : grouped.entrySet()) {
                DccPOCombinedViewDTO first = entry.getValue().get(0);
                for (DccPOCombinedViewDTO dto : entry.getValue()) {
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
                    setCell(row, col++, dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0);
                    setCell(row, col++, dto.getPoLineDescription());
                    setCell(row, col++, dto.getUplLineDescription());
                    setCell(row, col++, dto.getPoPendingQuantity());
                    setCell(row, col++, dto.getLnDeliveredQty());
                    setCell(row, col++, dto.getLnLocationName());
                    setCell(row, col++, dto.getLnScopeOfWork());
                    col = setDateCell(row, col, dto.getLnInserviceDate(), dateStyle, dateFmt);
                    setCell(row, col++, dto.getLinkId());
                    setCell(row, col++, dto.getTagNumber());
                    setCell(row, col++, dto.getLnRemarks());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        }
    }

    // ─── CSV BUILDER ──────────────────────────────────────────────────────────

    private byte[] buildCsv(List<DccPOCombinedViewDTO> rows) throws Exception {
        List<DccPOCombinedViewDTO> sorted = rows.stream()
                .sorted(Comparator.comparing(DccPOCombinedViewDTO::getDccRecordNo,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<Long, List<DccPOCombinedViewDTO>> grouped = sorted.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo,
                        LinkedHashMap::new, Collectors.toList()));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // UTF-8 BOM so Excel opens it correctly without encoding wizard
        baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            // Header
            pw.println(Arrays.stream(HEADERS)
                    .map(this::csvEscape).collect(Collectors.joining(",")));

            for (Map.Entry<Long, List<DccPOCombinedViewDTO>> entry : grouped.entrySet()) {
                DccPOCombinedViewDTO first = entry.getValue().get(0);
                for (DccPOCombinedViewDTO dto : entry.getValue()) {
                    String[] values = {
                        str(first.getDccRecordNo()),
                        str(first.getDccPoNumber()),
                        str(first.getProjectName()),
                        str(first.getDccAcceptanceType()),
                        str(first.getDccStatus()),
                        str(first.getDccCreatedDate()),
                        str(first.getDateApproved()),
                        str(first.getVendorName()),
                        str(first.getCreatedBy()),
                        str(first.getApprovalCount() != null ? first.getApprovalCount() : 0),
                        str(first.getPendingApprovers()),
                        str(first.getUserAging()),
                        str(first.getTotalAging()),
                        str(first.getVendorComment()),
                        str(first.getApproverComment()),
                        str(dto.getLineNumber()),
                        str(dto.getUplLineNumber()),
                        str(dto.getLnProductSerialNo()),
                        str(dto.getItemPartNumber()),
                        str(dto.getActualItemCode()),
                        str(dto.getUplLineItemCode()),
                        str(dto.getpoAcceptanceQty() != null ? dto.getpoAcceptanceQty() : 0),
                        str(dto.getPoLineDescription()),
                        str(dto.getUplLineDescription()),
                        str(dto.getPoPendingQuantity()),
                        str(dto.getLnDeliveredQty()),
                        str(dto.getLnLocationName()),
                        str(dto.getLnScopeOfWork()),
                        str(dto.getLnInserviceDate()),
                        str(dto.getLinkId()),
                        str(dto.getTagNumber()),
                        str(dto.getLnRemarks())
                    };
                    pw.println(Arrays.stream(values)
                            .map(this::csvEscape).collect(Collectors.joining(",")));
                }
            }
            pw.flush();
        }
        return baos.toByteArray();
    }

    // ─── CELL / CSV HELPERS ───────────────────────────────────────────────────

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null)                cell.setCellValue("");
        else if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        else                              cell.setCellValue(value.toString());
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

    private String str(Object v) {
        return v == null ? "" : v.toString();
    }

    /** Wraps a CSV value in quotes and escapes internal quotes. */
    private String csvEscape(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}