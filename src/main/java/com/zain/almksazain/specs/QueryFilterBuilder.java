package com.zain.almksazain.specs;



import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public final class QueryFilterBuilder {

    private QueryFilterBuilder() {}

    public static class OperatorAndValues {
        public String operator;
        public List<String> values;
    }

    public static OperatorAndValues normalizeOperatorAndValuesFromJson(JsonElement elem) {
        OperatorAndValues result = new OperatorAndValues();
        result.operator = null;
        result.values = new ArrayList<>();

        if (elem == null || elem.isJsonNull()) return result;

        if (elem.isJsonObject()) {
            JsonObject o = elem.getAsJsonObject();
            if (o.has("operator") && !o.get("operator").isJsonNull()) result.operator = o.get("operator").getAsString();
            if (o.has("value") && !o.get("value").isJsonNull()) {
                JsonElement val = o.get("value");
                if (val.isJsonArray()) {
                    for (JsonElement e : val.getAsJsonArray()) if (!e.isJsonNull()) result.values.add(e.getAsString());
                } else {
                    String s = val.getAsString();
                    if (s.contains(",")) {
                        for (String part : s.split(",")) if (!part.trim().isEmpty()) result.values.add(part.trim());
                    } else {
                        result.values.add(s);
                    }
                }
            }
            return result;
        }

        // array or scalar
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            for (JsonElement e : arr) if (!e.isJsonNull()) result.values.add(e.getAsString());
            return result;
        }

        String raw = elem.getAsString();
        if (raw.contains(",")) {
            for (String s : raw.split(",")) if (!s.trim().isEmpty()) result.values.add(s.trim());
        } else {
            result.values.add(raw);
        }
        return result;
    }

    public static String buildPredicateFragment(String mappedColumn, OperatorAndValues ov, List<Object> params) {
        String op = ov.operator != null ? ov.operator.trim().toLowerCase() : defaultOperatorForKey(mappedColumn);
        List<String> values = ov.values;

        // recordNo (numeric) special handling
        if ("recordNo".equals(mappedColumn)) {
            if ("isempty".equals(op) || "is empty".equals(op)) {
                return mappedColumn + " IS NULL";
            }
            if ("isnotempty".equals(op) || "is not empty".equals(op)) {
                return mappedColumn + " IS NOT NULL";
            }
            if ("isanyof".equals(op) || "is any of".equals(op)) {
                List<Long> longVals = new ArrayList<>();
                for (String v : values) {
                    try { longVals.add(Long.valueOf(v)); } catch (NumberFormatException ignored) {}
                }
                if (longVals.isEmpty()) return "1=0";
                String placeholders = longVals.stream().map(x -> "?").collect(Collectors.joining(","));
                params.addAll(longVals);
                return mappedColumn + " IN (" + placeholders + ")";
            }
            // contains/startsWith/endsWith on numeric -> use LIKE against string representation
            if ("contains".equals(op) || "startswith".equals(op) || "starts with".equals(op) || "endswith".equals(op) || "ends with".equals(op)) {
                List<String> likes = new ArrayList<>();
                for (String v : values) {
                    if (v == null) continue;
                    String pattern;
                    if ("startswith".equals(op) || "starts with".equals(op)) pattern = v + "%";
                    else if ("endswith".equals(op) || "ends with".equals(op)) pattern = "%" + v;
                    else pattern = "%" + v + "%";
                    likes.add(mappedColumn + " LIKE ?");
                    params.add(pattern);
                }
                return String.join(" OR ", likes);
            }
            // default/equals: numeric equality or OR of equals
            List<String> eqs = new ArrayList<>();
            for (String v : values) {
                try { Long lv = Long.valueOf(v); params.add(lv); eqs.add(mappedColumn + " = ?"); } catch (NumberFormatException ignored) {}
            }
            if (eqs.isEmpty()) return "1=0";
            return String.join(" OR ", eqs);
        }

        // Date/time fields: use LIKE fallback (matches yyyy-MM-dd etc.)
        if ("createdDatetime".equals(mappedColumn) || "recordDateTime".equals(mappedColumn)) {
            if ("isempty".equals(op) || "is empty".equals(op)) return mappedColumn + " IS NULL";
            if ("isnotempty".equals(op) || "is not empty".equals(op)) return mappedColumn + " IS NOT NULL";
            if ("isanyof".equals(op) || "is any of".equals(op)) {
                List<String> parts = new ArrayList<>();
                for (String v : values) {
                    parts.add(mappedColumn + " LIKE ?");
                    params.add("%" + v + "%");
                }
                return String.join(" OR ", parts);
            }
            // equals/contains default -> use LIKE with value
            List<String> parts = new ArrayList<>();
            for (String v : values) {
                parts.add(mappedColumn + " LIKE ?");
                params.add("%" + v + "%");
            }
            return String.join(" OR ", parts);
        }

        // Default string fields
        if ("isempty".equals(op) || "is empty".equals(op)) {
            return "(" + mappedColumn + " IS NULL OR TRIM(" + mappedColumn + ") = '')";
        }
        if ("isnotempty".equals(op) || "is not empty".equals(op)) {
            return "(" + mappedColumn + " IS NOT NULL AND TRIM(" + mappedColumn + ") <> '')";
        }
        if ("isanyof".equals(op) || "is any of".equals(op)) {
            List<String> placeholders = new ArrayList<>();
            for (String v : values) {
                placeholders.add("?");
                params.add(v);
            }
            if (placeholders.isEmpty()) return "1=0";
            return mappedColumn + " IN (" + String.join(",", placeholders) + ")";
        }
        if ("equals".equals(op)) {
            List<String> parts = new ArrayList<>();
            for (String v : values) {
                parts.add(mappedColumn + " = ?");
                params.add(v);
            }
            return String.join(" OR ", parts);
        }
        if ("startswith".equals(op) || "starts with".equals(op)) {
            List<String> parts = new ArrayList<>();
            for (String v : values) { parts.add(mappedColumn + " LIKE ?"); params.add(v + "%"); }
            return String.join(" OR ", parts);
        }
        if ("endswith".equals(op) || "ends with".equals(op)) {
            List<String> parts = new ArrayList<>();
            for (String v : values) { parts.add(mappedColumn + " LIKE ?"); params.add("%" + v); }
            return String.join(" OR ", parts);
        }
        // default contains
        List<String> parts = new ArrayList<>();
        for (String v : values) { parts.add(mappedColumn + " LIKE ?"); params.add("%" + v + "%"); }
        return String.join(" OR ", parts);
    }

    // private static String defaultOperatorForKey(String mappedColumn) {
    //     if (mappedColumn == null) return "contains";
    //     if ("recordNo".equals(mappedColumn)) return "equals";
    //     return "contains";
    // }

    private static String defaultOperatorForKey(String key) {
    return (key == null) ? "contains" : "contains";
}

}
