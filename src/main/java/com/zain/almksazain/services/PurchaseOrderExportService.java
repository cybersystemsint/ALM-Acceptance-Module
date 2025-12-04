package com.zain.almksazain.services;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    // cache of table columns (lower-case) per table name to avoid repeated metadata calls
    private static final ConcurrentHashMap<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();

    // synonyms for common DB column name variants
    private static final Map<String, String[]> FIELD_SYNONYMS = new HashMap<>();
    static {
        FIELD_SYNONYMS.put("createdDate", new String[] { "creation_date", "created_date", "creationdate", "createddate" });
        FIELD_SYNONYMS.put("approvedDate", new String[] { "approved_date", "approval_date", "approveddate" });
        FIELD_SYNONYMS.put("prNum", new String[] { "pr_num", "prnum" });
        // add additional synonyms as needed
    }

    public PurchaseOrderExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Backwards-compatible overload
    public void exportToExcel(String whereFragment, List<Object> params, HttpServletResponse response) throws SQLException, IOException {
        exportToExcel(whereFragment, params, response, null, null);
    }


    public void exportToExcel(String whereFragment, List<Object> params, HttpServletResponse response, Integer limit, Integer offset) throws SQLException, IOException {
        List<String> desiredCols = Arrays.asList(
                "poNumber", "po_type", "release_num", "releaseNum", "lineNumber", "recordNo", "projectName",
                "lineCancelFlag", "cancelReason", "itemPartNumber", "pnSubAllow", "countryOfOrigin",
                "poOrderQuantity", "descopeQty", "poQtyNew", "quantityReceived", "quantityDueOld",
                "quantityDueNew", "quantityBilled", "currencyCode", "unitPriceInPoCurrency",
                "unitPriceInSAR", "linePriceInPoCurrency", "linePriceInSAR", "newLinePriceInPoCurrency",
                "newLinePriceInSAR", "amountReceived", "amountDue", "amountDueNew", "amountBilled",
                "poLineDescription", "organizationName", "organizationCode", "subinventoryCode",
                "receiptRouting", "authorizationStatus", "authorisationStatus", "poClosureStatus", "departmentName",
                "poLineType", "costCenter", "chargeAccount", "serialControl", "vendorSerialNumberYn",
                "itemType", "itemCategoryInventory", "inventoryCategoryDescription", "itemCategoryFa",
                "faCategoryDescription", "itemCategoryPurchasing", "purchasingCategoryDescription",
                "vendorName", "vendorNumber", "approvedDate", "createdDate", "typeLookupCode", "descopedLinePriceInPoCurrency",
                "prNum"
        );

        String selectClause;
        boolean hasPoNumber = false;
        boolean hasLineNumber = false;

        // Table name used for metadata cache
        final String tableName = "tb_PurchaseOrder";

        try (Connection conn = dataSource.getConnection()) {
            try {
                conn.setAutoCommit(false);
            } catch (Exception ex) {
            }

            // Get or compute table columns (lower-case)
            Set<String> tableColsLower = tableColumnsCache.computeIfAbsent(tableName, t -> {
                Set<String> cols = new HashSet<>();
                try (Connection c2 = dataSource.getConnection()) {
                    DatabaseMetaData meta = c2.getMetaData();
                    try (ResultSet rs = meta.getColumns(c2.getCatalog(), null, tableName, null)) {
                        while (rs.next()) {
                            String col = rs.getString("COLUMN_NAME");
                            if (col != null) cols.add(col.toLowerCase(Locale.ROOT));
                        }
                    }
                } catch (Exception e) {
                }
                return cols;
            });

            // Build available column list from desiredCols intersecting table columns
            List<String> available = desiredCols.stream()
                    .filter(c -> tableColsLower.contains(c.toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());

            hasPoNumber = tableColsLower.contains("ponumber") || tableColsLower.contains("po_number");
            hasLineNumber = tableColsLower.contains("linenumber") || tableColsLower.contains("line_num") || tableColsLower.contains("line_number");

            if (available.isEmpty()) {
                selectClause = "PO.*";
            } else {
                selectClause = available.stream().map(c -> "PO." + c).collect(Collectors.joining(", "));
            }

            String orderBy = "";
            if (hasPoNumber) {
                orderBy = " ORDER BY PO.poNumber" + (hasLineNumber ? ", PO.lineNumber" : "");
            }

            String limitClause = "";
            if (limit != null && offset != null) {
                limitClause = " LIMIT ? OFFSET ? ";
            } else if (limit != null) {
                limitClause = " LIMIT ? ";
            }

            String sql = "SELECT " + selectClause + " FROM " + tableName + " PO WHERE 1=1 "
                    + (whereFragment == null ? "" : whereFragment)
                    + orderBy
                    + limitClause;

            // Prepare response headers
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"purchase_orders_export.xlsx\"");

            // Header labels (exact casing/order)
            List<String> headerLabels = Arrays.asList(
                    "PO_NUMBER",
                    "PO Type",
                    "RELEASE_NUM",
                    "LINE_NUM",
                    "PR_NUM",
                    "PROJECT_NAME",
                    "LINE_CANCEL_FLAG",
                    "CANCEL_REASON",
                    "ITEM_PART_NUMBER",
                    "PN_SUB_ALLOW",
                    "COUNTRY_OF_ORIGIN",
                    "PO_ORDER_QUANTITY",
                    "Descope Qty",
                    "PO_QTY_NEW",
                    "QUANTITY_RECEIVED",
                    "QUANTITY_DUE_OLD",
                    "QUANTITY_DUE_NEW",
                    "QUANTITY_BILLED",
                    "CURRENCY_CODE",
                    "UNIT_PRICE_IN_PO_CURRENCY",
                    "UNIT_PRICE_IN_SAR",
                    "LINE_PRICE_IN_PO_CURRENCY",
                    "LINE_PRICE_IN_SAR",
                    "New LINE_PRICE_IN_PO_CURRENCY",
                    "NEW_LINE_PRICE_IN_SAR",
                    "AMOUNT_RECEIVED",
                    "AMOUNT_DUE",
                    "AMOUNT_DUE_NEW",
                    "AMOUNT_BILLED",
                    "PO_LINE_DESCRIPTION",
                    "ORGANIZATION_NAME",
                    "ORGANIZATION_CODE",
                    "SUBINVENTORY_CODE",
                    "RECEIPT_ROUTING",
                    "AUTHORIZATION_STATUS",
                    "PO_CLOSURE_STATUS",
                    "DEPARTMENT_NAME",
                    "PO_LINE_TYPE",
                    "COST_CENTER",
                    "CHARGE_ACCOUNT",
                    "SERIAL_CONTROL",
                    "VENDOR_SERIAL_NUMBER_YN",
                    "ITEM_TYPE",
                    "ITEM_CATEGORY_INVENTORY",
                    "INVENTORY_CATEGORY_DESCRIPTION",
                    "ITEM_CATEGORY_FA",
                    "FA_CATEGORY_DESCRIPTION",
                    "ITEM_CATEGORY_PURCHASING",
                    "PURCHASING_CATEGORY_DESCRIPTION",
                    "VENDOR_NAME",
                    "VENDOR_NUMBER",
                    "APPROVED_DATE",
                    "CREATION_DATE"
            );

            List<String> fieldKeys = Arrays.asList(
                    "poNumber",
                    "typeLookupCode",
                    "releaseNum",
                    "lineNumber",
                    "prNum",
                    "projectName",
                    "lineCancelFlag",
                    "cancelReason",
                    "itemPartNumber",
                    "pnSubAllow",
                    "countryOfOrigin",
                    "poOrderQuantity",
                    "descopeQty",
                    "poQtyNew",
                    "quantityReceived",
                    "quantityDueOld",
                    "quantityDueNew",
                    "quantityBilled",
                    "currencyCode",
                    "unitPriceInPoCurrency",
                    "unitPriceInSAR",
                    "linePriceInPoCurrency",
                    "linePriceInSAR",
                    "descopedLinePriceInPoCurrency", // New LINE_PRICE_IN_PO_CURRENCY we may override with NEW_LINE_PRICE_IN_SAR
                    "newLinePriceInPoCurrency",
                    "amountReceived",
                    "amountDue",
                    "amountDueNew",
                    "amountBilled",
                    "poLineDescription",
                    "organizationName",
                    "organizationCode",
                    "subinventoryCode",
                    "receiptRouting",
                    "authorizationStatus",
                    "poClosureStatus",
                    "departmentName",
                    "poLineType",
                    "costCenter",
                    "chargeAccount",
                    "serialControl",
                    "vendorSerialNumberYn",
                    "itemType",
                    "itemCategoryInventory",
                    "inventoryCategoryDescription",
                    "itemCategoryFa",
                    "faCategoryDescription",
                    "itemCategoryPurchasing",
                    "purchasingCategoryDescription",
                    "vendorName",
                    "vendorNumber",
                    "approvedDate",
                    "createdDate"
            );

            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                Sheet sheet = workbook.createSheet("PO Export");
                int rowIdx = 0;

                // Header row
                Row header = sheet.createRow(rowIdx++);
                int colIdx = 0;
                for (String h : headerLabels) {
                    header.createCell(colIdx++).setCellValue(h);
                }

                // Date style
                CreationHelper createHelper = workbook.getCreationHelper();
                CellStyle dateCellStyle = workbook.createCellStyle();
                short dateFormat = createHelper.createDataFormat().getFormat("dd-mmm-yyyy");
                dateCellStyle.setDataFormat(dateFormat);
                dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

                try (PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    // streaming hint
                    try {
                        ps.setFetchSize(DEFAULT_FETCH_SIZE);
                    } catch (Exception ex) {
                    }

                    // bind filter params
                    int idx = 1;
                    if (params != null && !params.isEmpty()) {
                        for (Object p : params) {
                            ps.setObject(idx++, p);
                        }
                    }

                    // bind limit/offset
                    if (limit != null) {
                        ps.setObject(idx++, limit);
                        if (offset != null) {
                            ps.setObject(idx++, offset);
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
                            String poNumber = safeGetString(rs, "poNumber", rsColsLower);
                            if (poNumber == null) poNumber = "";

                            // detect change of PO -> flush block
                            if (currentPo != null && !currentPo.equals(poNumber)) {
                                rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle, headerLabels, fieldKeys);
                                poLines.clear();
                                poHeader = null;
                            }

                            if (poHeader == null) {
                                poHeader = new HashMap<>();
                                poHeader.put("poNumber", poNumber);
                                poHeader.put("projectName", safeGetString(rs, "projectName", rsColsLower));
                                poHeader.put("recordNo", safeGetString(rs, "recordNo", rsColsLower));
                                poHeader.put("prNum", safeGetString(rs, "prNum", rsColsLower));
                                poHeader.put("releaseNum", safeGetString(rs, "releaseNum", rsColsLower));
                                poHeader.put("typeLookupCode", safeGetString(rs, "typeLookupCode", rsColsLower));
                                poHeader.put("vendorName", safeGetString(rs, "vendorName", rsColsLower));
                                poHeader.put("vendorNumber", safeGetString(rs, "vendorNumber", rsColsLower));
                                poHeader.put("currencyCode", safeGetString(rs, "currencyCode", rsColsLower));

                                // createdDate / creation_date detection (best-effort)
                                if (rsColsLower.contains("createddate") || rsColsLower.contains("creation_date")) {
                                    try {
                                        // try both label forms
                                        if (rsColsLower.contains("createddate")) poHeader.put("createdDate", rs.getTimestamp("createddate"));
                                        else poHeader.put("createdDate", rs.getTimestamp("creation_date"));
                                    } catch (Exception ex) {
                                        // ignore
                                    }
                                }
                                if (rsColsLower.contains("approveddate") || rsColsLower.contains("approved_date")) {
                                    try {
                                        if (rsColsLower.contains("approveddate")) poHeader.put("approvedDate", rs.getTimestamp("approveddate"));
                                        else poHeader.put("approvedDate", rs.getTimestamp("approved_date"));
                                    } catch (Exception ex) {
                                        // ignore
                                    }
                                }

                                String authorization = getFirstAvailableString(rs, rsColsLower, "authorizationStatus", "authorisationStatus", "authorization_status", "authorisation_status");
                                poHeader.put("authorizationStatus", authorization);

                                poHeader.put("organizationName", safeGetString(rs, "organizationName", rsColsLower));
                                poHeader.put("organizationCode", safeGetString(rs, "organizationCode", rsColsLower));
                                poHeader.put("subinventoryCode", safeGetString(rs, "subinventoryCode", rsColsLower));
                                poHeader.put("receiptRouting", safeGetString(rs, "receiptRouting", rsColsLower));
                                poHeader.put("poClosureStatus", safeGetString(rs, "poClosureStatus", rsColsLower));
                                poHeader.put("departmentName", safeGetString(rs, "departmentName", rsColsLower));
                            }

                            // Build line map (only available columns)
                            Map<String, Object> line = new HashMap<>();
                            line.put("lineNumber", safeGetString(rs, "lineNumber", rsColsLower));
                            line.put("itemPartNumber", safeGetString(rs, "itemPartNumber", rsColsLower));
                            line.put("poLineDescription", safeGetString(rs, "poLineDescription", rsColsLower));
                            line.put("poOrderQuantity", safeGetObject(rs, "poOrderQuantity", rsColsLower));
                            Object descope = getFirstAvailableObject(rs, rsColsLower, "descopeQty", "descopedQty", "descoped_qty", "descope_qty", "descopedqty");
                            if (descope == null) descope = 0.0;
                            line.put("descopeQty", descope);
                            line.put("poQtyNew", safeGetObject(rs, "poQtyNew", rsColsLower));
                            line.put("quantityReceived", safeGetObject(rs, "quantityReceived", rsColsLower));
                            line.put("amountReceived", safeGetObject(rs, "amountReceived", rsColsLower));
                            line.put("quantityDueOld", safeGetObject(rs, "quantityDueOld", rsColsLower));
                            line.put("amountDue", safeGetObject(rs, "amountDue", rsColsLower));
                            line.put("quantityDueNew", safeGetObject(rs, "quantityDueNew", rsColsLower));
                            line.put("amountDueNew", safeGetObject(rs, "amountDueNew", rsColsLower));
                            line.put("quantityBilled", safeGetObject(rs, "quantityBilled", rsColsLower));
                            line.put("amountBilled", safeGetObject(rs, "amountBilled", rsColsLower));
                            line.put("unitPriceInPoCurrency", safeGetObject(rs, "unitPriceInPoCurrency", rsColsLower));
                            line.put("unitPriceInSAR", safeGetObject(rs, "unitPriceInSAR", rsColsLower));
                            line.put("linePriceInPoCurrency", safeGetObject(rs, "linePriceInPoCurrency", rsColsLower));
                            line.put("linePriceInSAR", safeGetObject(rs, "linePriceInSAR", rsColsLower));
                            line.put("descopedLinePriceInPoCurrency", getFirstAvailableObject(rs, rsColsLower, "descopedLinePriceInPoCurrency", "descoped_line_price_in_po_currency", "descoped_line_price"));
                            line.put("newLinePriceInPoCurrency", getFirstAvailableObject(rs, rsColsLower, "newLinePriceInPoCurrency", "new_line_price_in_po_currency", "newLinePriceInSAR", "new_line_price_in_sar"));
                            line.put("pnSubAllow", safeGetString(rs, "pnSubAllow", rsColsLower));
                            line.put("countryOfOrigin", safeGetString(rs, "countryOfOrigin", rsColsLower));
                            line.put("typeLookupCode", safeGetString(rs, "typeLookupCode", rsColsLower));
                            line.put("lineCancelFlag", safeGetString(rs, "lineCancelFlag", rsColsLower));
                            line.put("cancelReason", safeGetString(rs, "cancelReason", rsColsLower));
                            line.put("poLineType", safeGetString(rs, "poLineType", rsColsLower));
                            line.put("costCenter", safeGetString(rs, "costCenter", rsColsLower));
                            line.put("chargeAccount", safeGetString(rs, "chargeAccount", rsColsLower));
                            line.put("serialControl", safeGetString(rs, "serialControl", rsColsLower));
                            line.put("vendorSerialNumberYn", safeGetString(rs, "vendorSerialNumberYn", rsColsLower));
                            line.put("itemType", safeGetString(rs, "itemType", rsColsLower));
                            line.put("itemCategoryInventory", safeGetString(rs, "itemCategoryInventory", rsColsLower));
                            line.put("inventoryCategoryDescription", safeGetString(rs, "inventoryCategoryDescription", rsColsLower));
                            line.put("itemCategoryFa", safeGetString(rs, "itemCategoryFa", rsColsLower));
                            line.put("faCategoryDescription", safeGetString(rs, "faCategoryDescription", rsColsLower));
                            line.put("itemCategoryPurchasing", safeGetString(rs, "itemCategoryPurchasing", rsColsLower));
                            line.put("purchasingCategoryDescription", safeGetString(rs, "purchasingCategoryDescription", rsColsLower));

                            poLines.add(line);
                            currentPo = poNumber;
                        }

                        // flush last block
                        if (currentPo != null && !poLines.isEmpty()) {
                            rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle, headerLabels, fieldKeys);
                        }
                    }
                }

                // Write workbook using buffered output for fewer small writes
                try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream(), 64 * 1024)) {
                    workbook.write(bos);
                    bos.flush();
                }
                response.flushBuffer();
                workbook.dispose();
            }
        } finally {
            // nothing to do - connection autoCommit may be managed by pool
        }
    }

    // write all lines for a PO using headerLabels/fieldKeys to preserve exact order and casing
    private int writePoBlock(Sheet sheet, int startRow, Map<String, Object> poHeader, List<Map<String, Object>> lines, CellStyle dateCellStyle,
                             List<String> headerLabels, List<String> fieldKeys) {

        for (Map<String, Object> line : lines) {
            Row r = sheet.createRow(startRow++);
            int c = 0;
            for (int i = 0; i < headerLabels.size(); i++) {
                String key = fieldKeys.get(i);
                Object value = null;
                // prefer PO-level value if present; otherwise line-level
                if (poHeader != null && poHeader.containsKey(key) && poHeader.get(key) != null && !"".equals(String.valueOf(poHeader.get(key)))) {
                    value = poHeader.get(key);
                } else if (line != null && line.containsKey(key)) {
                    value = line.get(key);
                }

                if ("lineCancelFlag".equalsIgnoreCase(key)) {
                    String s = value == null ? "" : String.valueOf(value);
                    String normalized;
                    if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t") || s.equalsIgnoreCase("1") || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes")) {
                        normalized = "yes";
                    } else if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("f") || s.equalsIgnoreCase("0") || s.equalsIgnoreCase("n") || s.equalsIgnoreCase("no")) {
                        normalized = "no";
                    } else {
                        if (s.equalsIgnoreCase("y") || s.equalsIgnoreCase("Y")) normalized = "yes";
                        else if (s.equalsIgnoreCase("n") || s.equalsIgnoreCase("N")) normalized = "no";
                        else normalized = s;
                    }
                    r.createCell(c++).setCellValue(normalized);
                    continue;
                }

                if ("descopeQty".equalsIgnoreCase(key)) {
                    if (value == null || "".equals(String.valueOf(value))) {
                        r.createCell(c++).setCellValue(0.0);
                    } else if (value instanceof Number) {
                        r.createCell(c++).setCellValue(((Number) value).doubleValue());
                    } else {
                        Double maybe = tryParseDouble(String.valueOf(value));
                        if (maybe != null) r.createCell(c++).setCellValue(maybe);
                        else r.createCell(c++).setCellValue(0.0);
                    }
                    continue;
                }

                String headerText = headerLabels.get(i);
                if ("New LINE_PRICE_IN_PO_CURRENCY".equalsIgnoreCase(headerText)) {
                    int altIndex = -1;
                    for (int k = 0; k < headerLabels.size(); k++) {
                        if ("NEW_LINE_PRICE_IN_SAR".equalsIgnoreCase(headerLabels.get(k))) {
                            altIndex = k;
                            break;
                        }
                    }
                    String altKey = altIndex >= 0 ? fieldKeys.get(altIndex) : null;
                    Object altVal = null;
                    if (poHeader != null && altKey != null && poHeader.containsKey(altKey) && poHeader.get(altKey) != null) {
                        altVal = poHeader.get(altKey);
                    } else if (altKey != null && line != null && line.containsKey(altKey)) {
                        altVal = line.get(altKey);
                    }
                    if (altVal != null) {
                        if (altVal instanceof Number) {
                            r.createCell(c++).setCellValue(((Number) altVal).doubleValue());
                        } else {
                            Double maybe = tryParseDouble(String.valueOf(altVal));
                            if (maybe != null) r.createCell(c++).setCellValue(maybe);
                            else r.createCell(c++).setCellValue(String.valueOf(altVal));
                        }
                        continue;
                    }
                }

                if ("approvedDate".equalsIgnoreCase(key) || "createdDate".equalsIgnoreCase(key) || "creationDate".equalsIgnoreCase(key)) {
                    Timestamp ts = null;
                    if (value instanceof Timestamp) ts = (Timestamp) value;
                    else if (value instanceof java.util.Date) ts = new Timestamp(((java.util.Date) value).getTime());
                    if (ts != null) {
                        Cell cell = r.createCell(c++);
                        cell.setCellValue(new java.util.Date(ts.getTime()));
                        cell.setCellStyle(dateCellStyle);
                        continue;
                    } else {
                        r.createCell(c++).setCellValue("");
                        continue;
                    }
                }

                if (value == null) {
                    r.createCell(c++).setCellValue("");
                } else if (value instanceof Number) {
                    double dv = ((Number) value).doubleValue();
                    r.createCell(c++).setCellValue(dv);
                } else {
                    String s = String.valueOf(value);
                    Double maybe = tryParseDouble(s);
                    if (maybe != null) r.createCell(c++).setCellValue(maybe);
                    else r.createCell(c++).setCellValue(s);
                }
            }
        }
        return startRow;
    }

    // Safe getters: check whether the ResultSet had the column, else return null
    private static Object safeGetObject(ResultSet rs, String col, Set<String> rsColsLower) {
        try {
            if (col == null) return null;
            if (!rsColsLower.contains(col.toLowerCase(Locale.ROOT))) {
                String snake = toSnake(col);
                if (snake != null && rsColsLower.contains(snake.toLowerCase(Locale.ROOT))) {
                    return rs.getObject(snake);
                }
                String upper = col.toUpperCase(Locale.ROOT);
                if (rsColsLower.contains(upper.toLowerCase(Locale.ROOT))) {
                    return rs.getObject(upper);
                }
                return null;
            }
            return rs.getObject(col);
        } catch (SQLException e) {
            return null;
        }
    }

    private static String safeGetString(ResultSet rs, String col, Set<String> rsColsLower) {
        Object o = safeGetObject(rs, col, rsColsLower);
        if (o == null) {
            try {
                String snake = toSnake(col);
                if (snake != null && rsColsLower.contains(snake.toLowerCase(Locale.ROOT))) {
                    Object oo = rs.getObject(snake);
                    return oo == null ? "" : String.valueOf(oo);
                }
                String upper = col.toUpperCase(Locale.ROOT);
                if (rsColsLower.contains(upper.toLowerCase(Locale.ROOT))) {
                    Object oo = rs.getObject(upper);
                    return oo == null ? "" : String.valueOf(oo);
                }
            } catch (Exception ex) {
                // ignore
            }
            return "";
        }
        return String.valueOf(o);
    }

    private static Object getFirstAvailableObject(ResultSet rs, Set<String> rsColsLower, String... candidates) {
        if (candidates == null) return null;
        for (String cand : candidates) {
            if (cand == null) continue;
            try {
                if (rsColsLower.contains(cand.toLowerCase(Locale.ROOT))) {
                    Object o = rs.getObject(cand);
                    if (o != null) return o;
                }
                String snake = toSnake(cand);
                if (snake != null && rsColsLower.contains(snake.toLowerCase(Locale.ROOT))) {
                    Object o = rs.getObject(snake);
                    if (o != null) return o;
                }
                String upper = cand.toUpperCase(Locale.ROOT);
                if (rsColsLower.contains(upper.toLowerCase(Locale.ROOT))) {
                    Object o = rs.getObject(upper);
                    if (o != null) return o;
                }
            } catch (SQLException e) {
                // ignore and continue
            }
        }
        return null;
    }

    private static String getFirstAvailableString(ResultSet rs, Set<String> rsColsLower, String... candidates) {
        Object o = getFirstAvailableObject(rs, rsColsLower, candidates);
        return o == null ? "" : String.valueOf(o);
    }

    private static Double tryParseDouble(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    // Find column index for a given field key using common variants; returns index (1-based) or -1
    private static int findIndexForKey(String key, Map<String, Integer> colLabelToIndex) {
        if (key == null) return -1;
        String lower = key.toLowerCase(Locale.ROOT);
        if (colLabelToIndex.containsKey(lower)) return colLabelToIndex.get(lower);
        String snake = toSnake(key);
        if (snake != null) {
            String snakeLower = snake.toLowerCase(Locale.ROOT);
            if (colLabelToIndex.containsKey(snakeLower)) return colLabelToIndex.get(snakeLower);
        }
        String upper = key.toUpperCase(Locale.ROOT);
        String upperLower = upper.toLowerCase(Locale.ROOT);
        if (colLabelToIndex.containsKey(upperLower)) return colLabelToIndex.get(upperLower);

        // additional common variants
        String alt1 = key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT); // e.g. poNumber -> po_number
        if (colLabelToIndex.containsKey(alt1)) return colLabelToIndex.get(alt1);

        // not found
        return -1;
    }

    /**
     * Find index for a field key but also try configured synonyms (e.g. createdDate -> creation_date)
     */
    private static int findIndexForKeyWithSynonyms(String key, Map<String, Integer> colLabelToIndex) {
        int idx = findIndexForKey(key, colLabelToIndex);
        if (idx > 0) return idx;

        // try configured synonyms
        String[] syns = FIELD_SYNONYMS.get(key);
        if (syns != null) {
            for (String s : syns) {
                if (s == null) continue;
                String sLower = s.toLowerCase(Locale.ROOT);
                if (colLabelToIndex.containsKey(sLower)) return colLabelToIndex.get(sLower);
            }
        }

        return -1;
    }

    // small helper to get string by index safely
    private static String safeRsGetString(ResultSet rs, int index) {
        try {
            String s = rs.getString(index);
            return s;
        } catch (SQLException e) {
            return "";
        }
    }

    private static String toSnake(String camel) {
        if (camel == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('_').append(Character.toUpperCase(ch));
            } else {
                sb.append(Character.toUpperCase(ch));
            }
        }
        return sb.toString().replaceAll("__+", "_");
    }
}