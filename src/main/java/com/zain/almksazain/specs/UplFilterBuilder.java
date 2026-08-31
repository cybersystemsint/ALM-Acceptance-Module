package com.zain.almksazain.specs;

import java.util.List;
import java.util.Map;

/**
 * Shared WHERE-clause builder for tb_PurchaseOrderUPL filtering - used by both the interactive
 * /filterUPLs endpoint and the async /reports/getAllCreatedUPLs/export job, so a fix or added
 * column here applies to fetch and export identically (not fixed in only one place).
 *
 * Extracted from ReportsController.filterUPLs's original inline logic, plus two columns that
 * were selectable in the multi-filter dialog but had no predicate here at all (recordNo,
 * uplItemSerialized) - filtering on either was previously silently ignored.
 */
public final class UplFilterBuilder {

    private UplFilterBuilder() {
    }

    /** Returns the " AND ..." fragments to append after " WHERE 1=1"; appends matching values to params. */
    public static String buildWhereClause(Map<String, String> filters, List<Object> params) {
        StringBuilder whereClause = new StringBuilder();

        // String filters
        if (filters.containsKey("poNumber") && !filters.get("poNumber").isEmpty()) {
            whereClause.append(" AND UPL.poNumber = ?");
            params.add(filters.get("poNumber"));
        }
        if (filters.containsKey("vendor") && !filters.get("vendor").isEmpty()) {
            whereClause.append(" AND UPL.vendor = ?");
            params.add(filters.get("vendor"));
        }
        if (filters.containsKey("manufacturer") && !filters.get("manufacturer").isEmpty()) {
            whereClause.append(" AND UPL.manufacturer = ?");
            params.add(filters.get("manufacturer"));
        }
        if (filters.containsKey("countryOfOrigin") && !filters.get("countryOfOrigin").isEmpty()) {
            whereClause.append(" AND UPL.countryOfOrigin = ?");
            params.add(filters.get("countryOfOrigin"));
        }
        if (filters.containsKey("projectName") && !filters.get("projectName").isEmpty()) {
            whereClause.append(" AND UPL.projectName = ?");
            params.add(filters.get("projectName"));
        }
        if (filters.containsKey("poType") && !filters.get("poType").isEmpty()) {
            whereClause.append(" AND UPL.poType = ?");
            params.add(filters.get("poType"));
        }
        if (filters.containsKey("releaseNumber") && !filters.get("releaseNumber").isEmpty()) {
            whereClause.append(" AND UPL.releaseNumber = ?");
            params.add(filters.get("releaseNumber"));
        }
        if (filters.containsKey("poLineNumber") && !filters.get("poLineNumber").isEmpty()) {
            whereClause.append(" AND UPL.poLineNumber = ?");
            params.add(filters.get("poLineNumber"));
        }
        if (filters.containsKey("uplLine") && !filters.get("uplLine").isEmpty()) {
            whereClause.append(" AND UPL.uplLine = ?");
            params.add(filters.get("uplLine"));
        }
        if (filters.containsKey("poLineItemType") && !filters.get("poLineItemType").isEmpty()) {
            whereClause.append(" AND UPL.poLineItemType = ?");
            params.add(filters.get("poLineItemType"));
        }
        if (filters.containsKey("poLineItemCode") && !filters.get("poLineItemCode").isEmpty()) {
            whereClause.append(" AND UPL.poLineItemCode = ?");
            params.add(filters.get("poLineItemCode"));
        }
        if (filters.containsKey("poLineDescription") && !filters.get("poLineDescription").isEmpty()) {
            whereClause.append(" AND UPL.poLineDescription = ?");
            params.add(filters.get("poLineDescription"));
        }
        if (filters.containsKey("uplLineItemType") && !filters.get("uplLineItemType").isEmpty()) {
            whereClause.append(" AND UPL.uplLineItemType = ?");
            params.add(filters.get("uplLineItemType"));
        }
        if (filters.containsKey("uplLineItemCode") && !filters.get("uplLineItemCode").isEmpty()) {
            whereClause.append(" AND UPL.uplLineItemCode = ?");
            params.add(filters.get("uplLineItemCode"));
        }
        if (filters.containsKey("uplLineDescription") && !filters.get("uplLineDescription").isEmpty()) {
            whereClause.append(" AND UPL.uplLineDescription = ?");
            params.add(filters.get("uplLineDescription"));
        }
        if (filters.containsKey("zainItemCategoryCode") && !filters.get("zainItemCategoryCode").isEmpty()) {
            whereClause.append(" AND UPL.zainItemCategoryCode = ?");
            params.add(filters.get("zainItemCategoryCode"));
        }
        if (filters.containsKey("zainItemCategoryDescription") && !filters.get("zainItemCategoryDescription").isEmpty()) {
            whereClause.append(" AND UPL.zainItemCategoryDescription = ?");
            params.add(filters.get("zainItemCategoryDescription"));
        }
        if (filters.containsKey("uplItemSerialized") && !filters.get("uplItemSerialized").isEmpty()) {
            whereClause.append(" AND UPL.uplItemSerialized = ?");
            params.add(filters.get("uplItemSerialized"));
        }
        if (filters.containsKey("activeOrPassive") && !filters.get("activeOrPassive").isEmpty()) {
            whereClause.append(" AND UPL.activeOrPassive = ?");
            params.add(filters.get("activeOrPassive"));
        }
        if (filters.containsKey("uom") && !filters.get("uom").isEmpty()) {
            whereClause.append(" AND UPL.uom = ?");
            params.add(filters.get("uom"));
        }
        if (filters.containsKey("currency") && !filters.get("currency").isEmpty()) {
            whereClause.append(" AND UPL.currency = ?");
            params.add(filters.get("currency"));
        }
        if (filters.containsKey("substituteItemCode") && !filters.get("substituteItemCode").isEmpty()) {
            whereClause.append(" AND UPL.substituteItemCode = ?");
            params.add(filters.get("substituteItemCode"));
        }
        if (filters.containsKey("remarks") && !filters.get("remarks").isEmpty()) {
            whereClause.append(" AND UPL.remarks = ?");
            params.add(filters.get("remarks"));
        }
        if (filters.containsKey("createdByName") && !filters.get("createdByName").isEmpty()) {
            whereClause.append(" AND UPL.createdByName = ?");
            params.add(filters.get("createdByName"));
        }
        if (filters.containsKey("updatedByName") && !filters.get("updatedByName").isEmpty()) {
            whereClause.append(" AND UPL.uplModifiedBy = ?");
            params.add(filters.get("updatedByName"));
        }
        if (filters.containsKey("recordNo") && !filters.get("recordNo").isEmpty()) {
            try {
                Long value = Long.parseLong(filters.get("recordNo"));
                whereClause.append(" AND UPL.recordNo = ?");
                params.add(value);
            } catch (NumberFormatException e) {
                // Skip invalid recordNo value, same defensive style as the numeric filters below
            }
        }

        // Numeric filters
        if (filters.containsKey("poLineQuantity") && !filters.get("poLineQuantity").isEmpty()) {
            try {
                Double value = Double.parseDouble(filters.get("poLineQuantity"));
                whereClause.append(" AND UPL.poLineQuantity = ?");
                params.add(value);
            } catch (NumberFormatException e) {
                // Skip invalid value
            }
        }
        if (filters.containsKey("poLineUnitPrice") && !filters.get("poLineUnitPrice").isEmpty()) {
            try {
                Double value = Double.parseDouble(filters.get("poLineUnitPrice"));
                whereClause.append(" AND UPL.poLineUnitPrice = ?");
                params.add(value);
            } catch (NumberFormatException e) {
                // Skip invalid value
            }
        }
        if (filters.containsKey("uplLineQuantity") && !filters.get("uplLineQuantity").isEmpty()) {
            try {
                Double value = Double.parseDouble(filters.get("uplLineQuantity"));
                whereClause.append(" AND UPL.uplLineQuantity = ?");
                params.add(value);
            } catch (NumberFormatException e) {
                // Skip invalid value
            }
        }
        if (filters.containsKey("uplLineUnitPrice") && !filters.get("uplLineUnitPrice").isEmpty()) {
            try {
                Double value = Double.parseDouble(filters.get("uplLineUnitPrice"));
                whereClause.append(" AND UPL.uplLineUnitPrice = ?");
                params.add(value);
            } catch (NumberFormatException e) {
                // Skip invalid value
            }
        }

        // Date range filters
        if (filters.containsKey("recordDatetimeStart") && !filters.get("recordDatetimeStart").isEmpty()) {
            whereClause.append(" AND UPL.recordDatetime >= ?");
            params.add(filters.get("recordDatetimeStart"));
        }
        if (filters.containsKey("recordDatetimeEnd") && !filters.get("recordDatetimeEnd").isEmpty()) {
            whereClause.append(" AND UPL.recordDatetime <= ?");
            params.add(filters.get("recordDatetimeEnd"));
        }
        if (filters.containsKey("updatedDatetimeStart") && !filters.get("updatedDatetimeStart").isEmpty()) {
            whereClause.append(" AND UPL.uplModifiedDate >= ?");
            params.add(filters.get("updatedDatetimeStart"));
        }
        if (filters.containsKey("updatedDatetimeEnd") && !filters.get("updatedDatetimeEnd").isEmpty()) {
            whereClause.append(" AND UPL.uplModifiedDate <= ?");
            params.add(filters.get("updatedDatetimeEnd"));
        }

        return whereClause.toString();
    }
}
