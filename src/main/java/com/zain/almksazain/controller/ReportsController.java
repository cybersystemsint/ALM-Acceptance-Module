package com.zain.almksazain.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.zain.almksazain.model.DccPoCombinedView;
import com.zain.almksazain.model.upldata;
import com.zain.almksazain.repo.DccCombinedViewrepo;
import com.zain.almksazain.repo.dccpoviewrepo;
import com.zain.almksazain.repo.poviewrepo;
import com.zain.almksazain.repo.tbChargeAccountRepo;
import com.zain.almksazain.repo.uplrepo;
import com.zain.almksazain.specs.QueryFilterBuilder;
import com.zain.almzainksa.helper.helper;

@RestController
public class ReportsController {

    private final Logger loggger = LogManager.getLogger(ReportsController.class);
    private final JdbcTemplate jdbcTemplate;

    // acceptanceReceivingRequestReport() runs the same expensive 7-table join twice per request
    // (once to count, once for the page of keys) - the count doesn't need to be exact to the
    // second, so a short-lived cache keyed by the exact filter criteria lets repeated page turns
    // / the same default view skip re-running it. Per-instance only (no cross-host coordination);
    // that's fine, staleness is bounded to COUNT_CACHE_TTL_MS regardless of which host answers.
    private static final long COUNT_CACHE_TTL_MS = 30_000;
    private static final int COUNT_CACHE_MAX_ENTRIES = 500;
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> countCache =
            new java.util.concurrent.ConcurrentHashMap<>(); // value = {count, expiresAtEpochMs}
    @Autowired
    uplrepo uprepo;

    @Autowired
    poviewrepo povwrepo;

    @Autowired
    DccCombinedViewrepo dccpocombinedviewrp;

    @Autowired
    dccpoviewrepo dccpoviewrp;

    @Autowired
    tbChargeAccountRepo chargeAccountRepo;

    @Autowired
    public ReportsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    String genHeader(String msisdn, String reqid, String Channel) {
        return " | " + reqid + " | " + Channel + " | " + msisdn + " | ";
    }

    private Map<String, String> response(String result, String msg) {
        HashMap<String, String> map = new HashMap<>();
        map.put("responseCode", result.equalsIgnoreCase("success") ? "0" : "1001");
        map.put("responseMessage", msg);
        return map;
    }

