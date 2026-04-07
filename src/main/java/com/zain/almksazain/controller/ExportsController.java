package com.zain.almksazain.controller;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.zain.almksazain.services.PurchaseOrderExportService;
import com.zain.almksazain.specs.QueryFilterBuilder;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ExportsController {
    // private static final Logger logger = LoggerFactory.getLogger(ExportsController.class);
     private static final int DEFAULT_FETCH_SIZE = 1000;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
     private  PurchaseOrderExportService exportService;




  @PostMapping(value = "/reports/v2/capitalizationReport/export")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public void exportCapitalizationReport(
            @RequestBody String req,
            HttpServletResponse response
    ) throws IOException {

        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

        // Whitelist and map searchable columns to SQL
        Map<String, String> searchableColumns = new HashMap<>();
        searchableColumns.put("requestId", "DCC.recordNo");
        searchableColumns.put("poNumber", "DCC.poNumber");
        searchableColumns.put("poLineNumber", "LN2.lineNumber");
        searchableColumns.put("uplLineNumber", "LN2.uplLineNumber");
        searchableColumns.put("siteId", "LN2.locationName");
        searchableColumns.put("linkId", "LN2.linkId");
        searchableColumns.put("isd", "LN2.dateInService");
        searchableColumns.put("region", "rg.regionName");
        searchableColumns.put("siteTypeName", "siteType.siteTypeName");
        searchableColumns.put("projectName", "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END)");
        searchableColumns.put("description", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END)");
        searchableColumns.put("quantity", "LN2.deliveredQty");
        searchableColumns.put("partNumber", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END)");
        searchableColumns.put("itemSerializedStatus",
                "(CASE " +
                        "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('YES','Y','TRUE','1') THEN 'YES' " +
                        "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('NO','N','FALSE','0') THEN 'NO' " +
                        "ELSE NULL END)");
        searchableColumns.put("serialNumber", "LN2.serialNumber");
        searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
        searchableColumns.put("faBookingAmount", "(upl.uplLineUnitPrice * LN2.deliveredQty)");
        searchableColumns.put("currency", "'SAR'");
        searchableColumns.put("tagNumber", "LN2.tagNumber");
        searchableColumns.put("receiveddate", "rec.approvedDate");
        searchableColumns.put("recordNo", "DCC.recordNo");

        // Define your numeric columns (exact-match / IN behavior)
        Set<String> numericColumns = new HashSet<>(Arrays.asList(
                "requestId", "poLineNumber", "uplLineNumber", "sequenceNo", "quantity"
        ));


        // control keys
        Set<String> controlKeys = new HashSet<>(Arrays.asList(
                "receivedDateFrom", "receivedDateTo", "isdFrom", "isdTo",
                "page", "size", "sort", "filters", "columnName", "searchQuery"
        ));

        JsonObject filtersObj = new JsonObject();
        if (obj.has("filters") && obj.get("filters").isJsonObject()) {
            filtersObj = obj.getAsJsonObject("filters");
        } else {
            for (Map.Entry<String, JsonElement> ent : obj.entrySet()) {
                String key = ent.getKey();
                if (controlKeys.contains(key)) continue;
                JsonElement val = ent.getValue();
                if (val == null || val.isJsonNull()) continue;
                if (val.isJsonPrimitive()) {
                    filtersObj.add(key, val);
                }
            }
            if (obj.has("columnName") && obj.has("searchQuery")) {
                String col = obj.get("columnName").getAsString();
                String q = obj.get("searchQuery").getAsString();
                if (col != null && !col.trim().isEmpty() && q != null && !q.trim().isEmpty()) {
                    filtersObj.add(col, new JsonPrimitive(q));
                }
            }
        }

        // Build WHERE clause and params
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : filtersObj.entrySet()) {
            String columnKey = entry.getKey();
            if (!searchableColumns.containsKey(columnKey)) continue;
            String rawValue = entry.getValue().getAsString();
            if (rawValue == null) continue;
            rawValue = rawValue.trim();
            if (rawValue.isEmpty()) continue;

            String sqlCol = searchableColumns.get(columnKey);

            if ("receiveddate".equals(columnKey) || "isd".equals(columnKey)) {
                continue; // handled separately as date ranges
            }

            if (rawValue.contains(",")) {
                String[] tokens = Arrays.stream(rawValue.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
                if (tokens.length == 0) continue;
                if (numericColumns.contains(columnKey)) {
                    whereClause.append(" AND ").append(sqlCol).append(" IN (")
                            .append(String.join(",", Collections.nCopies(tokens.length, "?"))).append(") ");
                    for (String t : tokens) params.add(t);
                } else {
                    whereClause.append(" AND (");
                    for (int i = 0; i < tokens.length; i++) {
                        if (i > 0) whereClause.append(" OR ");
                        whereClause.append("LOWER(").append(sqlCol).append(") LIKE LOWER(?)");
                        params.add("%" + tokens[i] + "%");
                    }
                    whereClause.append(") ");
                }
            } else {
                if (numericColumns.contains(columnKey)) {
                    whereClause.append(" AND ").append(sqlCol).append(" = ? ");
                    params.add(rawValue);
                } else {
                    whereClause.append(" AND LOWER(").append(sqlCol).append(") LIKE LOWER(?) ");
                    params.add("%" + rawValue + "%");
                }
            }
        }

        // Date ranges: receivedDate (rec.approvedDate)
        String receivedDateFrom = obj.has("receivedDateFrom") ? obj.get("receivedDateFrom").getAsString() : "";
        String receivedDateTo = obj.has("receivedDateTo") ? obj.get("receivedDateTo").getAsString() : "";
        receivedDateFrom = convertToSqlDate(receivedDateFrom);
        receivedDateTo = convertToSqlDate(receivedDateTo);
        if (!receivedDateFrom.isEmpty() && !receivedDateTo.isEmpty()) {
            whereClause.append(" AND DATE(rec.approvedDate) BETWEEN ? AND ? ");
            params.add(receivedDateFrom);
            params.add(receivedDateTo);
        } else if (!receivedDateFrom.isEmpty()) {
            whereClause.append(" AND DATE(rec.approvedDate) >= ? ");
            params.add(receivedDateFrom);
        } else if (!receivedDateTo.isEmpty()) {
            whereClause.append(" AND DATE(rec.approvedDate) <= ? ");
            params.add(receivedDateTo);
        }

        // Date ranges: isd (LN2.dateInService)
        String isdFrom = obj.has("isdFrom") ? obj.get("isdFrom").getAsString() : "";
        String isdTo = obj.has("isdTo") ? obj.get("isdTo").getAsString() : "";
        isdFrom = convertToSqlDate(isdFrom);
        isdTo = convertToSqlDate(isdTo);
        if (!isdFrom.isEmpty() && !isdTo.isEmpty()) {
            whereClause.append(" AND DATE(LN2.dateInService) BETWEEN ? AND ? ");
            params.add(isdFrom);
            params.add(isdTo);
        } else if (!isdFrom.isEmpty()) {
            whereClause.append(" AND DATE(LN2.dateInService) >= ? ");
            params.add(isdFrom);
        } else if (!isdTo.isEmpty()) {
            whereClause.append(" AND DATE(LN2.dateInService) <= ? ");
            params.add(isdTo);
        }

        // Base SQL and final SQL (same logic you had)
        String baseSql = " FROM tb_DCC DCC " +
                "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber " +
                "JOIN ( " +
                "    SELECT t.acceptanceRequestRecordNo, MAX(t.recordNo) AS recordNo " +
                "    FROM tb_Category_Approval_Requests t " +
                "    WHERE t.status = 'approved' AND t.received = 1 " +
                "    GROUP BY t.acceptanceRequestRecordNo " +
                ") AR_latest ON DCC.recordNo = AR_latest.acceptanceRequestRecordNo " +
                "JOIN tb_Category_Approval_Requests AR ON AR.recordNo = AR_latest.recordNo " +
                "LEFT JOIN ( " +
                "    SELECT r.categoryApprovalRequestId, MAX(r.approvedDate) AS approvedDate " +
                "    FROM tb_AcceptanceRequest_Receipt r " +
                "    WHERE r.approvalStatus = 'received' " +
                "    GROUP BY r.categoryApprovalRequestId " +
                ") rec ON AR.recordNo = rec.categoryApprovalRequestId " +
                "JOIN tb_DCC_LN LN2 ON DCC.recordNo = LN2.dccId " +
                "LEFT JOIN tb_PurchaseOrderUPL upl ON DCC.poNumber = upl.poNumber AND LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber " +
                "LEFT JOIN tb_Site site ON LN2.locationName COLLATE utf8mb4_general_ci = site.siteId COLLATE utf8mb4_general_ci " +
                "LEFT JOIN tb_Site_Type siteType ON site.siteTypeId COLLATE utf8mb4_general_ci = siteType.recordNo COLLATE utf8mb4_general_ci " +
                "LEFT JOIN tb_Region rg ON site.regionId COLLATE utf8mb4_general_ci = rg.recordNo COLLATE utf8mb4_general_ci " +
                "WHERE (0 <> (CASE WHEN LENGTH(LN2.uplLineNumber) > 0 " +
                "  THEN (LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber AND upl.poNumber = DCC.poNumber) " +
                "  ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END)) " +
                "AND DCC.status = 'approved-received' " +
                whereClause.toString();

        String groupBy = " GROUP BY LN2.recordNo ";
        String sql = "SELECT " +
                "DCC.recordNo AS requestNo, " +
                "DCC.poNumber AS poNumber, " +
                "LN2.lineNumber AS poLineNumber, " +
                "LN2.uplLineNumber AS uplLineNumber, " +
                "LN2.locationName AS siteId, " +
                "LN2.linkId AS linkId, " +
                "LN2.dateInService AS isd, " +
                "rg.regionName AS region, " +
                "siteType.siteTypeName AS siteTypeName, " +
                "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END) AS projectName, " +
                "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END) AS description, " +
                "LN2.deliveredQty AS quantity, " +
                "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END) AS partNumber, " +
                " (CASE " +
                "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('YES','Y','TRUE','1') THEN 'YES' " +
                "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('NO','N','FALSE','0') THEN 'NO' " +
                "ELSE NULL END) AS itemSerializedStatus, " +
                "LN2.serialNumber AS serialNumber, " +
                "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, " +
                "(upl.uplLineUnitPrice * LN2.deliveredQty) AS faBookingAmount, " +
                "'SAR' AS currency, " +
                "LN2.tagNumber AS tagNumber, " +
                "rec.approvedDate AS receiveddate " +
                baseSql +
                groupBy;

        List<String> columns = Arrays.asList(
                "sequenceNo", "requestNo", "poNumber", "poLineNumber", "uplLineNumber", "siteId", "linkId", "isd",
                "region", "siteTypeName", "projectName", "description",
                "quantity", "partNumber", "itemSerializedStatus", "serialNumber",
                "uplItemCategoryCodeDescription", "faBookingAmount", "currency", "tagNumber", "receiveddate"
        );

        List<String> headerNames = Arrays.asList(
                "Sequence No",
                "Request No",
                "PO Number",
                "PO Line",
                "UPL Line",
                "Site ID",
                "Link ID",
                "ISD",
                "Region",
                "Site Type",
                "Project Name",
                "Description",
                "Quantity",
                "Part Number",
                "Item Serialized [Yes/No]",
                "Serial Number",
                "Category Description",
                "FA Booking Amount",
                "PO Currency",
                "TAG Number",
                "Received Date"
        );

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=capitalization_report.xlsx");

        // Try to set session sql_mode like before (MySQL-specific) - keep existing behavior
        try {
            jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");
        } catch (Exception ignore) {
            // ignore if DB doesn't support or user lacks privileges
        }

        try (Connection conn = dataSource.getConnection()) {
            // Try to enable server-side cursor behavior (Postgres/others)
            try {
                conn.setAutoCommit(false);
            } catch (Exception ignore) {
            }

            try (PreparedStatement ps = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                // Set fetch size to stream results rather than materialize all
                try {
                    ps.setFetchSize(DEFAULT_FETCH_SIZE);
                } catch (Exception ignore) {
                }

                // bind params
                int idx = 1;
                if (params != null && !params.isEmpty()) {
                    for (Object p : params) {
                        ps.setObject(idx++, p);
                    }
                }

                // Prepare workbook and styles before executing query
                try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                    Sheet sheet = workbook.createSheet("Capitalization Report");

                    // Write header row
                    Row header = sheet.createRow(0);
                    for (int i = 0; i < headerNames.size(); i++) {
                        header.createCell(i).setCellValue(headerNames.get(i));
                    }

                    AtomicInteger rowIdx = new AtomicInteger(1);
                    AtomicInteger sequenceNo = new AtomicInteger(1);

                    // Excel date cell style: dd-mmm-yyyy
                    CellStyle dateCellStyle = workbook.createCellStyle();
                    CreationHelper createHelper = workbook.getCreationHelper();
                    short dateFormat = createHelper.createDataFormat().getFormat("dd-mmm-yyyy");
                    dateCellStyle.setDataFormat(dateFormat);
                    dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData rsMd = rs.getMetaData();
                        Set<String> rsColsLower = new HashSet<>();
                        for (int i = 1; i <= rsMd.getColumnCount(); i++) {
                            String label = rsMd.getColumnLabel(i);
                            if (label != null) rsColsLower.add(label.toLowerCase(Locale.ROOT));
                        }

                        while (rs.next()) {
                            Row row = sheet.createRow(rowIdx.getAndIncrement());
                            for (int i = 0; i < columns.size(); i++) {
                                String colName = columns.get(i);
                                Cell cell = row.createCell(i);
                                if ("sequenceNo".equals(colName)) {
                                    cell.setCellValue(sequenceNo.getAndIncrement());
                                } else if ("receiveddate".equals(colName) || "isd".equals(colName)) {
                                    Timestamp ts = null;
                                    try {
                                        ts = rs.getTimestamp(colName);
                                    } catch (SQLException ignored) {
                                        // some DB drivers may require label-case or different label; fallback: try lower-case label
                                        try {
                                            ts = rs.getTimestamp(colName.toLowerCase(Locale.ROOT));
                                        } catch (Exception e) {
                                            ts = null;
                                        }
                                    }
                                    if (ts != null) {
                                        cell.setCellType(CellType.NUMERIC);
                                        cell.setCellValue(new java.util.Date(ts.getTime()));
                                        cell.setCellStyle(dateCellStyle);
                                    } else {
                                        cell.setBlank();
                                    }
                                } else {
                                    String val;
                                    try {
                                        val = rs.getString(colName);
                                    } catch (SQLException ex) {
                                        // fallback to lower-case label
                                        try {
                                            val = rs.getString(colName.toLowerCase(Locale.ROOT));
                                        } catch (Exception ex2) {
                                            val = null;
                                        }
                                    }
                                    cell.setCellValue(val == null ? "" : val);
                                }
                            }
                        }
                    }

                    // Write workbook to buffered output
                    try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream(), 64 * 1024)) {
                        workbook.write(bos);
                        bos.flush();
                    }
                    response.flushBuffer();
                    workbook.dispose();
                }
            }
        } catch (Exception e) {
            try {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.getWriter().write("Excel export failed: " + e.getMessage());
                response.getWriter().flush();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
   private String convertToSqlDate(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        try {
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String[] patterns = {"dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy"};
            for (String pattern : patterns) {
                try {
                    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(pattern);
                    LocalDate date = LocalDate.parse(input, inputFormatter);
                    return date.format(outputFormatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            throw new IllegalArgumentException("Invalid date format for input: " + input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for input: " + input + ". Supported formats: dd-MM-yyyy, yyyy-MM-dd, MM/dd/yyyy");
        }
    }



//ITEM CODE EXPORT
  @PostMapping(value = "/reports/getAllItemCodeSubstitutes/export")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public void exportItemCodeSubstitutes(@RequestBody String req, HttpServletResponse response) throws IOException {
        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

        // Allowed columns mapping (whitelist) - map frontend keys to DB columns
        Map<String, String> allowedColumns = new HashMap<>();
        allowedColumns.put("recordno", "recordNo");
        allowedColumns.put("record_no", "recordNo");
        allowedColumns.put("recorddatetime", "recordDateTime");
        allowedColumns.put("record_date_time", "recordDateTime");
        allowedColumns.put("itemcode", "itemCode");
        allowedColumns.put("relateditemcode", "relatedItemCode");
        allowedColumns.put("reciprocalflag", "reciprocalFlag");
        allowedColumns.put("createdby", "createdBy");
        allowedColumns.put("createddatetime", "createdDatetime");
        allowedColumns.put("created_datetime", "createdDatetime");
        allowedColumns.put("updatedby", "updatedBy");
        allowedColumns.put("updateddatetime", "updatedDateTime");

        String whereClause = " WHERE 1=1";
        List<Object> params = new ArrayList<>();

        // recordNo filter (top-level)
        Integer recordNo = obj.has("recordNo") && !obj.get("recordNo").isJsonNull() ? obj.get("recordNo").getAsInt() : 0;
        if (recordNo != null && recordNo != 0) {
            whereClause += " AND recordNo = ?";
            params.add(recordNo);
        }

        // legacy single-field search: columnName + searchQuery, but operator-aware now
        String columnName = obj.has("columnName") && !obj.get("columnName").isJsonNull() ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull() ? obj.get("searchQuery").getAsString() : "";
        String searchOperator = obj.has("searchOperator") && !obj.get("searchOperator").isJsonNull() ? obj.get("searchOperator").getAsString() : null;

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            String colKey = columnName.trim().toLowerCase();
            String mapped = allowedColumns.get(colKey);
            if (mapped != null) {
                QueryFilterBuilder.OperatorAndValues ov = new QueryFilterBuilder.OperatorAndValues();
                ov.operator = (searchOperator != null && !searchOperator.isBlank()) ? searchOperator.trim().toLowerCase() : null;
                ov.values = Collections.singletonList(searchQuery);
                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) {
                    whereClause += " AND (" + fragment + ")";
                }
            }
        }

        // filterBy - multi-filter support (operator-aware)
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterBy = obj.getAsJsonObject("filterBy");
            for (Map.Entry<String, JsonElement> entry : filterBy.entrySet()) {
                String rawKey = entry.getKey();
                if (rawKey == null) continue;
                String key = rawKey.trim().toLowerCase();
                String mapped = allowedColumns.get(key);
                if (mapped == null) {
                    // skip unknown keys
                    continue;
                }
                QueryFilterBuilder.OperatorAndValues ov = QueryFilterBuilder.normalizeOperatorAndValuesFromJson(entry.getValue());
                if (ov.values == null || ov.values.isEmpty()) continue;

                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) {
                    whereClause += " AND (" + fragment + ")";
                }
            }
        }

        // Build final SQL - export all matching rows (ignore pagination)
        String sql = "SELECT recordNo, itemCode, relatedItemCode, reciprocalFlag, createdBy, createdDatetime, updatedBy, updatedDateTime " +
                "FROM tb_ItemCodeSubstitute " + whereClause + " ORDER BY recordNo DESC";

        // Excel setup
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = "item_code_substitutes_export.xlsx";
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        // Columns & headers arranged: Record No, Item Code, Related Item Code, Reciprocal Flag, Created By, Created Datetime, Updated By, Updated Datetime
        List<String> columns = Arrays.asList("recordNo", "itemCode", "relatedItemCode",
                "reciprocalFlag", "createdBy", "createdDatetime", "updatedBy", "updatedDateTime");
        List<String> headers = Arrays.asList("Record No", "Item Code", "Related Item Code",
                "Reciprocal Flag", "Created By", "Created Datetime", "Updated By", "Updated Datetime");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("ItemCodeSubstitutes");

            // Header
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            AtomicInteger rowIdx = new AtomicInteger(1);

            // Date style for created/updated datetime columns (show as dd-mmm-yyyy)
            CellStyle dateCellStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            short dateFormat = createHelper.createDataFormat().getFormat("dd-mmm-yyyy");
            dateCellStyle.setDataFormat(dateFormat);
            dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

            // Query and write rows
            if (params.isEmpty()) {
                jdbcTemplate.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    Row row = sheet.createRow(rowIdx.getAndIncrement());
                    for (int i = 0; i < columns.size(); i++) {
                        String col = columns.get(i);
                        Cell cell = row.createCell(i);
                        if ("createdDatetime".equals(col) || "updatedDateTime".equals(col)) {
                            Timestamp ts = rs.getTimestamp(col);
                            if (ts != null) {
                                cell.setCellType(CellType.NUMERIC);
                                cell.setCellValue(new java.util.Date(ts.getTime()));
                                cell.setCellStyle(dateCellStyle);
                            } else {
                                cell.setBlank();
                            }
                        } else if ("recordNo".equals(col)) {
                            long rn = rs.getLong(col);
                            if (rs.wasNull()) {
                                cell.setCellValue("");
                            } else {
                                cell.setCellValue(rn);
                            }
                        } else {
                            String val = rs.getString(col);
                            cell.setCellValue(val == null ? "" : val);
                        }
                    }
                });
            } else {
                jdbcTemplate.query(sql, params.toArray(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    Row row = sheet.createRow(rowIdx.getAndIncrement());
                    for (int i = 0; i < columns.size(); i++) {
                        String col = columns.get(i);
                        Cell cell = row.createCell(i);
                        if ("createdDatetime".equals(col) || "updatedDateTime".equals(col)) {
                            Timestamp ts = rs.getTimestamp(col);
                            if (ts != null) {
                                cell.setCellType(CellType.NUMERIC);
                                cell.setCellValue(new java.util.Date(ts.getTime()));
                                cell.setCellStyle(dateCellStyle);
                            } else {
                                cell.setBlank();
                            }
                        } else if ("recordNo".equals(col)) {
                            long rn = rs.getLong(col);
                            if (rs.wasNull()) {
                                cell.setCellValue("");
                            } else {
                                cell.setCellValue(rn);
                            }
                        } else {
                            String val = rs.getString(col);
                            cell.setCellValue(val == null ? "" : val);
                        }
                    }
                });
            }

            // Write workbook out
            workbook.write(response.getOutputStream());
            response.flushBuffer();
            workbook.dispose();
        } catch (Exception e) {
            try {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.getWriter().write("Excel export failed: " + e.getMessage());
                response.getWriter().flush();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
        @PostMapping(value = "/reports/getNestedPurchaseOrders/export")
    public void exportPurchaseOrdersNested(@RequestBody String req, HttpServletResponse response) throws IOException {
        try {
            JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

            // Whitelist columns mapping (input key -> SQL expression)
            Map<String, String> searchableColumns = new HashMap<>();
            searchableColumns.put("poNumber", "PO.poNumber");
            searchableColumns.put("vendorNumber", "PO.vendorNumber");
            searchableColumns.put("supplierId", "PO.vendorNumber"); // alias
            searchableColumns.put("status", "PO.status");
            searchableColumns.put("currency", "PO.currency");
            searchableColumns.put("itemPartNumber", "PO.itemPartNumber");
            searchableColumns.put("poLineDescription", "PO.poLineDescription");
            searchableColumns.put("lineNumber", "PO.lineNumber");
            searchableColumns.put("recordNo", "PO.recordNo");
            searchableColumns.put("lineCancelFlag", "PO.lineCancelFlag");

            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "recordNo", "lineNumber", "vendorNumber", "supplierId", "poNumber"
            ));

            Set<String> controlKeys = new HashSet<>(Arrays.asList(
                    "page", "size", "sort", "filters", "columnName", "searchQuery",
                    "poDateFrom", "poDateTo", "format"
            ));

            // Build filtersObj
            JsonObject filtersObj = new JsonObject();
            if (obj.has("filters") && obj.get("filters").isJsonObject()) {
                filtersObj = obj.getAsJsonObject("filters");
            } else {
                for (Map.Entry<String, JsonElement> ent : obj.entrySet()) {
                    String key = ent.getKey();
                    if (controlKeys.contains(key)) continue;
                    JsonElement val = ent.getValue();
                    if (val == null || val.isJsonNull()) continue;
                    if (val.isJsonPrimitive()) {
                        filtersObj.add(key, val);
                    }
                }
                // legacy single-filter style
                if (obj.has("columnName") && obj.has("searchQuery")) {
                    String col = safeGetAsString(obj, "columnName");
                    String q = safeGetAsString(obj, "searchQuery");
                    if (col != null && !col.trim().isEmpty() && q != null && !q.trim().isEmpty()) {
                        filtersObj.add(col, new JsonPrimitive(q));
                    }
                }
            }

            // Build WHERE fragment (no leading WHERE) and params
            StringBuilder whereFrag = new StringBuilder();
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : filtersObj.entrySet()) {
                String columnKey = entry.getKey();
                if (!searchableColumns.containsKey(columnKey)) {
                    continue;
                }
                String rawValue = entry.getValue().getAsString();
                if (rawValue == null) continue;
                rawValue = rawValue.trim();
                if (rawValue.isEmpty()) continue;

                String sqlCol = searchableColumns.get(columnKey);

                if (rawValue.contains(",")) {
                    String[] tokens = Arrays.stream(rawValue.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toArray(String[]::new);
                    if (tokens.length == 0) continue;

                    if (numericColumns.contains(columnKey)) {
                        whereFrag.append(" AND ").append(sqlCol).append(" IN (")
                                .append(String.join(",", Collections.nCopies(tokens.length, "?"))).append(") ");
                        for (String t : tokens) params.add(t);
                    } else {
                        whereFrag.append(" AND (");
                        for (int i = 0; i < tokens.length; i++) {
                            if (i > 0) whereFrag.append(" OR ");
                            whereFrag.append("LOWER(").append(sqlCol).append(") LIKE LOWER(?)");
                            params.add("%" + tokens[i] + "%");
                        }
                        whereFrag.append(") ");
                    }
                } else {
                    if (numericColumns.contains(columnKey)) {
                        whereFrag.append(" AND ").append(sqlCol).append(" = ? ");
                        params.add(rawValue);
                    } else {
                        whereFrag.append(" AND LOWER(").append(sqlCol).append(") LIKE LOWER(?) ");
                        params.add("%" + rawValue + "%");
                    }
                }
            }

            // PO date range support (basic pass-through of date string)
            String poDateFrom = obj.has("poDateFrom") ? convertToSqlDate(safeGetAsString(obj, "poDateFrom")) : "";
            String poDateTo = obj.has("poDateTo") ? convertToSqlDate(safeGetAsString(obj, "poDateTo")) : "";
            if (!poDateFrom.isEmpty() && !poDateTo.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) BETWEEN ? AND ? ");
                params.add(poDateFrom);
                params.add(poDateTo);
            } else if (!poDateFrom.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) >= ? ");
                params.add(poDateFrom);
            } else if (!poDateTo.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) <= ? ");
                params.add(poDateTo);
            }

            // Pagination
            int page = obj.has("page") ? Math.max(1, obj.get("page").getAsInt()) : 1;
            int size = obj.has("size") ? Math.max(1, obj.get("size").getAsInt()) : 20000;

            // Instead of hitting DB twice, we stream directly. If client requested pagination, we pass limit/offset.
            Integer limit = null;
            Integer offset = null;
            if (!(page == 1 && size == 20000)) {
                limit = size;
                offset = (page - 1) * size;
            }

            // Delegate to streaming export service (single query)
            exportService.exportToExcel(whereFrag.toString(), params, response, limit, offset);

        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain; charset=utf-8");
                response.getWriter().write("Excel export failed: " + e.toString());
                response.getWriter().flush();
            }
        }
    }





    // Utility to send a tiny workbook that contains a message when no data matched
    @SuppressWarnings("unused")
    private void sendEmptyWorkbook(HttpServletResponse response, String message) throws IOException {
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(10)) {
            org.apache.poi.ss.usermodel.Sheet s = wb.createSheet("Empty");
            org.apache.poi.ss.usermodel.Row r = s.createRow(0);
            r.createCell(0).setCellValue(message);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"purchase_orders_export.xlsx\"");
            try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream(), 32 * 1024)) {
                wb.write(bos);
                bos.flush();
            }
            response.flushBuffer();
        }
    }    // helpers --------------------------------------------------------------

    private static String safeGetAsString(JsonObject obj, String member) {
        try {
            if (!obj.has(member) || obj.get(member).isJsonNull()) return null;
            return obj.get(member).getAsString();
        } catch (Exception ex) {
            return null;
        }
    }



}
