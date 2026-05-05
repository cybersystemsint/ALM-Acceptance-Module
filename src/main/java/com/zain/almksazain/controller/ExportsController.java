package com.zain.almksazain.controller;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.zain.almksazain.services.PurchaseOrderExportService;
import com.zain.almksazain.specs.QueryFilterBuilder;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ExportsController {

    private static final Logger logger = LoggerFactory.getLogger(ExportsController.class);
    private static final int DEFAULT_FETCH_SIZE = 1000;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PurchaseOrderExportService exportService;

    // ============================================================================
    // Acceptance Report Export
    // ============================================================================

    @PostMapping(value = "/reports/v2/acceptanceReport/export",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public DeferredResult<ResponseEntity<byte[]>> exportAcceptanceReport(
            @RequestBody String req) {

        // Returns to client immediately — 10 min async timeout
        DeferredResult<ResponseEntity<byte[]>> deferredResult = new DeferredResult<>(600000L);

        JsonObject obj     = JsonParser.parseString(req).getAsJsonObject();
        String poNumber    = obj.has("poNumber")    ? obj.get("poNumber").getAsString()    : "0";
        String columnName  = obj.has("columnName")  ? obj.get("columnName").getAsString()  : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        int limit          = obj.has("limit")       ? obj.get("limit").getAsInt()          : -1;

        Map<String, String> searchableColumns = buildSearchableColumns();
        Set<String> numericColumns            = buildNumericColumns();

        StringBuilder where = new StringBuilder();
        List<Object> whereParams = new ArrayList<>();
        buildWhereClause(obj, poNumber, columnName, searchQuery,
                searchableColumns, numericColumns, where, whereParams);

        // No DISTINCT — AR_latest subquery in buildBaseFrom already
        // guarantees one AR row per DCC. DISTINCT on 36 cols forces full sort = slow.
        String exportSql = "SELECT "
                + "DCC.recordNo        AS requestId, "
                + "DCC.status          AS requestStatus, "
                + "DCC.acceptanceType  AS acceptanceType, "
                + "HD.typeLookUpCode   AS typeLookUpCode, "
                + "DCC.poNumber        AS poNumber, "
                + "HD.releaseNum       AS releaseNumber, "
                + "LN2.lineNumber      AS poLineNumber, "
                + "CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END AS poPartNumber, "
                + "CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END AS poLineDescription, "
                + "CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END AS poItemSerializedStatus, "
                + "'SAR'               AS currency, "
                + "upl.poLineUnitPrice AS unitPrice, "
                + "LN2.locationName    AS siteId, "
                + "rg.regionName       AS region, "
                + "siteType.siteTypeName AS siteTypeName, "
                + "HD.newProjectName   AS newProjectName, "
                + "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y') AS inServiceDate, "
                + "LN2.uplLineNumber   AS uplLineNumber, "
                + "upl.uplLineItemCode AS uplPartNumber, "
                + "upl.uplLineDescription AS uplItemDescription, "
                + "LN2.actualItemCode  AS actualPartNumber, "
                + "upl.uplItemSerialized AS uplItemSerializedStatus, "
                + "LN2.serialNumber    AS serialNumber, "
                + "LN2.tagNumber       AS tagNumber, "
                + "LN2.linkId          AS linkId, "
                + "upl.activeOrPassive AS activeOrPassive, "
                + "upl.zainItemCategoryCode AS uplItemCategoryCode, "
                + "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, "
                + "upl.uplLineUnitPrice AS uplLineUnitPrice, "
                + "LN2.deliveredQty    AS acceptanceUplQty, "
                + "LN2.poAcceptanceQty AS acceptancePoQty, "
                + "(upl.uplLineUnitPrice * LN2.deliveredQty) AS totalAcceptanceAmount, "
                + "HD.vendorName       AS vendorName, "
                + "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y')  AS createdDate, "
                + "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y') AS approvalDate, "
                + "LN2.scopeOfWork     AS scopeOfWork "
                + buildBaseFrom()
                + where
                + " GROUP BY DCC.recordNo, LN2.recordNo"
                + " ORDER BY DCC.recordNo, LN2.recordNo"
                + (limit > 0 ? " LIMIT " + limit : "");

        final List<String> headers     = buildHeaders();
        final List<String> fields      = buildFields();
        final List<Object> finalParams = new ArrayList<>(whereParams);

        final Set<String> numericFields = new HashSet<>(Arrays.asList(
                "requestId", "poLineNumber", "unitPrice", "uplLineUnitPrice",
                "acceptanceUplQty", "acceptancePoQty", "totalAcceptanceAmount"
        ));

        // Process async — Tomcat thread freed immediately
        CompletableFuture.runAsync(() -> {
            SXSSFWorkbook workbook = new SXSSFWorkbook(2000);
            try (Connection conn = dataSource.getConnection()) {
                try { conn.setReadOnly(true);    } catch (Exception ignore) {}
                try { conn.setAutoCommit(false); } catch (Exception ignore) {}
                try (PreparedStatement sqlMode = conn.prepareStatement(
                        "SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))")) {
                    sqlMode.execute();
                } catch (Exception ignore) {}
                // ─

                try (PreparedStatement ps = conn.prepareStatement(
                        exportSql,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY)) {

                    ps.setFetchSize(Integer.MIN_VALUE); // MySQL true streaming

                    int idx = 1;
                    for (Object p : finalParams) ps.setObject(idx++, p);

                    Sheet sheet = workbook.createSheet("Acceptance Report");

                    CellStyle headerStyle = buildHeaderStyle(workbook);
                    CellStyle numberStyle = buildNumberStyle(workbook);

                    Row headerRow = sheet.createRow(0);
                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers.get(i));
                        cell.setCellStyle(headerStyle);
                    }

                    int rowIdx = 1;
                    final int fieldCount = fields.size();

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Row row = sheet.createRow(rowIdx++);
                            for (int i = 0; i < fieldCount; i++) {
                                String field = fields.get(i);
                                Cell cell    = row.createCell(i);
                                if (numericFields.contains(field)) {
                                    double d = rs.getDouble(field);
                                    if (rs.wasNull()) {
                                        cell.setBlank();
                                    } else {
                                        cell.setCellValue(d);
                                        cell.setCellStyle(numberStyle);
                                    }
                                } else {
                                    String val = rs.getString(field);
                                    cell.setCellValue(val != null ? val : "");
                                }
                            }
                        }
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    workbook.write(baos);

                    String filename = "acceptance_report_"
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            + ".xlsx";

                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.setContentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    responseHeaders.add("Content-Disposition", "attachment; filename=" + filename);

                    deferredResult.setResult(new ResponseEntity<>(
                            baos.toByteArray(), responseHeaders, HttpStatus.OK));
                }

            } catch (Exception e) {
                logger.error("Acceptance report export failed", e);
                deferredResult.setErrorResult(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(("Export failed: " + e.getMessage()).getBytes()));
            } finally {
                workbook.dispose();
            }
        });

        return deferredResult;
    }

    // ============================================================================
    // Capitalization Report Export
    // ============================================================================