    @PostMapping(value = "/reports/acceptanceReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> acceptanceReport(@RequestBody String req) {

        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();

        String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "0";
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        List<Object> params = new ArrayList<>();
        String whereClause = " WHERE 1=1";

        if (!poNumber.equalsIgnoreCase("0")) {
            whereClause += " AND poNumber = ?";
            params.add(poNumber);
        }
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName.toLowerCase() + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }
        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        String countDetails = "SELECT COUNT(*) FROM acceptanceReport" + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countDetails, Integer.class, params.toArray());

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;

        } else {
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        //List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());
        String newScript = "SELECT * FROM `acceptanceReport` "
                + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(newScript, params.toArray());

        // Add an incremental column programmatically
        AtomicInteger counter = new AtomicInteger(1);
        result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    @PostMapping(value = "/reports/capitalizationReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> capitalizationReport(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "";
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        //String sql = "SELECT * FROM `capitalizationReport` WHERE 1=1";
        ///
        String whereClause = " WHERE 1=1";
        List<Object> params = new ArrayList<>();

        if (!poNumber.equalsIgnoreCase("0")) {
            whereClause += " AND poNumber = ?";
            params.add(poNumber);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName.toLowerCase() + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        String countSql = "SELECT COUNT(*) FROM capitalizationReport " + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;

        } else {
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        String sql = "SELECT * FROM `capitalizationReport` "
                + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

        AtomicInteger counter = new AtomicInteger(1);
        result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;

    }

    @PostMapping(value = "/reports/v2/acceptanceReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> acceptanceReceivingRequestReport(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String poNumber = obj.has("poNumber") ? obj.get("poNumber").getAsString() : "0";
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        // Defensive pagination limits
        int page = obj.has("page") ? Math.max(0, obj.get("page").getAsInt()) : 0;
        int size = obj.has("size") ? Math.max(1, Math.min(5000, obj.get("size").getAsInt())) : 200; // lower default
        int offset = page * size;

        // searchable columns map and numeric set (same as your original mapping)
        Map<String, String> searchableColumns = new HashMap<>();
        searchableColumns.put("requestId", "DCC.recordNo");
        searchableColumns.put("requestStatus", "DCC.status");
        searchableColumns.put("acceptanceType", "DCC.acceptanceType");
        searchableColumns.put("poNumber", "DCC.poNumber");
        searchableColumns.put("poLineNumber", "LN2.lineNumber");
        searchableColumns.put("poPartNumber", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END)");
        searchableColumns.put("poLineDescription", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
        searchableColumns.put("poItemSerializedStatus", "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END)");
        searchableColumns.put("dccLnRecordNo", "LN2.recordNo");
        searchableColumns.put("siteId", "LN2.locationName");
        searchableColumns.put("siteTypeName", "siteType.siteTypeName");
        searchableColumns.put("inServiceDate", "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y')");
        searchableColumns.put("region", "rg.regionName");
        searchableColumns.put("typeLookUpCode", "HD.typeLookUpCode");
        searchableColumns.put("releaseNumber", "HD.releaseNum");
        searchableColumns.put("dccProjectName", "HD.newProjectName");
        searchableColumns.put("newProjectName", "HD.newProjectName");
        searchableColumns.put("uplLineNumber", "LN2.uplLineNumber");
        searchableColumns.put("uplPartNumber", "upl.uplLineItemCode");
        searchableColumns.put("uplItemDescription", "upl.uplLineDescription");
        searchableColumns.put("actualPartNumber", "LN2.actualItemCode");
        searchableColumns.put("uplItemSerializedStatus", "upl.uplItemSerialized");
        searchableColumns.put("serialNumber", "LN2.serialNumber");
        searchableColumns.put("uplItemCategoryCode", "upl.zainItemCategoryCode");
        searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
        searchableColumns.put("unitPrice", "upl.poLineUnitPrice");
        searchableColumns.put("acceptanceUplQty", "LN2.deliveredQty");
        searchableColumns.put("acceptancePoQty", "LN2.poAcceptanceQty");
        searchableColumns.put("totalAcceptanceAmount", "(upl.uplLineUnitPrice * LN2.deliveredQty)");
        searchableColumns.put("vendorName", "HD.vendorName");
        searchableColumns.put("recordNo", "DCC.recordNo");
        searchableColumns.put("tagNumber", "LN2.tagNumber");
        searchableColumns.put("linkId", "LN2.linkId");
        searchableColumns.put("activeOrPassive", "upl.activeOrPassive");
        searchableColumns.put("createdDate", "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y')");
        searchableColumns.put("approvalDate", "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y')");
        searchableColumns.put("scopeOfWork", "LN2.scopeOfWork");

        Set<String> numericColumns = new HashSet<>(Arrays.asList(
            "requestId", "poLineNumber", "uplLineNumber", "dccLnRecordNo",
            "acceptanceUplQty", "acceptancePoQty", "unitPrice", "totalAcceptanceAmount",
            "recordNo"
        ));

        // Build WHERE clause (for the key-only query and the count)
        StringBuilder where = new StringBuilder();
        List<Object> whereParams = new ArrayList<>();

        if (!"0".equalsIgnoreCase(poNumber)) {
            where.append(" AND DCC.poNumber = ?");
            whereParams.add(poNumber);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            String sqlCol = searchableColumns.get(columnName);
            if (sqlCol != null) {
                if (numericColumns.contains(columnName)) {
                    if (searchQuery.contains(",")) {
                        String[] vals = searchQuery.split(",");
                        where.append(" AND ").append(sqlCol).append(" IN (")
                             .append(String.join(",", Collections.nCopies(vals.length, "?"))).append(")");
                        for (String v : vals) whereParams.add(v.trim());
                    } else {
                        where.append(" AND ").append(sqlCol).append(" = ?");
                        whereParams.add(searchQuery.trim());
                    }
                } else {
                    // Prefer collation-aware comparison instead of LOWER() to use indexes
                    // If your database uses case-insensitive collation, you can drop LOWER()
                    where.append(" AND ").append(sqlCol).append(" LIKE ?");
                    whereParams.add("%" + searchQuery.trim() + "%");
                }
            }
        }
        
        
                // ── filterBy multi-filter (optional) ─────────────────────────────────
        // {
        //   "filterBy": {
        //     "region":       "Central",
        //     "serialNumber": "SN123456",
        //     "siteTypeName": "Outdoor",
        //     "requestId":    "1001,1002,1003"  ← comma = IN for numeric
        //     "vendorName":   "Nokia,Ericsson"  ← comma = OR LIKE for text
        //   }
        // }
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterBy = obj.getAsJsonObject("filterBy");
            for (Map.Entry<String, JsonElement> entry : filterBy.entrySet()) {
                String col = entry.getKey();
                if (col == null) continue;

                String sqlCol = searchableColumns.get(col);
                if (sqlCol == null) continue;

                JsonElement valEl = entry.getValue();
                if (valEl == null || valEl.isJsonNull()) continue;
                String val = valEl.getAsString().trim();
                if (val.isEmpty()) continue;

                if (numericColumns.contains(col)) {
                    if (val.contains(",")) {
                        // "requestId": "1001,1002,1003" → IN (?,?,?)
                        String[] vals = val.split(",");
                        where.append(" AND ").append(sqlCol).append(" IN (")
                             .append(String.join(",", Collections.nCopies(vals.length, "?"))).append(")");
                        for (String v : vals) whereParams.add(v.trim());
                    } else {
                        // "poLineNumber": "5" → = ?
                        where.append(" AND ").append(sqlCol).append(" = ?");
                        whereParams.add(val);
                    }
                } else {
                    if (val.contains(",")) {
                        // "vendorName": "Nokia,Ericsson" → (col LIKE ? OR col LIKE ?)
                        String[] tokens = val.split(",");
                        where.append(" AND (");
                        for (int i = 0; i < tokens.length; i++) {
                            if (i > 0) where.append(" OR ");
                            where.append(sqlCol).append(" LIKE ?");
                            whereParams.add("%" + tokens[i].trim() + "%");
                        }
                        where.append(")");
                    } else {
                        // "region": "Central" → col LIKE ?
                        where.append(" AND ").append(sqlCol).append(" LIKE ?");
                        whereParams.add("%" + val + "%");
                    }
                }
            }
        }
        // Base from/joins used by both queries (only selecting keys in first query)
        // HD is joined on lineNumber too (not poNumber alone) - a PO with N lines was otherwise
        // fanning every acceptance line out to all N of that PO's rows before the GROUP BY could
        // collapse it back down (~10.6x on average, confirmed via EXPLAIN ANALYZE). AR is deduped
        // to its latest row per DCC the same way exportCapitalizationReport already does, since a
        // DCC having multiple approval-request rows was fanning this join out too (~1.52x).
        String baseFrom = " FROM tb_DCC DCC " +
                "JOIN tb_DCC_LN LN2 ON DCC.recordNo = LN2.dccId " +
                "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber AND HD.lineNumber = LN2.lineNumber " +
                "JOIN ( " +
                "    SELECT acceptanceRequestRecordNo, MAX(recordNo) AS recordNo " +
                "    FROM tb_Category_Approval_Requests " +
                "    GROUP BY acceptanceRequestRecordNo " +
                ") AR_latest ON DCC.recordNo = AR_latest.acceptanceRequestRecordNo " +
                "JOIN tb_Category_Approval_Requests AR ON AR.recordNo = AR_latest.recordNo " +
                "LEFT JOIN tb_PurchaseOrderUPL upl ON DCC.poNumber = upl.poNumber AND LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber " +
                "LEFT JOIN tb_Site site ON LN2.locationName COLLATE utf8mb4_general_ci = site.siteId COLLATE utf8mb4_general_ci " +
                "LEFT JOIN tb_Site_Type siteType ON site.siteTypeId COLLATE utf8mb4_general_ci = siteType.recordNo COLLATE utf8mb4_general_ci " +
                "LEFT JOIN tb_Region rg ON site.regionId COLLATE utf8mb4_general_ci = rg.recordNo COLLATE utf8mb4_general_ci " +
                " WHERE (0 <> (CASE WHEN LENGTH(LN2.uplLineNumber) > 0 " +
                "  THEN (LN2.uplLineNumber = upl.uplLine AND upl.poLineNumber = LN2.lineNumber AND upl.poNumber = DCC.poNumber) " +
                "  ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END))";

        // 1) Count total using a grouped-subquery that only projects keys (lighter) - cached
        // briefly since this runs the same expensive join as the keys query below.
        String countCacheKey = where.toString() + "|" + whereParams;
        int totalRecords = 0;
        long[] cachedCount = countCache.get(countCacheKey);
        long now = System.currentTimeMillis();
        if (cachedCount != null && cachedCount[1] > now) {
            totalRecords = (int) cachedCount[0];
        } else {
            String countSubquery = "SELECT 1 " + baseFrom + where.toString() + " GROUP BY DCC.recordNo, LN2.recordNo";
            String countSql = "SELECT COUNT(*) FROM (" + countSubquery + ") t";
            try {
                Object cnt = jdbcTemplate.queryForObject(countSql, Integer.class, whereParams.toArray());
                totalRecords = cnt == null ? 0 : (Integer) cnt;
                if (countCache.size() >= COUNT_CACHE_MAX_ENTRIES) {
                    countCache.entrySet().removeIf(e -> e.getValue()[1] <= now);
                }
                countCache.put(countCacheKey, new long[]{totalRecords, now + COUNT_CACHE_TTL_MS});
            } catch (Exception e) {
                // fallback - log and proceed with 0
                totalRecords = 0;
            }
        }

        // 2) Select keys for requested page (only keys, cheap)
        String keysSql = "SELECT DCC.recordNo AS requestId, LN2.recordNo AS dccLnRecordNo " +
                baseFrom + where.toString() + " GROUP BY DCC.recordNo, LN2.recordNo " +
                " ORDER BY DCC.recordNo DESC, LN2.recordNo DESC LIMIT ? OFFSET ?";
        List<Object> keysParams = new ArrayList<>(whereParams);
        keysParams.add(size);
        keysParams.add(offset);

        List<Map<String, Object>> keys = jdbcTemplate.queryForList(keysSql, keysParams.toArray());
        if (keys.isEmpty()) {
            Map<String, Object> emptyResp = new HashMap<>();
            emptyResp.put("data", Collections.emptyList());
            emptyResp.put("totalRecords", totalRecords);
            emptyResp.put("currentPage", page);
            emptyResp.put("pageSize", size);
            emptyResp.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            return emptyResp;
        }

        // Build WHERE for full detail fetch using the selected key pairs.
        // Use an OR list: (DCC.recordNo = ? AND LN2.recordNo = ?) OR ...
        StringBuilder pairWhere = new StringBuilder(" AND (");
        List<Object> pairParams = new ArrayList<>();
        String prefix = "";
        for (Map<String, Object> key : keys) {
            pairWhere.append(prefix).append("(DCC.recordNo = ? AND LN2.recordNo = ?)");
            prefix = " OR ";
            pairParams.add(key.get("requestId"));
            pairParams.add(key.get("dccLnRecordNo"));
        }
        pairWhere.append(") ");

        // 3) Fetch full rows for those keys without GROUP BY (each pair is unique)
        String detailSql = "SELECT " +
                "DCC.recordNo AS requestId, " +
                "DCC.status AS requestStatus, " +
                "DCC.acceptanceType AS acceptanceType, " +
                "DCC.poNumber AS poNumber, " +
                "LN2.lineNumber AS poLineNumber, " +
                "CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END AS poPartNumber, " +
                "CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END AS poLineDescription, " +
                "CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END AS poItemSerializedStatus, " +
                "'SAR' AS currency, " +
                "upl.poLineUnitPrice AS unitPrice, " +
                "LN2.recordNo AS dccLnRecordNo, " +
                "LN2.locationName AS siteId, " +
                "siteType.siteTypeName AS siteTypeName, " +
                "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y') AS inServiceDate, " +
                "rg.regionName AS region, " +
                "HD.typeLookUpCode AS typeLookUpCode, " +
                "HD.releaseNum AS releaseNumber, " +
                "HD.newProjectName AS dccProjectName, " +
                "HD.newProjectName AS newProjectName, " +
                "LN2.uplLineNumber AS uplLineNumber, " +
                "upl.uplLineItemCode AS uplPartNumber, " +
                "upl.uplLineDescription AS uplItemDescription, " +
                "LN2.actualItemCode AS actualPartNumber, " +
                "upl.uplItemSerialized AS uplItemSerializedStatus, " +
                "LN2.serialNumber AS serialNumber, " +
                "upl.zainItemCategoryCode AS uplItemCategoryCode, " +
                "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, " +
                "upl.uplLineUnitPrice AS uplLineUnitPrice, " +
                "LN2.deliveredQty AS acceptanceUplQty, " +
                "LN2.poAcceptanceQty AS acceptancePoQty, " +
                "(upl.uplLineUnitPrice * LN2.deliveredQty) AS totalAcceptanceAmount, " +
                "HD.vendorName AS vendorName, " +
                "LN2.tagNumber AS tagNumber, " +
                "LN2.linkId AS linkId, " +
                "upl.activeOrPassive AS activeOrPassive, " +
                "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y') AS createdDate, " +
                "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y') AS approvalDate, " +
                "LN2.scopeOfWork AS scopeOfWork " +
                baseFrom + pairWhere.toString() +
                " ORDER BY DCC.recordNo, LN2.recordNo";

        List<Object> detailParams = new ArrayList<>(pairParams);
        // Run detail query
        List<Map<String, Object>> detailRows = jdbcTemplate.queryForList(detailSql, detailParams.toArray());

        // Keep original ordering by the keys list (important for consistent paging)
        Map<String, Map<String, Object>> byPair = new LinkedHashMap<>();
        for (Map<String, Object> row : detailRows) {
            String key = row.get("requestId") + "-" + row.get("dccLnRecordNo");
            byPair.put(key, row);
        }

        // Preserve the order from keys list and add incremental record numbers
        AtomicInteger counter = new AtomicInteger(1 + offset);
        List<Map<String, Object>> orderedResult = new ArrayList<>();
        for (Map<String, Object> k : keys) {
            String composite = k.get("requestId") + "-" + k.get("dccLnRecordNo");
            Map<String, Object> r = byPair.get(composite);
            if (r != null) {
                r.put("recordNo", counter.getAndIncrement());
                orderedResult.add(r);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", orderedResult);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

@PostMapping(value = "/reports/v2/capitalizationReport", produces = "application/json")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public Map<String, Object> capitalizationReceivingReport(@RequestBody String req) {
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
    // searchableColumns.put("description", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
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
    // searchableColumns.put("receiveddate", "DCC.approvedDate");
    searchableColumns.put("recordNo", "DCC.recordNo");

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
                // For comma-separated list ("1,2,3")
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
    // Join tb_AcceptanceRequest_Receipt as rec, get latest approvedDate per DCC.recordNo
 String baseSql = " FROM tb_DCC DCC " +
    "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber " +
    // Get the AR with status=approved and received=1 for this DCC
    "JOIN ( " +
    "    SELECT t.acceptanceRequestRecordNo, MAX(t.recordNo) AS recordNo " +
    "    FROM tb_Category_Approval_Requests t " +
    "    WHERE t.status = 'approved' AND t.received = 1 " +
    "    GROUP BY t.acceptanceRequestRecordNo " +
    ") AR_latest ON DCC.recordNo = AR_latest.acceptanceRequestRecordNo " +
    // Now join the AR row
    "JOIN tb_Category_Approval_Requests AR ON AR.recordNo = AR_latest.recordNo " +
    // Join to Receipt for received date
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
    String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql + groupBy + ") t";
    int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

      // --- PAGINATION: allow 0-based page ---
    int page = obj.has("page") ? obj.get("page").getAsInt() : 0; // default to 0 now
    int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

    page = Math.max(page, 0); // allow page 0
    size = Math.max(size, 1);

    int offset = page * size; // 0-based paging
    String paginationSql = " LIMIT " + size + " OFFSET " + offset;

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
            "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END) AS projectName, " +
            // "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END) AS description, " +
            "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.uplLineDescription ELSE HD.poLineDescription END) AS description, " +

            "LN2.deliveredQty AS quantity, " +
            "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN (CASE WHEN LENGTH(LN2.actualItemCode) > 0 THEN LN2.actualItemCode ELSE upl.uplLineItemCode END) ELSE HD.itemPartNumber END) AS partNumber, " +
            "(CASE " +
                "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('YES','Y','TRUE','1') THEN 'YES' " +
                "WHEN UPPER(TRIM(upl.uplItemSerialized)) IN ('NO','N','FALSE','0') THEN 'NO' " +
                "ELSE NULL END) AS itemSerializedStatus, " +
            "LN2.serialNumber AS serialNumber, " +
            "upl.zainItemCategoryDescription AS uplItemCategoryCodeDescription, " +
            "(upl.uplLineUnitPrice * LN2.deliveredQty) AS faBookingAmount, " +
            "'SAR' AS currency, " +
            "LN2.tagNumber AS tagNumber, " +
            "DATE_FORMAT(rec.approvedDate, '%d-%m-%Y') AS receiveddate " +
            // "DATE_FORMAT(DCC.approvedDate, '%d-%m-%Y') AS receiveddate" + 
            baseSql +
            groupBy +
            paginationSql;

    List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

    AtomicInteger counter = new AtomicInteger(1);
    result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

    Map<String, Object> response = new HashMap<>();
    response.put("data", result);
    response.put("totalRecords", totalRecords);
    response.put("currentPage", page);
    response.put("pageSize", size);
    response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

    return response;
}
//dd-MM-yyyy to yyyy-MM-dd:
private String convertToSqlDate(String input) {
    if (input == null || input.isEmpty()) return "";
    try {
        java.time.format.DateTimeFormatter inputFormatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
        java.time.format.DateTimeFormatter outputFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        java.time.LocalDate date = java.time.LocalDate.parse(input, inputFormatter);
        return date.format(outputFormatter);
    } catch (Exception e) {
        return input; // fallback, but ideally log or handle error
    }
}




    ///GET ALL CREATED CHARGE ACCOUNTS  
     @PostMapping(value = "/reports/getAllItemCodeSubstitutes", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getAllItemCodeSubstitutes(@RequestBody String req) {
        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();

        Integer recordNo = obj.has("recordNo") && !obj.get("recordNo").isJsonNull() ? obj.get("recordNo").getAsInt() : 0;
        String columnName = obj.has("columnName") && !obj.get("columnName").isJsonNull() ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull() ? obj.get("searchQuery").getAsString() : "";
        String searchOperator = obj.has("searchOperator") && !obj.get("searchOperator").isJsonNull()
                ? obj.get("searchOperator").getAsString() : null;

        int page = obj.has("page") && !obj.get("page").isJsonNull() ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") && !obj.get("size").isJsonNull() ? obj.get("size").getAsInt() : 100;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

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

        String whereClause = " WHERE 1=1";
        List<Object> params = new ArrayList<>();

        if (recordNo != null && recordNo != 0) {
            whereClause += " AND recordNo = ?";
            params.add(recordNo);
        }

        // single column search
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            String colKey = columnName.trim().toLowerCase();
            String mapped = allowedColumns.get(colKey);
            if (mapped != null) {
                QueryFilterBuilder.OperatorAndValues ov = new QueryFilterBuilder.OperatorAndValues();
                ov.operator = (searchOperator != null && !searchOperator.isBlank()) ? searchOperator.trim().toLowerCase()
                        : null;
                ov.values = Collections.singletonList(searchQuery);

                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) {
                    whereClause += " AND (" + fragment + ")";
                }
            }
        }

        // filterBy multi-filters
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterBy = obj.getAsJsonObject("filterBy");
            for (Map.Entry<String, JsonElement> entry : filterBy.entrySet()) {
                String rawKey = entry.getKey();
                if (rawKey == null) continue;
                String key = rawKey.trim().toLowerCase();
                String mapped = allowedColumns.get(key);
                if (mapped == null) continue;

                QueryFilterBuilder.OperatorAndValues ov = QueryFilterBuilder.normalizeOperatorAndValuesFromJson(entry.getValue());
                if (ov.values == null || ov.values.isEmpty()) continue;

                String fragment = QueryFilterBuilder.buildPredicateFragment(mapped, ov, params);
                if (fragment != null && !fragment.isEmpty()) whereClause += " AND (" + fragment + ")";
            }
        }

        // COUNT
        String countScript = "SELECT COUNT(*) FROM tb_ItemCodeSubstitute" + whereClause;
        int totalRecords = 0;
        if (params.isEmpty()) {
            totalRecords = jdbcTemplate.queryForObject(countScript, Integer.class);
        } else {
            totalRecords = jdbcTemplate.queryForObject(countScript, Integer.class, params.toArray());
        }

        // Pagination SQL
        String paginationSql = "";
        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        } else {
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        String itemCodes = "SELECT recordNo, recordDateTime, itemCode, relatedItemCode, reciprocalFlag, "
                + "createdBy, createdDatetime, updatedBy, updatedDateTime FROM tb_ItemCodeSubstitute"
                + whereClause + paginationSql;

        List<Map<String, Object>> result;
        if (params.isEmpty()) {
            result = jdbcTemplate.queryForList(itemCodes);
        } else {
            result = jdbcTemplate.queryForList(itemCodes, params.toArray());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", size > 0 ? (int) Math.ceil((double) totalRecords / size) : 0);

        return response;
    }
  

///GET ALL CREATED CHARGE ACCOUNTS
    @PostMapping(value = "/reports/getAllChargeAccounts", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public List<Map<String, Object>> getAllChargeAccounts(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        Integer recordNo = obj.get("recordNo").getAsInt();

        String sql = "SELECT recordNo, recordDatetime, chargeAccount, orgCode, orgName, subInventory, createdBy, createdDatetime, updatedBy, updatedDate AS updatedDatetime FROM tb_ChargeAccount;";
        if (recordNo != 0) {
            sql = "SELECT recordNo, recordDatetime, chargeAccount, orgCode, orgName, subInventory, createdBy, createdDatetime, updatedBy, updatedDate AS updatedDatetime FROM tb_ChargeAccount WHERE recordNo='" + recordNo + "'";
        }

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return result;
    }

    @PostMapping(value = "/reports/getAllPurchaseOrders", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getAllPurchaseOrders(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();

        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        String dateFrom = obj.has("dateFrom") ? obj.get("dateFrom").getAsString() : "";
        String dateTo = obj.has("dateTo") ? obj.get("dateTo").getAsString() : "";

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";
        String whereClause = " WHERE 1=1 ";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND PO.vendorNumber = ? ";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND PO." + columnName.toLowerCase() + " LIKE ? ";
        }

        String purchaseOrders = "SELECT COUNT(*) FROM tb_PurchaseOrder PO " + whereClause;

        List<Object> params = new ArrayList<>();
        if (!supplierId.equalsIgnoreCase("0")) {
            params.add(supplierId);
        }
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            params.add("%" + searchQuery + "%"); // Use LIKE for partial matching
        }

        int totalRecords = jdbcTemplate.queryForObject(purchaseOrders, params.toArray(), Integer.class);

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;

        } else {
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        String sql = "SELECT PO.recordNo, PO.poNumber, PO.typeLookUpCode, PO.blanketTotalAmount, PO.releaseNum, PO.lineNumber, "
                + "PO.prNum, PO.projectName, PO.lineCancelFlag, PO.cancelReason, PO.itemPartNumber, PO.prSubAllow, "
                + "PO.countryOfOrigin, PO.poOrderQuantity, PO.poQtyNew, PO.quantityReceived, PO.quantityDueOld, PO.quantityDueNew, "
                + "PO.quantityBilled, PO.currencyCode, PO.unitPriceInPoCurrency, PO.unitPriceInSAR, PO.linePriceInPoCurrency, "
                + "PO.linePriceInSAR, PO.amountReceived, PO.amountDue, PO.amountDueNew, PO.amountBilled, PO.poLineDescription, "
                + "PO.organizationName, PO.organizationCode, PO.subInventoryCode, PO.receiptRouting, PO.authorisationStatus, "
                + "PO.poClosureStatus, PO.departmentName, PO.businessOwner, PO.poLineType, PO.acceptanceType, PO.costCenter, "
                + "PO.chargeAccount, PO.serialControl, PO.vendorSerialNumberYN, PO.itemType, PO.itemCategoryInventory, "
                + "PO.inventoryCategoryDescription, PO.itemCategoryFA, PO.FACategoryDescription, PO.itemCategoryPurchasing, "
                + "PO.PurchasingCategoryDescription, PO.vendorName, PO.vendorNumber, PO.approvedDate, PO.createdDate, "
                + "CASE WHEN `PO`.`lineCancelFlag` = 0 AND `PO`.`authorisationStatus` = 'APPROVED' AND `PO`.`poClosureStatus` = 'OPEN' "
                + "THEN 'YES' ELSE 'NO' END AS `canRaiseAcceptance`, PO.createdByName, PO.descopedLinePriceInPoCurrency, "
                + "PO.newLinePriceInPoCurrency FROM tb_PurchaseOrder PO " + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());
        loggger.info("PURCHASE ORDER QUERY " + sql);

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    //===========GET NESTED PO =====
    @PostMapping(value = "/reports/getNestedPurchaseOrders", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getNestedPurchaseOrders(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String poID = obj.get("poNumber").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        int page = Math.max(obj.has("page") ? obj.get("page").getAsInt() : 1, 1);
        int size = Math.max(obj.has("size") ? obj.get("size").getAsInt() : 20000, 1);

        String whereClause = " WHERE 1=1";
        List<Object> params = new ArrayList<>();

        if (!supplierId.equalsIgnoreCase("0") && !poID.equalsIgnoreCase("0")) {
            whereClause += " AND PO.vendorNumber = ? AND PO.poNumber = ?";
            params.add(supplierId);
            params.add(poID);
        } else if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND PO.vendorNumber = ?";
            params.add(supplierId);
        } else if (!poID.equalsIgnoreCase("0")) {
            whereClause += " AND PO.poNumber = ?";
            params.add(poID);
        }
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND PO." + columnName + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        // "Fetch all" mode (page=1, size=20000): single query, no pagination overhead
        if (page == 1 && size == 20000) {
            String fetchAllSql = "SELECT PO.* FROM tb_PurchaseOrder PO" + whereClause
                    + " ORDER BY CAST(PO.isFavourite AS UNSIGNED) DESC, PO.poNumber, PO.lineNumber";
            List<Map<String, Object>> allLineItems = jdbcTemplate.queryForList(fetchAllSql, params.toArray());
            Map<String, Map<String, Object>> groupedResults = groupLineItemsByPO(allLineItems);
            int totalPOs = groupedResults.size();
            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", 1);
            response.put("pageSize", totalPOs);
            response.put("totalRecords", totalPOs);
            response.put("totalPages", 1);
            response.put("data", sortByFavouriteFirst(groupedResults));
            return response;
        }

        // Paginated mode: count distinct POs, then fetch line items for the current page via join
        String countSql = "SELECT COUNT(DISTINCT PO.poNumber) FROM tb_PurchaseOrder PO" + whereClause;
        Integer totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);
        if (totalRecords == null || totalRecords == 0) {
            return buildEmptyResponse(page, size);
        }

        int offset = (page - 1) * size;
        String innerWhere = whereClause.replace("PO.", "PO2.");
        List<Object> lineParams = new ArrayList<>(params);
        lineParams.add(size);
        lineParams.add(offset);

        String lineItemsSql =
                "SELECT PO.* FROM tb_PurchaseOrder PO " +
                "INNER JOIN (" +
                "  SELECT paged_pos.poNumber FROM (" +
                "    SELECT PO2.poNumber FROM tb_PurchaseOrder PO2" + innerWhere +
                "    GROUP BY PO2.poNumber" +
                "    ORDER BY MAX(CAST(PO2.isFavourite AS UNSIGNED)) DESC, PO2.poNumber" +
                "    LIMIT ? OFFSET ?" +
                "  ) paged_pos" +
                ") paged ON PO.poNumber = paged.poNumber " +
                "ORDER BY CAST(PO.isFavourite AS UNSIGNED) DESC, PO.poNumber, PO.lineNumber";

        List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql, lineParams.toArray());
        Map<String, Map<String, Object>> groupedResults = groupLineItemsByPO(lineItems);

        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", totalRecords);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
        response.put("data", sortByFavouriteFirst(groupedResults));
        return response;
    }

    //==================GET ALL CREATED ACCEPTANCE PER SUPPLIER NESTED =====
    private String getFixedColumnName(String columnName) {
        if ("recordno".equalsIgnoreCase(columnName)) {
            return "dccRecordNo";
        } else if ("projectname".equalsIgnoreCase(columnName)) {
            return "dccProjectName";
        } else if ("vendorname".equalsIgnoreCase(columnName)) {
            return "dccVendorName";
        }
        return columnName;
    }

    private Map<String, Object> buildEmptyResponse(int page, int size) {
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", 0);
        response.put("totalPages", 0);
        response.put("data", Collections.emptyList());
        return response;
    }

    // ===== SHARED PO NESTING HELPERS =====

    private double toDouble(Object val) {
        return val != null ? ((Number) val).doubleValue() : 0.0;
    }

    private boolean toBoolean(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue() != 0;
        }
        if (val instanceof byte[]) {
            return ((byte[]) val)[0] != 0;
        }
        String text = val.toString();
        return !text.equalsIgnoreCase("false") && !text.equals("0");
    }

    private Object getMapValueIgnoreCase(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isRowFavourite(Map<String, Object> row) {
        return toBoolean(getMapValueIgnoreCase(row, "isFavourite"));
    }

    private Map<String, Object> buildPoLineItem(Map<String, Object> lineItem) {
        Map<String, Object> li = new LinkedHashMap<>();
        li.put("recordNo", lineItem.get("recordNo"));
        li.put("poNumber", lineItem.get("poNumber"));
        li.put("lineNumber", lineItem.get("lineNumber"));
        li.put("itemPartNumber", lineItem.get("itemPartNumber"));
        li.put("activeOrPassive", lineItem.get("activeOrPassive"));
        li.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
        li.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
        li.put("poQtyNew", lineItem.get("poQtyNew"));
        li.put("quantityReceived", lineItem.get("quantityReceived"));
        li.put("quantityDueOld", lineItem.get("quantityDueOld"));
        li.put("quantityDueNew", lineItem.get("quantityDueNew"));
        li.put("quantityBilled", lineItem.get("quantityBilled"));
        li.put("unitPriceInPoCurrency", lineItem.get("unitPriceInPoCurrency"));
        li.put("unitPriceInSAR", lineItem.get("unitPriceInSAR"));
        li.put("linePriceInPoCurrency", lineItem.get("linePriceInPoCurrency"));
        li.put("linePriceInSAR", lineItem.get("linePriceInSAR"));
        li.put("amountReceived", lineItem.get("amountReceived"));
        li.put("amountDue", lineItem.get("amountDue"));
        li.put("amountDueNew", lineItem.get("amountDueNew"));
        li.put("amountBilled", lineItem.get("amountBilled"));
        li.put("poLineDescription", lineItem.get("poLineDescription"));
        li.put("vendorSerialNumberYN", lineItem.get("vendorSerialNumberYN"));
        li.put("itemCategoryInventory", lineItem.get("itemCategoryInventory"));
        li.put("inventoryCategoryDescription", lineItem.get("inventoryCategoryDescription"));
        li.put("itemCategoryFA", lineItem.get("itemCategoryFA"));
        li.put("FACategoryDescription", lineItem.get("FACategoryDescription"));
        li.put("descopedLinePriceInPoCurrency", lineItem.get("descopedLinePriceInPoCurrency"));
        li.put("newLinePriceInPoCurrency", lineItem.get("newLinePriceInPoCurrency"));
        return li;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> groupLineItemsByPO(List<Map<String, Object>> lineItems) {
        List<String> lineSpecificFields = Arrays.asList(
                "recordNo", "lineNumber", "countryOfOrigin", "poOrderQuantity", "poQtyNew",
                "quantityReceived", "quantityDueOld", "quantityDueNew", "quantityBilled",
                "unitPriceInPoCurrency", "unitPriceInSAR", "linePriceInPoCurrency", "linePriceInSAR",
                "amountReceived", "amountDue", "amountDueNew", "amountBilled",
                "poLineDescription", "vendorSerialNumberYN", "itemCategoryInventory",
                "inventoryCategoryDescription", "itemCategoryFA", "FACategoryDescription",
                "descopedLinePriceInPoCurrency", "newLinePriceInPoCurrency", "isFavourite");
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> lineItem : lineItems) {
            String poNumber = (String) lineItem.get("poNumber");
            if (!grouped.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                for (String field : lineSpecificFields) groupedRow.remove(field);
                groupedRow.put("lineCancelFlag", lineItem.get("lineCancelFlag").toString().equalsIgnoreCase("false") ? "N" : "Y");
                groupedRow.put("prSubAllow", lineItem.get("prSubAllow").toString().equalsIgnoreCase("false") ? "N" : "Y");
                groupedRow.put("recordNo", lineItem.get("recordNo"));
                groupedRow.put("isFavourite", isRowFavourite(lineItem));
                groupedRow.put("totalPoQtyNew", 0.0);
                groupedRow.put("totalQuantityReceived", 0.0);
                groupedRow.put("totalQuantityDueOld", 0.0);
                groupedRow.put("totalQuantityDueNew", 0.0);
                groupedRow.put("totalQuantityBilled", 0.0);
                groupedRow.put("totalpoOrderQuantity", 0.0);
                groupedRow.put("totalunitPriceInPoCurrency", 0.0);
                groupedRow.put("totalunitPriceInSAR", 0.0);
                groupedRow.put("totallinePriceInPoCurrency", 0.0);
                groupedRow.put("totallinePriceInSAR", 0.0);
                groupedRow.put("totalamountReceived", 0.0);
                groupedRow.put("totalamountDue", 0.0);
                groupedRow.put("totalamountDueNew", 0.0);
                groupedRow.put("totalamountBilled", 0.0);
                groupedRow.put("totalDescopedLinePriceInPoCurrency", 0.0);
                groupedRow.put("totalNewLinePriceInPoCurrency", 0.0);
                groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                grouped.put(poNumber, groupedRow);
            }
            ((List<Map<String, Object>>) grouped.get(poNumber).get("POlineItems")).add(buildPoLineItem(lineItem));
            Map<String, Object> r = grouped.get(poNumber);
            r.put("totalPoQtyNew", (double) r.get("totalPoQtyNew") + toDouble(lineItem.get("poQtyNew")));
            r.put("totalQuantityReceived", (double) r.get("totalQuantityReceived") + toDouble(lineItem.get("quantityReceived")));
            r.put("totalQuantityDueOld", (double) r.get("totalQuantityDueOld") + toDouble(lineItem.get("quantityDueOld")));
            r.put("totalQuantityDueNew", (double) r.get("totalQuantityDueNew") + toDouble(lineItem.get("quantityDueNew")));
            r.put("totalQuantityBilled", (double) r.get("totalQuantityBilled") + toDouble(lineItem.get("quantityBilled")));
            r.put("totalpoOrderQuantity", (double) r.get("totalpoOrderQuantity") + toDouble(lineItem.get("poOrderQuantity")));
            r.put("totalunitPriceInPoCurrency", (double) r.get("totalunitPriceInPoCurrency") + toDouble(lineItem.get("unitPriceInPoCurrency")));
            r.put("totalunitPriceInSAR", (double) r.get("totalunitPriceInSAR") + toDouble(lineItem.get("unitPriceInSAR")));
            r.put("totallinePriceInPoCurrency", (double) r.get("totallinePriceInPoCurrency") + toDouble(lineItem.get("linePriceInPoCurrency")));
            r.put("totallinePriceInSAR", (double) r.get("totallinePriceInSAR") + toDouble(lineItem.get("linePriceInSAR")));
            r.put("totalamountReceived", (double) r.get("totalamountReceived") + toDouble(lineItem.get("amountReceived")));
            r.put("totalamountDue", (double) r.get("totalamountDue") + toDouble(lineItem.get("amountDue")));
            r.put("totalamountDueNew", (double) r.get("totalamountDueNew") + toDouble(lineItem.get("amountDueNew")));
            r.put("totalamountBilled", (double) r.get("totalamountBilled") + toDouble(lineItem.get("amountBilled")));
            r.put("totalDescopedLinePriceInPoCurrency", (double) r.get("totalDescopedLinePriceInPoCurrency") + toDouble(lineItem.get("descopedLinePriceInPoCurrency")));
            r.put("totalNewLinePriceInPoCurrency", (double) r.get("totalNewLinePriceInPoCurrency") + toDouble(lineItem.get("newLinePriceInPoCurrency")));
            if (toBoolean(getMapValueIgnoreCase(lineItem, "isFavourite"))) {
                r.put("isFavourite", true);
            }
        }
        return grouped;
    }

    private List<Map<String, Object>> sortByFavouriteFirst(Map<String, Map<String, Object>> groupedResults) {
        List<Map<String, Object>> favourites = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();

        for (Map<String, Object> row : groupedResults.values()) {
            boolean favourite = isRowFavourite(row);
            row.put("isFavourite", favourite);
            if (favourite) {
                favourites.add(row);
            } else {
                others.add(row);
            }
        }

        java.util.Comparator<Map<String, Object>> byPoNumber = (a, b) ->
                Objects.toString(a.get("poNumber"), "").compareTo(Objects.toString(b.get("poNumber"), ""));
        favourites.sort(byPoNumber);
        others.sort(byPoNumber);

        List<Map<String, Object>> sorted = new ArrayList<>(favourites.size() + others.size());
        sorted.addAll(favourites);
        sorted.addAll(others);
        return sorted;
    }

    // ===== END SHARED PO NESTING HELPERS =====

    @PostMapping(value = "/reports/getNestedDccData", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getNestedDccData(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        // Map<String, Object> response = new HashMap<>();
        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        String fixedColumnName = "";
        // Validate page and size
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        String paginationSql = "";
        String whereClause = " WHERE 1=1 ";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND PO.supplierid='" + supplierId + "'";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {

            if (columnName.equalsIgnoreCase("recordNo")) {
                fixedColumnName = "dccRecordNo";
            } else if (columnName.equalsIgnoreCase("projectName")) {
                fixedColumnName = "dccProjectName";
            } else if (columnName.equalsIgnoreCase("vendorName")) {
                fixedColumnName = "dccVendorName";
            } else {
                fixedColumnName = columnName;
            }

            whereClause += " AND PO." + fixedColumnName + " LIKE '%" + searchQuery + "%'";
        }

        // Step 1: Fetch unique POs
        String uniquePOsSql = "SELECT DISTINCT PO.dccRecordNo FROM dccPOCombinedView PO " + whereClause;
        List<String> uniquePONumbers = jdbcTemplate.queryForList(uniquePOsSql, String.class);

        if (page == 1 && size == 20000) {
            String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN ("
                    + String.join(",", uniquePONumbers.stream()
                            .map(po -> po.toString()) // 
                            .collect(Collectors.toList()))
                    + ")";

            loggger.info("GET NESTED SQL 1  " + lineItemsSql);
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);
            loggger.info("GET NESTED SQL RESPONSE  " + lineItemsSql);

            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            lineItems.forEach(lineItem -> {
                Object poNumberObj = lineItem.get("dccRecordNo");
                String poNumber = String.valueOf(poNumberObj);
                if (!groupedResults.containsKey(poNumber)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                    // Remove unnecessary fields
                    groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                    groupedRow.put("projectName", lineItem.get("dccProjectName"));
                    groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                    groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                    groupedRow.put("vendorNumber", lineItem.get("supplierId"));
                    groupedRow.put("dccCurrency", lineItem.get("dccCurrency"));

                    groupedRow.remove("lnRecordNo");
                    groupedRow.remove("lnProductName");
                    groupedRow.remove("lnProductSerialNo");
                    groupedRow.remove("lnDeliveredQty");
                    groupedRow.remove("lnLocationName");
                    groupedRow.remove("lnInserviceDate");
                    groupedRow.remove("lnUnitPrice");
                    groupedRow.remove("lnScopeOfWork");
                    groupedRow.remove("lnRemarks");
                    groupedRow.remove("lnItemCode");
                    groupedRow.remove("linkId");
                    groupedRow.remove("tagNumber");
                    groupedRow.remove("dccCurrency");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("actualItemCode");
                    groupedRow.remove("uplLineNumber");
                    groupedRow.remove("UPLACPTRequestValue");
                    groupedRow.remove("POAcceptanceQty");
                    groupedRow.remove("POLineAcceptanceQty");
                    groupedRow.remove("poPendingQuantity");
                    groupedRow.remove("poOrderQuantity");
                    groupedRow.remove("itemPartNumber");
                    groupedRow.remove("poLineDescription");
                    groupedRow.remove("uplLineQuantity");
                    groupedRow.remove("poLineQuantity");
                    groupedRow.remove("uplLineItemCode");
                    groupedRow.remove("uplLineDescription");
                    groupedRow.remove("unitOfMeasure");
                    groupedRow.remove("activeOrPassive");
                    groupedRow.remove("uplPendingQuantity");

                    groupedRow.put("lineItems", new ArrayList<Map<String, Object>>());
                    groupedResults.put(poNumber, groupedRow);

                    groupedRow.remove("dccRecordNo");
                    groupedRow.remove("dccProjectName");
                    groupedRow.remove("dccVendorName");
                    groupedRow.remove("dccVendorEmail");

                }

                Map<String, Object> poLineItem = new LinkedHashMap<>();
                poLineItem.put("recordNo", lineItem.get("lnRecordNo"));
                poLineItem.put("lnProductName", lineItem.get("lnProductName"));
                poLineItem.put("serialNumber", lineItem.get("lnProductSerialNo"));
                poLineItem.put("deliveredQty", lineItem.get("lnDeliveredQty"));
                poLineItem.put("locationName", lineItem.get("lnLocationName"));
                poLineItem.put("dateInService", (lineItem.get("lnInserviceDate")));
                poLineItem.put("lnUnitPrice", (lineItem.get("lnUnitPrice")));
                poLineItem.put("scopeOfWork", (lineItem.get("lnScopeOfWork")));
                poLineItem.put("remarks", (lineItem.get("lnRemarks")));
                poLineItem.put("itemCode", (lineItem.get("lnItemCode")));
                poLineItem.put("linkId", (lineItem.get("linkId")));
                poLineItem.put("tagNumber", lineItem.get("tagNumber"));
                poLineItem.put("poLineNumber", lineItem.get("lineNumber"));
                poLineItem.put("actualItemCode", lineItem.get("actualItemCode"));
                poLineItem.put("uplLineNumber", lineItem.get("uplLineNumber"));
                poLineItem.put("currency", lineItem.get("dccCurrency"));
                poLineItem.put("poId", lineItem.get("poId"));
                poLineItem.put("UPLACPTRequestValue", lineItem.get("UPLACPTRequestValue"));
                poLineItem.put("POAcceptanceQty", lineItem.get("POAcceptanceQty"));
                poLineItem.put("POLineAcceptanceQty", lineItem.get("POLineAcceptanceQty"));
                poLineItem.put("poPendingQuantity", lineItem.get("poPendingQuantity"));
                poLineItem.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
                poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
                poLineItem.put("poLineDescription", lineItem.get("poLineDescription"));
                poLineItem.put("uplLineQuantity", lineItem.get("uplLineQuantity"));
                poLineItem.put("poLineQuantity", lineItem.get("poLineQuantity"));
                poLineItem.put("uplLineItemCode", lineItem.get("uplLineItemCode"));
                poLineItem.put("uplLineDescription", lineItem.get("uplLineDescription"));
                poLineItem.put("uom", lineItem.get("unitOfMeasure"));
                poLineItem.put("activeOrPassive", lineItem.get("activeOrPassive"));
                poLineItem.put("uplPendingQuantity", lineItem.get("uplPendingQuantity"));

                ((List<Map<String, Object>>) groupedResults.get(poNumber).get("lineItems")).add(poLineItem);

            });
            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", uniquePONumbers.size());
            response.put("totalRecords", uniquePONumbers.size());
            response.put("totalPages", 1);
            response.put("data", new ArrayList<>(groupedResults.values()));
            return response;
        }

        String uniquePOsSql2 = "SELECT DISTINCT PO.dccRecordNo FROM dccPOCombinedView PO " + whereClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
        List<String> uniquePONumbers2 = jdbcTemplate.queryForList(uniquePOsSql2, String.class);

        if (uniquePONumbers2.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalRecords", 0);
            response.put("totalPages", 0);
            response.put("data", new ArrayList<>());
            return response;
        }
//        String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccPoNumber IN ("
//                + String.join(",", uniquePONumbers2.stream().map(po -> "'" + po + "'").collect(Collectors.toList()))
//                + ") " + whereClause.replace("WHERE 1=1", "").trim()
//                + " LIMIT " + size + " OFFSET " + (page - 1) * size;
        String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN ("
                + String.join(",", uniquePONumbers2.stream()
                        .map(po -> po.toString()) // no quotes, integer literals
                        .collect(Collectors.toList()))
                + ")";

        //String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN (" + String.join(",", uniquePONumbers2.stream().map(po -> "'" + po + "'").collect(Collectors.toList())) + ")";
        List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

        loggger.info("GET NESTED SQL 2  " + lineItemsSql);

        Map<String, Map<String, Object>> paginatedGroupedResults = new LinkedHashMap<>();
        lineItems.forEach(lineItem -> {
            Object poNumberObj = lineItem.get("dccRecordNo");
            String poNumber = String.valueOf(poNumberObj);
            if (!paginatedGroupedResults.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                // Remove unnecessary fields
                groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                groupedRow.put("projectName", lineItem.get("dccProjectName"));
                groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                groupedRow.put("vendorNumber", lineItem.get("supplierId"));
                groupedRow.put("dccCurrency", lineItem.get("dccCurrency"));
                groupedRow.remove("lnRecordNo");
                groupedRow.remove("lnProductName");
                groupedRow.remove("lnProductSerialNo");
                groupedRow.remove("lnDeliveredQty");
                groupedRow.remove("lnLocationName");
                groupedRow.remove("lnInserviceDate");
                groupedRow.remove("dccCurrency");
                groupedRow.remove("lnUnitPrice");
                groupedRow.remove("lnScopeOfWork");
                groupedRow.remove("lnRemarks");
                groupedRow.remove("lnItemCode");
                groupedRow.remove("linkId");
                groupedRow.remove("tagNumber");
                groupedRow.remove("dccCurrency");
                groupedRow.remove("lineNumber");
                groupedRow.remove("actualItemCode");
                groupedRow.remove("uplLineNumber");
                groupedRow.remove("UPLACPTRequestValue");
                groupedRow.remove("POAcceptanceQty");
                groupedRow.remove("POLineAcceptanceQty");
                groupedRow.remove("poPendingQuantity");
                groupedRow.remove("poOrderQuantity");
                groupedRow.remove("itemPartNumber");
                groupedRow.remove("poLineDescription");
                groupedRow.remove("uplLineQuantity");
                groupedRow.remove("poLineQuantity");
                groupedRow.remove("uplLineItemCode");
                groupedRow.remove("uplLineDescription");
                groupedRow.remove("unitOfMeasure");
                groupedRow.remove("activeOrPassive");
                groupedRow.remove("uplPendingQuantity");
                //groupedRow.remove("approverComment");

                // Add POlineItems key with an empty list
                groupedRow.put("lineItems", new ArrayList<Map<String, Object>>());
                paginatedGroupedResults.put(poNumber, groupedRow);

                //remove them 
                groupedRow.remove("dccRecordNo");
                groupedRow.remove("dccProjectName");
                groupedRow.remove("dccVendorName");
                groupedRow.remove("dccVendorEmail");
            }
            Map<String, Object> poLineItem = new LinkedHashMap<>();
            poLineItem.put("recordNo", lineItem.get("lnRecordNo"));
            poLineItem.put("lnProductName", lineItem.get("lnProductName"));
            poLineItem.put("serialNumber", lineItem.get("lnProductSerialNo"));
            poLineItem.put("deliveredQty", lineItem.get("lnDeliveredQty"));
            poLineItem.put("locationName", lineItem.get("lnLocationName"));
            poLineItem.put("dateInService", (lineItem.get("lnInserviceDate")));
            poLineItem.put("lnUnitPrice", (lineItem.get("lnUnitPrice")));
            poLineItem.put("scopeOfWork", (lineItem.get("lnScopeOfWork")));
            poLineItem.put("remarks", (lineItem.get("lnRemarks")));
            poLineItem.put("itemCode", (lineItem.get("lnItemCode")));
            poLineItem.put("linkId", (lineItem.get("linkId")));
            poLineItem.put("tagNumber", lineItem.get("tagNumber"));
            poLineItem.put("poLineNumber", lineItem.get("lineNumber"));
            poLineItem.put("actualItemCode", lineItem.get("actualItemCode"));
            poLineItem.put("uplLineNumber", lineItem.get("uplLineNumber"));
            poLineItem.put("currency", lineItem.get("dccCurrency"));
            poLineItem.put("poId", lineItem.get("poId"));

            poLineItem.put("UPLACPTRequestValue", lineItem.get("UPLACPTRequestValue"));
            poLineItem.put("POAcceptanceQty", lineItem.get("POAcceptanceQty"));
            poLineItem.put("POLineAcceptanceQty", lineItem.get("POLineAcceptanceQty"));
            poLineItem.put("poPendingQuantity", lineItem.get("poPendingQuantity"));

            poLineItem.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
            poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
            poLineItem.put("poLineDescription", lineItem.get("poLineDescription"));
            poLineItem.put("uplLineQuantity", lineItem.get("uplLineQuantity"));
            poLineItem.put("poLineQuantity", lineItem.get("poLineQuantity"));
            poLineItem.put("uplLineItemCode", lineItem.get("uplLineItemCode"));
            poLineItem.put("uplLineDescription", lineItem.get("uplLineDescription"));
            poLineItem.put("uom", lineItem.get("unitOfMeasure"));
            poLineItem.put("activeOrPassive", lineItem.get("activeOrPassive"));
            poLineItem.put("uplPendingQuantity", lineItem.get("uplPendingQuantity"));
            // poLineItem.put("approverComment", lineItem.get("approverComment"));

            // Add the line item to the POlineItems list
            ((List<Map<String, Object>>) paginatedGroupedResults.get(poNumber).get("lineItems")).add(poLineItem);

        });
        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", uniquePONumbers.size());
        response.put("totalPages", (int) Math.ceil((double) uniquePONumbers.size() / size));
        response.put("data", new ArrayList<>(paginatedGroupedResults.values()));
        return response;
    }
//==================GET ALL CREATED ACCEPTANCE PER SUPPLIER NESTED =====

    @PostMapping(value = "/reports/agingReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getAgingReport(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        // Map<String, Object> response = new HashMap<>();
        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        String fixedColumnName = "";
        // Validate page and size
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        String paginationSql = "";
        String whereClause = " WHERE 1=1 ";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND PO.supplierid='" + supplierId + "'";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {

            if (columnName.equalsIgnoreCase("recordNo")) {
                fixedColumnName = "dccRecordNo";
            } else if (columnName.equalsIgnoreCase("projectName")) {
                fixedColumnName = "dccProjectName";
            } else if (columnName.equalsIgnoreCase("vendorName")) {
                fixedColumnName = "dccVendorName";
            } else {
                fixedColumnName = columnName;
            }
            whereClause += " AND PO." + fixedColumnName + " LIKE '%" + searchQuery + "%'";
        }

        // Step 1: Fetch unique POs
        String uniquePOsSql = "SELECT DISTINCT PO.dccRecordNo FROM dccPOCombinedView PO " + whereClause;
        List<String> uniquePONumbers = jdbcTemplate.queryForList(uniquePOsSql, String.class
        );

        if (page == 1 && size == 20000) {
            // Fetch line items for all unique POs
            String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN ("
                    + String.join(",", uniquePONumbers.stream()
                            .map(po -> po.toString()) // no quotes, integer literals
                            .collect(Collectors.toList()))
                    + ")";

            loggger.info("GET NESTED SQL 1  " + lineItemsSql);
            //   String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN (" + String.join(",", uniquePONumbers.stream().map(po -> "'" + po + "'").collect(Collectors.toList())) + ")";
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

            // Step 3: Group line items by PO number
            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            lineItems.forEach(lineItem -> {
                Object poNumberObj = lineItem.get("dccRecordNo");
                String poNumber = String.valueOf(poNumberObj);
                if (!groupedResults.containsKey(poNumber)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);

                    groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                    groupedRow.put("projectName", lineItem.get("dccProjectName"));
                    groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                    groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                    groupedRow.put("vendorNumber", lineItem.get("supplierId"));
                    groupedRow.put("dccCurrency", lineItem.get("dccCurrency"));

                    groupedRow.remove("lnRecordNo");
                    groupedRow.remove("lnProductName");
                    groupedRow.remove("lnProductSerialNo");
                    groupedRow.remove("lnDeliveredQty");
                    groupedRow.remove("lnLocationName");
                    groupedRow.remove("lnInserviceDate");
                    groupedRow.remove("lnUnitPrice");
                    groupedRow.remove("lnScopeOfWork");
                    groupedRow.remove("lnRemarks");
                    groupedRow.remove("lnItemCode");
                    groupedRow.remove("linkId");
                    groupedRow.remove("tagNumber");
                    groupedRow.remove("dccCurrency");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("actualItemCode");
                    groupedRow.remove("uplLineNumber");
                    groupedRow.remove("UPLACPTRequestValue");
                    groupedRow.remove("POAcceptanceQty");
                    groupedRow.remove("POLineAcceptanceQty");
                    groupedRow.remove("poPendingQuantity");
                    groupedRow.remove("poOrderQuantity");
                    groupedRow.remove("itemPartNumber");
                    groupedRow.remove("poLineDescription");
                    groupedRow.remove("uplLineQuantity");
                    groupedRow.remove("poLineQuantity");
                    groupedRow.remove("uplLineItemCode");
                    groupedRow.remove("uplLineDescription");
                    groupedRow.remove("unitOfMeasure");
                    groupedRow.remove("activeOrPassive");
                    groupedRow.remove("uplPendingQuantity");
                    //groupedRow.remove("approverComment");

                    // Add POlineItems key with an empty list
                    groupedResults.put(poNumber, groupedRow);

                    //remove them 
                    groupedRow.remove("dccRecordNo");
                    groupedRow.remove("dccProjectName");
                    groupedRow.remove("dccVendorName");
                    groupedRow.remove("dccVendorEmail");

                }

            });
            // Prepare the response
            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", uniquePONumbers.size());
            response.put("totalRecords", uniquePONumbers.size());
            response.put("totalPages", 1);
            response.put("data", new ArrayList<>(groupedResults.values()));
            return response;
        }

        // Step 1: Fetch unique POs with pagination
        String uniquePOsSql2 = "SELECT DISTINCT PO.dccRecordNo FROM dccPOCombinedView PO " + whereClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
        List<String> uniquePONumbers2 = jdbcTemplate.queryForList(uniquePOsSql2, String.class
        );

        // Step 2: Fetch line items for the unique POs
        if (uniquePONumbers2.isEmpty()) {
            // If no unique POs found, return an empty response
            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalRecords", 0);
            response.put("totalPages", 0);
            response.put("data", new ArrayList<>());
            return response;
        }
//        String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccPoNumber IN ("
//                + String.join(",", uniquePONumbers2.stream().map(po -> "'" + po + "'").collect(Collectors.toList()))
//                + ") " + whereClause.replace("WHERE 1=1", "").trim()
//                + " LIMIT " + size + " OFFSET " + (page - 1) * size;
        String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN ("
                + String.join(",", uniquePONumbers2.stream()
                        .map(po -> po.toString()) // no quotes, integer literals
                        .collect(Collectors.toList()))
                + ")";

        //String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN (" + String.join(",", uniquePONumbers2.stream().map(po -> "'" + po + "'").collect(Collectors.toList())) + ")";
        List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

        loggger.info("GET NESTED SQL 2  " + lineItemsSql);

        Map<String, Map<String, Object>> paginatedGroupedResults = new LinkedHashMap<>();
        lineItems.forEach(lineItem -> {
            Object poNumberObj = lineItem.get("dccRecordNo");
            String poNumber = String.valueOf(poNumberObj);
            if (!paginatedGroupedResults.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                // Remove unnecessary fields
                groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                groupedRow.put("projectName", lineItem.get("dccProjectName"));
                groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                groupedRow.put("vendorNumber", lineItem.get("supplierId"));
                groupedRow.put("dccCurrency", lineItem.get("dccCurrency"));
                groupedRow.remove("lnRecordNo");
                groupedRow.remove("lnProductName");
                groupedRow.remove("lnProductSerialNo");
                groupedRow.remove("lnDeliveredQty");
                groupedRow.remove("lnLocationName");
                groupedRow.remove("lnInserviceDate");
                groupedRow.remove("dccCurrency");
                groupedRow.remove("lnUnitPrice");
                groupedRow.remove("lnScopeOfWork");
                groupedRow.remove("lnRemarks");
                groupedRow.remove("lnItemCode");
                groupedRow.remove("linkId");
                groupedRow.remove("tagNumber");
                groupedRow.remove("dccCurrency");
                groupedRow.remove("lineNumber");
                groupedRow.remove("actualItemCode");
                groupedRow.remove("uplLineNumber");
                groupedRow.remove("UPLACPTRequestValue");
                groupedRow.remove("POAcceptanceQty");
                groupedRow.remove("POLineAcceptanceQty");
                groupedRow.remove("poPendingQuantity");
                groupedRow.remove("poOrderQuantity");
                groupedRow.remove("itemPartNumber");
                groupedRow.remove("poLineDescription");
                groupedRow.remove("uplLineQuantity");
                groupedRow.remove("poLineQuantity");
                groupedRow.remove("uplLineItemCode");
                groupedRow.remove("uplLineDescription");
                groupedRow.remove("unitOfMeasure");
                groupedRow.remove("activeOrPassive");
                groupedRow.remove("uplPendingQuantity");
                //groupedRow.remove("approverComment");

                // Add POlineItems key with an empty list
                paginatedGroupedResults.put(poNumber, groupedRow);

                //remove them 
                groupedRow.remove("dccRecordNo");
                groupedRow.remove("dccProjectName");
                groupedRow.remove("dccVendorName");
                groupedRow.remove("dccVendorEmail");
            }

        });
        // Prepare the response
        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalRecords", uniquePONumbers.size());
        response.put("totalPages", (int) Math.ceil((double) uniquePONumbers.size() / size));
        response.put("data", new ArrayList<>(paginatedGroupedResults.values()));

        return response;
    }

    //==================GET ALL CREATED ACCEPTANCE PER SUPPLIER  =====
    //BACK UP 20250512
    //==================GET ALL CREATED ACCEPTANCE PER SUPPLIER  =====
    @PostMapping(value = "/reports/getdccdata", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getdccdata(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        String paginationSql = "";
        String whereClause = " WHERE 1=1 ";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND PO.supplierid='" + supplierId + "'";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND PO." + columnName + " LIKE '%" + searchQuery + "%'";
        }

        String countSql = "SELECT COUNT(*) FROM dccPOCombinedView PO " + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class
        );

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        } else {
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        String sql = " SELECT * FROM ALM_ZAIN_KSA.dccPOCombinedView  PO " + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);

        //   List<Map<String, Object>> result = jdbcTemplate.queryForList(finalSql);
        // Create a response map to include pagination details
        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    private Object getNullIfZero(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0 ? null : value;
        }
        return value;
    }

    //==================GET POS PER VENDOR AND PO USING NEW FORMART  =====
    @PostMapping(value = "/reports/poUplPerSupplierAndPoNumber", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    // public List<Map<String, Object>> poUplPerSupplierAndPoNumber(@RequestBody String req) {
    public Map<String, Object> poUplPerSupplierAndPoNumber(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String poID = obj.get("poId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        String dateFrom = obj.has("dateFrom") ? obj.get("dateFrom").getAsString() : "";
        String dateTo = obj.has("dateTo") ? obj.get("dateTo").getAsString() : "";

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";
        List<Object> params = new ArrayList<>();
        String whereClause = " WHERE 1=1";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND poVendorNumber = ?";
            params.add(supplierId);
        }
        if (!poID.equalsIgnoreCase("0")) {
            whereClause += " AND poNumber = ?";
            params.add(poID);
        }
        if (!dateFrom.isEmpty() && !dateTo.isEmpty()) {
            whereClause += " AND recordDateTime BETWEEN ? AND ?";
            params.add(dateFrom);
            params.add(dateTo);
        }
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        int totalRecords;
        List<Map<String, Object>> result;

        if (page == 1 && size == 20000) {
            // Fetch-all mode: single query, no COUNT needed
            String sql = "SELECT * FROM combinedPurchaseOrderView" + whereClause;
            result = jdbcTemplate.queryForList(sql, params.toArray());
            totalRecords = result.size();
            page = 1;
            size = Math.max(totalRecords, 1);
        } else {
            String countSql = "SELECT COUNT(*) FROM combinedPurchaseOrderView" + whereClause;
            totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

            if (page == 0 && size == 0) {
                paginationSql = "";
            } else {
                page = Math.max(page, 1);
                size = Math.max(size, 1);
                int offset = (page - 1) * size;
                paginationSql = " LIMIT " + size + " OFFSET " + offset;
            }

            String sql = "SELECT * FROM combinedPurchaseOrderView" + whereClause + paginationSql;
            result = jdbcTemplate.queryForList(sql, params.toArray());
        }
        loggger.info("Fetch record query :  " + whereClause);
        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;

    }

    //==================GET NEW UPLS CREATED  =====    
    @PostMapping(value = "/reports/getAllCreatedUPLs", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getAllCreatedUPLs(@RequestBody String req) {
        //  public List<Map<String, Object>> getAllCreatedUPLs(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String poNumber = obj.get("poNumber").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        // Columns the UX is allowed to filter by — columnName comes straight off the request
        // body, so it's whitelisted here rather than concatenated in, to close a SQL-injection
        // hole (the value itself is still passed as a bind parameter, never concatenated).
        Set<String> filterableColumns = new HashSet<>(Arrays.asList(
                "recordNo", "vendor", "manufacturer", "countryOfOrigin", "projectName", "poType",
                "releaseNumber", "poNumber", "poLineNumber", "uplLine", "poLineItemType", "poLineItemCode",
                "poLineDescription", "uplLineItemType", "uplLineItemCode", "uplLineDescription",
                "zainItemCategoryCode", "zainItemCategoryDescription", "uplItemSerialized", "activeOrPassive",
                "uom", "currency", "poLineQuantity", "poLineUnitPrice", "uplLineQuantity", "uplLineUnitPrice",
                "substituteItemCode", "remarks", "createdByName"));

        String paginationSql = "";
        String whereClause = " WHERE 1=1 AND UPL.status = 'ACTIVE'";
        List<Object> params = new ArrayList<>();

        if (!poNumber.equalsIgnoreCase("0")) {
            whereClause += " AND UPL.poNumber = ?";
            params.add(poNumber);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty() && filterableColumns.contains(columnName)) {
            whereClause += " AND UPL." + columnName + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        String countSql = "SELECT COUNT(*) FROM tb_PurchaseOrderUPL UPL" + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

        if (page == 0 && size == 0) {
            paginationSql = "";
        } else if (page == 1 && size == 20000) {
            page = 0;
            size = totalRecords;
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;

            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        } else {
            page = Math.max(page, 1); // Ensure page is at least 1 if not 0
            size = Math.max(size, 1); // Ensure size is at least 1 if not 0
            int offset = (page - 1) * size;
            paginationSql = " LIMIT " + size + " OFFSET " + offset;
        }

        String sql = "SELECT UPL.recordNo, UPL.recordDatetime, UPL.vendor, UPL.manufacturer, UPL.countryOfOrigin, UPL.projectName, "
                + "UPL.poType, UPL.releaseNumber, UPL.poNumber, UPL.poLineNumber, UPL.uplLine, UPL.poLineItemType, UPL.poLineItemCode, "
                + "UPL.poLineDescription, UPL.uplLineItemType, UPL.uplLineItemCode, UPL.uplLineDescription, UPL.zainItemCategoryCode, "
                + "UPL.zainItemCategoryDescription, UPL.uplItemSerialized, UPL.activeOrPassive, UPL.uom, UPL.currency, "
                + "UPL.poLineQuantity, UPL.poLineUnitPrice, UPL.uplLineQuantity, UPL.uplLineUnitPrice, UPL.substituteItemCode, "
                + "UPL.remarks, UPL.status,"
                + "UPL.createdByName, UPL.uplModifiedBy AS updatedByName, UPL.uplModifiedDate AS updatedDatetime "
                + "FROM tb_PurchaseOrderUPL UPL " + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

        return response;
    }

    //==================GET ALL CREATED ACCEPTANCE PER SUPPLIER AND RECORD NO   =====
    @PostMapping(value = "/reports/getdccperrecordNo", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public List<Map<String, Object>> getdccperrecordNo(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        Integer recordNo = obj.get("recordNo").getAsInt();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

        String whereClause = " WHERE 1=1 ";

        if (!supplierId.equalsIgnoreCase("0")) {
            whereClause += " AND supplierid='" + supplierId + "' AND dccRecordNo='" + recordNo + "'";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName + " LIKE '%" + searchQuery + "%'";
        }

        String sql = "SELECT * FROM ALM_ZAIN_KSA.dccPOCombinedView " + whereClause;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return result;
    }

    @PostMapping(value = "/reports/getdccstatusdata")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public String getdccstatusdata(@RequestBody String req) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Gson gsondt = new Gson();
            helper.logToFile(genHeader("N/A", "GetDCCStatusData", "GetDCCStatusData") + "GetDCCStatusRequest " + req, "INFO");
            JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
            String supplierId = obj.get("supplierId").getAsString();
            List<DccPoCombinedView> dccpostatus = dccpocombinedviewrp.findBySupplierIdAndDccStatus(supplierId, "inprocess");
            if (!dccpostatus.isEmpty()) {
                return (gsondt.toJson(dccpostatus));
            } else {
                return ("No DCC Status Data found.");
            }
        } catch (JsonSyntaxException exc) {
            String err = exc.toString();
            helper.logToFile(genHeader("N/A", "GetDCCStatusData", "GetDCCStatusData") + "GetDCCStatusData error " + err, "INFO");
        }
        return null;
    }

    @PostMapping(value = "/reports/getupldata")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public String getupldata(@RequestBody String req) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Gson gsondt = new Gson();
            helper.logToFile(genHeader("N/A", "GetUPLData", "GetUPLData") + "GetUPLDataRequest " + req, "INFO");
            JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
            String poId = obj.get("poId").getAsString();
            List<upldata> upldata = uprepo.findByPoId(poId);
            if (!upldata.isEmpty()) {
                return (gsondt.toJson(upldata));
            } else {
                return ("No UPL Data found.");
            }
        } catch (JsonSyntaxException exc) {
            String err = exc.toString();
            helper.logToFile(genHeader("N/A", "GetUPLData", "GetUPLData") + "GetUPLData error " + err, "INFO");
        }
        return null;
    }

    @GetMapping(value = "/reports/getallupls")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public String getallupls() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Gson gsondt = new Gson();

            List<upldata> upldata = uprepo.findAll();
            if (!upldata.isEmpty()) {
                return (gsondt.toJson(upldata));
            } else {
                return ("No UPL Data found.");
            }
        } catch (Exception exc) {
            loggger.info("Exception " + exc.toString());
        }
        return null;
    }

    @PostMapping("/filter")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterPurchaseOrders(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            // Validate page and size
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            // Initialize WHERE clause and parameters
            String whereClause = " WHERE 1=1";
            List<Object> params = new ArrayList<>();

            // Build WHERE clause for filters
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            // String filters
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                whereClause += " AND PO.poNumber = ?";
                params.add(filters.get("poNumber"));
            }
            if (filters.containsKey("projectName") && !filters.get("projectName").isEmpty()) {
                whereClause += " AND PO.projectName = ?";
                params.add(filters.get("projectName"));
            }
            if (filters.containsKey("prNum") && !filters.get("prNum").isEmpty()) {
                whereClause += " AND PO.prNum = ?";
                params.add(filters.get("prNum"));
            }
            if (filters.containsKey("typeLookUpCode") && !filters.get("typeLookUpCode").isEmpty()) {
                whereClause += " AND PO.typeLookUpCode = ?";
                params.add(filters.get("typeLookUpCode"));
            }
            if (filters.containsKey("vendorName") && !filters.get("vendorName").isEmpty()) {
                whereClause += " AND PO.vendorName = ?";
                params.add(filters.get("vendorName"));
            }
            if (filters.containsKey("currencyCode") && !filters.get("currencyCode").isEmpty()) {
                whereClause += " AND PO.currencyCode = ?";
                params.add(filters.get("currencyCode"));
            }

            // Numeric filters
            if (filters.containsKey("totalPoQtyNew") && !filters.get("totalPoQtyNew").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalPoQtyNew"));
                    whereClause += " AND PO.poQtyNew = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalPoQtyNew format: " + filters.get("totalPoQtyNew"), e);
                }
            }
            if (filters.containsKey("totalpoOrderQuantity") && !filters.get("totalpoOrderQuantity").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalpoOrderQuantity"));
                    whereClause += " AND PO.poOrderQuantity = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalpoOrderQuantity format: " + filters.get("totalpoOrderQuantity"), e);
                }
            }
            if (filters.containsKey("totalQuantityReceived") && !filters.get("totalQuantityReceived").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalQuantityReceived"));
                    whereClause += " AND PO.quantityReceived = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityReceived format: " + filters.get("totalQuantityReceived"), e);
                }
            }
            if (filters.containsKey("totalQuantityDueOld") && !filters.get("totalQuantityDueOld").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalQuantityDueOld"));
                    whereClause += " AND PO.quantityDueOld = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityDueOld format: " + filters.get("totalQuantityDueOld"), e);
                }
            }
            if (filters.containsKey("totalQuantityDueNew") && !filters.get("totalQuantityDueNew").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalQuantityDueNew"));
                    whereClause += " AND PO.quantityDueNew = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityDueNew format: " + filters.get("totalQuantityDueNew"), e);
                }
            }
            if (filters.containsKey("totalQuantityBilled") && !filters.get("totalQuantityBilled").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totalQuantityBilled"));
                    whereClause += " AND PO.quantityBilled = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityBilled format: " + filters.get("totalQuantityBilled"), e);
                }
            }
            if (filters.containsKey("totallinePriceInSAR") && !filters.get("totallinePriceInSAR").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("totallinePriceInSAR"));
                    whereClause += " AND PO.linePriceInSAR = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totallinePriceInSAR format: " + filters.get("totallinePriceInSAR"), e);
                }
            }

            // Date range filters
            try {
                if (filters.containsKey("createdDateStart") && !filters.get("createdDateStart").isEmpty()) {
                    whereClause += " AND PO.createdDate >= ?";
                    params.add(filters.get("createdDateStart"));
                }
                if (filters.containsKey("createdDateEnd") && !filters.get("createdDateEnd").isEmpty()) {
                    whereClause += " AND PO.createdDate <= ?";
                    params.add(filters.get("createdDateEnd"));
                }
                if (filters.containsKey("approvedDateStart") && !filters.get("approvedDateStart").isEmpty()) {
                    whereClause += " AND PO.approvedDate >= ?";
                    params.add(filters.get("approvedDateStart"));
                }
                if (filters.containsKey("approvedDateEnd") && !filters.get("approvedDateEnd").isEmpty()) {
                    whereClause += " AND PO.approvedDate <= ?";
                    params.add(filters.get("approvedDateEnd"));
                }
            } catch (Exception e) {
                loggger.error("Error parsing date filters", e);
            }

            // Count total records
            String countSql = "SELECT COUNT(*) FROM tb_PurchaseOrder PO" + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class
            );

            // Build pagination
            String paginationSql = "";
            if (size > 0) {
                int offset = page * size;
                paginationSql = " LIMIT ? OFFSET ?";
                params.add(size);
                params.add(offset);
            }

            // Build sorting
            String orderBy = "";
            if (!sortBy.isEmpty()) {
                orderBy = " ORDER BY PO." + sortBy + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            }

            // Main query (limited to relevant columns)
            String sql = "SELECT PO.recordNo, PO.poNumber, PO.projectName, PO.prNum, PO.typeLookUpCode, PO.vendorName, "
                    + "PO.currencyCode, PO.poQtyNew, PO.poOrderQuantity, PO.quantityReceived, PO.quantityDueOld, "
                    + "PO.quantityDueNew, PO.quantityBilled, PO.linePriceInSAR, PO.createdDate, PO.approvedDate "
                    + "FROM tb_PurchaseOrder PO" + whereClause + orderBy + paginationSql;

            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", result); // Match frontend's expected key
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("Purchase Order Filter Query: " + sql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering purchase orders", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering purchase orders: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

        @PostMapping("/filterby")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterPurchaseOrders(
            @RequestBody List<Map<String, Object>> filterList,  // Changed: Array of {columnName, operator, value}
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "poNumber") String sortBy,  // Default to poNumber for unique sort
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);
            int offset = page * size;

            String whereClause = " WHERE 1=1";
            List<Object> params = new ArrayList<>();

            // Build WHERE from filter list (multi-column, operators)
            if (filterList != null) {
                for (Map<String, Object> filter : filterList) {
                    String columnName = (String) filter.get("columnName");
                    String operator = (String) filter.get("operator");
                    Object value = filter.get("value");
                    if (columnName == null || value == null || value.toString().trim().isEmpty()) continue;

                    whereClause += " AND PO." + columnName + " ";
                    String valStr = value.toString();
                    switch (operator.toLowerCase()) {
                        case "equals":
                            whereClause += "= ?";
                            params.add(valStr);
                            break;
                        case "contains":
                            whereClause += "LIKE ?";
                            params.add("%" + valStr + "%");
                            break;
                        case "startswith":
                            whereClause += "LIKE ?";
                            params.add(valStr + "%");
                            break;
                        case "endswith":
                            whereClause += "LIKE ?";
                            params.add("%" + valStr);
                            break;
                        case "greaterthan":
                            whereClause += "> ?";
                            params.add(valStr);  // Parse if numeric/dat/,e needed
                            break;
                        case "lessthan":
                            whereClause += "< ?";
                            params.add(valStr);
                            break;
                        case "greaterthanorequal":
                            whereClause += ">= ?";
                            params.add(valStr);
                            break;
                        case "lessthanorequal":
                            whereClause += "<= ?";
                            params.add(valStr);
                            break;
                        default:
                            loggger.warn("Unknown operator: " + operator + " for column: " + columnName);
                            continue;
                    }
                }
            }

            // Count unique POs
            String countSql = "SELECT COUNT(DISTINCT PO.poNumber) FROM tb_PurchaseOrder PO" + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            if (totalRecords == 0) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("data", new ArrayList<>());
                emptyResponse.put("totalRecords", 0);
                emptyResponse.put("currentPage", page);
                emptyResponse.put("pageSize", size);
                emptyResponse.put("totalPages", 0);
                return new ResponseEntity<>(emptyResponse, HttpStatus.OK);
            }

            // Paginated unique POs (sorted)
            String orderBy = " ORDER BY PO." + sortBy + " " + (sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC");
            List<Object> queryParams = new ArrayList<>(params); // Copy params for query
            String paginationSql = " LIMIT ? OFFSET ?";
            queryParams.add(size);
            queryParams.add(offset);
            String uniquePOsSql = "SELECT DISTINCT PO.poNumber FROM tb_PurchaseOrder PO " + whereClause + orderBy + paginationSql;
            List<String> uniquePONumbers = jdbcTemplate.queryForList(uniquePOsSql, queryParams.toArray(), String.class);

            loggger.info("Filter SQL (unique): " + uniquePOsSql);

            // Fetch ALL line items for these POs (no pagination here)
            if (uniquePONumbers.isEmpty()) {
                // Empty response (handled above)
                return new ResponseEntity<>(Collections.singletonMap("data", new ArrayList<>()), HttpStatus.OK);
            }
            String inClause = String.join(",", uniquePONumbers.stream().map(po -> "'" + po + "'").collect(Collectors.toList()));
            String lineItemsSql = "SELECT * FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" + inClause + ")";
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

            loggger.info("Filter SQL (lines): " + lineItemsSql);

            // Group lines by poNumber, sum totals, nest POlineItems (like getNestedPurchaseOrders)
            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            for (Map<String, Object> lineItem : lineItems) {
                String poNumber = (String) lineItem.get("poNumber");
                if (!groupedResults.containsKey(poNumber)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                    // Remove line-specific fields (keep PO-level)
                    groupedRow.remove("recordNo");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("countryOfOrigin");
                    groupedRow.remove("poOrderQuantity");
                    groupedRow.remove("poQtyNew");
                    groupedRow.remove("quantityReceived");
                    groupedRow.remove("quantityDueOld");
                    groupedRow.remove("quantityDueNew");
                    groupedRow.remove("quantityBilled");
                    groupedRow.remove("unitPriceInPoCurrency");
                    groupedRow.remove("unitPriceInSAR");
                    groupedRow.remove("linePriceInPoCurrency");
                    groupedRow.remove("linePriceInSAR");
                    groupedRow.remove("amountReceived");
                    groupedRow.remove("amountDue");
                    groupedRow.remove("amountDueNew");
                    groupedRow.remove("amountBilled");
                    groupedRow.remove("poLineDescription");
                    groupedRow.remove("vendorSerialNumberYN");
                    groupedRow.remove("itemCategoryInventory");
                    groupedRow.remove("inventoryCategoryDescription");
                    groupedRow.remove("itemCategoryFA");
                    groupedRow.remove("FACategoryDescription");
                    groupedRow.remove("descopedLinePriceInPoCurrency");
                    groupedRow.remove("newLinePriceInPoCurrency");

                    // Handle flags
                    String lineCancel = lineItem.get("lineCancelFlag").toString();
                    String subAllow = lineItem.get("prSubAllow").toString();
                    groupedRow.put("lineCancelFlag", lineCancel.equalsIgnoreCase("false") ? "N" : "Y");
                    groupedRow.put("prSubAllow", subAllow.equalsIgnoreCase("false") ? "N" : "Y");

                    // Init totals
                    groupedRow.put("totalPoQtyNew", 0.0);
                    groupedRow.put("totalQuantityReceived", 0.0);
                    groupedRow.put("totalQuantityDueOld", 0.0);
                    groupedRow.put("totalQuantityDueNew", 0.0);
                    groupedRow.put("totalQuantityBilled", 0.0);
                    groupedRow.put("totalpoOrderQuantity", 0.0);
                    groupedRow.put("totalunitPriceInPoCurrency", 0.0);
                    groupedRow.put("totalunitPriceInSAR", 0.0);
                    groupedRow.put("totallinePriceInPoCurrency", 0.0);
                    groupedRow.put("totallinePriceInSAR", 0.0);
                    groupedRow.put("totalamountReceived", 0.0);
                    groupedRow.put("totalamountDue", 0.0);
                    groupedRow.put("totalamountDueNew", 0.0);
                    groupedRow.put("totalamountBilled", 0.0);
                    groupedRow.put("totalDescopedLinePriceInPoCurrency", 0.0);
                    groupedRow.put("totalNewLinePriceInPoCurrency", 0.0);

                    groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                    groupedResults.put(poNumber, groupedRow);
                }

                // Create line item
                Map<String, Object> poLineItem = new LinkedHashMap<>();
                poLineItem.put("recordNo", lineItem.get("recordNo"));
                poLineItem.put("poNumber", lineItem.get("poNumber"));
                poLineItem.put("lineNumber", lineItem.get("lineNumber"));
                poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
                poLineItem.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
                poLineItem.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
                poLineItem.put("poQtyNew", lineItem.get("poQtyNew"));
                poLineItem.put("quantityReceived", lineItem.get("quantityReceived"));
                poLineItem.put("quantityDueOld", lineItem.get("quantityDueOld"));
                poLineItem.put("quantityDueNew", lineItem.get("quantityDueNew"));
                poLineItem.put("quantityBilled", lineItem.get("quantityBilled"));
                poLineItem.put("unitPriceInPoCurrency", lineItem.get("unitPriceInPoCurrency"));
                poLineItem.put("unitPriceInSAR", lineItem.get("unitPriceInSAR"));
                poLineItem.put("linePriceInPoCurrency", lineItem.get("linePriceInPoCurrency"));
                poLineItem.put("linePriceInSAR", lineItem.get("linePriceInSAR"));
                poLineItem.put("amountReceived", lineItem.get("amountReceived"));
                poLineItem.put("amountDue", lineItem.get("amountDue"));
                poLineItem.put("amountDueNew", lineItem.get("amountDueNew"));
                poLineItem.put("amountBilled", lineItem.get("amountBilled"));
                poLineItem.put("poLineDescription", lineItem.get("poLineDescription"));
                poLineItem.put("vendorSerialNumberYN", lineItem.get("vendorSerialNumberYN"));
                poLineItem.put("itemCategoryInventory", lineItem.get("itemCategoryInventory"));
                poLineItem.put("inventoryCategoryDescription", lineItem.get("inventoryCategoryDescription"));
                poLineItem.put("itemCategoryFA", lineItem.get("itemCategoryFA"));
                poLineItem.put("FACategoryDescription", lineItem.get("FACategoryDescription"));
                poLineItem.put("descopedLinePriceInPoCurrency", lineItem.get("descopedLinePriceInPoCurrency"));
                poLineItem.put("newLinePriceInPoCurrency", lineItem.get("newLinePriceInPoCurrency"));

                // Add to nested array
                ((List<Map<String, Object>>) groupedResults.get(poNumber).get("POlineItems")).add(poLineItem);

                // Update totals (as in getNested)
                Double poOrderQuantityD = (lineItem.get("poOrderQuantity") != null) ? ((Number) lineItem.get("poOrderQuantity")).doubleValue() : 0.0;
                Double poQtyNew = (lineItem.get("poQtyNew") != null) ? ((Number) lineItem.get("poQtyNew")).doubleValue() : 0.0;
                Double quantityReceived = (lineItem.get("quantityReceived") != null) ? ((Number) lineItem.get("quantityReceived")).doubleValue() : 0.0;
                Double quantityDueOld = (lineItem.get("quantityDueOld") != null) ? ((Number) lineItem.get("quantityDueOld")).doubleValue() : 0.0;
                Double quantityDueNew = (lineItem.get("quantityDueNew") != null) ? ((Number) lineItem.get("quantityDueNew")).doubleValue() : 0.0;
                Double quantityBilled = (lineItem.get("quantityBilled") != null) ? ((Number) lineItem.get("quantityBilled")).doubleValue() : 0.0;
                Double unitPriceInPoCurrency = (lineItem.get("unitPriceInPoCurrency") != null) ? ((Number) lineItem.get("unitPriceInPoCurrency")).doubleValue() : 0.0;
                Double unitPriceInSAR = (lineItem.get("unitPriceInSAR") != null) ? ((Number) lineItem.get("unitPriceInSAR")).doubleValue() : 0.0;
                Double linePriceInPoCurrency = (lineItem.get("linePriceInPoCurrency") != null) ? ((Number) lineItem.get("linePriceInPoCurrency")).doubleValue() : 0.0;
                Double linePriceInSAR = (lineItem.get("linePriceInSAR") != null) ? ((Number) lineItem.get("linePriceInSAR")).doubleValue() : 0.0;
                Double amountReceived = (lineItem.get("amountReceived") != null) ? ((Number) lineItem.get("amountReceived")).doubleValue() : 0.0;
                Double amountDue = (lineItem.get("amountDue") != null) ? ((Number) lineItem.get("amountDue")).doubleValue() : 0.0;
                Double amountDueNew = (lineItem.get("amountDueNew") != null) ? ((Number) lineItem.get("amountDueNew")).doubleValue() : 0.0;
                Double amountBilled = (lineItem.get("amountBilled") != null) ? ((Number) lineItem.get("amountBilled")).doubleValue() : 0.0;
                Double descopedLinePriceInPoCurrency = (lineItem.get("descopedLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("descopedLinePriceInPoCurrency")).doubleValue() : 0.0;
                Double newLinePriceInPoCurrency = (lineItem.get("newLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("newLinePriceInPoCurrency")).doubleValue() : 0.0;

                Map<String, Object> groupedRow = groupedResults.get(poNumber);
                groupedRow.put("totalPoQtyNew", ((Double) groupedRow.get("totalPoQtyNew") + poQtyNew));
                groupedRow.put("totalQuantityReceived", ((Double) groupedRow.get("totalQuantityReceived") + quantityReceived));
                groupedRow.put("totalQuantityDueOld", ((Double) groupedRow.get("totalQuantityDueOld") + quantityDueOld));
                groupedRow.put("totalQuantityDueNew", ((Double) groupedRow.get("totalQuantityDueNew") + quantityDueNew));
                groupedRow.put("totalQuantityBilled", ((Double) groupedRow.get("totalQuantityBilled") + quantityBilled));
                groupedRow.put("totalpoOrderQuantity", ((Double) groupedRow.get("totalpoOrderQuantity") + poOrderQuantityD));
                groupedRow.put("totalunitPriceInPoCurrency", ((Double) groupedRow.get("totalunitPriceInPoCurrency") + unitPriceInPoCurrency));
                groupedRow.put("totalunitPriceInSAR", ((Double) groupedRow.get("totalunitPriceInSAR") + unitPriceInSAR));
                groupedRow.put("totallinePriceInPoCurrency", ((Double) groupedRow.get("totallinePriceInPoCurrency") + linePriceInPoCurrency));
                groupedRow.put("totallinePriceInSAR", ((Double) groupedRow.get("totallinePriceInSAR") + linePriceInSAR));
                groupedRow.put("totalamountReceived", ((Double) groupedRow.get("totalamountReceived") + amountReceived));
                groupedRow.put("totalamountDue", ((Double) groupedRow.get("totalamountDue") + amountDue));
                groupedRow.put("totalamountDueNew", ((Double) groupedRow.get("totalamountDueNew") + amountDueNew));
                groupedRow.put("totalamountBilled", ((Double) groupedRow.get("totalamountBilled") + amountBilled));
                groupedRow.put("totalDescopedLinePriceInPoCurrency", ((Double) groupedRow.get("totalDescopedLinePriceInPoCurrency") + descopedLinePriceInPoCurrency));
                groupedRow.put("totalNewLinePriceInPoCurrency", ((Double) groupedRow.get("totalNewLinePriceInPoCurrency") + newLinePriceInPoCurrency));
            }

            // Response (match getNested style)
            Map<String, Object> response = new HashMap<>();
            response.put("data", new ArrayList<>(groupedResults.values()));
            response.put("totalRecords", totalRecords);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering purchase orders", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/search")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> searchPurchaseOrder(@RequestBody Map<String, String> request) {
        try {
            String poNumber = request.get("poNumber");
            if (poNumber == null || poNumber.trim().isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "PO Number required"), HttpStatus.BAD_REQUEST);
            }

            String whereClause = " WHERE PO.poNumber = ?";
            List<Object> params = new ArrayList<>();
            params.add(poNumber);

            // Count (should be 0 or 1)
            String countSql = "SELECT COUNT(DISTINCT PO.poNumber) FROM tb_PurchaseOrder PO" + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            if (totalRecords == 0) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("data", new ArrayList<>());
                emptyResponse.put("totalRecords", 0);
                return new ResponseEntity<>(emptyResponse, HttpStatus.OK);
            }

            // Fetch lines for this PO
            String lineItemsSql = "SELECT * FROM tb_PurchaseOrder PO" + whereClause;
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql, params.toArray());

            loggger.info("Search SQL (lines): " + lineItemsSql);

            // Group lines by poNumber, sum totals, nest POlineItems (consistent with /filter and getNestedPurchaseOrders)
            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            for (Map<String, Object> lineItem : lineItems) {
                String poNum = (String) lineItem.get("poNumber");
                if (!groupedResults.containsKey(poNum)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                    // Remove line-specific fields (keep PO-level)
                    groupedRow.remove("recordNo");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("countryOfOrigin");
                    groupedRow.remove("poOrderQuantity");
                    groupedRow.remove("poQtyNew");
                    groupedRow.remove("quantityReceived");
                    groupedRow.remove("quantityDueOld");
                    groupedRow.remove("quantityDueNew");
                    groupedRow.remove("quantityBilled");
                    groupedRow.remove("unitPriceInPoCurrency");
                    groupedRow.remove("unitPriceInSAR");
                    groupedRow.remove("linePriceInPoCurrency");
                    groupedRow.remove("linePriceInSAR");
                    groupedRow.remove("amountReceived");
                    groupedRow.remove("amountDue");
                    groupedRow.remove("amountDueNew");
                    groupedRow.remove("amountBilled");
                    groupedRow.remove("poLineDescription");
                    groupedRow.remove("vendorSerialNumberYN");
                    groupedRow.remove("itemCategoryInventory");
                    groupedRow.remove("inventoryCategoryDescription");
                    groupedRow.remove("itemCategoryFA");
                    groupedRow.remove("FACategoryDescription");
                    groupedRow.remove("descopedLinePriceInPoCurrency");
                    groupedRow.remove("newLinePriceInPoCurrency");

                    // Handle flags
                    String lineCancel = lineItem.get("lineCancelFlag").toString();
                    String subAllow = lineItem.get("prSubAllow").toString();
                    groupedRow.put("lineCancelFlag", lineCancel.equalsIgnoreCase("false") ? "N" : "Y");
                    groupedRow.put("prSubAllow", subAllow.equalsIgnoreCase("false") ? "N" : "Y");

                    // Initialize totals
                    groupedRow.put("totalPoQtyNew", 0.0);
                    groupedRow.put("totalQuantityReceived", 0.0);
                    groupedRow.put("totalQuantityDueOld", 0.0);
                    groupedRow.put("totalQuantityDueNew", 0.0);
                    groupedRow.put("totalQuantityBilled", 0.0);
                    groupedRow.put("totalpoOrderQuantity", 0.0);
                    groupedRow.put("totalunitPriceInPoCurrency", 0.0);
                    groupedRow.put("totalunitPriceInSAR", 0.0);
                    groupedRow.put("totallinePriceInPoCurrency", 0.0);
                    groupedRow.put("totallinePriceInSAR", 0.0);
                    groupedRow.put("totalamountReceived", 0.0);
                    groupedRow.put("totalamountDue", 0.0);
                    groupedRow.put("totalamountDueNew", 0.0);
                    groupedRow.put("totalamountBilled", 0.0);
                    groupedRow.put("totalDescopedLinePriceInPoCurrency", 0.0);
                    groupedRow.put("totalNewLinePriceInPoCurrency", 0.0);

                    groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                    groupedResults.put(poNum, groupedRow);
                }

                // Create line item
                Map<String, Object> poLineItem = new LinkedHashMap<>();
                poLineItem.put("recordNo", lineItem.get("recordNo"));
                poLineItem.put("poNumber", lineItem.get("poNumber"));
                poLineItem.put("lineNumber", lineItem.get("lineNumber"));
                poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
                poLineItem.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
                poLineItem.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
                poLineItem.put("poQtyNew", lineItem.get("poQtyNew"));
                poLineItem.put("quantityReceived", lineItem.get("quantityReceived"));
                poLineItem.put("quantityDueOld", lineItem.get("quantityDueOld"));
                poLineItem.put("quantityDueNew", lineItem.get("quantityDueNew"));
                poLineItem.put("quantityBilled", lineItem.get("quantityBilled"));
                poLineItem.put("unitPriceInPoCurrency", lineItem.get("unitPriceInPoCurrency"));
                poLineItem.put("unitPriceInSAR", lineItem.get("unitPriceInSAR"));
                poLineItem.put("linePriceInPoCurrency", lineItem.get("linePriceInPoCurrency"));
                poLineItem.put("linePriceInSAR", lineItem.get("linePriceInSAR"));
                poLineItem.put("amountReceived", lineItem.get("amountReceived"));
                poLineItem.put("amountDue", lineItem.get("amountDue"));
                poLineItem.put("amountDueNew", lineItem.get("amountDueNew"));
                poLineItem.put("amountBilled", lineItem.get("amountBilled"));
                poLineItem.put("poLineDescription", lineItem.get("poLineDescription"));
                poLineItem.put("vendorSerialNumberYN", lineItem.get("vendorSerialNumberYN"));
                poLineItem.put("itemCategoryInventory", lineItem.get("itemCategoryInventory"));
                poLineItem.put("inventoryCategoryDescription", lineItem.get("inventoryCategoryDescription"));
                poLineItem.put("itemCategoryFA", lineItem.get("itemCategoryFA"));
                poLineItem.put("FACategoryDescription", lineItem.get("FACategoryDescription"));
                poLineItem.put("descopedLinePriceInPoCurrency", lineItem.get("descopedLinePriceInPoCurrency"));
                poLineItem.put("newLinePriceInPoCurrency", lineItem.get("newLinePriceInPoCurrency"));

                // Add to nested array
                ((List<Map<String, Object>>) groupedResults.get(poNum).get("POlineItems")).add(poLineItem);

                // Update totals
                Double poOrderQuantity = (lineItem.get("poOrderQuantity") != null) ? ((Number) lineItem.get("poOrderQuantity")).doubleValue() : 0.0;
                Double poQtyNew = (lineItem.get("poQtyNew") != null) ? ((Number) lineItem.get("poQtyNew")).doubleValue() : 0.0;
                Double quantityReceived = (lineItem.get("quantityReceived") != null) ? ((Number) lineItem.get("quantityReceived")).doubleValue() : 0.0;
                Double quantityDueOld = (lineItem.get("quantityDueOld") != null) ? ((Number) lineItem.get("quantityDueOld")).doubleValue() : 0.0;
                Double quantityDueNew = (lineItem.get("quantityDueNew") != null) ? ((Number) lineItem.get("quantityDueNew")).doubleValue() : 0.0;
                Double quantityBilled = (lineItem.get("quantityBilled") != null) ? ((Number) lineItem.get("quantityBilled")).doubleValue() : 0.0;
                Double unitPriceInPoCurrency = (lineItem.get("unitPriceInPoCurrency") != null) ? ((Number) lineItem.get("unitPriceInPoCurrency")).doubleValue() : 0.0;
                Double unitPriceInSAR = (lineItem.get("unitPriceInSAR") != null) ? ((Number) lineItem.get("unitPriceInSAR")).doubleValue() : 0.0;
                Double linePriceInPoCurrency = (lineItem.get("linePriceInPoCurrency") != null) ? ((Number) lineItem.get("linePriceInPoCurrency")).doubleValue() : 0.0;
                Double linePriceInSAR = (lineItem.get("linePriceInSAR") != null) ? ((Number) lineItem.get("linePriceInSAR")).doubleValue() : 0.0;
                Double amountReceived = (lineItem.get("amountReceived") != null) ? ((Number) lineItem.get("amountReceived")).doubleValue() : 0.0;
                Double amountDue = (lineItem.get("amountDue") != null) ? ((Number) lineItem.get("amountDue")).doubleValue() : 0.0;
                Double amountDueNew = (lineItem.get("amountDueNew") != null) ? ((Number) lineItem.get("amountDueNew")).doubleValue() : 0.0;
                Double amountBilled = (lineItem.get("amountBilled") != null) ? ((Number) lineItem.get("amountBilled")).doubleValue() : 0.0;
                Double descopedLinePriceInPoCurrency = (lineItem.get("descopedLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("descopedLinePriceInPoCurrency")).doubleValue() : 0.0;
                Double newLinePriceInPoCurrency = (lineItem.get("newLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("newLinePriceInPoCurrency")).doubleValue() : 0.0;

                Map<String, Object> groupedRow = groupedResults.get(poNum);
                groupedRow.put("totalPoQtyNew", ((Double) groupedRow.get("totalPoQtyNew") + poQtyNew));
                groupedRow.put("totalQuantityReceived", ((Double) groupedRow.get("totalQuantityReceived") + quantityReceived));
                groupedRow.put("totalQuantityDueOld", ((Double) groupedRow.get("totalQuantityDueOld") + quantityDueOld));
                groupedRow.put("totalQuantityDueNew", ((Double) groupedRow.get("totalQuantityDueNew") + quantityDueNew));
                groupedRow.put("totalQuantityBilled", ((Double) groupedRow.get("totalQuantityBilled") + quantityBilled));
                groupedRow.put("totalpoOrderQuantity", ((Double) groupedRow.get("totalpoOrderQuantity") + poOrderQuantity));
                groupedRow.put("totalunitPriceInPoCurrency", ((Double) groupedRow.get("totalunitPriceInPoCurrency") + unitPriceInPoCurrency));
                groupedRow.put("totalunitPriceInSAR", ((Double) groupedRow.get("totalunitPriceInSAR") + unitPriceInSAR));
                groupedRow.put("totallinePriceInPoCurrency", ((Double) groupedRow.get("totallinePriceInPoCurrency") + linePriceInPoCurrency));
                groupedRow.put("totallinePriceInSAR", ((Double) groupedRow.get("totallinePriceInSAR") + linePriceInSAR));
                groupedRow.put("totalamountReceived", ((Double) groupedRow.get("totalamountReceived") + amountReceived));
                groupedRow.put("totalamountDue", ((Double) groupedRow.get("totalamountDue") + amountDue));
                groupedRow.put("totalamountDueNew", ((Double) groupedRow.get("totalamountDueNew") + amountDueNew));
                groupedRow.put("totalamountBilled", ((Double) groupedRow.get("totalamountBilled") + amountBilled));
                groupedRow.put("totalDescopedLinePriceInPoCurrency", ((Double) groupedRow.get("totalDescopedLinePriceInPoCurrency") + descopedLinePriceInPoCurrency));
                groupedRow.put("totalNewLinePriceInPoCurrency", ((Double) groupedRow.get("totalNewLinePriceInPoCurrency") + newLinePriceInPoCurrency));
            }

            // Response (match getNested and /filter style)
            Map<String, Object> response = new HashMap<>();
            response.put("data", new ArrayList<>(groupedResults.values()));  // Array with 1 PO
            response.put("totalRecords", 1);
            response.put("currentPage", 0);  // Single PO, so page is 0
            response.put("pageSize", 1);     // Single PO
            response.put("totalPages", 1);   // Single PO

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error searching PO", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/addfavorite")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> addFavoritePO(@RequestBody Map<String, String> request) {
        try {
            String poNumber = request.get("poNumber");
            String userId = request.get("userId");
            String userRole = request.get("userRole");

            // Validate inputs
            if (poNumber == null || poNumber.trim().isEmpty() || userId == null || userId.trim().isEmpty() || userRole == null || userRole.trim().isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "PO Number, User ID, and User Role are required"), HttpStatus.BAD_REQUEST);
            }

            // Check for duplicate
            String checkSql = "SELECT COUNT(*) FROM tb_FavoritePOs WHERE userId = ? AND poNumber = ?";
            int count = jdbcTemplate.queryForObject(checkSql, new Object[]{userId, poNumber}, Integer.class);

            if (count > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "PO already in favorites");
                response.put("poNumber", poNumber);
                response.put("userId", userId);
                return new ResponseEntity<>(response, HttpStatus.CONFLICT);
            }

            // Insert into favorites
            String insertSql = "INSERT INTO tb_FavoritePOs (userId, poNumber, userRole) VALUES (?, ?, ?)";
            int rowsAffected = jdbcTemplate.update(insertSql, userId, poNumber, userRole);

            Map<String, Object> response = new HashMap<>();
            if (rowsAffected > 0) {
                response.put("message", "PO added to favorites successfully");
                response.put("poNumber", poNumber);
                response.put("userId", userId);
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                response.put("message", "Failed to add PO to favorites");
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            loggger.error("Error adding favorite PO", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/favorites")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> getFavoritePOs(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String userRole = (String) request.get("userRole");
            Integer page = request.get("page") != null ? Integer.parseInt(request.get("page").toString()) : 0;
            Integer size = request.get("size") != null ? Integer.parseInt(request.get("size").toString()) : 10;
            String sortBy = (String) request.getOrDefault("sortBy", "poNumber");
            String sortDir = (String) request.getOrDefault("sortDir", "asc");

            page = Math.max(page, 0);
            size = Math.max(size, 1);
            int offset = page * size;

            if (userId == null || userId.trim().isEmpty() || userRole == null || userRole.trim().isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "User ID and User Role are required"), HttpStatus.BAD_REQUEST);
            }

            // Get favorite poNumbers
            String favWhereClause = " WHERE userId = ? AND userRole = ?";
            String favCountSql = "SELECT COUNT(*) FROM tb_FavoritePOs " + favWhereClause;
            int totalRecords = jdbcTemplate.queryForObject(favCountSql, new Object[]{userId, userRole}, Integer.class);

            if (totalRecords == 0) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("data", new ArrayList<>());
                emptyResponse.put("totalRecords", 0);
                emptyResponse.put("currentPage", page);
                emptyResponse.put("pageSize", size);
                emptyResponse.put("totalPages", 0);
                return new ResponseEntity<>(emptyResponse, HttpStatus.OK);
            }

            String favOrderBy = " ORDER BY " + sortBy + " " + (sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC");
            String favPagination = " LIMIT ? OFFSET ?";
            String favSql = "SELECT poNumber FROM tb_FavoritePOs " + favWhereClause + favOrderBy + favPagination;
            List<String> favoritePONumbers = jdbcTemplate.queryForList(favSql, new Object[]{userId, userRole, size, offset}, String.class);

            loggger.info("Favorites SQL: " + favSql);

            // Fetch nested PO data for these favorites
            if (favoritePONumbers.isEmpty()) {
                Map<String, Object> emptyResponse = new HashMap<>();
                emptyResponse.put("data", new ArrayList<>());
                emptyResponse.put("totalRecords", 0);
                emptyResponse.put("currentPage", page);
                emptyResponse.put("pageSize", size);
                emptyResponse.put("totalPages", 0);
                return new ResponseEntity<>(emptyResponse, HttpStatus.OK);
            }

            String inClause = String.join(",", favoritePONumbers.stream().map(po -> "'" + po + "'").collect(Collectors.toList()));
            String lineItemsSql = "SELECT * FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" + inClause + ")";
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

            loggger.info("Favorites line items SQL: " + lineItemsSql);

            // Group and nest (same as /filter)
            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            for (Map<String, Object> lineItem : lineItems) {
                String poNum = (String) lineItem.get("poNumber");
                if (!groupedResults.containsKey(poNum)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                    // Remove line-specific fields
                    groupedRow.remove("recordNo");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("countryOfOrigin");
                    groupedRow.remove("poOrderQuantity");
                    groupedRow.remove("poQtyNew");
                    groupedRow.remove("quantityReceived");
                    groupedRow.remove("quantityDueOld");
                    groupedRow.remove("quantityDueNew");
                    groupedRow.remove("quantityBilled");
                    groupedRow.remove("unitPriceInPoCurrency");
                    groupedRow.remove("unitPriceInSAR");
                    groupedRow.remove("linePriceInPoCurrency");
                    groupedRow.remove("linePriceInSAR");
                    groupedRow.remove("amountReceived");
                    groupedRow.remove("amountDue");
                    groupedRow.remove("amountDueNew");
                    groupedRow.remove("amountBilled");
                    groupedRow.remove("poLineDescription");
                    groupedRow.remove("vendorSerialNumberYN");
                    groupedRow.remove("itemCategoryInventory");
                    groupedRow.remove("inventoryCategoryDescription");
                    groupedRow.remove("itemCategoryFA");
                    groupedRow.remove("FACategoryDescription");
                    groupedRow.remove("descopedLinePriceInPoCurrency");
                    groupedRow.remove("newLinePriceInPoCurrency");

                    // Handle flags
                    String lineCancel = lineItem.get("lineCancelFlag").toString();
                    String subAllow = lineItem.get("prSubAllow").toString();
                    groupedRow.put("lineCancelFlag", lineCancel.equalsIgnoreCase("false") ? "N" : "Y");
                    groupedRow.put("prSubAllow", subAllow.equalsIgnoreCase("false") ? "N" : "Y");

                    // Initialize totals
                    groupedRow.put("totalPoQtyNew", 0.0);
                    groupedRow.put("totalQuantityReceived", 0.0);
                    groupedRow.put("totalQuantityDueOld", 0.0);
                    groupedRow.put("totalQuantityDueNew", 0.0);
                    groupedRow.put("totalQuantityBilled", 0.0);
                    groupedRow.put("totalpoOrderQuantity", 0.0);
                    groupedRow.put("totalunitPriceInPoCurrency", 0.0);
                    groupedRow.put("totalunitPriceInSAR", 0.0);
                    groupedRow.put("totallinePriceInPoCurrency", 0.0);
                    groupedRow.put("totallinePriceInSAR", 0.0);
                    groupedRow.put("totalamountReceived", 0.0);
                    groupedRow.put("totalamountDue", 0.0);
                    groupedRow.put("totalamountDueNew", 0.0);
                    groupedRow.put("totalamountBilled", 0.0);
                    groupedRow.put("totalDescopedLinePriceInPoCurrency", 0.0);
                    groupedRow.put("totalNewLinePriceInPoCurrency", 0.0);

                    groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                    groupedResults.put(poNum, groupedRow);
                }

                // Create line item
                Map<String, Object> poLineItem = new LinkedHashMap<>();
                poLineItem.put("recordNo", lineItem.get("recordNo"));
                poLineItem.put("poNumber", lineItem.get("poNumber"));
                poLineItem.put("lineNumber", lineItem.get("lineNumber"));
                poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
                poLineItem.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
                poLineItem.put("poOrderQuantity", lineItem.get("poOrderQuantity"));
                poLineItem.put("poQtyNew", lineItem.get("poQtyNew"));
                poLineItem.put("quantityReceived", lineItem.get("quantityReceived"));
                poLineItem.put("quantityDueOld", lineItem.get("quantityDueOld"));
                poLineItem.put("quantityDueNew", lineItem.get("quantityDueNew"));
                poLineItem.put("quantityBilled", lineItem.get("quantityBilled"));
                poLineItem.put("unitPriceInPoCurrency", lineItem.get("unitPriceInPoCurrency"));
                poLineItem.put("unitPriceInSAR", lineItem.get("unitPriceInSAR"));
                poLineItem.put("linePriceInPoCurrency", lineItem.get("linePriceInPoCurrency"));
                poLineItem.put("linePriceInSAR", lineItem.get("linePriceInSAR"));
                poLineItem.put("amountReceived", lineItem.get("amountReceived"));
                poLineItem.put("amountDue", lineItem.get("amountDue"));
                poLineItem.put("amountDueNew", lineItem.get("amountDueNew"));
                poLineItem.put("amountBilled", lineItem.get("amountBilled"));
                poLineItem.put("poLineDescription", lineItem.get("poLineDescription"));
                poLineItem.put("vendorSerialNumberYN", lineItem.get("vendorSerialNumberYN"));
                poLineItem.put("itemCategoryInventory", lineItem.get("itemCategoryInventory"));
                poLineItem.put("inventoryCategoryDescription", lineItem.get("inventoryCategoryDescription"));
                poLineItem.put("itemCategoryFA", lineItem.get("itemCategoryFA"));
                poLineItem.put("FACategoryDescription", lineItem.get("FACategoryDescription"));
                poLineItem.put("descopedLinePriceInPoCurrency", lineItem.get("descopedLinePriceInPoCurrency"));
                poLineItem.put("newLinePriceInPoCurrency", lineItem.get("newLinePriceInPoCurrency"));

                ((List<Map<String, Object>>) groupedResults.get(poNum).get("POlineItems")).add(poLineItem);

                // Update totals
                Double poOrderQuantity = (lineItem.get("poOrderQuantity") != null) ? ((Number) lineItem.get("poOrderQuantity")).doubleValue() : 0.0;
                Double poQtyNew = (lineItem.get("poQtyNew") != null) ? ((Number) lineItem.get("poQtyNew")).doubleValue() : 0.0;
                Double quantityReceived = (lineItem.get("quantityReceived") != null) ? ((Number) lineItem.get("quantityReceived")).doubleValue() : 0.0;
                Double quantityDueOld = (lineItem.get("quantityDueOld") != null) ? ((Number) lineItem.get("quantityDueOld")).doubleValue() : 0.0;
                Double quantityDueNew = (lineItem.get("quantityDueNew") != null) ? ((Number) lineItem.get("quantityDueNew")).doubleValue() : 0.0;
                Double quantityBilled = (lineItem.get("quantityBilled") != null) ? ((Number) lineItem.get("quantityBilled")).doubleValue() : 0.0;
                Double unitPriceInPoCurrency = (lineItem.get("unitPriceInPoCurrency") != null) ? ((Number) lineItem.get("unitPriceInPoCurrency")).doubleValue() : 0.0;
                Double unitPriceInSAR = (lineItem.get("unitPriceInSAR") != null) ? ((Number) lineItem.get("unitPriceInSAR")).doubleValue() : 0.0;
                Double linePriceInPoCurrency = (lineItem.get("linePriceInPoCurrency") != null) ? ((Number) lineItem.get("linePriceInPoCurrency")).doubleValue() : 0.0;
                Double linePriceInSAR = (lineItem.get("linePriceInSAR") != null) ? ((Number) lineItem.get("linePriceInSAR")).doubleValue() : 0.0;
                Double amountReceived = (lineItem.get("amountReceived") != null) ? ((Number) lineItem.get("amountReceived")).doubleValue() : 0.0;
                Double amountDue = (lineItem.get("amountDue") != null) ? ((Number) lineItem.get("amountDue")).doubleValue() : 0.0;
                Double amountDueNew = (lineItem.get("amountDueNew") != null) ? ((Number) lineItem.get("amountDueNew")).doubleValue() : 0.0;
                Double amountBilled = (lineItem.get("amountBilled") != null) ? ((Number) lineItem.get("amountBilled")).doubleValue() : 0.0;
                Double descopedLinePriceInPoCurrency = (lineItem.get("descopedLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("descopedLinePriceInPoCurrency")).doubleValue() : 0.0;
                Double newLinePriceInPoCurrency = (lineItem.get("newLinePriceInPoCurrency") != null) ? ((Number) lineItem.get("newLinePriceInPoCurrency")).doubleValue() : 0.0;

                Map<String, Object> groupedRow = groupedResults.get(poNum);
                groupedRow.put("totalPoQtyNew", ((Double) groupedRow.get("totalPoQtyNew") + poQtyNew));
                groupedRow.put("totalQuantityReceived", ((Double) groupedRow.get("totalQuantityReceived") + quantityReceived));
                groupedRow.put("totalQuantityDueOld", ((Double) groupedRow.get("totalQuantityDueOld") + quantityDueOld));
                groupedRow.put("totalQuantityDueNew", ((Double) groupedRow.get("totalQuantityDueNew") + quantityDueNew));
                groupedRow.put("totalQuantityBilled", ((Double) groupedRow.get("totalQuantityBilled") + quantityBilled));
                groupedRow.put("totalpoOrderQuantity", ((Double) groupedRow.get("totalpoOrderQuantity") + poOrderQuantity));
                groupedRow.put("totalunitPriceInPoCurrency", ((Double) groupedRow.get("totalunitPriceInPoCurrency") + unitPriceInPoCurrency));
                groupedRow.put("totalunitPriceInSAR", ((Double) groupedRow.get("totalunitPriceInSAR") + unitPriceInSAR));
                groupedRow.put("totallinePriceInPoCurrency", ((Double) groupedRow.get("totallinePriceInPoCurrency") + linePriceInPoCurrency));
                groupedRow.put("totallinePriceInSAR", ((Double) groupedRow.get("totallinePriceInSAR") + linePriceInSAR));
                groupedRow.put("totalamountReceived", ((Double) groupedRow.get("totalamountReceived") + amountReceived));
                groupedRow.put("totalamountDue", ((Double) groupedRow.get("totalamountDue") + amountDue));
                groupedRow.put("totalamountDueNew", ((Double) groupedRow.get("totalamountDueNew") + amountDueNew));
                groupedRow.put("totalamountBilled", ((Double) groupedRow.get("totalamountBilled") + amountBilled));
                groupedRow.put("totalDescopedLinePriceInPoCurrency", ((Double) groupedRow.get("totalDescopedLinePriceInPoCurrency") + descopedLinePriceInPoCurrency));
                groupedRow.put("totalNewLinePriceInPoCurrency", ((Double) groupedRow.get("totalNewLinePriceInPoCurrency") + newLinePriceInPoCurrency));
            }

            // Response
            Map<String, Object> response = new HashMap<>();
            response.put("data", new ArrayList<>(groupedResults.values()));
            response.put("totalRecords", totalRecords);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error fetching favorite POs", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/favorite/{poNumber}")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> removeFavoritePO(
            @PathVariable String poNumber,
            @RequestParam String userId,  // From auth or query param
            @RequestParam String userRole) {  // 'ZAIN' or 'VENDOR'
        try {
            if (poNumber == null || poNumber.trim().isEmpty() || userId == null || userRole == null) {
                return new ResponseEntity<>(Collections.singletonMap("message", "PO Number, User ID, and User Role are required"), HttpStatus.BAD_REQUEST);
            }

            // Delete from favorites
            String deleteSql = "DELETE FROM tb_FavoritePOs WHERE userId = ? AND poNumber = ? AND userRole = ?";
            int rowsAffected = jdbcTemplate.update(deleteSql, userId, poNumber, userRole);

            Map<String, Object> response = new HashMap<>();
            if (rowsAffected > 0) {
                response.put("message", "PO removed from favorites successfully");
                response.put("poNumber", poNumber);
                response.put("userId", userId);
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                response.put("message", "PO not found in favorites");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            loggger.error("Error removing favorite PO", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filterNestedPurchaseOrders")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterNestedPurchaseOrders(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "poNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            String baseWhereClause = " WHERE 1=1";
            List<Object> baseParams = new ArrayList<>();

            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                baseWhereClause += " AND PO.poNumber = ?";
                baseParams.add(filters.get("poNumber"));
            }
            if (filters.containsKey("projectName") && !filters.get("projectName").isEmpty()) {
                baseWhereClause += " AND PO.projectName = ?";
                baseParams.add(filters.get("projectName"));
            }
            if (filters.containsKey("prNum") && !filters.get("prNum").isEmpty()) {
                baseWhereClause += " AND PO.prNum = ?";
                baseParams.add(filters.get("prNum"));
            }
            if (filters.containsKey("typeLookUpCode") && !filters.get("typeLookUpCode").isEmpty()) {
                baseWhereClause += " AND PO.typeLookUpCode = ?";
                baseParams.add(filters.get("typeLookUpCode"));
            }
            if (filters.containsKey("vendorName") && !filters.get("vendorName").isEmpty()) {
                baseWhereClause += " AND PO.vendorName = ?";
                baseParams.add(filters.get("vendorName"));
            }
            if (filters.containsKey("currencyCode") && !filters.get("currencyCode").isEmpty()) {
                baseWhereClause += " AND PO.currencyCode = ?";
                baseParams.add(filters.get("currencyCode"));
            }
            if (filters.containsKey("supplierId") && !filters.get("supplierId").isEmpty()) {
                baseWhereClause += " AND PO.vendorNumber = ?";
                baseParams.add(filters.get("supplierId"));
            }
            try {
                if (filters.containsKey("createdDateStart") && !filters.get("createdDateStart").isEmpty()) {
                    baseWhereClause += " AND PO.createdDate >= ?";
                    baseParams.add(filters.get("createdDateStart"));
                }
                if (filters.containsKey("createdDateEnd") && !filters.get("createdDateEnd").isEmpty()) {
                    baseWhereClause += " AND PO.createdDate <= ?";
                    baseParams.add(filters.get("createdDateEnd"));
                }
                if (filters.containsKey("approvedDateStart") && !filters.get("approvedDateStart").isEmpty()) {
                    baseWhereClause += " AND PO.approvedDate >= ?";
                    baseParams.add(filters.get("approvedDateStart"));
                }
                if (filters.containsKey("approvedDateEnd") && !filters.get("approvedDateEnd").isEmpty()) {
                    baseWhereClause += " AND PO.approvedDate <= ?";
                    baseParams.add(filters.get("approvedDateEnd"));
                }
            } catch (Exception e) {
                loggger.error("Error parsing date filters", e);
            }

            String havingClause = "";
            List<Object> havingParams = new ArrayList<>();
            if (filters.containsKey("totalPoQtyNew") && !filters.get("totalPoQtyNew").isEmpty()) {
                try { havingClause += " AND SUM(PO.poQtyNew) = ?"; havingParams.add(Double.parseDouble(filters.get("totalPoQtyNew"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalPoQtyNew", e); }
            }
            if (filters.containsKey("totalpoOrderQuantity") && !filters.get("totalpoOrderQuantity").isEmpty()) {
                try { havingClause += " AND SUM(PO.poOrderQuantity) = ?"; havingParams.add(Double.parseDouble(filters.get("totalpoOrderQuantity"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalpoOrderQuantity", e); }
            }
            if (filters.containsKey("totalQuantityReceived") && !filters.get("totalQuantityReceived").isEmpty()) {
                try { havingClause += " AND SUM(PO.quantityReceived) = ?"; havingParams.add(Double.parseDouble(filters.get("totalQuantityReceived"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalQuantityReceived", e); }
            }
            if (filters.containsKey("totalQuantityDueOld") && !filters.get("totalQuantityDueOld").isEmpty()) {
                try { havingClause += " AND SUM(PO.quantityDueOld) = ?"; havingParams.add(Double.parseDouble(filters.get("totalQuantityDueOld"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalQuantityDueOld", e); }
            }
            if (filters.containsKey("totalQuantityDueNew") && !filters.get("totalQuantityDueNew").isEmpty()) {
                try { havingClause += " AND SUM(PO.quantityDueNew) = ?"; havingParams.add(Double.parseDouble(filters.get("totalQuantityDueNew"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalQuantityDueNew", e); }
            }
            if (filters.containsKey("totalQuantityBilled") && !filters.get("totalQuantityBilled").isEmpty()) {
                try { havingClause += " AND SUM(PO.quantityBilled) = ?"; havingParams.add(Double.parseDouble(filters.get("totalQuantityBilled"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totalQuantityBilled", e); }
            }
            if (filters.containsKey("totallinePriceInSAR") && !filters.get("totallinePriceInSAR").isEmpty()) {
                try { havingClause += " AND SUM(PO.linePriceInSAR) = ?"; havingParams.add(Double.parseDouble(filters.get("totallinePriceInSAR"))); }
                catch (NumberFormatException e) { loggger.error("Invalid totallinePriceInSAR", e); }
            }

            List<Object> subqueryParams = new ArrayList<>(baseParams);
            subqueryParams.addAll(havingParams);

            String havingFragment = havingClause.isEmpty() ? "" : " HAVING " + havingClause.substring(5);
            String subquery = "SELECT PO.poNumber FROM tb_PurchaseOrder PO" + baseWhereClause +
                    " GROUP BY PO.poNumber" + havingFragment;

            // Count total unique POs
            String countSql = "SELECT COUNT(*) FROM (" + subquery + ") sub";
            Integer totalRecords = jdbcTemplate.queryForObject(countSql, subqueryParams.toArray(), Integer.class);
            if (totalRecords == null || totalRecords == 0) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("reports", Collections.emptyList());
                empty.put("currentPage", page);
                empty.put("totalItems", 0);
                empty.put("totalPages", 0);
                empty.put("first", true);
                empty.put("last", true);
                empty.put("size", size);
                empty.put("sort", sortBy + "," + sortDir);
                return new ResponseEntity<>(empty, HttpStatus.OK);
            }

            // Build sorted subquery and fetch line items for the current page in one query
            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "totalPoQtyNew", "totalpoOrderQuantity", "totalQuantityReceived", "totalQuantityDueOld",
                    "totalQuantityDueNew", "totalQuantityBilled", "totallinePriceInSAR"));

            int offset = page * size;
            String sortClause = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";
            List<Object> lineParams = new ArrayList<>(subqueryParams);
            lineParams.add(size);
            lineParams.add(offset);

            String lineSql;
            // Wrap paged subquery in a derived table — MySQL does not allow LIMIT directly inside IN(...)
            if (numericColumns.contains(sortBy)) {
                String sortField = sortBy.substring(5).toLowerCase();
                String sortedSubquery = "SELECT PO.poNumber, SUM(PO." + sortField + ") AS sortVal" +
                        " FROM tb_PurchaseOrder PO" + baseWhereClause +
                        " GROUP BY PO.poNumber" + havingFragment;
                lineSql = "SELECT PO.* FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" +
                        "SELECT poNumber FROM (SELECT poNumber FROM (" + sortedSubquery + ") s1 ORDER BY sortVal " + sortClause + " LIMIT ? OFFSET ?) paged_pos" +
                        ") ORDER BY PO.poNumber, PO.lineNumber";
            } else {
                String orderClause = sortBy.isEmpty() ? "" : " ORDER BY poNumber " + sortClause;
                lineSql = "SELECT PO.* FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" +
                        "SELECT poNumber FROM (SELECT poNumber FROM (" + subquery + ") s1" + orderClause + " LIMIT ? OFFSET ?) paged_pos" +
                        ") ORDER BY PO.poNumber, PO.lineNumber";
            }

            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineSql, lineParams.toArray());
            List<Map<String, Object>> nestedReports = new ArrayList<>(groupLineItemsByPO(lineItems).values());

            Map<String, Object> response = new HashMap<>();
            response.put("reports", nestedReports);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", nestedReports.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("filterNestedPurchaseOrders: Count SQL: " + countSql + ", Line SQL: " + lineSql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering purchase orders", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering purchase orders: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filterUPLs")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterUPLs(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordNo") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            // Validate page and size
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            // Initialize WHERE clause and parameters
            String whereClause = " WHERE 1=1 AND UPL.status = 'ACTIVE'";
            List<Object> params = new ArrayList<>();

            // Build WHERE clause for filters
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            // String filters
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                whereClause += " AND UPL.poNumber = ?";
                params.add(filters.get("poNumber"));
            }
            if (filters.containsKey("vendor") && !filters.get("vendor").isEmpty()) {
                whereClause += " AND UPL.vendor = ?";
                params.add(filters.get("vendor"));
            }
            if (filters.containsKey("manufacturer") && !filters.get("manufacturer").isEmpty()) {
                whereClause += " AND UPL.manufacturer = ?";
                params.add(filters.get("manufacturer"));
            }
            if (filters.containsKey("countryOfOrigin") && !filters.get("countryOfOrigin").isEmpty()) {
                whereClause += " AND UPL.countryOfOrigin = ?";
                params.add(filters.get("countryOfOrigin"));
            }
            if (filters.containsKey("projectName") && !filters.get("projectName").isEmpty()) {
                whereClause += " AND UPL.projectName = ?";
                params.add(filters.get("projectName"));
            }
            if (filters.containsKey("poType") && !filters.get("poType").isEmpty()) {
                whereClause += " AND UPL.poType = ?";
                params.add(filters.get("poType"));
            }
            if (filters.containsKey("releaseNumber") && !filters.get("releaseNumber").isEmpty()) {
                whereClause += " AND UPL.releaseNumber = ?";
                params.add(filters.get("releaseNumber"));
            }
            if (filters.containsKey("poLineNumber") && !filters.get("poLineNumber").isEmpty()) {
                whereClause += " AND UPL.poLineNumber = ?";
                params.add(filters.get("poLineNumber"));
            }
            if (filters.containsKey("uplLine") && !filters.get("uplLine").isEmpty()) {
                whereClause += " AND UPL.uplLine = ?";
                params.add(filters.get("uplLine"));
            }
            if (filters.containsKey("poLineItemType") && !filters.get("poLineItemType").isEmpty()) {
                whereClause += " AND UPL.poLineItemType = ?";
                params.add(filters.get("poLineItemType"));
            }
            if (filters.containsKey("poLineItemCode") && !filters.get("poLineItemCode").isEmpty()) {
                whereClause += " AND UPL.poLineItemCode = ?";
                params.add(filters.get("poLineItemCode"));
            }
            if (filters.containsKey("poLineDescription") && !filters.get("poLineDescription").isEmpty()) {
                whereClause += " AND UPL.poLineDescription = ?";
                params.add(filters.get("poLineDescription"));
            }
            if (filters.containsKey("uplLineItemType") && !filters.get("uplLineItemType").isEmpty()) {
                whereClause += " AND UPL.uplLineItemType = ?";
                params.add(filters.get("uplLineItemType"));
            }
            if (filters.containsKey("uplLineItemCode") && !filters.get("uplLineItemCode").isEmpty()) {
                whereClause += " AND UPL.uplLineItemCode = ?";
                params.add(filters.get("uplLineItemCode"));
            }
            if (filters.containsKey("uplLineDescription") && !filters.get("uplLineDescription").isEmpty()) {
                whereClause += " AND UPL.uplLineDescription = ?";
                params.add(filters.get("uplLineDescription"));
            }
            if (filters.containsKey("zainItemCategoryCode") && !filters.get("zainItemCategoryCode").isEmpty()) {
                whereClause += " AND UPL.zainItemCategoryCode = ?";
                params.add(filters.get("zainItemCategoryCode"));
            }
            if (filters.containsKey("zainItemCategoryDescription") && !filters.get("zainItemCategoryDescription").isEmpty()) {
                whereClause += " AND UPL.zainItemCategoryDescription = ?";
                params.add(filters.get("zainItemCategoryDescription"));
            }
            if (filters.containsKey("activeOrPassive") && !filters.get("activeOrPassive").isEmpty()) {
                whereClause += " AND UPL.activeOrPassive = ?";
                params.add(filters.get("activeOrPassive"));
            }
            if (filters.containsKey("uom") && !filters.get("uom").isEmpty()) {
                whereClause += " AND UPL.uom = ?";
                params.add(filters.get("uom"));
            }
            if (filters.containsKey("currency") && !filters.get("currency").isEmpty()) {
                whereClause += " AND UPL.currency = ?";
                params.add(filters.get("currency"));
            }
            if (filters.containsKey("substituteItemCode") && !filters.get("substituteItemCode").isEmpty()) {
                whereClause += " AND UPL.substituteItemCode = ?";
                params.add(filters.get("substituteItemCode"));
            }
            if (filters.containsKey("remarks") && !filters.get("remarks").isEmpty()) {
                whereClause += " AND UPL.remarks = ?";
                params.add(filters.get("remarks"));
            }
            if (filters.containsKey("createdByName") && !filters.get("createdByName").isEmpty()) {
                whereClause += " AND UPL.createdByName = ?";
                params.add(filters.get("createdByName"));
            }
            if (filters.containsKey("updatedByName") && !filters.get("updatedByName").isEmpty()) {
                whereClause += " AND UPL.uplModifiedBy = ?";
                params.add(filters.get("updatedByName"));
            }

            // Numeric filters
            if (filters.containsKey("poLineQuantity") && !filters.get("poLineQuantity").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("poLineQuantity"));
                    whereClause += " AND UPL.poLineQuantity = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid poLineQuantity format: " + filters.get("poLineQuantity"), e);
                }
            }
            if (filters.containsKey("poLineUnitPrice") && !filters.get("poLineUnitPrice").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("poLineUnitPrice"));
                    whereClause += " AND UPL.poLineUnitPrice = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid poLineUnitPrice format: " + filters.get("poLineUnitPrice"), e);
                }
            }
            if (filters.containsKey("uplLineQuantity") && !filters.get("uplLineQuantity").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("uplLineQuantity"));
                    whereClause += " AND UPL.uplLineQuantity = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid uplLineQuantity format: " + filters.get("uplLineQuantity"), e);
                }
            }
            if (filters.containsKey("uplLineUnitPrice") && !filters.get("uplLineUnitPrice").isEmpty()) {
                try {
                    Double value = Double.parseDouble(filters.get("uplLineUnitPrice"));
                    whereClause += " AND UPL.uplLineUnitPrice = ?";
                    params.add(value);
                } catch (NumberFormatException e) {
                    loggger.error("Invalid uplLineUnitPrice format: " + filters.get("uplLineUnitPrice"), e);
                }
            }

            // Date range filters
            try {
                if (filters.containsKey("recordDatetimeStart") && !filters.get("recordDatetimeStart").isEmpty()) {
                    whereClause += " AND UPL.recordDatetime >= ?";
                    params.add(filters.get("recordDatetimeStart"));
                }
                if (filters.containsKey("recordDatetimeEnd") && !filters.get("recordDatetimeEnd").isEmpty()) {
                    whereClause += " AND UPL.recordDatetime <= ?";
                    params.add(filters.get("recordDatetimeEnd"));
                }
                if (filters.containsKey("updatedDatetimeStart") && !filters.get("updatedDatetimeStart").isEmpty()) {
                    whereClause += " AND UPL.uplModifiedDate >= ?";
                    params.add(filters.get("updatedDatetimeStart"));
                }
                if (filters.containsKey("updatedDatetimeEnd") && !filters.get("updatedDatetimeEnd").isEmpty()) {
                    whereClause += " AND UPL.uplModifiedDate <= ?";
                    params.add(filters.get("updatedDatetimeEnd"));
                }
            } catch (Exception e) {
                loggger.error("Error parsing date filters", e);
            }

            // Count total records
            String countSql = "SELECT COUNT(*) FROM tb_PurchaseOrderUPL UPL" + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // Build pagination
            String paginationSql = "";
            if (size > 0) {
                int offset = page * size;
                paginationSql = " LIMIT ? OFFSET ?";
                params.add(size);
                params.add(offset);
            }

            // Build sorting
            String orderBy = "";
            if (!sortBy.isEmpty()) {
                orderBy = " ORDER BY UPL." + sortBy + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            }

            // Main query (all relevant columns)
            String sql = "SELECT UPL.recordNo, UPL.recordDatetime, UPL.vendor, UPL.manufacturer, UPL.countryOfOrigin, UPL.projectName, "
                    + "UPL.poType, UPL.releaseNumber, UPL.poNumber, UPL.poLineNumber, UPL.uplLine, UPL.poLineItemType, UPL.poLineItemCode, "
                    + "UPL.poLineDescription, UPL.uplLineItemType, UPL.uplLineItemCode, UPL.uplLineDescription, UPL.zainItemCategoryCode, "
                    + "UPL.zainItemCategoryDescription, UPL.uplItemSerialized, UPL.activeOrPassive, UPL.uom, UPL.currency, "
                    + "UPL.poLineQuantity, UPL.poLineUnitPrice, UPL.uplLineQuantity, UPL.uplLineUnitPrice, UPL.substituteItemCode, "
                    + "UPL.remarks, UPL.status, UPL.createdByName, UPL.uplModifiedBy AS updatedByName, UPL.uplModifiedDate AS updatedDatetime "
                    + "FROM tb_PurchaseOrderUPL UPL" + whereClause + orderBy + paginationSql;

            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", result);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("UPL Filter Query: " + sql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering UPLs", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering UPLs: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== UPL APPROVAL AUDIT TRAIL ====================
    // One row per level decision on a tb_UPL_Change_Request — a request still
    // pending at level 1 with zero decisions yet still appears (LEFT JOINs),
    // with decision-related columns null. columnName/searchQuery and the
    // multi-filter map are both validated against uplAuditTrailColumns before
    // being used in the query, same pattern as getAllCreatedUPLs/filterUPLs.

    private static final Map<String, String> uplAuditTrailColumns = new HashMap<>();
    static {
        uplAuditTrailColumns.put("recordId", "cr.recordId");
        uplAuditTrailColumns.put("batchId", "cr.batchId");
        uplAuditTrailColumns.put("uplRecordNo", "cr.uplRecordNo");
        uplAuditTrailColumns.put("poNumber", "upl.poNumber");
        uplAuditTrailColumns.put("poLineNumber", "upl.poLineNumber");
        uplAuditTrailColumns.put("uplLine", "upl.uplLine");
        uplAuditTrailColumns.put("changeType", "cr.changeType");
        uplAuditTrailColumns.put("requestStatus", "cr.status");
        uplAuditTrailColumns.put("currentLevelNo", "cr.currentLevelNo");
        uplAuditTrailColumns.put("totalLevels", "cr.totalLevels");
        uplAuditTrailColumns.put("requestedByName", "cr.requestedByName");
        uplAuditTrailColumns.put("requestedAt", "cr.requestedAt");
        uplAuditTrailColumns.put("levelNo", "d.levelNo");
        uplAuditTrailColumns.put("decision", "d.decision");
        uplAuditTrailColumns.put("decidedByName", "d.decidedByName");
        uplAuditTrailColumns.put("decidedAt", "d.decidedAt");
        uplAuditTrailColumns.put("comments", "d.comments");
    }

    private static final String UPL_AUDIT_TRAIL_SELECT =
            "SELECT cr.recordId AS recordId, cr.batchId AS batchId, cr.uplRecordNo AS uplRecordNo, "
                    + "upl.poNumber AS poNumber, upl.poLineNumber AS poLineNumber, upl.uplLine AS uplLine, "
                    + "cr.changeType AS changeType, cr.fieldChanges AS fieldChanges, cr.status AS requestStatus, "
                    + "cr.currentLevelNo AS currentLevelNo, cr.totalLevels AS totalLevels, "
                    + "cr.requestedBy AS requestedBy, cr.requestedByName AS requestedByName, cr.requestedAt AS requestedAt, "
                    + "d.levelNo AS levelNo, d.decision AS decision, d.decidedBy AS decidedBy, "
                    + "d.decidedByName AS decidedByName, d.decidedAt AS decidedAt, d.comments AS comments ";
    private static final String UPL_AUDIT_TRAIL_FROM =
            "FROM tb_UPL_Change_Request cr "
                    + "LEFT JOIN tb_PurchaseOrderUPL upl ON upl.recordNo = cr.uplRecordNo "
                    + "LEFT JOIN tb_UPL_Change_Request_Decision d ON d.changeRequestId = cr.recordId ";

    @PostMapping(value = "/reports/getUplAuditTrail", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> getUplAuditTrail(@RequestBody String req) {
        try {
            JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
            String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
            String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
            int page = obj.has("page") ? obj.get("page").getAsInt() : 0;
            int size = obj.has("size") ? obj.get("size").getAsInt() : 100;
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            String whereClause = " WHERE 1=1";
            List<Object> params = new ArrayList<>();
            if (!columnName.isEmpty() && !searchQuery.isEmpty() && uplAuditTrailColumns.containsKey(columnName)) {
                whereClause += " AND " + uplAuditTrailColumns.get(columnName) + " LIKE ?";
                params.add("%" + searchQuery + "%");
            }

            String countSql = "SELECT COUNT(*) " + UPL_AUDIT_TRAIL_FROM + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            int offset = page * size;
            String sql = UPL_AUDIT_TRAIL_SELECT + UPL_AUDIT_TRAIL_FROM + whereClause
                    + " ORDER BY cr.recordId DESC, d.levelNo ASC LIMIT ? OFFSET ?";
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(size);
            sqlParams.add(offset);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, sqlParams.toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("responseCode", "0");
            response.put("auditTrail", result);
            response.put("totalElements", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("currentPage", page);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error fetching UPL audit trail", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error fetching UPL audit trail: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/uplAuditTrailFilter")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterUplAuditTrail(@RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "recordId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            String whereClause = " WHERE 1=1";
            List<Object> params = new ArrayList<>();
            if (filters != null) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    if (uplAuditTrailColumns.containsKey(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        whereClause += " AND " + uplAuditTrailColumns.get(entry.getKey()) + " = ?";
                        params.add(entry.getValue());
                    }
                }
            }

            String countSql = "SELECT COUNT(*) " + UPL_AUDIT_TRAIL_FROM + whereClause;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            String orderColumn = uplAuditTrailColumns.getOrDefault(sortBy, "cr.recordId");
            String orderBy = " ORDER BY " + orderColumn + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            int offset = page * size;
            String sql = UPL_AUDIT_TRAIL_SELECT + UPL_AUDIT_TRAIL_FROM + whereClause + orderBy + " LIMIT ? OFFSET ?";
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(size);
            sqlParams.add(offset);
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, sqlParams.toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("reports", result);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering UPL audit trail", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering UPL audit trail: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filterAcceptanceReports")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterAcceptanceReports(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "requestId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            // Validate page and size
            page = Math.max(page, 0);
            size = Math.max(size, 1);

            // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

            // Whitelist and map searchable columns to SQL with proper table aliases
            Map<String, String> searchableColumns = new HashMap<>();
            searchableColumns.put("requestId", "DCC.recordNo");
            searchableColumns.put("requestStatus", "DCC.status");
            searchableColumns.put("acceptanceType", "DCC.acceptanceType");
            searchableColumns.put("poNumber", "DCC.poNumber");
            searchableColumns.put("poLineNumber", "LN2.lineNumber");
            searchableColumns.put("poPartNumber", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END)");
            searchableColumns.put("poLineDescription", "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END)");
            searchableColumns.put("poItemSerializedStatus", "(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END)");
            searchableColumns.put("dccLnRecordNo", "LN2.recordNo");
            searchableColumns.put("siteId", "LN2.locationName");
            searchableColumns.put("siteTypeName", "siteType.siteTypeName");
            searchableColumns.put("inServiceDate", "DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y')");
            searchableColumns.put("region", "rg.regionName");
            searchableColumns.put("typeLookUpCode", "HD.typeLookUpCode");
            searchableColumns.put("releaseNumber", "HD.releaseNum");
            searchableColumns.put("dccProjectName", "HD.newProjectName");
            searchableColumns.put("newProjectName", "HD.newProjectName");
            searchableColumns.put("uplLineNumber", "LN2.uplLineNumber");
            searchableColumns.put("uplPartNumber", "upl.uplLineItemCode");
            searchableColumns.put("uplItemDescription", "upl.uplLineDescription");
            searchableColumns.put("actualPartNumber", "LN2.actualItemCode");
            searchableColumns.put("uplItemSerializedStatus", "upl.uplItemSerialized");
            searchableColumns.put("serialNumber", "LN2.serialNumber");
            searchableColumns.put("uplItemCategoryCode", "upl.zainItemCategoryCode");
            searchableColumns.put("uplItemCategoryCodeDescription", "upl.zainItemCategoryDescription");
            searchableColumns.put("unitPrice", "upl.poLineUnitPrice");
            searchableColumns.put("acceptanceUplQty", "LN2.deliveredQty");
            searchableColumns.put("acceptancePoQty", "LN2.poAcceptanceQty");
            searchableColumns.put("totalAcceptanceAmount", "(upl.uplLineUnitPrice * LN2.deliveredQty)");
            searchableColumns.put("vendorName", "HD.vendorName");
            searchableColumns.put("recordNo", "DCC.recordNo");
            searchableColumns.put("tagNumber", "LN2.tagNumber");
            searchableColumns.put("linkId", "LN2.linkId");
            searchableColumns.put("activeOrPassive", "upl.activeOrPassive");
            searchableColumns.put("createdDate", "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y')");
            searchableColumns.put("approvalDate", "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y')");
            searchableColumns.put("scopeOfWork", "LN2.scopeOfWork");
            // Define numeric columns for exact matching
            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "requestId", "poLineNumber", "uplLineNumber", "dccLnRecordNo",
                    "acceptanceUplQty", "acceptancePoQty", "unitPrice", "totalAcceptanceAmount",
                    "recordNo"
            ));

            // Initialize WHERE clause and parameters
            String whereClause = "";
            List<Object> params = new ArrayList<>();

            // PO Number filter
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                whereClause += " AND DCC.poNumber = ?";
                params.add(filters.get("poNumber"));
            }

            // Dynamic filtering for other columns
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String columnName = entry.getKey();
                String searchQuery = entry.getValue();
                if (!searchQuery.isEmpty() && searchableColumns.containsKey(columnName)) {
                    String sqlCol = searchableColumns.get(columnName);
                    if (numericColumns.contains(columnName)) {
                        // For numeric columns, use exact matching
                        try {
                            if (columnName.equals("totalAcceptanceAmount")) {
                                // Special handling for computed field; approximate by filtering components if needed, but for exact, use HAVING later
                                // For simplicity, skip direct filter or use subquery; here, assume numeric parse and add to HAVING
                                Double value = Double.parseDouble(searchQuery);
                                // We'll add to HAVING later
                            } else {
                                Number value = columnName.contains("Qty") || columnName.contains("Price") ? Double.parseDouble(searchQuery) : Integer.parseInt(searchQuery);
                                whereClause += " AND " + sqlCol + " = ?";
                                params.add(value);
                            }
                        } catch (NumberFormatException e) {
                            loggger.error("Invalid numeric format for " + columnName + ": " + searchQuery, e);
                        }
                    } else {
                        // For text columns, use LIKE with case-insensitive search
                        whereClause += " AND LOWER(" + sqlCol + ") LIKE LOWER(?)";
                        params.add("%" + searchQuery + "%");
                    }
                }
            }

            // Base SQL with proper joins and conditions
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
                    "  ELSE (HD.lineNumber = LN2.lineNumber AND HD.poNumber = DCC.poNumber) END))" +
                    whereClause;

            // Only group by tb_DCC and tb_DCC_LN unique keys
            String groupBy = " GROUP BY DCC.recordNo, LN2.recordNo ";

            // Count query with the same grouping to get accurate total
            String countSql = "SELECT COUNT(DISTINCT CONCAT(DCC.recordNo, '-', LN2.recordNo)) " + baseSql;
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // Pagination
            String paginationSql = " LIMIT ? OFFSET ?";
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(size);
            queryParams.add(page * size);

            // Build sorting (add to ORDER BY)
            String orderBy = "";
            if (!sortBy.isEmpty()) {
                // Map sortBy to sqlCol if computed
                String sqlSortCol = searchableColumns.getOrDefault(sortBy, sortBy);
                orderBy = " ORDER BY " + sqlSortCol + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            }

            // Main query selecting all the required fields
            String sql = "SELECT " +
                    "DCC.recordNo AS requestId, " +
                    "MAX(DCC.status) AS requestStatus, " +
                    "MAX(DCC.acceptanceType) AS acceptanceType, " +
                    "MAX(DCC.poNumber) AS poNumber, " +
                    "MAX(LN2.lineNumber) AS poLineNumber, " +
                    "MAX(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineItemCode ELSE HD.itemPartNumber END) AS poPartNumber, " +
                    "MAX(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END) AS poLineDescription, " +
                    "MAX(CASE WHEN HD.serialControl = 'NO CONTROL' THEN 'NO' ELSE 'YES' END) AS poItemSerializedStatus, " +
                    "'SAR' AS currency, " +
                    "MAX(upl.poLineUnitPrice) AS unitPrice, " +
                    "LN2.recordNo AS dccLnRecordNo, " +
                    "MAX(LN2.locationName) AS siteId, " +
                    "MAX(siteType.siteTypeName) AS siteTypeName, " +
                    "MAX(DATE_FORMAT(CAST(LN2.dateInService AS DATE),'%e-%b-%Y')) AS inServiceDate, " +
                    "MAX(rg.regionName) AS region, " +
                    "MAX(HD.typeLookUpCode) AS typeLookUpCode, " +
                    "MAX(HD.releaseNum) AS releaseNumber, " +
                    "MAX(HD.newProjectName) AS dccProjectName, " +
                    "MAX(HD.newProjectName) AS newProjectName, " +
                    "MAX(LN2.uplLineNumber) AS uplLineNumber, " +
                    "MAX(upl.uplLineItemCode) AS uplPartNumber, " +
                    "MAX(upl.uplLineDescription) AS uplItemDescription, " +
                    "MAX(LN2.actualItemCode) AS actualPartNumber, " +
                    "MAX(upl.uplItemSerialized) AS uplItemSerializedStatus, " +
                    "MAX(LN2.serialNumber) AS serialNumber, " +
                    "MAX(upl.zainItemCategoryCode) AS uplItemCategoryCode, " +
                    "MAX(upl.zainItemCategoryDescription) AS uplItemCategoryCodeDescription, " +
                    "MAX(upl.uplLineUnitPrice) AS uplLineUnitPrice, " +
                    "MAX(LN2.deliveredQty) AS acceptanceUplQty, " +
                    "MAX(LN2.poAcceptanceQty) AS acceptancePoQty, " +
                    "MAX(upl.uplLineUnitPrice * LN2.deliveredQty) AS totalAcceptanceAmount, " +
                    "MAX(HD.vendorName) AS vendorName, " +
                    "LN2.tagNumber AS tagNumber, " +
                    "LN2.linkId AS linkId, " +
                    "upl.activeOrPassive AS activeOrPassive, " +
                    "DATE_FORMAT(CAST(DCC.createdDate AS DATE),'%e-%b-%Y') AS createdDate, " +
                    "DATE_FORMAT(CAST(DCC.approvedDate AS DATE),'%e-%b-%Y') AS approvalDate, " +
                    "LN2.scopeOfWork AS scopeOfWork " +
                    baseSql + groupBy + orderBy + paginationSql;

            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, queryParams.toArray());

            // Add incremental record number
            AtomicInteger counter = new AtomicInteger(1);
            result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", result);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("Acceptance Report Filter Query: " + sql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering acceptance reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering acceptance reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filterCapitalizationReports")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> filterCapitalizationReports(
            @RequestBody Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "requestId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            // ONLY_FULL_GROUP_BY is disabled via datasource connection-init-sql in application.properties

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

            // Define numeric columns for exact matching
            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "requestId", "poLineNumber", "uplLineNumber", "recordNo"
            ));

            // Initialize WHERE clause and parameters
            String whereClause = "";
            List<Object> params = new ArrayList<>();

            // PO Number filter
            if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
                whereClause += " AND DCC.poNumber = ?";
                params.add(filters.get("poNumber"));
            }

            // Dynamic filtering for other columns
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String columnName = entry.getKey();
                String searchQuery = entry.getValue();
                if (!searchQuery.isEmpty() && searchableColumns.containsKey(columnName) && !columnName.equals("poNumber")) {
                    String sqlCol = searchableColumns.get(columnName);
                    if (numericColumns.contains(columnName)) {
                        // For numeric columns, use exact matching
                        try {
                            Number value = columnName.equals("quantity") || columnName.equals("faBookingAmount") ? Double.parseDouble(searchQuery) : Integer.parseInt(searchQuery);
                            whereClause += " AND " + sqlCol + " = ?";
                            params.add(value);
                        } catch (NumberFormatException e) {
                            loggger.error("Invalid numeric format for " + columnName + ": " + searchQuery, e);
                        }
                    } else {
                        // For text columns, use LIKE with case-insensitive search
                        whereClause += " AND LOWER(" + sqlCol + ") LIKE LOWER(?)";
                        params.add("%" + searchQuery + "%");
                    }
                }
            }

            // Date range filtering for receiveddate (rec.approvedDate)
            String receivedDateStart = filters.containsKey("receivedDateStart") ? convertToSqlDate(filters.get("receivedDateStart")) : "";
            String receivedDateEnd = filters.containsKey("receivedDateEnd") ? convertToSqlDate(filters.get("receivedDateEnd")) : "";
            if (!receivedDateStart.isEmpty() && !receivedDateEnd.isEmpty()) {
                whereClause += " AND DATE(rec.approvedDate) BETWEEN ? AND ?";
                params.add(receivedDateStart);
                params.add(receivedDateEnd);
            } else if (!receivedDateStart.isEmpty()) {
                whereClause += " AND DATE(rec.approvedDate) >= ?";
                params.add(receivedDateStart);
            } else if (!receivedDateEnd.isEmpty()) {
                whereClause += " AND DATE(rec.approvedDate) <= ?";
                params.add(receivedDateEnd);
            }

            // Date range filtering for isd (LN2.dateInService)
            String isdStart = filters.containsKey("isdStart") ? convertToSqlDate(filters.get("isdStart")) : "";
            String isdEnd = filters.containsKey("isdEnd") ? convertToSqlDate(filters.get("isdEnd")) : "";
            if (!isdStart.isEmpty() && !isdEnd.isEmpty()) {
                whereClause += " AND DATE(LN2.dateInService) BETWEEN ? AND ?";
                params.add(isdStart);
                params.add(isdEnd);
            } else if (!isdStart.isEmpty()) {
                whereClause += " AND DATE(LN2.dateInService) >= ?";
                params.add(isdStart);
            } else if (!isdEnd.isEmpty()) {
                whereClause += " AND DATE(LN2.dateInService) <= ?";
                params.add(isdEnd);
            }

            // Join tb_AcceptanceRequest_Receipt as rec, get latest approvedDate per DCC.recordNo
            String baseSql = " FROM tb_DCC DCC " +
                    "JOIN tb_PurchaseOrder HD ON DCC.poNumber = HD.poNumber " +
                    // Get the AR with status=approved and received=1 for this DCC
                    "JOIN ( " +
                    "    SELECT t.acceptanceRequestRecordNo, MAX(t.recordNo) AS recordNo " +
                    "    FROM tb_Category_Approval_Requests t " +
                    "    WHERE t.status = 'approved' AND t.received = 1 " +
                    "    GROUP BY t.acceptanceRequestRecordNo " +
                    ") AR_latest ON DCC.recordNo = AR_latest.acceptanceRequestRecordNo " +
                    // Now join the AR row
                    "JOIN tb_Category_Approval_Requests AR ON AR.recordNo = AR_latest.recordNo " +
                    // Join to Receipt for received date
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
            String countSql = "SELECT COUNT(*) FROM (SELECT 1 " + baseSql + groupBy + ") t";
            int totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            // Pagination
            String paginationSql = " LIMIT ? OFFSET ?";
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(size);
            queryParams.add(page * size);

            // Build sorting
            String orderBy = "";
            if (!sortBy.isEmpty()) {
                String sqlSortCol = searchableColumns.getOrDefault(sortBy, sortBy);
                orderBy = " ORDER BY " + sqlSortCol + (sortDir.equalsIgnoreCase("asc") ? " ASC" : " DESC");
            }

            // Main query
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
                    "(CASE WHEN HD.newProjectName IS NULL OR LENGTH(TRIM(HD.newProjectName)) = 0 THEN HD.projectName ELSE HD.newProjectName END) AS projectName, " +
                    "(CASE WHEN LENGTH(LN2.uplLineNumber) > 0 THEN upl.poLineDescription ELSE HD.poLineDescription END) AS description, " +
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
                    "DATE_FORMAT(rec.approvedDate, '%d-%m-%Y') AS receiveddate " +
                    baseSql + groupBy + orderBy + paginationSql;

            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, queryParams.toArray());

            // Add incremental record number
            AtomicInteger counter = new AtomicInteger(1);
            result.forEach(row -> row.put("recordNo", counter.getAndIncrement()));

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("reports", result);
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", result.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("Capitalization Report Filter Query: " + sql);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            loggger.error("Error filtering capitalization reports", e);
            return new ResponseEntity<>(Collections.singletonMap("message", "Error filtering capitalization reports: " + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
