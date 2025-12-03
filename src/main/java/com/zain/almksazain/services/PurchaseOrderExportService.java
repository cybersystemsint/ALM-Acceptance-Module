package com.zain.almksazain.services;

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
        // Desired columns (DB column names / aliases in camelCase where possible)
        // Extended list to increase chance we select the columns used in the headers below
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
                "vendorName", "vendorNumber", "approvedDate", "createdDate", // keep camel-case variants too
                "typeLookupCode", "currencyCode", "descopedLinePriceInPoCurrency", "newLinePriceInPoCurrency", "prNum",
                "descopedQty", "descoped_qty"
        );

        // Build SELECT clause dynamically based on table metadata
        String selectClause;
        boolean hasPoNumber = false;
        boolean hasLineNumber = false;
        try (Connection conn = dataSource.getConnection()) {
            // collect actual columns in the table (case-insensitive)
            Set<String> tableColsLower = new HashSet<>();
            DatabaseMetaData meta = conn.getMetaData();
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

            hasPoNumber = tableColsLower.contains("ponumber") || tableColsLower.contains("po_number");
            hasLineNumber = tableColsLower.contains("linenumber") || tableColsLower.contains("line_num") || tableColsLower.contains("line_number");

            if (available.isEmpty()) {
                // fallback to select all columns to avoid SQL errors
                selectClause = "PO.*";
            } else {
                // map to PO.<column> using actual column names if available; simplest is to use PO.<name>
                selectClause = available.stream().map(c -> "PO." + c).collect(Collectors.joining(", "));
            }

            
            // Build ORDER BY clause safely: require poNumber; include lineNumber if present
            String orderBy = "";
            if (hasPoNumber) {
                orderBy = " ORDER BY PO.poNumber" + (hasLineNumber ? ", PO.lineNumber" : "");
            } else {
                orderBy = "";
            }

            String sql = "SELECT " + selectClause + " FROM tb_PurchaseOrder PO WHERE 1=1 "
                    + (whereFragment == null ? "" : whereFragment)
                    + orderBy;

            // Prepare response headers (XLSX binary)
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"purchase_orders_export.xlsx\"");

            // Header labels (exact casing and order requested — matching screenshot)
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

            // Corresponding keys we will look up on the PO-header map and line map (camelCase keys / db column names)
            // Order must match headerLabels above.
            List<String> fieldKeys = Arrays.asList(
                    "poNumber",                 // PO_NUMBER
                    "typeLookupCode",           // PO Type
                    "releaseNum",              // RELEASE_NUM
                    "lineNumber",               // LINE_NUM
                    "prNum",                 // PR_NUM
                    "projectName",              // PROJECT_NAME
                    "lineCancelFlag",           // LINE_CANCEL_FLAG
                    "cancelReason",             // CANCEL_REASON
                    "itemPartNumber",           // ITEM_PART_NUMBER
                    "pnSubAllow",               // PN_SUB_ALLOW
                    "countryOfOrigin",          // COUNTRY_OF_ORIGIN
                    "poOrderQuantity",          // PO_ORDER_QUANTITY
                    "descopeQty",               // Descope Qty
                    "poQtyNew",                 // PO_QTY_NEW
                    "quantityReceived",         // QUANTITY_RECEIVED
                    "quantityDueOld",           // QUANTITY_DUE_OLD
                    "quantityDueNew",           // QUANTITY_DUE_NEW
                    "quantityBilled",           // QUANTITY_BILLED
                    "currencyCode",             // CURRENCY_CODE
                    "unitPriceInPoCurrency",    // UNIT_PRICE_IN_PO_CURRENCY
                    "unitPriceInSAR",           // UNIT_PRICE_IN_SAR
                    "linePriceInPoCurrency",    // LINE_PRICE_IN_PO_CURRENCY
                    "linePriceInSAR",           // LINE_PRICE_IN_SAR
                    "descopedLinePriceInPoCurrency", // New LINE_PRICE_IN_PO_CURRENCY  <-- note: we will override this later to use newLinePriceInPoCurrency if needed
                    "newLinePriceInPoCurrency", // NEW_LINE_PRICE_IN_SAR
                    "amountReceived",           // AMOUNT_RECEIVED
                    "amountDue",                // AMOUNT_DUE
                    "amountDueNew",             // AMOUNT_DUE_NEW
                    "amountBilled",             // AMOUNT_BILLED
                    "poLineDescription",        // PO_LINE_DESCRIPTION
                    "organizationName",         // ORGANIZATION_NAME
                    "organizationCode",         // ORGANIZATION_CODE
                    "subinventoryCode",         // SUBINVENTORY_CODE
                    "receiptRouting",           // RECEIPT_ROUTING
                    "authorizationStatus",      // AUTHORIZATION_STATUS
                    "poClosureStatus",          // PO_CLOSURE_STATUS
                    "departmentName",           // DEPARTMENT_NAME
                    "poLineType",               // PO_LINE_TYPE
                    "costCenter",               // COST_CENTER
                    "chargeAccount",            // CHARGE_ACCOUNT
                    "serialControl",            // SERIAL_CONTROL
                    "vendorSerialNumberYn",     // VENDOR_SERIAL_NUMBER_YN
                    "itemType",                 // ITEM_TYPE
                    "itemCategoryInventory",    // ITEM_CATEGORY_INVENTORY
                    "inventoryCategoryDescription", // INVENTORY_CATEGORY_DESCRIPTION
                    "itemCategoryFa",           // ITEM_CATEGORY_FA
                    "faCategoryDescription",    // FA_CATEGORY_DESCRIPTION
                    "itemCategoryPurchasing",   // ITEM_CATEGORY_PURCHASING
                    "purchasingCategoryDescription", // PURCHASING_CATEGORY_DESCRIPTION
                    "vendorName",               // VENDOR_NAME
                    "vendorNumber",             // VENDOR_NUMBER
                    "approvedDate",             // APPROVED_DATE (date)
                    "createdDate"               // CREATION_DATE (date)
            );

            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                Sheet sheet = workbook.createSheet("PO Export");
                int rowIdx = 0;

                // Header row (use requested casing/order)
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
                                rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle, headerLabels, fieldKeys);
                                poLines.clear();
                                poHeader = null;
                            }

                            // capture PO header once
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
                                // createdDate may be named differently in DB (createdDate / creation_date)
                                poHeader.put("createdDate", rsColsLower.contains("createddate") || rsColsLower.contains("creation_date") ? rs.getTimestamp("createdDate") : null);
                                poHeader.put("approvedDate", rsColsLower.contains("approveddate") ? rs.getTimestamp("approvedDate") : null);

                                // authorizationStatus might come as 'authorizationStatus' or 'authorisationStatus'
                                String authorization = getFirstAvailableString(rs, rsColsLower, "authorizationStatus", "authorisationStatus", "authorization_status", "authorisation_status");
                                poHeader.put("authorizationStatus", authorization);

                                // store a few other possible PO-level columns if present
                                poHeader.put("organizationName", safeGetString(rs, "organizationName", rsColsLower));
                                poHeader.put("organizationCode", safeGetString(rs, "organizationCode", rsColsLower));
                                poHeader.put("subinventoryCode", safeGetString(rs, "subinventoryCode", rsColsLower));
                                poHeader.put("receiptRouting", safeGetString(rs, "receiptRouting", rsColsLower));
                                // map both spellings into the canonical key used in headers
                                poHeader.put("poClosureStatus", safeGetString(rs, "poClosureStatus", rsColsLower));
                                poHeader.put("departmentName", safeGetString(rs, "departmentName", rsColsLower));
                            }

                            // build line-level map using only available columns
                            Map<String, Object> line = new HashMap<>();
                            line.put("lineNumber", safeGetString(rs, "lineNumber", rsColsLower));
                            line.put("itemPartNumber", safeGetString(rs, "itemPartNumber", rsColsLower));
                            line.put("poLineDescription", safeGetString(rs, "poLineDescription", rsColsLower));
                            line.put("poOrderQuantity", safeGetObject(rs, "poOrderQuantity", rsColsLower));
                            // Descope Qty: attempt multiple possible DB names (descopeQty, descopedQty, descoped_qty, descopedqty)
                            Object descope = getFirstAvailableObject(rs, rsColsLower, "descopeQty", "descopedQty", "descoped_qty", "descope_qty", "descopedqty");
                            // ensure a numeric 0.0 if null so export won't leave an empty cell
                            if (descope == null) {
                                descope = 0.0;
                            }
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
                            // descopedLinePriceInPoCurrency / newLinePriceInPoCurrency might come with different names in DB
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

                        // flush last PO block
                        if (currentPo != null && !poLines.isEmpty()) {
                            rowIdx = writePoBlock(sheet, rowIdx, poHeader, poLines, dateCellStyle, headerLabels, fieldKeys);
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

                // Special handling for some columns requested:
                // 1) lineCancelFlag: convert booleans / Y/N / true/false to "yes"/"no"
                if ("lineCancelFlag".equalsIgnoreCase(key)) {
                    String s = value == null ? "" : String.valueOf(value);
                    String normalized;
                    if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t") || s.equalsIgnoreCase("1") || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("y")) {
                        normalized = "yes";
                    } else if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("f") || s.equalsIgnoreCase("0") || s.equalsIgnoreCase("n") || s.equalsIgnoreCase("no")) {
                        normalized = "no";
                    } else {
                        // in case GET_NESTED returns "Y"/"N"
                        if (s.equalsIgnoreCase("y") || s.equalsIgnoreCase("Y")) normalized = "yes";
                        else if (s.equalsIgnoreCase("n") || s.equalsIgnoreCase("N")) normalized = "no";
                        else normalized = s; // keep whatever it is (could be empty)
                    }
                    r.createCell(c++).setCellValue(normalized);
                    continue;
                }

                // 1b) Ensure Descope Qty is written as numeric 0.0 when null/empty
                if ("descopeQty".equalsIgnoreCase(key)) {
                    // value may be null or non-numeric; we want numeric 0.0 default
                    if (value == null || "".equals(String.valueOf(value))) {
                        r.createCell(c++).setCellValue(0.0);
                    } else if (value instanceof Number) {
                        r.createCell(c++).setCellValue(((Number) value).doubleValue());
                    } else {
                        Double maybe = tryParseDouble(String.valueOf(value));
                        if (maybe != null) r.createCell(c++).setCellValue(maybe);
                        else r.createCell(c++).setCellValue(0.0); // fallback
                    }
                    continue;
                }

                // 2) "New LINE_PRICE_IN_PO_CURRENCY" header should match values of NEW_LINE_PRICE_IN_SAR (user request)
                // find header text for this column index
                String headerText = headerLabels.get(i);
                if ("New LINE_PRICE_IN_PO_CURRENCY".equalsIgnoreCase(headerText)) {
                    // attempt to get the value from the field corresponding to NEW_LINE_PRICE_IN_SAR
                    String altKey = null;
                    // fieldKeys index for NEW_LINE_PRICE_IN_SAR is expected to be the next one (but safer: find index)
                    int altIndex = -1;
                    for (int k = 0; k < headerLabels.size(); k++) {
                        if ("NEW_LINE_PRICE_IN_SAR".equalsIgnoreCase(headerLabels.get(k))) {
                            altIndex = k;
                            break;
                        }
                    }
                    if (altIndex >= 0) altKey = fieldKeys.get(altIndex);
                    Object altVal = null;
                    if (poHeader != null && altKey != null && poHeader.containsKey(altKey) && poHeader.get(altKey) != null) {
                        altVal = poHeader.get(altKey);
                    } else if (altKey != null && line != null && line.containsKey(altKey)) {
                        altVal = line.get(altKey);
                    }
                    // fallback to existing value if alt missing
                    if (altVal != null) {
                        // write altVal as numeric if possible
                        if (altVal instanceof Number) {
                            r.createCell(c++).setCellValue(((Number) altVal).doubleValue());
                        } else {
                            Double maybe = tryParseDouble(String.valueOf(altVal));
                            if (maybe != null) r.createCell(c++).setCellValue(maybe);
                            else r.createCell(c++).setCellValue(String.valueOf(altVal));
                        }
                        continue;
                    }
                    // else continue to normal handling below (will write original 'value')
                }

                // handle dates specially (approvedDate / createdDate)
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

                // numeric representation where possible
                if (value == null) {
                    r.createCell(c++).setCellValue("");
                } else if (value instanceof Number) {
                    // preserve numeric display as number
                    double dv = ((Number) value).doubleValue();
                    r.createCell(c++).setCellValue(dv);
                } else {
                    // try to parse as double for numeric-looking fields
                    String s = String.valueOf(value);
                    Double maybe = tryParseDouble(s);
                    if (maybe != null) {
                        r.createCell(c++).setCellValue(maybe);
                    } else {
                        r.createCell(c++).setCellValue(s);
                    }
                }
            }
        }
        return startRow;
    }

    // Safe getters: check whether the ResultSet had the column, else return null
    private static Object safeGetObject(ResultSet rs, String col, Set<String> rsColsLower) {
        try {
            if (col == null) return null;
            // try different common db name variants
            if (!rsColsLower.contains(col.toLowerCase(Locale.ROOT))) {
                // try snake_case
                String snake = toSnake(col);
                if (snake != null && rsColsLower.contains(snake.toLowerCase(Locale.ROOT))) {
                    return rs.getObject(snake);
                }
                // try upper snake
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
            // try a few common variants (snake_case / upper case)
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

    // Try multiple candidate column names (returns first non-null value)
    private static Object getFirstAvailableObject(ResultSet rs, Set<String> rsColsLower, String... candidates) {
        if (candidates == null) return null;
        for (String cand : candidates) {
            if (cand == null) continue;
            try {
                if (rsColsLower.contains(cand.toLowerCase(Locale.ROOT))) {
                    Object o = rs.getObject(cand);
                    if (o != null) return o;
                }
                // try snake/upper variants as well
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
        // collapse consecutive underscores
        return sb.toString().replaceAll("__+", "_");
    }
}