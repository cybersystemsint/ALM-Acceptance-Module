package com.zain.almksazain.controller;
import com.zain.almksazain.repo.DccCombinedViewrepo;
import com.zain.almksazain.repo.poviewrepo;
import com.zain.almksazain.repo.dccpoviewrepo;
import com.zain.almksazain.repo.uplrepo;
import com.zain.almksazain.dto.PurchaseOrderRequest;
import com.zain.almksazain.dto.PurchaseOrderResponse;
import com.zain.almksazain.model.upldata;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.zain.almzainksa.helper.helper;
import com.zain.almksazain.repo.tbChargeAccountRepo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.text.SimpleDateFormat;
import java.util.Collections;
import com.zain.almksazain.model.DccPoCombinedView;
import com.zain.almksazain.services.DccPoCombinedService;
import com.zain.almksazain.services.CombinedPurchaseOrderService;
import com.zain.almksazain.services.PurchaseOrderService;


@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class ReportsController {
   
    private final Logger loggger = LogManager.getLogger(ReportsController.class);
    
    private final JdbcTemplate jdbcTemplate;
    @Autowired
    uplrepo uprepo;

    @Autowired
    poviewrepo povwrepo;
  
    @Autowired
    DccCombinedViewrepo dccpocombinedviewrp;

    @Autowired
    DccPoCombinedService dccPoCombinedService;

    @Autowired
    dccpoviewrepo dccpoviewrp;

    @Autowired
   CombinedPurchaseOrderService combinedPurchaseOrderService;

    @Autowired
    PurchaseOrderService purchaseOrderService;

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
        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

    ///GET ALL CREATED CHARGE ACCOUNTS
    @PostMapping(value = "/reports/getAllItemCodeSubstitutes", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public Map<String, Object> getAllItemCodeSubstitutes(@RequestBody String req) {
        JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
        Integer recordNo = obj.get("recordNo").getAsInt();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";

        List<Object> params = new ArrayList<>();
        String whereClause = " WHERE 1=1";

        if (recordNo != 0) {
            whereClause += " AND recordNo = ?";
            params.add(recordNo);
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND " + columnName + " LIKE ?";
            params.add("%" + searchQuery + "%");
        }

        String countScript = "SELECT COUNT(*) FROM tb_ItemCodeSubstitute" + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countScript, Integer.class, params.toArray());

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

        List<Map<String, Object>> result = jdbcTemplate.queryForList(itemCodes, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", (int) Math.ceil((double) totalRecords / size));

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
        String dateFrom = obj.has("dateFrom") ? obj.get("dateFrom").getAsString() : "";
        String dateTo = obj.has("dateTo") ? obj.get("dateTo").getAsString() : "";

        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        // Validate page and size
        page = Math.max(page, 1);
        size = Math.max(size, 1);

        String conditionSql = "";
        String whereClause = " WHERE 1=1 ";
        List<Object> params2 = new ArrayList<>();

        if (!supplierId.equalsIgnoreCase("0") && !poID.equalsIgnoreCase("0")) {
            whereClause += " AND PO.vendorNumber = ? AND PO.poNumber = ?";
            params2.add(supplierId);
            params2.add(poID);
        } else if (!supplierId.equalsIgnoreCase("0") && poID.equalsIgnoreCase("0")) {
            whereClause += " AND PO.vendorNumber = ?";
            params2.add(supplierId);
        } else if (supplierId.equalsIgnoreCase("0") && !poID.equalsIgnoreCase("0")) {
            whereClause += " AND PO.poNumber = ?";
            params2.add(poID);
        }
        // Filtering by columnName and searchQuery
        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND PO." + columnName + " LIKE ?";
            params2.add("%" + searchQuery + "%");
        }

        // Step 1: Fetch unique POs
        String uniquePOsSql = "SELECT DISTINCT PO.poNumber FROM tb_PurchaseOrder PO " + whereClause;
        //List<String> uniquePONumbers = jdbcTemplate.queryForList(uniquePOsSql, String.class);
        List<String> uniquePONumbers = jdbcTemplate.queryForList(uniquePOsSql, params2.toArray(), String.class);

        loggger.info("GET NESTED SQL 1  " + uniquePOsSql);
        // If page is 1 and size is 20000, return all unique POs
        if (page == 1 && size == 20000) {
            // No pagination needed, just fetch all unique POs
            // Fetch line items for all unique POs
            String lineItemsSql = "SELECT * FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" + String.join(",", uniquePONumbers.stream().map(po -> "'" + po + "'").collect(Collectors.toList())) + ")";
            Map<String, Object> params = new HashMap<>();
            //   params.put("poNumbers", uniquePONumbers);
            List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

            // Step 3: Group line items by PO number
            Map<String, Map<String, Object>> groupedResults = new LinkedHashMap<>();
            for (Map<String, Object> lineItem : lineItems) {
                String Linecancel = "";
                String subAllow = "";
                String poNumber = (String) lineItem.get("poNumber");

                if (!groupedResults.containsKey(poNumber)) {
                    Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                    // Remove unnecessary fields
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

                    Linecancel = lineItem.get("lineCancelFlag").toString();
                    subAllow = lineItem.get("prSubAllow").toString();

                    if (Linecancel.equalsIgnoreCase("false")) {
                        groupedRow.put("lineCancelFlag", "N");
                    } else {
                        groupedRow.put("lineCancelFlag", "Y");
                    }
                    if (subAllow.equalsIgnoreCase("false")) {
                        groupedRow.put("prSubAllow", "N");
                    } else {
                        groupedRow.put("prSubAllow", "Y");
                    }

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
                    // Add POlineItems key with an empty list
                    groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                    groupedResults.put(poNumber, groupedRow);
                }

                Map<String, Object> poLineItem = new LinkedHashMap<>();
                poLineItem.put("recordNo", lineItem.get("recordNo"));
                poLineItem.put("poNumber", lineItem.get("poNumber"));
                poLineItem.put("lineNumber", lineItem.get("lineNumber"));
                poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
                poLineItem.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
                poLineItem.put("poOrderQuantity", (lineItem.get("poOrderQuantity")));
                poLineItem.put("poQtyNew", (lineItem.get("poQtyNew")));

                poLineItem.put("quantityReceived", (lineItem.get("quantityReceived")));
                poLineItem.put("quantityDueOld", (lineItem.get("quantityDueOld")));
                poLineItem.put("quantityDueNew", (lineItem.get("quantityDueNew")));
                poLineItem.put("quantityBilled", (lineItem.get("quantityBilled")));
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

                // Add the line item to the POlineItems list
                ((List<Map<String, Object>>) groupedResults.get(poNumber).get("POlineItems")).add(poLineItem);

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

                Map<String, Object> groupedRow = groupedResults.get(poNumber);
                groupedRow.put("totalPoQtyNew", ((Double) groupedRow.get("totalPoQtyNew") + poQtyNew));
                groupedRow.put("totalQuantityReceived", ((Double) groupedRow.get("totalQuantityReceived") + quantityReceived));
                groupedRow.put("totalQuantityDueOld", ((Double) groupedRow.get("totalQuantityDueOld") + quantityDueOld));
                groupedRow.put("totalQuantityDueNew", ((Double) groupedRow.get("totalQuantityDueNew") + quantityDueNew));
                groupedRow.put("totalQuantityBilled", ((Double) groupedRow.get("totalQuantityBilled") + quantityBilled));
                groupedRow.put("totalpoOrderQuantity", ((Double) groupedRow.get("totalpoOrderQuantity") + poOrderQuantity));
                groupedRow.put("totalunitPriceInPoCurrency", (Double) groupedRow.get("totalunitPriceInPoCurrency") + unitPriceInPoCurrency);
                groupedRow.put("totalunitPriceInSAR", (Double) groupedRow.get("totalunitPriceInSAR") + unitPriceInSAR);
                groupedRow.put("totallinePriceInPoCurrency", (Double) groupedRow.get("totallinePriceInPoCurrency") + linePriceInPoCurrency);
                groupedRow.put("totallinePriceInSAR", (Double) groupedRow.get("totallinePriceInSAR") + linePriceInSAR);
                groupedRow.put("totalamountReceived", (Double) groupedRow.get("totalamountReceived") + amountReceived);
                groupedRow.put("totalamountDue", (Double) groupedRow.get("totalamountDue") + amountDue);
                groupedRow.put("totalamountDueNew", (Double) groupedRow.get("totalamountDueNew") + amountDueNew);
                groupedRow.put("totalamountBilled", (Double) groupedRow.get("totalamountBilled") + amountBilled);
                groupedRow.put("totalDescopedLinePriceInPoCurrency", (Double) groupedRow.get("totalDescopedLinePriceInPoCurrency") + descopedLinePriceInPoCurrency);
                groupedRow.put("totalNewLinePriceInPoCurrency", (Double) groupedRow.get("totalNewLinePriceInPoCurrency") + newLinePriceInPoCurrency);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", uniquePONumbers.size());
            response.put("totalRecords", uniquePONumbers.size());
            response.put("totalPages", 1); // Since we are returning all records
            response.put("data", new ArrayList<>(groupedResults.values()));

            return response;
        }

        // Step 1: Fetch unique POs with pagination
        String uniquePOsSql2 = "SELECT DISTINCT PO.poNumber FROM tb_PurchaseOrder PO " + whereClause + " LIMIT " + size + " OFFSET " + (page - 1) * size;
        List<String> uniquePONumbers2 = jdbcTemplate.queryForList(uniquePOsSql2, params2.toArray(), String.class);

        //List<String> uniquePONumbers2 = jdbcTemplate.queryForList(uniquePOsSql2, String.class);
        loggger.info("GET NESTED SQL 2  " + uniquePOsSql2);

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

        String lineItemsSql = "SELECT * FROM tb_PurchaseOrder PO WHERE PO.poNumber IN (" + String.join(",", uniquePONumbers2.stream().map(po -> "'" + po + "'").collect(Collectors.toList())) + ")";
        List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

        Map<String, Map<String, Object>> paginatedGroupedResults = new LinkedHashMap<>();
        for (Map<String, Object> lineItem : lineItems) {
            String poNumber = (String) lineItem.get("poNumber");
            String Linecancel = "";
            String subAllow = "";

            if (!paginatedGroupedResults.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                // Remove unnecessary fields
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

                Linecancel = lineItem.get("lineCancelFlag").toString();
                subAllow = lineItem.get("prSubAllow").toString();

                if (Linecancel.equalsIgnoreCase("false")) {
                    groupedRow.put("lineCancelFlag", "N");
                } else {
                    groupedRow.put("lineCancelFlag", "Y");
                }
                if (subAllow.equalsIgnoreCase("false")) {
                    groupedRow.put("prSubAllow", "N");
                } else {
                    groupedRow.put("prSubAllow", "Y");
                }

                //  lineCancelFlag
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
                // Add POlineItems key with an empty list
                groupedRow.put("POlineItems", new ArrayList<Map<String, Object>>());
                paginatedGroupedResults.put(poNumber, groupedRow);
            }

            Map<String, Object> poLineItem = new LinkedHashMap<>();
            poLineItem.put("recordNo", lineItem.get("recordNo"));
            poLineItem.put("poNumber", lineItem.get("poNumber"));
            poLineItem.put("lineNumber", lineItem.get("lineNumber"));
            poLineItem.put("itemPartNumber", lineItem.get("itemPartNumber"));
            poLineItem.put("countryOfOrigin", lineItem.get("countryOfOrigin"));
            poLineItem.put("poOrderQuantity", (lineItem.get("poOrderQuantity")));
            poLineItem.put("poQtyNew", (lineItem.get("poQtyNew")));
            poLineItem.put("quantityReceived", (lineItem.get("quantityReceived")));
            poLineItem.put("quantityDueOld", (lineItem.get("quantityDueOld")));
            poLineItem.put("quantityDueNew", (lineItem.get("quantityDueNew")));
            poLineItem.put("quantityBilled", (lineItem.get("quantityBilled")));
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

            // Add the line item to the POlineItems list
            ((List<Map<String, Object>>) paginatedGroupedResults.get(poNumber).get("POlineItems")).add(poLineItem);

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

            Map<String, Object> groupedRow = paginatedGroupedResults.get(poNumber);
            groupedRow.put("totalPoQtyNew", ((Double) groupedRow.get("totalPoQtyNew") + poQtyNew));
            groupedRow.put("totalQuantityReceived", ((Double) groupedRow.get("totalQuantityReceived") + quantityReceived));
            groupedRow.put("totalQuantityDueOld", ((Double) groupedRow.get("totalQuantityDueOld") + quantityDueOld));
            groupedRow.put("totalQuantityDueNew", ((Double) groupedRow.get("totalQuantityDueNew") + quantityDueNew));
            groupedRow.put("totalQuantityBilled", ((Double) groupedRow.get("totalQuantityBilled") + quantityBilled));
            groupedRow.put("totalpoOrderQuantity", ((Double) groupedRow.get("totalpoOrderQuantity") + poOrderQuantity));
            groupedRow.put("totalunitPriceInPoCurrency", (Double) groupedRow.get("totalunitPriceInPoCurrency") + unitPriceInPoCurrency);
            groupedRow.put("totalunitPriceInSAR", (Double) groupedRow.get("totalunitPriceInSAR") + unitPriceInSAR);
            groupedRow.put("totallinePriceInPoCurrency", (Double) groupedRow.get("totallinePriceInPoCurrency") + linePriceInPoCurrency);
            groupedRow.put("totallinePriceInSAR", (Double) groupedRow.get("totallinePriceInSAR") + linePriceInSAR);
            groupedRow.put("totalamountReceived", (Double) groupedRow.get("totalamountReceived") + amountReceived);
            groupedRow.put("totalamountDue", (Double) groupedRow.get("totalamountDue") + amountDue);
            groupedRow.put("totalamountDueNew", (Double) groupedRow.get("totalamountDueNew") + amountDueNew);
            groupedRow.put("totalamountBilled", (Double) groupedRow.get("totalamountBilled") + amountBilled);
            groupedRow.put("totalDescopedLinePriceInPoCurrency", (Double) groupedRow.get("totalDescopedLinePriceInPoCurrency") + descopedLinePriceInPoCurrency);
            groupedRow.put("totalNewLinePriceInPoCurrency", (Double) groupedRow.get("totalNewLinePriceInPoCurrency") + newLinePriceInPoCurrency);
        }

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

                    // Add renamed fields
                    groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                    // groupedRow.put("projectName", lineItem.get("dccProjectName"));
                    String dccProjectName = (String) lineItem.get("dccProjectName");
                    String newProjectName = (String) lineItem.get("newProjectName");
                    String finalProjectName = (newProjectName != null && !newProjectName.trim().isEmpty()) ? newProjectName : dccProjectName;
                    groupedRow.put("projectName", finalProjectName);
                    groupedRow.put("newProjectName", newProjectName);

                    groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                    groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                    groupedRow.put("vendorNumber", lineItem.get("supplierId"));

                    // Add required fields that were previously removed
                    groupedRow.put("dccCreatedDate", lineItem.get("dccCreatedDate"));
                    groupedRow.put("dateApproved", lineItem.get("dateApproved"));
                    groupedRow.put("UPLACPTRequestValue", lineItem.get("UPLACPTRequestValue"));
                    groupedRow.put("lnLocationName", lineItem.get("lnLocationName"));
                    groupedRow.put("lnScopeOfWork", lineItem.get("lnScopeOfWork"));
                    groupedRow.put("lnInserviceDate", lineItem.get("lnInserviceDate"));

                    groupedRow.put("departmentName", lineItem.get("departmentName"));

                    // Extract days from aging strings and add calculated fields
                    String userAging = (String) lineItem.get("userAging");
                    String totalAging = (String) lineItem.get("totalAging");

                    groupedRow.put("userAging", userAging); // Keep original
                    groupedRow.put("totalAging", totalAging); // Keep original
                    groupedRow.put("userAgingInDays", extractDaysFromAging(userAging));
                    groupedRow.put("totalAgingInDays", extractDaysFromAging(totalAging));

                    // Calculate Request Amount (SAR) = lnUnitPrice * UPLACPTRequestValue
                    Double unitPriceInSAR = (Double) lineItem.get("unitPriceInSAR");
                    Double uplRequestValue = (Double) lineItem.get("UPLACPTRequestValue");
                    Double requestAmountSAR = calculateRequestAmount(unitPriceInSAR, uplRequestValue);
                    groupedRow.put("Request Amount (SAR)", requestAmountSAR);

                    // Remove line-item specific fields (keep the ones we need)
                    groupedRow.remove("lnRecordNo");
                    groupedRow.remove("lnProductName");
                    groupedRow.remove("lnProductSerialNo");
                    groupedRow.remove("lnDeliveredQty");
                    // Don't remove lnLocationName - we need it
                    // Don't remove lnInserviceDate - we need it
                    groupedRow.remove("lnUnitPrice");
                    // Don't remove lnScopeOfWork - we need it
                    groupedRow.remove("lnRemarks");
                    groupedRow.remove("lnItemCode");
                    groupedRow.remove("linkId");
                    groupedRow.remove("tagNumber");
                    groupedRow.remove("dccCurrency");
                    groupedRow.remove("lineNumber");
                    groupedRow.remove("actualItemCode");
                    groupedRow.remove("uplLineNumber");
                    // Don't remove UPLACPTRequestValue - we need it
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

        String lineItemsSql = "SELECT * FROM dccPOCombinedView PO WHERE PO.dccRecordNo IN ("
                + String.join(",", uniquePONumbers2.stream()
                .map(po -> po.toString()) // no quotes, integer literals
                .collect(Collectors.toList()))
                + ")";

        List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineItemsSql);

        loggger.info("GET NESTED SQL 2  " + lineItemsSql);

        Map<String, Map<String, Object>> paginatedGroupedResults = new LinkedHashMap<>();
        lineItems.forEach(lineItem -> {
            Object poNumberObj = lineItem.get("dccRecordNo");
            String poNumber = String.valueOf(poNumberObj);
            if (!paginatedGroupedResults.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);

                // Add renamed fields
                groupedRow.put("recordNo", lineItem.get("dccRecordNo"));
                // groupedRow.put("projectName", lineItem.get("dccProjectName"));
                String dccProjectName = (String) lineItem.get("dccProjectName");
                String newProjectName = (String) lineItem.get("newProjectName");
                String finalProjectName = (newProjectName != null && !newProjectName.trim().isEmpty()) ? newProjectName : dccProjectName;
                groupedRow.put("projectName", finalProjectName);
                groupedRow.put("newProjectName", newProjectName);
                groupedRow.put("vendorName", lineItem.get("dccVendorName"));
                groupedRow.put("vendorEmail", lineItem.get("dccVendorEmail"));
                groupedRow.put("vendorNumber", lineItem.get("supplierId"));

                // Add required fields that were previously removed
                groupedRow.put("dccCreatedDate", lineItem.get("dccCreatedDate"));
                groupedRow.put("dateApproved", lineItem.get("dateApproved"));
                groupedRow.put("UPLACPTRequestValue", lineItem.get("UPLACPTRequestValue"));
                groupedRow.put("lnLocationName", lineItem.get("lnLocationName"));
                groupedRow.put("lnScopeOfWork", lineItem.get("lnScopeOfWork"));
                groupedRow.put("lnInserviceDate", lineItem.get("lnInserviceDate"));

                groupedRow.put("departmentName", lineItem.get("departmentName"));
                // Extract days from aging strings and add calculated fields
                String userAging = (String) lineItem.get("userAging");
                String totalAging = (String) lineItem.get("totalAging");

                groupedRow.put("userAging", userAging); // Keep original
                groupedRow.put("totalAging", totalAging); // Keep original
                groupedRow.put("userAgingInDays", extractDaysFromAging(userAging));
                groupedRow.put("totalAgingInDays", extractDaysFromAging(totalAging));

                // Calculate Request Amount (SAR) = lnUnitPrice * UPLACPTRequestValue
                Double unitPriceInSAR = (Double) lineItem.get("unitPriceInSAR");
                Double uplRequestValue = (Double) lineItem.get("UPLACPTRequestValue");
                Double requestAmountSAR = calculateRequestAmount(unitPriceInSAR, uplRequestValue);
                groupedRow.put("Request Amount (SAR)", requestAmountSAR);
                // Remove unnecessary fields (keep the ones we need)
                groupedRow.remove("lnRecordNo");
                groupedRow.remove("lnProductName");
                groupedRow.remove("lnProductSerialNo");
                groupedRow.remove("lnDeliveredQty");
                // Don't remove lnLocationName - we need it
                // Don't remove lnInserviceDate - we need it
                groupedRow.remove("dccCurrency");
                groupedRow.remove("lnUnitPrice");
                // Don't remove lnScopeOfWork - we need it
                groupedRow.remove("lnRemarks");
                groupedRow.remove("lnItemCode");
                groupedRow.remove("linkId");
                groupedRow.remove("tagNumber");
                groupedRow.remove("lineNumber");
                groupedRow.remove("actualItemCode");
                groupedRow.remove("uplLineNumber");
                // Don't remove UPLACPTRequestValue - we need it
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


      // Helper method to extract days from aging string
    private int extractDaysFromAging(String agingString) {
        if (agingString == null || agingString.trim().isEmpty()) {
            return 0;
        }

        try {
            // Extract number before "days" - handles formats like "0 days 9 hrs 22 mins" or "1 days 4 hrs 36 mins"
            String[] parts = agingString.trim().split("\\s+");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0]);
            }
        } catch (NumberFormatException e) {
            // If parsing fails, return 0
            return 0;
        }
        return 0;
    }

    // Helper method to calculate Request Amount (SAR)
    private Double calculateRequestAmount(Double unitPriceInSAR, Double uplRequestValue) {
        double unitPrice = (unitPriceInSAR != null) ? unitPriceInSAR : 0.0;
        double requestValue = (uplRequestValue != null) ? uplRequestValue : 0.0;
        return unitPrice * requestValue;
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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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

        String countSql = "SELECT COUNT(*) FROM combinedPurchaseOrderView" + whereClause;
        int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class,
                params.toArray());

        //  int totalRecords = jdbcTemplate.queryForObject(countSql, Integer.class);
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

        String sql = "SELECT * FROM combinedPurchaseOrderView" + whereClause + paginationSql;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, params.toArray());

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

        page = Math.max(page, 0);
        size = Math.max(size, 0);

        String paginationSql = "";
        String whereClause = " WHERE 1=1 ";

        if (!poNumber.equalsIgnoreCase("0")) {
            whereClause += " AND UPL.poNumber='" + poNumber + "'";
        }

        if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
            whereClause += " AND UPL." + columnName + " LIKE '%" + searchQuery + "%'";
        }

        String countSql = "SELECT COUNT(*) FROM tb_PurchaseOrderUPL UPL" + whereClause;
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

        String sql = "SELECT UPL.recordNo, UPL.recordDatetime, UPL.vendor, UPL.manufacturer, UPL.countryOfOrigin, UPL.projectName, "
                + "UPL.poType, UPL.releaseNumber, UPL.poNumber, UPL.poLineNumber, UPL.uplLine, UPL.poLineItemType, UPL.poLineItemCode, "
                + "UPL.poLineDescription, UPL.uplLineItemType, UPL.uplLineItemCode, UPL.uplLineDescription, UPL.zainItemCategoryCode, "
                + "UPL.zainItemCategoryDescription, UPL.uplItemSerialized, UPL.activeOrPassive, UPL.uom, UPL.currency, "
                + "UPL.poLineQuantity, UPL.poLineUnitPrice, UPL.uplLineQuantity, UPL.uplLineUnitPrice, UPL.substituteItemCode, "
                + "UPL.remarks,"
                + "UPL.createdByName, UPL.uplModifiedBy AS updatedByName, UPL.uplModifiedDate AS updatedDatetime "
                + "FROM tb_PurchaseOrderUPL UPL " + whereClause + paginationSql;

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);

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

        jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");

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



