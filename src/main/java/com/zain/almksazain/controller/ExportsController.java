package com.zain.almksazain.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.Writer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ExportsController {

    

    @Autowired
   private  JdbcTemplate jdbcTemplate;


@PostMapping(value = "/reports/capitalizationReport/export")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public void exportCapitalizationReport(
        @RequestBody String req,
        @RequestParam(name = "format", defaultValue = "csv") String format,
        HttpServletResponse response
) throws IOException {
    JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
    String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "";
    String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
    String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

    jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

    String whereClause = "";
    List<Object> params = new ArrayList<>();

    // Whitelist and map searchable columns to SQL
    Map<String, String> searchableColumns = new HashMap<>();
    searchableColumns.put("requestId", "DCC.recordNo");
    searchableColumns.put("poNumber", "DCC.poNumber");
    searchableColumns.put("poLineNumber", "LN2.lineNumber");
    searchableColumns.put("uplLineNumber", "LN2.uplLineNumber");
    searchableColumns.put("siteId", "LN2.locationName");
    searchableColumns.put("linkId", "LN2.linkId");
    searchableColumns.put("isd", "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%d-%m-%Y')");
    searchableColumns.put("region", "rg.regionName");
    searchableColumns.put("siteTypeName", "siteType.siteTypeName");
    searchableColumns.put("projectName", "HD.newProjectName");
    searchableColumns.put("newProjectName", "HD.newProjectName");
    searchableColumns.put("description", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
    searchableColumns.put("quantity", "LN2.deliveredQty");
    searchableColumns.put("partNumber", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END)");
    searchableColumns.put("itemSerializedStatus", "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END)");
    searchableColumns.put("serialNumber", "LN2.serialNumber");
    searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
    searchableColumns.put("faBookingAmount", "(upl.uplLineUnitPrice * LN2.deliveredQty)");
    searchableColumns.put("currency", "'SAR'");
    searchableColumns.put("tagNumber", "LN2.tagNumber");
    searchableColumns.put("recordNo", "DCC.recordNo");

    // Dynamic filters
    if (!poNumber.equalsIgnoreCase("0")) {
        whereClause += " AND DCC.poNumber = ?";
        params.add(poNumber);
    }
    if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
        String sqlCol = searchableColumns.get(columnName);
        if (sqlCol != null) {
            whereClause += " AND LOWER(" + sqlCol + ") LIKE LOWER(?) ";
            params.add("%" + searchQuery + "%");
        }
    }

    String baseSql = " FROM tb_DCC DCC " +
        "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber " +
        "JOIN tb_Category_Approval_Requests AR ON DCC.recordNo = AR.acceptanceRequestRecordNo " +
        "JOIN tb_DCC_LN LN2 ON DCC.recordNo = LN2.dccId " +
        "LEFT JOIN tb_PurchaseOrderUPL upl ON DCC.poNumber = upl.poNumber AND LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber " +
        "LEFT JOIN tb_Site site ON LN2.locationName COLLATE utf8mb4_general_ci = site.siteId COLLATE utf8mb4_general_ci " +
        "LEFT JOIN tb_Site_Type siteType ON site.siteTypeId COLLATE utf8mb4_general_ci = siteType.recordNo COLLATE utf8mb4_general_ci " +
        "LEFT JOIN tb_Region rg ON site.regionId COLLATE utf8mb4_general_ci = rg.recordNo COLLATE utf8mb4_general_ci " +
        "WHERE (0 <> (CASE WHEN LENGTH(LN2.uplLineNumber) > 0 " +
        "  THEN (LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber AND upl.poNumber = DCC.poNumber) " +
        "  ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END)) " +
        "AND DCC.status = 'approved' " +
        whereClause;

    // --- NO GROUP BY, NO DISTINCT ---
    String sql = "SELECT " +
        "DCC.recordNo AS requestId, " +
        "DCC.poNumber AS poNumber, " +
        "LN2.lineNumber AS poLineNumber, " +
        "LN2.uplLineNumber AS uplLineNumber, " +
        "LN2.locationName AS siteId, " +
        "LN2.linkId AS linkId, " +
        "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%d-%m-%Y') AS isd, " +
        "rg.regionName AS region, " +
        "siteType.siteTypeName AS siteTypeName, " +
        "HD.newProjectName AS projectName, " +
        "HD.newProjectName AS newProjectName, " +
        "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END) AS description, " +
        "LN2.deliveredQty AS quantity, " +
        "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END) AS partNumber, " +
        "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END) AS itemSerializedStatus, " +
        "LN2.serialNumber AS serialNumber, " +
        "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, " +
        "(upl.uplLineUnitPrice * LN2.deliveredQty) AS faBookingAmount, " +
        "'SAR' AS currency, " +
        "LN2.tagNumber AS tagNumber " +
        baseSql;

    List<String> columns = Arrays.asList(
        "requestId", "poNumber", "poLineNumber", "uplLineNumber", "siteId", "linkId", "isd",
        "region", "siteTypeName", "projectName", "newProjectName", "description",
        "quantity", "partNumber", "itemSerializedStatus", "serialNumber",
        "uplItemCategoryCodeDescription", "faBookingAmount", "currency", "tagNumber"
    );

    // Streaming CSV
    if ("csv".equalsIgnoreCase(format)) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=capitalization_report.csv");
        Writer writer = response.getWriter();
        writer.write(String.join(",", columns) + "\n");

        jdbcTemplate.query(sql, params.toArray(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            try {
                for (int i = 0; i < columns.size(); i++) {
                    String val = rs.getString(columns.get(i));
                    writer.write("\"" + (val == null ? "" : val.replace("\"", "\"\"")) + "\"");
                    if (i < columns.size() - 1) writer.write(",");
                }
                writer.write("\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writer.flush();
    } else { // Streaming Excel
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=capitalization_report.xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Keeps 100 rows in memory
            Sheet sheet = workbook.createSheet("Capitalization Report");
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i));
            }
            AtomicInteger rowIdx = new AtomicInteger(1);

            jdbcTemplate.query(sql, params.toArray(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                Row row = sheet.createRow(rowIdx.getAndIncrement());
                for (int i = 0; i < columns.size(); i++) {
                    String val = rs.getString(columns.get(i));
                    row.createCell(i).setCellValue(val == null ? "" : val);
                }
            });
            workbook.write(response.getOutputStream());
            workbook.dispose(); // Free temp files
        }
    }
}
}
