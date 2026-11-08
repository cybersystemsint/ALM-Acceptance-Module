package com.zain.almksazain.controller;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Shared request-parsing logic for the Aging Report / Full Aging Report fetch and
 * export endpoints (AgingReportController, ExportsController) - previously duplicated
 * verbatim in each fetch method. Supports both the legacy single columnName+searchQuery
 * filter and the newer filterBy object with multiple filters, merging both into one
 * flat Map<String,String>. Deliberately has no logging side effect (callers use
 * different logging facades - slf4j in ExportsController, log4j2 in
 * AgingReportController) - log the parsed result at the call site if needed.
 */
public final class AgingReportRequestParser {

    private AgingReportRequestParser() {
    }

    public record ParsedRequest(String supplierId, int page, int size, Map<String, String> filters) {
    }

    public static ParsedRequest parse(JsonObject obj) {
        String supplierId = (obj.has("supplierId") && !obj.get("supplierId").isJsonNull())
                ? obj.get("supplierId").getAsString() : null;
        int page = (obj.has("page") && !obj.get("page").isJsonNull()) ? obj.get("page").getAsInt() : 1;
        int size = (obj.has("size") && !obj.get("size").isJsonNull()) ? obj.get("size").getAsInt() : 100;

        Map<String, String> filters = new HashMap<>();

        // Legacy format: single columnName + searchQuery
        if (obj.has("columnName") && !obj.get("columnName").isJsonNull() && !obj.get("columnName").getAsString().isEmpty()) {
            String columnName = obj.get("columnName").getAsString();
            String searchQuery = (obj.has("searchQuery") && !obj.get("searchQuery").isJsonNull())
                    ? obj.get("searchQuery").getAsString() : "";
            if (!searchQuery.isEmpty()) {
                filters.put(columnName, searchQuery);
            }
        }

        // New format: filterBy object with multiple filters
        if (obj.has("filterBy") && obj.get("filterBy").isJsonObject()) {
            JsonObject filterByObj = obj.get("filterBy").getAsJsonObject();
            for (String key : filterByObj.keySet()) {
                if (!filterByObj.get(key).isJsonNull()) {
                    String value = filterByObj.get(key).getAsString().trim();
                    if (!value.isEmpty()) {
                        filters.put(key, value);
                    }
                }
            }
        }

        return new ParsedRequest(supplierId, page, size, filters);
    }
}
