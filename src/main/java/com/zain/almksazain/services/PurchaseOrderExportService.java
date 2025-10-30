package com.zain.almksazain.services;



import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.DatabaseMetaData;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderExportService {

    private final DataSource dataSource;
    private static final int DEFAULT_FETCH_SIZE = 1000;

    public PurchaseOrderExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }
   public void exportToExcel(String whereFragment, List<Object> params, HttpServletResponse response) throws SQLException, IOException {
        // Desired columns (DB column names, without PO. prefix)
        List<String> desiredCols = Arrays.asList(
                "poNumber", "projectName", "recordNo", "typeLookupCode", "vendorName","currencyCode",
                "createdDate", "approvedDate", "lineNumber", "itemPartNumber", "poLineDescription",
                "poOrderQuantity", "poQtyNew", "quantityReceived", "amountReceived",
                "quantityDueOld", "amountDue", "quantityDueNew", "amountDueNew",
                "quantityBilled", "amountBilled", "unitPriceInSAR", "linePriceInSAR",
                "descopedLinePriceInPoCurrency", "newLinePriceInPoCurrency"
        );

        // Build SELECT clause dynamically based on table metadata
        String selectClause;
        boolean hasPoNumber = false;
        boolean hasLineNumber = false;
        try (Connection conn = dataSource.getConnection()) {
            // collect actual columns in the table (case-insensitive)
            Set<String> tableColsLower = new HashSet<>();
            DatabaseMetaData meta = conn.getMetaData();
            // Use current catalog and null schema pattern to be portable
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, "tb_PurchaseOrder", null)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) tableColsLower.add(col.toLowerCase(Locale.ROOT));
                }
            }

            // build list of available desired columns
            List<String> available = desiredCols.stream()
                    .filter(c -> tableColsLower.contains(c.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());

            hasPoNumber = tableColsLower.contains("ponumber");
            hasLineNumber = tableColsLower.contains("linenumber");

            if (available.isEmpty()) {
                // fallback to select all columns to avoid SQL errors
                selectClause = "PO.*";
            } else {
                selectClause = available.stream().map(c -> "PO." + c).collect(Collectors.joining(", "));
            }

            // Build ORDER BY clause safely: require poNumber; include lineNumber if present
            String orderBy = "";
            if (hasPoNumber) {
                orderBy = " ORDER BY PO.poNumber" + (hasLineNumber ? ", PO.lineNumber" : "");
            } else {
                // If there's no poNumber, just avoid ORDER BY to prevent errors (unlikely)
                orderBy = "";
            }

            String sql = "SELECT " + selectClause + " FROM tb_PurchaseOrder PO WHERE 1=1 "
                    + (whereFragment == null ? "" : whereFragment)
                    + orderBy;

            // Prepare response headers (XLSX binary)
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"purchase_orders_export.xlsx\"");

            // Excel column headings (these are static labels for the sheet; missing DB columns will result in blank cells)
            List<String> poCols = Arrays.asList(
                    "Purchase Order", "Project Name", "PR (recordNo)", "Type Lookup Code", "Vendor Name",
                    "Currency Code",
                    "Created Date", "Approved Date"
            );

            List<String> lineCols = Arrays.asList(
                    "Line Number", "Item Part Number", "Line Description", "Order Qty", "PO Qty New",
                    "Qty Received", "Amount Received", "Qty Due Old", "Amount Due Old", "Qty Due New",
                    "Amount Due New", "Qty Billed", "Amount Billed", "Unit Price (SAR)", "Line Price (SAR)",
                    "Descoped Line Price PO Currency (SAR)", "New Line Price PO Currency (SAR)"
            );

            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                Sheet sheet = workbook.createSheet("PO Export");
                int rowIdx = 0;

                // Header row
                Row header = sheet.createRow(rowIdx++);
                int colIdx = 0;
                for (String h : poCols) header.createCell(colIdx++).setCellValue(h);
                for (String h : lineCols) header.createCell(colIdx++).setCellValue(h);

                // Date style
                CreationHelper createHelper = workbook.getCreationHelper();
                CellStyle dateCellStyle = workbook.createCellStyle();
                short dateFormat = createHelper.createDataFormat().getFormat("dd-mmm-yyyy");
                dateCellStyle.setDataFormat(dateFormat);
                dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

                // Execute streaming query and write rows as we iterate
                try (PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    // streaming hint
                    ps.setFetchSize(DEFAULT_FETCH_SIZE);

                    // bind params (if any)
                    if (params != null && !params.isEmpty()) {
                        int idx = 1;
                        for (Object p : params) {
                            ps.setObject(idx++, p);
                        }
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData rsMd = rs.getMetaData();
                        Set<String> rsColsLower = new HashSet<>();
                        for (int i = 1; i <= rsMd.getColumnCount(); i++) {
                            String label = rsMd.getColumnLabel(i);
                            if (label != null) rsColsLower.add(label.toLowerCase(Locale.ROOT));
                        }

                        String currentPo = null;
                        Map<String, Object> poHeader = null;
                        List<Map<String, Object>> poLines = new ArrayList<>();

                        while (rs.next()) {
                            // read available columns safely
                            String poNumber = safeGetString(rs, "poNumber", rsColsLower);
                            if (poNumber == null) poNumber = "";

                            // when we detect a new PO, flush previous PO block
                            if (currentPo != null && !currentPo.equals(poNumber)) {
                                rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle);
                                poLines.clear();
                                poHeader = null;
                            }

                            // capture PO header once
                            if (poHeader == null) {
                                poHeader = new HashMap<>();
                                poHeader.put("poNumber", poNumber);
                                poHeader.put("projectName", safeGetString(rs, "projectName", rsColsLower));
                                poHeader.put("recordNo", safeGetString(rs, "recordNo", rsColsLower));
                                poHeader.put("typeLookupCode", safeGetString(rs, "typeLookupCode", rsColsLower));
                                poHeader.put("vendorName", safeGetString(rs, "vendorName", rsColsLower));
                                poHeader.put("currencyCode", safeGetString(rs, "currencyCode", rsColsLower));
                                poHeader.put("createdDate", rsColsLower.contains("createddate") ? rs.getTimestamp("createdDate") : null);
                                poHeader.put("approvedDate", rsColsLower.contains("approveddate") ? rs.getTimestamp("approvedDate") : null);
                            }

                            // build line-level map using only                                          available columns
                            Map<String, Object> line = new HashMap<>();
                            line.put("lineNumber", safeGetString(rs, "lineNumber", rsColsLower));
                            line.put("itemPartNumber", safeGetString(rs, "itemPartNumber", rsColsLower));
                            line.put("poLineDescription", safeGetString(rs, "poLineDescription", rsColsLower));
                            line.put("poOrderQuantity", safeGetObject(rs, "poOrderQuantity", rsColsLower));
                            line.put("poQtyNew", safeGetObject(rs, "poQtyNew", rsColsLower));
                            line.put("quantityReceived", safeGetObject(rs, "quantityReceived", rsColsLower));
                            line.put("amountReceived", safeGetObject(rs, "amountReceived", rsColsLower));
                            line.put("quantityDueOld", safeGetObject(rs, "quantityDueOld", rsColsLower));
                            line.put("amountDue", safeGetObject(rs, "amountDue", rsColsLower));
                            line.put("quantityDueNew", safeGetObject(rs, "quantityDueNew", rsColsLower));
                            line.put("amountDueNew", safeGetObject(rs, "amountDueNew", rsColsLower));
                            line.put("quantityBilled", safeGetObject(rs, "quantityBilled", rsColsLower));
                            line.put("amountBilled", safeGetObject(rs, "amountBilled", rsColsLower));
                            line.put("unitPriceInSAR", safeGetObject(rs, "unitPriceInSAR", rsColsLower));
                            line.put("linePriceInSAR", safeGetObject(rs, "linePriceInSAR", rsColsLower));
                            line.put("descopedLinePriceInPoCurrency", safeGetObject(rs, "descopedLinePriceInPoCurrency", rsColsLower));
                            line.put("newLinePriceInPoCurrency", safeGetObject(rs, "newLinePriceInPoCurrency", rsColsLower));

                            poLines.add(line);
                            currentPo = poNumber;
                        }
  
                        // flush last PO block
                        if (currentPo != null && !poLines.isEmpty()) {
                            rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle);
                        }
                    }
                }

                // write workbook to response
                workbook.write(response.getOutputStream());
                response.flushBuffer();
                workbook.dispose();
            }
        }
    }

    // write all lines for a PO (no PO-level totals written anymore)
    private int writePoBlock(Sheet sheet, int startRow, Map<String, Object> poHeader, List<Map<String, Object>> lines, CellStyle dateCellStyle) {

        for (Map<String, Object> line : lines) {
            Row r = sheet.createRow(startRow++);
            int c = 0;
            r.createCell(c++).setCellValue(defaultString(poHeader.get("poNumber")));
            r.createCell(c++).setCellValue(defaultString(poHeader.get("projectName")));
            r.createCell(c++).setCellValue(defaultString(poHeader.get("recordNo")));
            r.createCell(c++).setCellValue(defaultString(poHeader.get("typeLookupCode")));
            r.createCell(c++).setCellValue(defaultString(poHeader.get("vendorName")));
            r.createCell(c++).setCellValue(defaultString(poHeader.get("currencyCode")));

            Timestamp cd = (poHeader.get("createdDate") instanceof Timestamp) ? (Timestamp) poHeader.get("createdDate") : null;
            if (cd != null) {
                Cell cell = r.createCell(c++);
                cell.setCellValue(new java.util.Date(cd.getTime()));
                cell.setCellStyle(dateCellStyle);
            } else {
                r.createCell(c++).setCellValue("");
            }

            Timestamp ad = (poHeader.get("approvedDate") instanceof Timestamp) ? (Timestamp) poHeader.get("approvedDate") : null;
            if (ad != null) {
                Cell cell = r.createCell(c++);
                cell.setCellValue(new java.util.Date(ad.getTime()));
                cell.setCellStyle(dateCellStyle);
            } else {
                r.createCell(c++).setCellValue("");
            }

            // Line columns
            r.createCell(c++).setCellValue(defaultString(line.get("lineNumber")));
            r.createCell(c++).setCellValue(defaultString(line.get("itemPartNumber")));
            r.createCell(c++).setCellValue(defaultString(line.get("poLineDescription")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("poOrderQuantity")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("poQtyNew")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("quantityReceived")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("amountReceived")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("quantityDueOld")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("amountDue")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("quantityDueNew")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("amountDueNew")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("quantityBilled")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("amountBilled")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("unitPriceInSAR")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("linePriceInSAR")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("descopedLinePriceInPoCurrency")));
            r.createCell(c++).setCellValue(getDoubleString(line.get("newLinePriceInPoCurrency")));
        }
        return startRow;
    }

    // Safe getters: check whether the ResultSet had the column, else return null
    private static Object safeGetObject(ResultSet rs, String col, Set<String> rsColsLower) {
        try {
            if (col == null || !rsColsLower.contains(col.toLowerCase(Locale.ROOT))) return null;
            return rs.getObject(col);
        } catch (SQLException e) {
            return null;
        }
    }

    private static String safeGetString(ResultSet rs, String col, Set<String> rsColsLower) {
        Object o = safeGetObject(rs, col, rsColsLower);
        return o == null ? "" : String.valueOf(o);
    }

    // helpers
    private static Double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String defaultString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String getDoubleString(Object o) {
        if (o == null) return "0";
        if (o instanceof Number) return String.valueOf(((Number) o).doubleValue());
        try {
            return String.valueOf(Double.parseDouble(String.valueOf(o)));
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}