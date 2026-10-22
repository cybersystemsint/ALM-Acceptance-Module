package com.zain.almksazain.specs;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared SELECT / WHERE / ORDER BY for the TAG Numbers page and the TAG Number Audit Trail page -
 * used by both the paged fetch endpoints (TagNumberController) and their export jobs
 * (ExportsController), so a filter behaves the same on screen and in the exported file.
 *
 * A request carries a list of conditions, {field, operator, value}, all ANDed: the grid's column
 * filter and the "Filter By" dialog each add their own, so they never overwrite each other.
 * The older flat shape ({field: value, field_op: operator, dateFieldStart/End: ...}) is accepted
 * too. Only whitelisted fields are used; values are always bound as parameters.
 *
 * Text matching is case-insensitive (the columns use the schema's _ci collation).
 */
public final class TagNumberFilterBuilder {

    private TagNumberFilterBuilder() {
    }

    /** Acceptance request statuses that do NOT count as "active" (same as APIController#postdcc). */
    public static final String INACTIVE_DCC_STATUSES = "'incomplete', 'rejected', 'returned'";

    public enum Kind { TEXT, NUMBER, DATE, REQUEST_NO }

    public static final class Column {
        final String sql;
        final Kind kind;
        final String defaultOperator;
        final String sortSql;

        Column(String sql, Kind kind, String defaultOperator, String sortSql) {
            this.sql = sql;
            this.kind = kind;
            this.defaultOperator = defaultOperator;
            this.sortSql = sortSql;
        }

        static Column text(String sql) {
            return new Column(sql, Kind.TEXT, "contains", sql);
        }

        static Column exact(String sql) {
            return new Column(sql, Kind.TEXT, "equals", sql);
        }

        static Column number(String sql) {
            return new Column(sql, Kind.NUMBER, "equals", sql);
        }

        static Column date(String sql) {
            return new Column(sql, Kind.DATE, "equals", sql);
        }
    }

    public static final class Condition {
        public final String field;
        public final String operator;
        public final Object value;

        public Condition(String field, String operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }

    // ============================================================
    // TAG Numbers page
    // ============================================================

    // Every acceptance request whose lines carry this TAG (any status), newest first.
    private static final String REQUEST_NO_SQL = "(SELECT GROUP_CONCAT(DISTINCT ln.dccId "
            + "ORDER BY CAST(ln.dccId AS UNSIGNED) DESC SEPARATOR ', ') "
            + "FROM tb_DCC_LN ln WHERE ln.tagNumber = t.tagNumber)";
    private static final String REQUEST_NO_SORT_SQL = "(SELECT MAX(CAST(ln.dccId AS UNSIGNED)) "
            + "FROM tb_DCC_LN ln WHERE ln.tagNumber = t.tagNumber)";
    // Lines on active requests - such a TAG can't be edited or deleted (TagNumberService).
    private static final String ACTIVE_REQUEST_COUNT_SQL = "(SELECT COUNT(*) FROM tb_DCC_LN ln "
            + "JOIN tb_DCC d ON d.recordNo = ln.dccId WHERE ln.tagNumber = t.tagNumber "
            + "AND LOWER(d.status) NOT IN (" + INACTIVE_DCC_STATUSES + "))";

    public static final Map<String, Column> TAG_NUMBER_COLUMNS = new LinkedHashMap<>();
    static {
        TAG_NUMBER_COLUMNS.put("recordNo", Column.number("t.recordNo"));
        TAG_NUMBER_COLUMNS.put("tagNumber", Column.text("t.tagNumber"));
        TAG_NUMBER_COLUMNS.put("vendorNumber", Column.text("t.vendorNumber"));
        TAG_NUMBER_COLUMNS.put("vendorName", Column.text("t.vendorName"));
        TAG_NUMBER_COLUMNS.put("status", Column.exact("t.status"));
        TAG_NUMBER_COLUMNS.put("requestNo", new Column(null, Kind.REQUEST_NO, "equals", REQUEST_NO_SORT_SQL));
        TAG_NUMBER_COLUMNS.put("createdBy", Column.text("t.createdBy"));
        TAG_NUMBER_COLUMNS.put("createdDate", Column.date("t.createdDate"));
        TAG_NUMBER_COLUMNS.put("lastUpdatedBy", Column.text("t.lastUpdatedBy"));
        TAG_NUMBER_COLUMNS.put("lastUpdatedDate", Column.date("t.lastUpdatedDate"));
    }

    public static final String TAG_NUMBER_SELECT = "SELECT t.recordNo AS recordNo, t.tagNumber AS tagNumber, "
            + "t.vendorNumber AS vendorNumber, t.vendorName AS vendorName, t.status AS status, "
            + REQUEST_NO_SQL + " AS requestNo, " + ACTIVE_REQUEST_COUNT_SQL + " AS activeRequestCount, "
            + "t.createdBy AS createdBy, t.createdDate AS createdDate, t.lastUpdatedBy AS lastUpdatedBy, "
            + "t.lastUpdatedDate AS lastUpdatedDate ";
    // Same columns minus activeRequestCount, which only drives the page's Edit/Delete buttons.
    public static final String TAG_NUMBER_EXPORT_SELECT = "SELECT t.recordNo AS recordNo, t.tagNumber AS tagNumber, "
            + "t.vendorNumber AS vendorNumber, t.vendorName AS vendorName, t.status AS status, "
            + REQUEST_NO_SQL + " AS requestNo, t.createdBy AS createdBy, t.createdDate AS createdDate, "
            + "t.lastUpdatedBy AS lastUpdatedBy, t.lastUpdatedDate AS lastUpdatedDate ";
    // Deleted TAGs never reach the UI.
    public static final String TAG_NUMBER_FROM = "FROM tb_TagNumber t WHERE t.status <> 'Deleted'";

    // ============================================================
    // TAG Number Audit Trail page
    // ============================================================

    public static final Map<String, Column> AUDIT_TRAIL_COLUMNS = new LinkedHashMap<>();
    static {
        AUDIT_TRAIL_COLUMNS.put("recordId", Column.number("a.recordId"));
        AUDIT_TRAIL_COLUMNS.put("tagRecordNo", Column.number("a.tagRecordNo"));
        AUDIT_TRAIL_COLUMNS.put("changeType", Column.exact("a.changeType"));
        AUDIT_TRAIL_COLUMNS.put("tagNumber", Column.text("a.tagNumber"));
        AUDIT_TRAIL_COLUMNS.put("vendorNumber", Column.text("a.vendorNumber"));
        AUDIT_TRAIL_COLUMNS.put("vendorName", Column.text("a.vendorName"));
        AUDIT_TRAIL_COLUMNS.put("changedByName", Column.text("a.changedByName"));
        AUDIT_TRAIL_COLUMNS.put("changedAt", Column.date("a.changedAt"));
        AUDIT_TRAIL_COLUMNS.put("batchId", Column.text("a.batchId"));
    }

    public static final String AUDIT_TRAIL_SELECT =
            "SELECT a.recordId AS recordId, a.batchId AS batchId, a.tagRecordNo AS tagRecordNo, "
                    + "a.changeType AS changeType, a.tagNumber AS tagNumber, a.vendorNumber AS vendorNumber, "
                    + "a.vendorName AS vendorName, a.fieldChanges AS fieldChanges, a.changedBy AS changedBy, "
                    + "a.changedByName AS changedByName, a.changedAt AS changedAt ";
    public static final String AUDIT_TRAIL_FROM = "FROM tb_TagNumber_Audit_Log a WHERE 1=1";

    // ============================================================
    // Request parsing
    // ============================================================

    /**
     * Reads {conditions: [{field, operator, value}]} or the flat {field: value} shape. Date fields
     * also accept the "Filter By" dialog's range keys, {field}Start / {field}End.
     */
    public static List<Condition> parseConditions(Map<String, Object> body, Map<String, Column> columns) {
        List<Condition> conditions = new ArrayList<>();
        if (body == null) {
            return conditions;
        }
        Object raw = body.get("conditions");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Object field = m.get("field");
                    Object operator = m.get("operator");
                    if (field != null) {
                        conditions.add(new Condition(String.valueOf(field),
                                operator == null ? null : String.valueOf(operator), m.get("value")));
                    }
                }
            }
            return conditions;
        }
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_op") || key.equals("conditions")) {
                continue;
            }
            if (columns.containsKey(key)) {
                Object operator = body.get(key + "_op");
                conditions.add(new Condition(key, operator == null ? null : String.valueOf(operator), entry.getValue()));
            } else if (key.endsWith("Start") && isDate(columns, key.substring(0, key.length() - 5))) {
                conditions.add(new Condition(key.substring(0, key.length() - 5), "gte", entry.getValue()));
            } else if (key.endsWith("End") && isDate(columns, key.substring(0, key.length() - 3))) {
                conditions.add(new Condition(key.substring(0, key.length() - 3), "lte", entry.getValue()));
            }
        }
        return conditions;
    }

    /** True when the request filters on anything - used for the export's "_FILTERED" file name. */
    public static boolean hasActiveConditions(List<Condition> conditions) {
        for (Condition c : conditions) {
            String op = normalizeOperator(c.operator, "equals");
            if (op.equals("isEmpty") || op.equals("isNotEmpty") || !values(c.value).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // SQL building
    // ============================================================

    /**
     * Returns the " AND ..." fragments for these conditions (to follow the FROM constant, which
     * already has a WHERE) and appends their values to params. Unknown fields are ignored; an
     * unknown operator or an unreadable number/date throws IllegalArgumentException.
     */
    public static String buildWhere(Map<String, Column> columns, List<Condition> conditions, List<Object> params) {
        StringBuilder where = new StringBuilder();
        for (Condition c : conditions) {
            Column column = columns.get(c.field);
            if (column == null) {
                continue;
            }
            String op = normalizeOperator(c.operator, column.defaultOperator);
            List<String> values = values(c.value);
            boolean noValueNeeded = op.equals("isEmpty") || op.equals("isNotEmpty");
            if (values.isEmpty() && !noValueNeeded) {
                continue;
            }
            switch (column.kind) {
                case NUMBER:
                    where.append(" AND ").append(numberClause(c.field, column.sql, op, values, params));
                    break;
                case DATE:
                    where.append(" AND ").append(dateClause(c.field, column.sql, op, values.isEmpty() ? null : values.get(0), params));
                    break;
                case REQUEST_NO:
                    where.append(" AND ").append(requestNoClause(op, values, params));
                    break;
                default:
                    where.append(" AND ").append(textClause(column.sql, op, values, params));
            }
        }
        return where.toString();
    }

    /** " ORDER BY ..." for a whitelisted sortBy (else fallbackSql), always ending on a stable key. */
    public static String orderBy(Map<String, Column> columns, String sortBy, String sortDir, String fallbackSql) {
        Column column = sortBy == null ? null : columns.get(sortBy);
        String sql = column != null && column.sortSql != null ? column.sortSql : fallbackSql;
        String dir = "asc".equalsIgnoreCase(sortDir) ? " ASC" : " DESC";
        String order = " ORDER BY " + sql + dir;
        return sql.equals(fallbackSql) ? order : order + ", " + fallbackSql + " DESC";
    }

    /**
     * Up to `limit` distinct values of one column across all matching rows (not just the page on
     * screen) for the "Filter By" dialog's suggestions; null for date or unknown columns.
     */
    public static String distinctValuesSql(Map<String, Column> columns, String field, String from, int limit) {
        Column column = field == null ? null : columns.get(field);
        if (column == null || column.kind == Kind.DATE) {
            return null;
        }
        if (column.kind == Kind.REQUEST_NO) {
            return "SELECT DISTINCT ln.dccId AS v FROM tb_DCC_LN ln JOIN tb_TagNumber t ON ln.tagNumber = t.tagNumber "
                    + "WHERE t.status <> 'Deleted' ORDER BY CAST(ln.dccId AS UNSIGNED) DESC LIMIT " + limit;
        }
        String notBlank = column.kind == Kind.NUMBER
                ? column.sql + " IS NOT NULL"
                : column.sql + " IS NOT NULL AND " + column.sql + " <> ''";
        return "SELECT DISTINCT " + column.sql + " AS v " + from + " AND " + notBlank
                + " ORDER BY v" + (column.kind == Kind.NUMBER ? " DESC" : "") + " LIMIT " + limit;
    }

    private static String textClause(String col, String op, List<String> values, List<Object> params) {
        String v = values.isEmpty() ? null : values.get(0);
        switch (op) {
            case "contains":
                params.add("%" + escapeLike(v) + "%");
                return col + " LIKE ?";
            case "notContains":
                params.add("%" + escapeLike(v) + "%");
                return "(" + col + " IS NULL OR " + col + " NOT LIKE ?)";
            case "startsWith":
                params.add(escapeLike(v) + "%");
                return col + " LIKE ?";
            case "endsWith":
                params.add("%" + escapeLike(v));
                return col + " LIKE ?";
            case "equals":
                params.add(v);
                return col + " = ?";
            case "notEquals":
                params.add(v);
                return "(" + col + " IS NULL OR " + col + " <> ?)";
            case "isAnyOf":
                params.addAll(values);
                return col + " IN (" + placeholders(values.size()) + ")";
            case "isEmpty":
                return "(" + col + " IS NULL OR " + col + " = '')";
            case "isNotEmpty":
                return "(" + col + " IS NOT NULL AND " + col + " <> '')";
            default:
                throw new IllegalArgumentException("Unsupported filter operator '" + op + "'.");
        }
    }

    private static String numberClause(String field, String col, String op, List<String> values, List<Object> params) {
        switch (op) {
            case "contains":
            case "notContains":
            case "startsWith":
            case "endsWith":
                return textClause("CAST(" + col + " AS CHAR)", op, values, params);
            case "isEmpty":
                return col + " IS NULL";
            case "isNotEmpty":
                return col + " IS NOT NULL";
            case "equals":
            case "isAnyOf":
                List<String> numbers = splitCommas(values);
                for (String v : numbers) {
                    params.add(parseNumber(field, v));
                }
                return numbers.size() == 1 ? col + " = ?" : col + " IN (" + placeholders(numbers.size()) + ")";
            default:
                String sqlOp = comparison(op);
                params.add(parseNumber(field, values.get(0)));
                return col + " " + sqlOp + " ?";
        }
    }

    /**
     * A date-only value means the whole day ("on 2026-10-22" = any time that day; "after" it =
     * from the next day on); a value with a time is compared exactly.
     */
    private static String dateClause(String field, String col, String op, String value, List<Object> params) {
        if (op.equals("isEmpty")) {
            return col + " IS NULL";
        }
        if (op.equals("isNotEmpty")) {
            return col + " IS NOT NULL";
        }
        LocalDateTime dateTime = parseDateTime(value);
        if (dateTime != null) {
            if (op.equals("notEquals")) {
                params.add(Timestamp.valueOf(dateTime));
                return "(" + col + " IS NULL OR " + col + " <> ?)";
            }
            params.add(Timestamp.valueOf(dateTime));
            return col + " " + (op.equals("equals") ? "=" : comparison(op)) + " ?";
        }
        LocalDate day = parseDate(field, value);
        Timestamp dayStart = Timestamp.valueOf(day.atStartOfDay());
        Timestamp nextDayStart = Timestamp.valueOf(day.plusDays(1).atStartOfDay());
        switch (op) {
            case "equals":
                params.add(dayStart);
                params.add(nextDayStart);
                return "(" + col + " >= ? AND " + col + " < ?)";
            case "notEquals":
                params.add(dayStart);
                params.add(nextDayStart);
                return "(" + col + " IS NULL OR " + col + " < ? OR " + col + " >= ?)";
            case "gt":
                params.add(nextDayStart);
                return col + " >= ?";
            case "gte":
                params.add(dayStart);
                return col + " >= ?";
            case "lt":
                params.add(dayStart);
                return col + " < ?";
            case "lte":
                params.add(nextDayStart);
                return col + " < ?";
            default:
                throw new IllegalArgumentException("Unsupported date filter operator '" + op + "'.");
        }
    }

    /** Matches TAGs by the acceptance request numbers (tb_DCC_LN.dccId) whose lines carry them. */
    private static String requestNoClause(String op, List<String> values, List<Object> params) {
        String lines = "SELECT 1 FROM tb_DCC_LN ln WHERE ln.tagNumber = t.tagNumber";
        switch (op) {
            case "isEmpty":
                return "NOT EXISTS (" + lines + ")";
            case "isNotEmpty":
                return "EXISTS (" + lines + ")";
            case "equals":
            case "isAnyOf":
                // Driven from tb_DCC_LN's dccId index: the request's lines, then their TAGs.
                List<String> requestNos = splitCommas(values);
                for (String v : requestNos) {
                    params.add(String.valueOf(parseNumber("requestNo", v)));
                }
                return "t.tagNumber IN (SELECT ln.tagNumber FROM tb_DCC_LN ln WHERE ln.dccId IN ("
                        + placeholders(requestNos.size()) + "))";
            case "notEquals":
                params.add(String.valueOf(parseNumber("requestNo", values.get(0))));
                return "NOT EXISTS (" + lines + " AND ln.dccId = ?)";
            case "contains":
            case "startsWith":
            case "endsWith":
                return "EXISTS (" + lines + " AND " + textClause("ln.dccId", op, values, params) + ")";
            case "notContains":
                params.add("%" + escapeLike(values.get(0)) + "%");
                return "NOT EXISTS (" + lines + " AND ln.dccId LIKE ?)";
            default:
                throw new IllegalArgumentException("Unsupported Request No filter operator '" + op + "'.");
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    // The grid's own operator names, and the "Filter By" dialog's, mapped onto one set.
    private static final Map<String, String> OPERATOR_ALIASES = new HashMap<>();
    static {
        OPERATOR_ALIASES.put("=", "equals");
        OPERATOR_ALIASES.put("is", "equals");
        OPERATOR_ALIASES.put("!=", "notEquals");
        OPERATOR_ALIASES.put("not", "notEquals");
        OPERATOR_ALIASES.put("doesNotContain", "notContains");
        OPERATOR_ALIASES.put(">", "gt");
        OPERATOR_ALIASES.put("after", "gt");
        OPERATOR_ALIASES.put("greaterThan", "gt");
        OPERATOR_ALIASES.put(">=", "gte");
        OPERATOR_ALIASES.put("onOrAfter", "gte");
        OPERATOR_ALIASES.put("<", "lt");
        OPERATOR_ALIASES.put("before", "lt");
        OPERATOR_ALIASES.put("lessThan", "lt");
        OPERATOR_ALIASES.put("<=", "lte");
        OPERATOR_ALIASES.put("onOrBefore", "lte");
    }

    private static String normalizeOperator(String operator, String fallback) {
        if (operator == null || operator.trim().isEmpty()) {
            return fallback;
        }
        String op = operator.trim();
        return OPERATOR_ALIASES.getOrDefault(op, op);
    }

    private static String comparison(String op) {
        switch (op) {
            case "gt":
                return ">";
            case "gte":
                return ">=";
            case "lt":
                return "<";
            case "lte":
                return "<=";
            default:
                throw new IllegalArgumentException("Unsupported filter operator '" + op + "'.");
        }
    }

    /** A list value (isAnyOf) or one string. */
    private static List<String> values(Object value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value instanceof List) {
            for (Object o : (List<?>) value) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    out.add(String.valueOf(o).trim());
                }
            }
            return out;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? out : new ArrayList<>(Collections.singletonList(s));
    }

    /** "12, 15" typed into a number filter means either of them. */
    private static List<String> splitCommas(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            for (String part : v.split(",")) {
                if (!part.trim().isEmpty()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    private static long parseNumber(String field, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value + "' is not a valid number for " + field + ".");
        }
    }

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    };

    private static LocalDateTime parseDateTime(String value) {
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value.trim(), format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static LocalDate parseDate(String field, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + value + "' is not a valid date for " + field + ".");
        }
    }

    private static boolean isDate(Map<String, Column> columns, String field) {
        Column column = columns.get(field);
        return column != null && column.kind == Kind.DATE;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