@PostMapping(value = "/reports/v2/poUplPerSupplierAndPoNumber", produces = "application/json")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public Map<String, Object> poUplPerSupplierAndPoNumbers(@RequestBody String req) {
    loggger.info("Received request for /reports/poUplPerSupplierAndPoNumber: {}", req);
    JsonObject obj = new JsonParser().parse(req).getAsJsonObject();
    String supplierId = obj.get("supplierId").getAsString();
    String poID = obj.get("poId").getAsString();
    String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
    String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
    String dateFrom = obj.has("dateFrom") ? obj.get("dateFrom").getAsString() : "";
    String dateTo = obj.has("dateTo") ? obj.get("dateTo").getAsString() : "";

    int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
    int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

    loggger.info("Parsed request params: supplierId={}, poID={}, columnName={}, searchQuery={}, dateFrom={}, dateTo={}, page={}, size={}",
            supplierId, poID, columnName, searchQuery, dateFrom, dateTo, page, size);

    Map<String, Object> response = combinedPurchaseOrderService.poUplPerSupplierAndPoNumberReport(
            supplierId, poID, columnName, searchQuery, dateFrom, dateTo, page, size
    );

    loggger.info("Returning response for /reports/poUplPerSupplierAndPoNumber. Data size: {}, totalRecords: {}",
            ((List<?>) response.getOrDefault("data", Collections.emptyList())).size(),
            response.getOrDefault("totalRecords", "N/A"));

    return response;
}



    @PostMapping(value = "/reports/v2/agingReport", produces = "application/json")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
        public Map<String, Object> getAgingReports(@RequestBody String req) {
        loggger.info("Received request for /api/reports/agingReport: {}", req);
        JsonObject obj = JsonParser.parseString(req).getAsJsonObject();
        String supplierId = obj.get("supplierId").getAsString();
        String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
        String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
        int page = obj.has("page") ? obj.get("page").getAsInt() : 1;
        int size = obj.has("size") ? obj.get("size").getAsInt() : 20000;

        loggger.debug("Parsed params - supplierId: {}, columnName: {}, searchQuery: {}, page: {}, size: {}", 
            supplierId, columnName, searchQuery, page, size);

        Map<String, Object> response = dccPoCombinedService.getAgingReport(supplierId, columnName, searchQuery, page, size);

        loggger.info("Returning aging report response with {} records (page {}/{})", 
            response.get("data") != null ? ((Iterable<?>) response.get("data")).spliterator().getExactSizeIfKnown() : 0,
            response.get("currentPage"), response.get("totalPages"));

        return response;
    }

    // @PostMapping(value = "/reports/v2/getNestedPurchaseOrders", produces = "application/json")
    // public ResponseEntity<PurchaseOrderResponse> getPurchaseOrdersSummary(@RequestBody PurchaseOrderRequest request) {
    //     loggger.info("Received POST /reports/v2/getNestedPurchaseOrders with request: {}", request);

    //     try {
    //         PurchaseOrderResponse response = purchaseOrderService.getPurchaseOrderResponse(request);

    //         if (response.getTotalRecords() == null || response.getTotalRecords() == 0) {
    //             loggger.warn("No purchase orders found for request: {}", request);
    //             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    //         }

    //         loggger.info("Returning purchase order response with {} records.", response.getTotalRecords());
    //         return ResponseEntity.ok(response);

    //     } catch (Exception e) {
    //         loggger.error("Error processing purchase orders request", e);
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    //     }
    // }

    

}