@PostMapping(value = "/reports/v2/capitalizationReport/export")
public void exportCapitalizationReport(@RequestBody String req,
                                       HttpServletResponse response) throws IOException {

    JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

    Map<String, String> searchableColumns = new HashMap<>();
    searchableColumns.put("requestId",                      "DCC.recordNo");
    searchableColumns.put("poNumber",                       "DCC.poNumber");
    searchableColumns.put("poLineNumber",                   "LN2.lineNumber");
    searchableColumns.put("uplLineNumber",                  "LN2.uplLineNumber");
    searchableColumns.put("siteId",                         "LN2.locationName");
    searchableColumns.put("linkId",                         "LN2.linkId");
    searchableColumns.put("isd",                            "LN2.dateInService");
    searchableColumns.put("region",                         "rg.regionName");
    searchableColumns.put("siteTypeName",                   "siteType.siteTypeName");
    searchableColumns.put("projectName",                    "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END)");
    searchableColumns.put("description",                    "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END)");
    searchableColumns.put("quantity",                       "LN2.deliveredQty");
    searchableColumns.put("partNumber",                     "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END)");
    searchableColumns.put("itemSerializedStatus",
            "(CASE WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('YES','Y','TRUE','1') THEN 'YES' " +
                    "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('NO','N','FALSE','0') THEN 'NO' ELSE NULL END)");
    searchableColumns.put("serialNumber",                   "LN2.serialNumber");
    searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
    searchableColumns.put("faBookingAmount",                "(upl.uplLineUnitPrice * LN2.deliveredQty)");
    searchableColumns.put("currency",                       "'SAR'");
    searchableColumns.put("tagNumber",                      "LN2.tagNumber");
    searchableColumns.put("receiveddate",                   "rec.approvedDate");
    searchableColumns.put("recordNo",                       "DCC.recordNo");

    Set<String> numericColumns = new HashSet<>(Arrays.asList(
            "requestId", "poLineNumber", "uplLineNumber", "sequenceNo", "quantity"
    ));

    Set<String> controlKeys = new HashSet<>(Arrays.asList(
            "poNumber",                                         
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
            if (val.isJsonPrimitive()) filtersObj.add(key, val);
        }
        if (obj.has("columnName") && obj.has("searchQuery")) {
            String col = obj.get("columnName").getAsString();
            String q   = obj.get("searchQuery").getAsString();
            if (col != null && !col.trim().isEmpty() && q != null && !q.trim().isEmpty()) {
                filtersObj.add(col, new JsonPrimitive(q));
            }
        }
    }

    StringBuilder whereClause = new StringBuilder();
    List<Object> params = new ArrayList<>();

    // FIX: handle poNumber with the same exact-match + "0" guard as the fetch endpoint
    String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "0";
    if (!poNumber.equalsIgnoreCase("0") && !poNumber.isEmpty()) {
        whereClause.append(" AND DCC.poNumber = ?");
        params.add(poNumber);
    }

    for (Map.Entry<String, JsonElement> entry : filtersObj.entrySet()) {
        String columnKey = entry.getKey();
        if (!searchableColumns.containsKey(columnKey)) continue;
        String rawValue = entry.getValue().getAsString();
        if (rawValue == null) continue;
        rawValue = rawValue.trim();
        if (rawValue.isEmpty()) continue;

        String sqlCol = searchableColumns.get(columnKey);
        if ("receiveddate".equals(columnKey) || "isd".equals(columnKey)) continue;

        if (rawValue.contains(",")) {
            String[] tokens = Arrays.stream(rawValue.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
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
    String receivedDateFrom = convertToSqlDate(obj.has("receivedDateFrom") ? obj.get("receivedDateFrom").getAsString() : "");
    String receivedDateTo   = convertToSqlDate(obj.has("receivedDateTo")   ? obj.get("receivedDateTo").getAsString()   : "");
    if (!receivedDateFrom.isEmpty() && !receivedDateTo.isEmpty()) {
        whereClause.append(" AND DATE(rec.approvedDate) BETWEEN ? AND ? ");
        params.add(receivedDateFrom); params.add(receivedDateTo);
    } else if (!receivedDateFrom.isEmpty()) {
        whereClause.append(" AND DATE(rec.approvedDate) >= ? ");
        params.add(receivedDateFrom);
    } else if (!receivedDateTo.isEmpty()) {
        whereClause.append(" AND DATE(rec.approvedDate) <= ? ");
        params.add(receivedDateTo);
    }

    String isdFrom = convertToSqlDate(obj.has("isdFrom") ? obj.get("isdFrom").getAsString() : "");
    String isdTo   = convertToSqlDate(obj.has("isdTo")   ? obj.get("isdTo").getAsString()   : "");
    if (!isdFrom.isEmpty() && !isdTo.isEmpty()) {
        whereClause.append(" AND DATE(LN2.dateInService) BETWEEN ? AND ? ");
        params.add(isdFrom); params.add(isdTo);
    } else if (!isdFrom.isEmpty()) {
        whereClause.append(" AND DATE(LN2.dateInService) >= ? ");
        params.add(isdFrom);
    } else if (!isdTo.isEmpty()) {
        whereClause.append(" AND DATE(LN2.dateInService) <= ? ");
        params.add(isdTo);
    }

    String baseSql = " FROM tb_DCC DCC "
            + "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber "
            + "JOIN ( "
            + "    SELECT t.acceptanceRequestRecordNo, MAX(t.recordNo) AS recordNo "
            + "    FROM tb_Category_Approval_Requests t "
            + "    WHERE t.status = 'approved' AND t.received = 1 "
            + "    GROUP BY t.acceptanceRequestRecordNo "
            + ") AR_latest ON DCC.recordNo = AR_latest.acceptanceRequestRecordNo "
            + "JOIN tb_Category_Approval_Requests AR ON AR.recordNo = AR_latest.recordNo "
            + "LEFT JOIN ( "
            + "    SELECT r.categoryApprovalRequestId, MAX(r.approvedDate) AS approvedDate "
            + "    FROM tb_AcceptanceRequest_Receipt r "
            + "    WHERE r.approvalStatus = 'received' "
            + "    GROUP BY r.categoryApprovalRequestId "
            + ") rec ON AR.recordNo = rec.categoryApprovalRequestId "
            + "JOIN tb_DCC_LN LN2 ON DCC.recordNo = LN2.dccId "
            + "LEFT JOIN tb_PurchaseOrderUPL upl ON DCC.poNumber = upl.poNumber AND LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber "
            + "LEFT JOIN tb_Site site ON LN2.locationName COLLATE utf8mb4_general_ci = site.siteId COLLATE utf8mb4_general_ci "
            + "LEFT JOIN tb_Site_Type siteType ON site.siteTypeId COLLATE utf8mb4_general_ci = siteType.recordNo COLLATE utf8mb4_general_ci "
            + "LEFT JOIN tb_Region rg ON site.regionId COLLATE utf8mb4_general_ci = rg.recordNo COLLATE utf8mb4_general_ci "
            + "WHERE (0 <> (CASE WHEN LENGTH(LN2.uplLineNumber) > 0 "
            + "  THEN (LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber AND upl.poNumber = DCC.poNumber) "
            + "  ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END)) "
            + "AND DCC.status = 'approved-received' "
            + whereClause;

    String sql = "SELECT "
            + "DCC.recordNo AS requestNo, "
            + "DCC.poNumber AS poNumber, "
            + "LN2.lineNumber AS poLineNumber, "
            + "LN2.uplLineNumber AS uplLineNumber, "
            + "LN2.locationName AS siteId, "
            + "LN2.linkId AS linkId, "
            + "LN2.dateInService AS isd, "
            + "rg.regionName AS region, "
            + "siteType.siteTypeName AS siteTypeName, "
            + "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END) AS projectName, "
            + "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END) AS description, "
            + "LN2.deliveredQty AS quantity, "
            + "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END) AS partNumber, "
            + "(CASE WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('YES','Y','TRUE','1') THEN 'YES' "
            + "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('NO','N','FALSE','0') THEN 'NO' ELSE NULL END) AS itemSerializedStatus, "
            + "LN2.serialNumber AS serialNumber, "
            + "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, "
            + "(upl.uplLineUnitPrice * LN2.deliveredQty) AS faBookingAmount, "
            + "'SAR' AS currency, "
            + "LN2.tagNumber AS tagNumber, "
            + "rec.approvedDate AS receiveddate "
            + baseSql
            + " GROUP BY LN2.recordNo";

    List<String> columns = Arrays.asList(
            "sequenceNo", "requestNo", "poNumber", "poLineNumber", "uplLineNumber",
            "siteId", "linkId", "isd", "region", "siteTypeName", "projectName",
            "description", "quantity", "partNumber", "itemSerializedStatus",
            "serialNumber", "uplItemCategoryCodeDescription", "faBookingAmount",
            "currency", "tagNumber", "receiveddate"
    );

    List<String> headerNames = Arrays.asList(
            "Sequence No", "Request No", "PO Number", "PO Line", "UPL Line",
            "Site ID", "Link ID", "ISD", "Region", "Site Type", "Project Name",
            "Description", "Quantity", "Part Number", "Item Serialized [Yes/No]",
            "Serial Number", "Category Description", "FA Booking Amount",
            "PO Currency", "TAG Number", "Received Date"
    );

    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader("Content-Disposition", "attachment; filename=capitalization_report.xlsx");

    try {
        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");
    } catch (Exception ignore) {}

    SXSSFWorkbook workbook = new SXSSFWorkbook(500);
    try (Connection conn = dataSource.getConnection()) {
        try { conn.setAutoCommit(false); } catch (Exception ignore) {}

        try (PreparedStatement ps = conn.prepareStatement(sql,
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            try { ps.setFetchSize(DEFAULT_FETCH_SIZE); } catch (Exception ignore) {}

            int idx = 1;
            for (Object p : params) ps.setObject(idx++, p);

            Sheet sheet = workbook.createSheet("Capitalization Report");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headerNames.size(); i++) {
                header.createCell(i).setCellValue(headerNames.get(i));
            }

            CellStyle dateCellStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-mmm-yyyy"));
            dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

            AtomicInteger rowIdx     = new AtomicInteger(1);
            AtomicInteger sequenceNo = new AtomicInteger(1);

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
                            try { ts = rs.getTimestamp(colName); } catch (SQLException ignored) {}
                            if (ts != null) {
                                cell.setCellType(CellType.NUMERIC);
                                cell.setCellValue(new java.util.Date(ts.getTime()));
                                cell.setCellStyle(dateCellStyle);
                            } else {
                                cell.setBlank();
                            }
                        } else {
                            String val = null;
                            try { val = rs.getString(colName); } catch (SQLException ex) {}
                            cell.setCellValue(val == null ? "" : val);
                        }
                    }
                }
            }

            try (BufferedOutputStream bos = new BufferedOutputStream(
                    response.getOutputStream(), 128 * 1024)) {
                workbook.write(bos);
                bos.flush();
            }
            response.flushBuffer();
        }

    } catch (Exception e) {
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Excel export failed: " + e.getMessage());
            response.getWriter().flush();
        }
    } finally {
        workbook.dispose();
    }
}
    // ============================================================================
    // Item Code Substitutes Export
    // ============================================================================

    @PostMapping(value = "/reports/getAllItemCodeSubstitutes/export")
    public void exportItemCodeSubstitutes(@RequestBody String req,
                                          HttpServletResponse response) throws IOException {

        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

        Map<String, String> allowedColumns = new HashMap<>();
        allowedColumns.put("recordno",         "recordNo");
        allowedColumns.put("record_no",        "recordNo");
        allowedColumns.put("recorddatetime",   "recordDateTime");
        allowedColumns.put("record_date_time", "recordDateTime");
        allowedColumns.put("itemcode",         "itemCode");
        allowedColumns.put("relateditemcode",  "relatedItemCode");
        allowedColumns.put("reciprocalflag",   "reciprocalFlag");
        allowedColumns.put("createdby",        "createdBy");
        allowedColumns.put("createddatetime",  "createdDatetime");
        allowedColumns.put("created_datetime", "createdDatetime");
        allowedColumns.put("updatedby",        "updatedBy");
        allowedColumns.put("updateddatetime",  "updatedDateTime");

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        Integer recordNo = obj.has("recordNo") && !obj.get("recordNo").isJsonNull()
                ? obj.get("recordNo").getAsInt() : 0;
        if (recordNo != null && recordNo != 0) {
            whereClause.append(" AND recordNo = ?");
            params.add(recordNo);
        }

        String columnName     = obj.has("columnName")     && !obj.get("columnName").isJsonNull()     ? obj.get("columnName").getAsString()     : "";
        String searchQuery    = obj.has("searchQuery")    && !obj.get("searchQuery").isJsonNull()    ? obj.get("searchQuery").getAsString()    : "";
        String searchOperator = obj.has("searchOperator") && !obj.get("searchOperator").isJsonNull() ? obj.get("searchOperator").getAsString() : null;

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            String mapped = allowedColumns.get(columnName.trim().toLowerCase());
            if (mapped != null) {
                QueryFilterBuilder.OperatorAndValues ov = new QueryFilterBuilder.OperatorAndValues();
                ov.operator = (searchOperator != null && !searchOperator.isBlank()) ? searchOperator.trim().toLowerCase() : null;
                ov.values   = Collections.singletonList(searchQuery);
                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) {
                    whereClause.append(" AND (").append(fragment).append(")");
                }
            }
        }

        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterBy = obj.getAsJsonObject("filterBy");
            for (Map.Entry<String, JsonElement> entry : filterBy.entrySet()) {
                String rawKey = entry.getKey();
                if (rawKey == null) continue;
                String mapped = allowedColumns.get(rawKey.trim().toLowerCase());
                if (mapped == null) continue;
                QueryFilterBuilder.OperatorAndValues ov =
                        QueryFilterBuilder.normalizeOperatorAndValuesFromJson(entry.getValue());
                if (ov.values == null || ov.values.isEmpty()) continue;
                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) {
                    whereClause.append(" AND (").append(fragment).append(")");
                }
            }
        }

        String sql = "SELECT recordNo, itemCode, relatedItemCode, reciprocalFlag, "
                + "createdBy, createdDatetime, updatedBy, updatedDateTime "
                + "FROM tb_ItemCodeSubstitute " + whereClause + " ORDER BY recordNo DESC";

        List<String> columns = Arrays.asList(
                "recordNo", "itemCode", "relatedItemCode", "reciprocalFlag",
                "createdBy", "createdDatetime", "updatedBy", "updatedDateTime"
        );
        List<String> headers = Arrays.asList(
                "Record No", "Item Code", "Related Item Code", "Reciprocal Flag",
                "Created By", "Created Datetime", "Updated By", "Updated Datetime"
        );

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=item_code_substitutes_export.xlsx");

        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream(), 128 * 1024)) {

            Sheet sheet = workbook.createSheet("ItemCodeSubstitutes");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            CellStyle dateCellStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-mmm-yyyy"));
            dateCellStyle.setAlignment(HorizontalAlignment.CENTER);

            AtomicInteger rowIdx = new AtomicInteger(1);

            // Single call — no duplicate if/else block
            jdbcTemplate.query(sql, params.isEmpty() ? new Object[0] : params.toArray(), (RowCallbackHandler) rs -> {
                Row row = sheet.createRow(rowIdx.getAndIncrement());
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i);
                    Cell cell  = row.createCell(i);
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
                        cell.setCellValue(rs.wasNull() ? 0 : rn);
                    } else {
                        String val = rs.getString(col);
                        cell.setCellValue(val == null ? "" : val);
                    }
                }
            });

            workbook.write(bos);
            bos.flush();
            response.flushBuffer();

        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.getWriter().write("Excel export failed: " + e.getMessage());
                response.getWriter().flush();
            }
        } finally {
            workbook.dispose();
        }
    }

    // ============================================================================
    // Nested Purchase Orders Export
    // ============================================================================

    @PostMapping(value = "/reports/getNestedPurchaseOrders/export")
    public void exportPurchaseOrdersNested(@RequestBody String req,
                                           HttpServletResponse response) throws IOException {
        try {
            JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

            Map<String, String> searchableColumns = new HashMap<>();
            searchableColumns.put("poNumber",          "PO.poNumber");
            searchableColumns.put("vendorNumber",      "PO.vendorNumber");
            searchableColumns.put("supplierId",        "PO.vendorNumber");
            searchableColumns.put("status",            "PO.status");
            searchableColumns.put("currency",          "PO.currency");
            searchableColumns.put("itemPartNumber",    "PO.itemPartNumber");
            searchableColumns.put("poLineDescription", "PO.poLineDescription");
            searchableColumns.put("lineNumber",        "PO.lineNumber");
            searchableColumns.put("recordNo",          "PO.recordNo");
            searchableColumns.put("lineCancelFlag",    "PO.lineCancelFlag");

            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "recordNo", "lineNumber", "vendorNumber", "supplierId", "poNumber"
            ));

            Set<String> controlKeys = new HashSet<>(Arrays.asList(
                    "page", "size", "sort", "filters", "columnName", "searchQuery",
                    "poDateFrom", "poDateTo", "format"
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
                    if (val.isJsonPrimitive()) filtersObj.add(key, val);
                }
                if (obj.has("columnName") && obj.has("searchQuery")) {
                    String col = safeGetAsString(obj, "columnName");
                    String q   = safeGetAsString(obj, "searchQuery");
                    if (col != null && !col.trim().isEmpty() && q != null && !q.trim().isEmpty()) {
                        filtersObj.add(col, new JsonPrimitive(q));
                    }
                }
            }

            StringBuilder whereFrag = new StringBuilder();
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : filtersObj.entrySet()) {
                String columnKey = entry.getKey();
                if (!searchableColumns.containsKey(columnKey)) continue;
                String rawValue = entry.getValue().getAsString();
                if (rawValue == null) continue;
                rawValue = rawValue.trim();
                if (rawValue.isEmpty()) continue;

                String sqlCol = searchableColumns.get(columnKey);
                if (rawValue.contains(",")) {
                    String[] tokens = Arrays.stream(rawValue.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
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

            String poDateFrom = obj.has("poDateFrom") ? convertToSqlDate(safeGetAsString(obj, "poDateFrom")) : "";
            String poDateTo   = obj.has("poDateTo")   ? convertToSqlDate(safeGetAsString(obj, "poDateTo"))   : "";
            if (!poDateFrom.isEmpty() && !poDateTo.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) BETWEEN ? AND ? ");
                params.add(poDateFrom); params.add(poDateTo);
            } else if (!poDateFrom.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) >= ? ");
                params.add(poDateFrom);
            } else if (!poDateTo.isEmpty()) {
                whereFrag.append(" AND DATE(PO.poDate) <= ? ");
                params.add(poDateTo);
            }

            int page = obj.has("page") ? Math.max(1, obj.get("page").getAsInt()) : 1;
            int size = obj.has("size") ? Math.max(1, obj.get("size").getAsInt()) : 20000;

            Integer limit  = null;
            Integer offset = null;
            if (!(page == 1 && size == 20000)) {
                limit  = size;
                offset = (page - 1) * size;
            }

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

    // ============================================================================
    // Acceptance Report Helper Methods
    // ============================================================================

    private Map<String, String> buildSearchableColumns() {
        Map<String, String> map = new HashMap<>();
        map.put("requestId",                      "DCC.recordNo");
        map.put("requestStatus",                  "DCC.status");
        map.put("acceptanceType",                 "DCC.acceptanceType");
        map.put("poNumber",                       "DCC.poNumber");
        map.put("poLineNumber",                   "LN2.lineNumber");
        map.put("poPartNumber",                   "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END)");
        map.put("poLineDescription",              "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
        map.put("poItemSerializedStatus",         "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END)");
        map.put("dccLnRecordNo",                  "LN2.recordNo");
        map.put("siteId",                         "LN2.locationName");
        map.put("siteTypeName",                   "siteType.siteTypeName");
        map.put("inServiceDate",                  "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y')");
        map.put("region",                         "rg.regionName");
        map.put("typeLookUpCode",                 "HD.typeLookUpCode");
        map.put("releaseNumber",                  "HD.releaseNum");
        map.put("dccProjectName",                 "HD.newProjectName");
        map.put("newProjectName",                 "HD.newProjectName");
        map.put("uplLineNumber",                  "LN2.uplLineNumber");
        map.put("uplPartNumber",                  "upl.uplLineItemCode");
        map.put("uplItemDescription",             "upl.uplLineDescription");
        map.put("actualPartNumber",               "LN2.actualItemCode");
        map.put("uplItemSerializedStatus",        "upl.uplItemSerialized");
        map.put("serialNumber",                   "LN2.serialNumber");
        map.put("uplItemCategoryCode",            "upl.zainItemCategoryCode");
        map.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
        map.put("unitPrice",                      "upl.poLineUnitPrice");
        map.put("acceptanceUplQty",               "LN2.deliveredQty");
        map.put("acceptancePoQty",                "LN2.poAcceptanceQty");
        map.put("totalAcceptanceAmount",          "(upl.uplLineUnitPrice * LN2.deliveredQty)");
        map.put("vendorName",                     "HD.vendorName");
        map.put("recordNo",                       "DCC.recordNo");
        map.put("tagNumber",                      "LN2.tagNumber");
        map.put("linkId",                         "LN2.linkId");
        map.put("activeOrPassive",                "upl.activeOrPassive");
        map.put("createdDate",                    "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y')");
        map.put("approvalDate",                   "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y')");
        map.put("scopeOfWork",                    "LN2.scopeOfWork");
        return map;
    }

    private Set<String> buildNumericColumns() {
        return new HashSet<>(Arrays.asList(
                "requestId", "poLineNumber", "uplLineNumber", "dccLnRecordNo",
                "acceptanceUplQty", "acceptancePoQty", "unitPrice", "totalAcceptanceAmount", "recordNo"
        ));
    }

    private void buildWhereClause(JsonObject obj,
                                  String poNumber,
                                  String columnName,
                                  String searchQuery,
                                  Map<String, String> searchableColumns,
                                  Set<String> numericColumns,
                                  StringBuilder where,
                                  List<Object> params) {
        if (!"0".equalsIgnoreCase(poNumber)) {
            where.append(" AND DCC.poNumber = ?");
            params.add(poNumber);
        }
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            String sqlCol = searchableColumns.get(columnName);
            if (sqlCol != null) {
                appendCondition(where, params, sqlCol, columnName, searchQuery, numericColumns);
            }
        }
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterBy = obj.getAsJsonObject("filterBy");
            for (Map.Entry<String, JsonElement> entry : filterBy.entrySet()) {
                String col    = entry.getKey();
                String sqlCol = searchableColumns.get(col);
                if (sqlCol == null) continue;
                String val = entry.getValue().getAsString().trim();
                if (val.isEmpty()) continue;
                appendCondition(where, params, sqlCol, col, val, numericColumns);
            }
        }
    }

    private void appendCondition(StringBuilder where,
                                 List<Object> params,
                                 String sqlCol,
                                 String columnKey,
                                 String value,
                                 Set<String> numericColumns) {
        if (numericColumns.contains(columnKey)) {
            if (value.contains(",")) {
                String[] vals = value.split(",");
                where.append(" AND ").append(sqlCol)
                        .append(" IN (")
                        .append(String.join(",", Collections.nCopies(vals.length, "?")))
                        .append(")");
                for (String v : vals) params.add(v.trim());
            } else {
                where.append(" AND ").append(sqlCol).append(" = ?");
                params.add(value.trim());
            }
        } else {
            where.append(" AND ").append(sqlCol).append(" LIKE ?");
            params.add("%" + value.trim() + "%");
        }
    }

    private String buildBaseFrom() {
        return " FROM tb_DCC DCC "
                + "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber "
                + "JOIN tb_Category_Approval_Requests AR ON DCC.recordNo = AR.acceptanceRequestRecordNo "
                + "JOIN tb_DCC_LN LN2 ON DCC.recordNo = LN2.dccId "
                + "LEFT JOIN tb_PurchaseOrderUPL upl "
                + "    ON DCC.poNumber = upl.poNumber "
                + "    AND LN2.uplLineNumber = upl.uplLine "
                + "    AND upl.poLineNumber = LN2.lineNumber "
                + "LEFT JOIN tb_Site site "
                + "    ON LN2.locationName COLLATE utf8mb4_general_ci = site.siteId COLLATE utf8mb4_general_ci "
                + "LEFT JOIN tb_Site_Type siteType "
                + "    ON site.siteTypeId COLLATE utf8mb4_general_ci = siteType.recordNo COLLATE utf8mb4_general_ci "
                + "LEFT JOIN tb_Region rg "
                + "    ON site.regionId COLLATE utf8mb4_general_ci = rg.recordNo COLLATE utf8mb4_general_ci "
                + " WHERE (0 <> (CASE WHEN LENGTH(LN2.uplLineNumber) > 0 "
                + "   THEN (LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber AND upl.poNumber = DCC.poNumber) "
                + "   ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END))";
    }

    private List<String> buildHeaders() {
        return Arrays.asList(
                "Request No",                    "Request Status",                 "Acceptance Type",
                "PO Type",                       "PO Number",                      "Release Number",
                "PO Line Number",                "PO Part Number",                 "PO Line Description",
                "PO Item Serialized Status",     "Currency",                       "PO Line Unit Price",
                "Site ID",                       "Region",                         "Site Type Name",
                "Project Name",                  "In Service Date",                "UPL Line Number",
                "UPL Part Number",               "UPL Item Description",           "Actual Part Number",
                "UPL Item Serialized Status",    "Serial Number",                  "Tag Number",
                "Link ID",                       "Active / Passive",               "UPL Item Category Code",
                "UPL Item Category Description", "UPL Line Unit Price",            "Acceptance UPL Qty",
                "Acceptance PO Qty",             "Total Acceptance Amount",        "Vendor Name",
                "Created Date",                  "Approval Date",                  "Scope of Work"
        );
    }

    private List<String> buildFields() {
        return Arrays.asList(
                "requestId",                     "requestStatus",                  "acceptanceType",
                "typeLookUpCode",                "poNumber",                       "releaseNumber",
                "poLineNumber",                  "poPartNumber",                   "poLineDescription",
                "poItemSerializedStatus",        "currency",                       "unitPrice",
                "siteId",                        "region",                         "siteTypeName",
                "newProjectName",                "inServiceDate",                  "uplLineNumber",
                "uplPartNumber",                 "uplItemDescription",             "actualPartNumber",
                "uplItemSerializedStatus",       "serialNumber",                   "tagNumber",
                "linkId",                        "activeOrPassive",                "uplItemCategoryCode",
                "uplItemCategoryCodeDescription","uplLineUnitPrice",               "acceptanceUplQty",
                "acceptancePoQty",               "totalAcceptanceAmount",          "vendorName",
                "createdDate",                   "approvalDate",                   "scopeOfWork"
        );
    }

    private CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildNumberStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
        return style;
    }

    // ============================================================================
    // Shared Utilities
    // ============================================================================

    private String convertToSqlDate(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        try {
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (String pattern : new String[]{"dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy"}) {
                try {
                    return LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern))
                            .format(outputFormatter);
                } catch (DateTimeParseException ignored) {}
            }
            throw new IllegalArgumentException("Invalid date format: " + input);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid date format: " + input + ". Supported: dd-MM-yyyy, yyyy-MM-dd, MM/dd/yyyy");
        }
    }

    private static String safeGetAsString(JsonObject obj, String member) {
        try {
            if (!obj.has(member) || obj.get(member).isJsonNull()) return null;
            return obj.get(member).getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private void sendEmptyWorkbook(HttpServletResponse response, String message) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(10);
        try {
            Sheet s = wb.createSheet("Empty");
            s.createRow(0).createCell(0).setCellValue(message);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=purchase_orders_export.xlsx");
            try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream(), 32 * 1024)) {
                wb.write(bos);
                bos.flush();
            }
            response.flushBuffer();
        } finally {
            wb.dispose();
        }
    }
}