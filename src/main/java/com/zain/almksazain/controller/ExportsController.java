package com.zain.almksazain.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletResponse;

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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ExportsController {

    @Autowired
    private JdbcTemplate jdbcTemplate;




@PostMapping(value = "/reports/v2/capitalizationReport/export")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public void exportCapitalizationReport(
        @RequestBody String req,
        HttpServletResponse response
) throws IOException {
    JsonObject obj = JsonParser.parseString(req).getAsJsonObject();
    String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "";
    String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
    String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

    jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

    String whereClause = "";
    List<Object> params = new ArrayList<>();

    // Whitelist and map searchable columns to SQL
    Map<String, String> searchableColumns = new HashMap<>();
    searchableColumns.put("requestNo", "DCC.recordNo");
    searchableColumns.put("poNumber", "DCC.poNumber");
    searchableColumns.put("poLineNumber", "LN2.lineNumber");
    searchableColumns.put("uplLineNumber", "LN2.uplLineNumber");
    searchableColumns.put("siteId", "LN2.locationName");
    searchableColumns.put("linkId", "LN2.linkId");
    // searchableColumns.put("isd", "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%d-%m-%Y')");
    searchableColumns.put("isd", "LN2.dateInService");
    searchableColumns.put("region", "rg.regionName");
    searchableColumns.put("siteTypeName", "siteType.siteTypeName");
    // Use projectName logic here
    searchableColumns.put("projectName", "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END)");
    // searchableColumns.put("description", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
    searchableColumns.put("description", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END)");
    searchableColumns.put("quantity", "LN2.deliveredQty");
    searchableColumns.put("partNumber", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END)");
    searchableColumns.put("itemSerializedStatus", "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END)");
    searchableColumns.put("serialNumber", "LN2.serialNumber");
    searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
    searchableColumns.put("faBookingAmount", "(upl.uplLineUnitPrice * LN2.deliveredQty)");
    searchableColumns.put("currency", "'SAR'");
    searchableColumns.put("tagNumber", "LN2.tagNumber");
    // searchableColumns.put("receiveddate", "DATE_FORMAT(rec.approvedDate, '%d-%m-%Y')");
    searchableColumns.put("receiveddate", "rec.approvedDate");
    searchableColumns.put("sequenceNo", "DCC.recordNo");

    // Dynamic filters
    if (!poNumber.equalsIgnoreCase("0")) {
        whereClause += " AND DCC.poNumber = ?";
        params.add(poNumber);
    }

    // Define your numeric columns
    Set<String> numericColumns = new HashSet<>(Arrays.asList(
        "requestId", "poLineNumber", "uplLineNumber", "recordNo"
    ));

    if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
        String sqlCol = searchableColumns.get(columnName);
        if (sqlCol != null) {
            if (numericColumns.contains(columnName)) {
                if (searchQuery.contains(",")) {
                    String[] nums = searchQuery.split(",");
                    whereClause += " AND " + sqlCol + " IN (" + String.join(",", Collections.nCopies(nums.length, "?")) + ") ";
                    for (String num : nums) params.add(num.trim());
                } else {
                    whereClause += " AND " + sqlCol + " = ? ";
                    params.add(searchQuery.trim());
                }
            } else {
                whereClause += " AND LOWER(" + sqlCol + ") LIKE LOWER(?) ";
                params.add("%" + searchQuery.trim() + "%");
            }
        }
    }
    // --- Date range filtering for receiveddate (rec.approvedDate) ---
    String receivedDateFrom = obj.has("receivedDateFrom") ? obj.get("receivedDateFrom").getAsString() : "";
    String receivedDateTo = obj.has("receivedDateTo") ? obj.get("receivedDateTo").getAsString() : "";
    receivedDateFrom = convertToSqlDate(receivedDateFrom);
    receivedDateTo = convertToSqlDate(receivedDateTo);

    if (!receivedDateFrom.isEmpty() && !receivedDateTo.isEmpty()) {
        whereClause += " AND DATE(rec.approvedDate) BETWEEN ? AND ? ";
        params.add(receivedDateFrom);
        params.add(receivedDateTo);
    } else if (!receivedDateFrom.isEmpty()) {
        whereClause += " AND DATE(rec.approvedDate) >= ? ";
        params.add(receivedDateFrom);
    } else if (!receivedDateTo.isEmpty()) {
        whereClause += " AND DATE(rec.approvedDate) <= ? ";
        params.add(receivedDateTo);
    }

    // --- Date range filtering for isd (LN2.dateInService) ---
    String isdFrom = obj.has("isdFrom") ? obj.get("isdFrom").getAsString() : "";
    String isdTo = obj.has("isdTo") ? obj.get("isdTo").getAsString() : "";
    isdFrom = convertToSqlDate(isdFrom);
    isdTo = convertToSqlDate(isdTo);

    if (!isdFrom.isEmpty() && !isdTo.isEmpty()) {
        whereClause += " AND DATE(LN2.dateInService) BETWEEN ? AND ? ";
        params.add(isdFrom);
        params.add(isdTo);
    } else if (!isdFrom.isEmpty()) {
        whereClause += " AND DATE(LN2.dateInService) >= ? ";
        params.add(isdFrom);
    } else if (!isdTo.isEmpty()) {
        whereClause += " AND DATE(LN2.dateInService) <= ? ";
        params.add(isdTo);
    }

    

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
        whereClause;

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
            // only one projectName column
            "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END) AS projectName, " +
            "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END) AS description, " +
            "LN2.deliveredQty AS quantity, " +
            "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END) AS partNumber, " +
            "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END) AS itemSerializedStatus, " +
            "LN2.serialNumber AS serialNumber, " +
            "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, " +
            "(upl.uplLineUnitPrice * LN2.deliveredQty) AS faBookingAmount, " +
            "'SAR' AS currency, " +
            "LN2.tagNumber AS tagNumber, " +
           "rec.approvedDate AS receiveddate " +
            baseSql +
            groupBy;

   List<String> columns = Arrays.asList(
    "sequenceNo","requestNo", "poNumber", "poLineNumber", "uplLineNumber", "siteId", "linkId", "isd",
    "region", "siteTypeName", "projectName", "description",
    "quantity", "partNumber", "itemSerializedStatus", "serialNumber",
    "uplItemCategoryCodeDescription", "faBookingAmount", "currency", "tagNumber", "receiveddate"
);

// New header names corresponding to each SQL column above (order matters!)
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

jdbcTemplate.query(sql, params.toArray(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
    Row row = sheet.createRow(rowIdx.getAndIncrement());
    for (int i = 0; i < columns.size(); i++) {
        String colName = columns.get(i);
        Cell cell = row.createCell(i);
        if ("sequenceNo".equals(colName)) {
            cell.setCellValue(sequenceNo.getAndIncrement());
        } else if ("receiveddate".equals(colName) || "isd".equals(colName)) {
            java.sql.Timestamp ts = rs.getTimestamp(colName);
            if (ts != null) {
                System.out.println("Column: " + colName + ", Timestamp: " + ts);
                // Convert to java.util.Date for reliable POI handling
                cell.setCellType(CellType.NUMERIC);
                cell.setCellValue(new java.util.Date(ts.getTime()));
                cell.setCellStyle(dateCellStyle);
            } else {
                System.out.println("Column: " + colName + ", Timestamp: null");
                cell.setBlank();
            }
        } else {
            String val = rs.getString(colName);
            cell.setCellValue(val == null ? "" : val);
        }
    }
});
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

private String convertToSqlDate(String input) {
    if (input == null || input.trim().isEmpty()) return "";
    try {
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        // Try multiple input formats
        String[] patterns = {"dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy"};
        for (String pattern : patterns) {
            try {
                DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(pattern);
                LocalDate date = LocalDate.parse(input, inputFormatter);
                return date.format(outputFormatter);
            } catch (DateTimeParseException ignored) {
                // Try next pattern
            }
        }
        throw new IllegalArgumentException("Invalid date format for input: " + input);
    } catch (Exception e) {
        throw new IllegalArgumentException("Invalid date format for input: " + input + ". Supported formats: dd-MM-yyyy, yyyy-MM-dd, MM/dd/yyyy");
    }
}


}
