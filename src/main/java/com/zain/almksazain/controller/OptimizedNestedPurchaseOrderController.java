package com.zain.almksazain.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/reports")
@CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
public class OptimizedNestedPurchaseOrderController {

    private static final Logger loggger = LoggerFactory.getLogger(OptimizedNestedPurchaseOrderController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * SINGLE OPTIMIZED ENDPOINT
     *
     */
    @PostMapping(value = "/v2/getNestedPurchaseOrders", produces = "application/json")
    public Map<String, Object> getNestedPurchaseOrders(@RequestBody String req) {
        try {
            JsonObject obj = new JsonParser().parse(req).getAsJsonObject();

            String supplierId = obj.get("supplierId").getAsString();
            String poID     = obj.get("poNumber").getAsString();
            String columnName = obj.has("columnName") ? obj.get("columnName").getAsString() : "";
            String searchQuery = obj.has("searchQuery") ? obj.get("searchQuery").getAsString() : "";
            int page = Math.max(obj.has("page") ? obj.get("page").getAsInt() : 1, 1);
            int size = Math.max(obj.has("size") ? obj.get("size").getAsInt() : 20000, 1);

            List<Object> params = new ArrayList<>();
            String whereClause = " WHERE 1=1";

            if (!supplierId.equalsIgnoreCase("0")) {
                whereClause += " AND PO.vendorNumber = ?";
                params.add(supplierId);
            }
            if (!poID.equalsIgnoreCase("0")) {
                whereClause += " AND PO.poNumber = ?";
                params.add(poID);
            }
            if (!columnName.isEmpty() && !searchQuery.isEmpty()) {
                whereClause += " AND PO." + columnName + " LIKE ?";
                params.add("%" + searchQuery + "%");
            }

            // ===================================================================
            // 1. PO NUMBER PROVIDED → Return only its LINE ITEMS (fast child fetch)
            // ===================================================================
            if (!poID.equalsIgnoreCase("0")) {
                String lineSql = "SELECT PO.* FROM tb_PurchaseOrder PO" + whereClause +
                        " ORDER BY PO.lineNumber";

                List<Map<String, Object>> lineItems = jdbcTemplate.queryForList(lineSql, params.toArray());

                Map<String, Map<String, Object>> grouped = groupLineItemsByPO(lineItems);

                Map<String, Object> response = new HashMap<>();
                response.put("currentPage", 1);
//                response.put("pageSize", lineItems.size());
                response.put("pageSize", size);
                response.put("totalRecords", grouped.size());
                response.put("totalPages", 1);
                response.put("data", new ArrayList<>(grouped.values()));

                loggger.info("getNestedPurchaseOrders → LINE ITEMS for PO {} ({} lines)", poID, lineItems.size());
                return response;
            }



            // ===================================================================
            // 2. poNumber = "0" → Return PAGINATED PARENTS (fast summary only)
            // ===================================================================
            String countSql = "SELECT COUNT(DISTINCT PO.poNumber) FROM tb_PurchaseOrder PO" + whereClause;
            Integer totalRecords = jdbcTemplate.queryForObject(countSql, params.toArray(), Integer.class);

            if (totalRecords == null || totalRecords == 0) {
                return buildEmptyResponse(page, size);
            }

            // Aggregated parent query
            List<Object> parentParams = new ArrayList<>(params);
//            String parentSql =
//                    "SELECT " +
//                            "PO.poNumber, " +
//                            "MAX(PO.vendorNumber) AS vendorNumber, " +
//                            "MAX(PO.vendorName) AS vendorName, " +
//                            "MAX(PO.projectName) AS projectName, " +
//                            "MAX(PO.prNum) AS prNum, " +
//                            "MAX(PO.typeLookUpCode) AS typeLookUpCode, " +
//                            "MAX(PO.currencyCode) AS currencyCode, " +
//                            "MAX(PO.createdDate) AS createdDate, " +
//                            "MAX(PO.approvedDate) AS approvedDate, " +
//                            "SUM(PO.poQtyNew) AS totalPoQtyNew, " +
//                            "SUM(PO.poOrderQuantity) AS totalpoOrderQuantity, " +
//                            "SUM(PO.quantityReceived) AS totalQuantityReceived, " +
//                            "SUM(PO.quantityDueOld) AS totalQuantityDueOld, " +
//                            "SUM(PO.quantityDueNew) AS totalQuantityDueNew, " +
//                            "SUM(PO.quantityBilled) AS totalQuantityBilled, " +
//                            "SUM(PO.linePriceInSAR) AS totallinePriceInSAR " +
//                            "FROM tb_PurchaseOrder PO" + whereClause +
//                            " GROUP BY PO.poNumber " +
//                            "ORDER BY PO.poNumber";

            // Aggregated parent query
            String parentSql =
                    "SELECT " +
                            "PO.poNumber, " +
                            "MAX(PO.vendorNumber) AS vendorNumber, " +
                            "MAX(PO.vendorName) AS vendorName, " +
                            "MAX(PO.projectName) AS projectName, " +
                            "MAX(PO.prNum) AS prNum, " +
                            "MAX(PO.typeLookUpCode) AS typeLookUpCode, " +
                            "MAX(PO.currencyCode) AS currencyCode, " +
                            "MAX(PO.createdDate) AS createdDate, " +
                            "MAX(PO.approvedDate) AS approvedDate, " +

                            // ALL totals that groupLineItemsByPO calculates
                            "SUM(PO.poQtyNew) AS totalPoQtyNew, " +
                            "SUM(PO.poOrderQuantity) AS totalpoOrderQuantity, " +
                            "SUM(PO.quantityReceived) AS totalQuantityReceived, " +
                            "SUM(PO.quantityDueOld) AS totalQuantityDueOld, " +
                            "SUM(PO.quantityDueNew) AS totalQuantityDueNew, " +
                            "SUM(PO.quantityBilled) AS totalQuantityBilled, " +
                            "SUM(PO.unitPriceInPoCurrency) AS totalunitPriceInPoCurrency, " +
                            "SUM(PO.unitPriceInSAR) AS totalunitPriceInSAR, " +
                            "SUM(PO.linePriceInPoCurrency) AS totallinePriceInPoCurrency, " +
                            "SUM(PO.linePriceInSAR) AS totallinePriceInSAR, " +
                            "SUM(PO.amountReceived) AS totalamountReceived, " +
                            "SUM(PO.amountDue) AS totalamountDue, " +
                            "SUM(PO.amountDueNew) AS totalamountDueNew, " +
                            "SUM(PO.amountBilled) AS totalamountBilled, " +
                            "SUM(PO.descopedLinePriceInPoCurrency) AS totalDescopedLinePriceInPoCurrency, " +
                            "SUM(PO.newLinePriceInPoCurrency) AS totalNewLinePriceInPoCurrency " +

                            "FROM tb_PurchaseOrder PO" + whereClause +
                            " GROUP BY PO.poNumber " +
                            "ORDER BY PO.poNumber";

            List<Map<String, Object>> parents;
            if (page == 1 && size == 20000) {
                parents = jdbcTemplate.queryForList(parentSql, parentParams.toArray());
            } else {
                parentParams.add(size);
                parentParams.add((page - 1) * size);
                parents = jdbcTemplate.queryForList(parentSql + " LIMIT ? OFFSET ?", parentParams.toArray());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalRecords", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("data", parents);   // ← PO summaries only

            loggger.info("getNestedPurchaseOrders → PARENTS page {} ({} POs)", page, parents.size());
            return response;

        } catch (Exception e) {
            loggger.error("Error in getNestedPurchaseOrders", e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Error: " + e.getMessage());
            return error;
        }
    }

    @PostMapping("/v2/filterNestedPurchaseOrders")
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

            // Base filters (same as before)
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

            // Date filters
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

            // Having filters (aggregates)
            String havingClause = "";
            List<Object> havingParams = new ArrayList<>();
            if (filters.containsKey("totalPoQtyNew") && !filters.get("totalPoQtyNew").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.poQtyNew) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalPoQtyNew")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalPoQtyNew", e);
                }
            }
            if (filters.containsKey("totalpoOrderQuantity") && !filters.get("totalpoOrderQuantity").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.poOrderQuantity) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalpoOrderQuantity")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalpoOrderQuantity", e);
                }
            }
            if (filters.containsKey("totalQuantityReceived") && !filters.get("totalQuantityReceived").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.quantityReceived) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalQuantityReceived")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityReceived", e);
                }
            }
            if (filters.containsKey("totalQuantityDueOld") && !filters.get("totalQuantityDueOld").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.quantityDueOld) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalQuantityDueOld")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityDueOld", e);
                }
            }
            if (filters.containsKey("totalQuantityDueNew") && !filters.get("totalQuantityDueNew").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.quantityDueNew) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalQuantityDueNew")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityDueNew", e);
                }
            }
            if (filters.containsKey("totalQuantityBilled") && !filters.get("totalQuantityBilled").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.quantityBilled) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totalQuantityBilled")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totalQuantityBilled", e);
                }
            }
            if (filters.containsKey("totallinePriceInSAR") && !filters.get("totallinePriceInSAR").isEmpty()) {
                try {
                    havingClause += " AND SUM(PO.linePriceInSAR) = ?";
                    havingParams.add(Double.parseDouble(filters.get("totallinePriceInSAR")));
                } catch (NumberFormatException e) {
                    loggger.error("Invalid totallinePriceInSAR", e);
                }
            }

            List<Object> subqueryParams = new ArrayList<>(baseParams);
            subqueryParams.addAll(havingParams);

            String havingFragment = havingClause.isEmpty() ? "" : " HAVING " + havingClause.substring(5);

            // ===================================================================
            // COUNT unique POs (fast)
            // ===================================================================
            String countSql = "SELECT COUNT(*) FROM (" +
                    "SELECT PO.poNumber FROM tb_PurchaseOrder PO" + baseWhereClause +
                    " GROUP BY PO.poNumber" + havingFragment +
                    ") sub";

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

            // ===================================================================
            // PARENT QUERY – aggregated summaries only (fast, no lines!)
            // ===================================================================
            int offset = page * size;
            String sortClause = sortDir.equalsIgnoreCase("asc") ? "ASC" : "DESC";

            List<Object> parentParams = new ArrayList<>(baseParams);
            parentParams.addAll(havingParams); // for HAVING

            String parentSql = "SELECT " +
                    "PO.poNumber, " +
                    "MAX(PO.vendorNumber) AS vendorNumber, " +
                    "MAX(PO.vendorName) AS vendorName, " +
                    "MAX(PO.projectName) AS projectName, " +
                    "MAX(PO.prNum) AS prNum, " +
                    "MAX(PO.typeLookUpCode) AS typeLookUpCode, " +
                    "MAX(PO.currencyCode) AS currencyCode, " +
                    "MAX(PO.createdDate) AS createdDate, " +
                    "MAX(PO.approvedDate) AS approvedDate, " +
                    "SUM(PO.poQtyNew) AS totalPoQtyNew, " +
                    "SUM(PO.poOrderQuantity) AS totalpoOrderQuantity, " +
                    "SUM(PO.quantityReceived) AS totalQuantityReceived, " +
                    "SUM(PO.quantityDueOld) AS totalQuantityDueOld, " +
                    "SUM(PO.quantityDueNew) AS totalQuantityDueNew, " +
                    "SUM(PO.quantityBilled) AS totalQuantityBilled, " +
                    "SUM(PO.unitPriceInPoCurrency) AS totalunitPriceInPoCurrency, " +
                    "SUM(PO.unitPriceInSAR) AS totalunitPriceInSAR, " +
                    "SUM(PO.linePriceInPoCurrency) AS totallinePriceInPoCurrency, " +
                    "SUM(PO.linePriceInSAR) AS totallinePriceInSAR, " +
                    "SUM(PO.amountReceived) AS totalamountReceived, " +
                    "SUM(PO.amountDue) AS totalamountDue, " +
                    "SUM(PO.amountDueNew) AS totalamountDueNew, " +
                    "SUM(PO.amountBilled) AS totalamountBilled, " +
                    "SUM(PO.descopedLinePriceInPoCurrency) AS totalDescopedLinePriceInPoCurrency, " +
                    "SUM(PO.newLinePriceInPoCurrency) AS totalNewLinePriceInPoCurrency " +
                    "FROM tb_PurchaseOrder PO" + baseWhereClause +
                    " GROUP BY PO.poNumber" + havingFragment;

            // Handle sorting on numeric aggregate columns
            Set<String> numericColumns = new HashSet<>(Arrays.asList(
                    "totalPoQtyNew", "totalpoOrderQuantity", "totalQuantityReceived",
                    "totalQuantityDueOld", "totalQuantityDueNew", "totalQuantityBilled",
                    "totallinePriceInSAR"));

            if (numericColumns.contains(sortBy)) {
                String sortField = sortBy.substring(5).toLowerCase(); // remove "total" prefix if needed
                parentSql += " ORDER BY SUM(PO." + sortField + ") " + sortClause;
            } else if (!sortBy.isEmpty()) {
                parentSql += " ORDER BY PO.poNumber " + sortClause;
            }

            // Add pagination
            parentParams.add(size);
            parentParams.add(offset);

            List<Map<String, Object>> parents = jdbcTemplate.queryForList(
                    parentSql + " LIMIT ? OFFSET ?", parentParams.toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("reports", parents);           // ← now contains parent summaries only
            response.put("currentPage", page);
            response.put("totalItems", totalRecords);
            response.put("totalPages", (int) Math.ceil((double) totalRecords / size));
            response.put("first", page == 0);
            response.put("last", parents.size() < size || (page + 1) * size >= totalRecords);
            response.put("size", size);
            response.put("sort", sortBy + "," + sortDir);

            loggger.info("filterNestedPurchaseOrders → Returned {} parents (page {})", parents.size(), page);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            loggger.error("Error filtering purchase orders", e);
            return new ResponseEntity<>(Collections.singletonMap("message",
                    "Error filtering purchase orders: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> buildEmptyResponse(int page, int size) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("currentPage", page);
        empty.put("pageSize", size);
        empty.put("totalRecords", 0);
        empty.put("totalPages", 0);
        empty.put("data", Collections.emptyList());
        return empty;
    }

//    private Map<String, Object> buildPoLineItem(Map<String, Object> lineItem) {
//
//        return new LinkedHashMap<>(lineItem);
//    }
    private Map<String, Object> buildPoLineItem(Map<String, Object> lineItem) {
        Map<String, Object> cleanLine = new LinkedHashMap<>();

        List<String> lineFields = Arrays.asList(
                "recordNo", "lineNumber", "countryOfOrigin", "poOrderQuantity", "poQtyNew",
                "quantityReceived", "quantityDueOld", "quantityDueNew", "quantityBilled",
                "unitPriceInPoCurrency", "unitPriceInSAR", "linePriceInPoCurrency", "linePriceInSAR",
                "amountReceived", "amountDue", "amountDueNew", "amountBilled",
                "poLineDescription", "vendorSerialNumberYN", "itemCategoryInventory",
                "inventoryCategoryDescription", "itemCategoryFA", "FACategoryDescription",
                "descopedLinePriceInPoCurrency", "newLinePriceInPoCurrency");

        for (String field : lineFields) {
            if (lineItem.containsKey(field)) {
                cleanLine.put(field, lineItem.get(field));
            }
        }
        return cleanLine;
    }


    private Map<String, Map<String, Object>> groupLineItemsByPO(List<Map<String, Object>> lineItems) {
        List<String> lineSpecificFields = Arrays.asList(
                "recordNo", "lineNumber", "countryOfOrigin", "poOrderQuantity", "poQtyNew",
                "quantityReceived", "quantityDueOld", "quantityDueNew", "quantityBilled",
                "unitPriceInPoCurrency", "unitPriceInSAR", "linePriceInPoCurrency", "linePriceInSAR",
                "amountReceived", "amountDue", "amountDueNew", "amountBilled",
                "poLineDescription", "vendorSerialNumberYN", "itemCategoryInventory",
                "inventoryCategoryDescription", "itemCategoryFA", "FACategoryDescription",
                "descopedLinePriceInPoCurrency", "newLinePriceInPoCurrency");

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> lineItem : lineItems) {
            String poNumber = (String) lineItem.get("poNumber");

            if (!grouped.containsKey(poNumber)) {
                Map<String, Object> groupedRow = new LinkedHashMap<>(lineItem);
                for (String field : lineSpecificFields) {
                    groupedRow.remove(field);
                }
                groupedRow.put("lineCancelFlag", lineItem.get("lineCancelFlag").toString().equalsIgnoreCase("false") ? "N" : "Y");
                groupedRow.put("prSubAllow", lineItem.get("prSubAllow").toString().equalsIgnoreCase("false") ? "N" : "Y");

                // Initialize all totals
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

            // Add line item
            ((List<Map<String, Object>>) grouped.get(poNumber).get("POlineItems")).add(buildPoLineItem(lineItem));

            // Accumulate totals
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
        }
        return grouped;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }

    }


}