package com.zain.almksazain.specs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.tbPurchaseOrder;

/**
 * Database-side narrowing for the aging report grids' filters.
 *
 * Aging rows are built in Java (DccPoCombinedService.buildGroupedRow) and filtered
 * there by matchesFilter / matchesDateRanges. Without help, a filtered request had
 * to build a row for every DCC before it could filter. The predicates here are
 * necessary conditions of those Java filters, applied to tb_DCC (and the tables a
 * row's values come from) before anything is loaded, so only DCCs that could
 * possibly match get built.
 *
 * Every predicate may let through more DCCs than finally match, never fewer: the
 * Java filter still runs on what is loaded and stays the source of truth. Columns
 * computed in Java (aging, approval count, pending approver, amounts, approved
 * date) have no predicate here and are only filtered in Java.
 */
public final class AgingReportDccSpecifications {

    // Same format and default locale as the row values (DccPoCombinedService.DATE_FORMAT).
    private static final String ROW_DATE_FORMAT = "d-MMM-yyyy";
    private static final String RANGE_DATE_FORMAT = "yyyy-MM-dd";
    private static final char LIKE_ESCAPE = '\\';

    private AgingReportDccSpecifications() {
    }

    /**
     * DCCs in the report's base scope: the status rule of the report and, for a
     * vendor, only DCCs on that vendor's POs (same rule as the repository's
     * findAllBySupplierVendorAndStatus / ...StatusNot queries).
     *
     * @param onlyStatus true for "status = status" (Aging Report), false for
     *                   "status <> status" (Full Aging Report)
     */
    public static Specification<DCC> inReportScope(String supplierId, String status, boolean onlyStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Expression<String> rowStatus = cb.lower(root.get("status"));
            String lowerStatus = status.toLowerCase();
            predicates.add(onlyStatus ? cb.equal(rowStatus, lowerStatus) : cb.notEqual(rowStatus, lowerStatus));

            if (supplierId != null && !"0".equals(supplierId.trim())) {
                Subquery<String> vendorPos = query.subquery(String.class);
                Root<tbPurchaseOrder> po = vendorPos.from(tbPurchaseOrder.class);
                vendorPos.select(po.get("poNumber")).where(cb.equal(po.get("vendorNumber"), supplierId));
                predicates.add(root.get("poNumber").in(vendorPos));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Narrowing for the given filters, or null when none of them can be checked in
     * the database (the caller then needs the full row set).
     *
     * @param regularFilters filter key -> search text, as passed to matchesFilter
     * @param rangeFilters   "<dateColumn>Start"/"<dateColumn>End" -> ISO date
     * @param rowKeyOf       resolves a filter key to the row key matchesFilter reads
     *                       (the same COLUMN_MAPPINGS lookup)
     */
    public static Specification<DCC> narrowing(Map<String, String> regularFilters, Map<String, String> rangeFilters,
            Function<String, String> rowKeyOf) {

        List<Specification<DCC>> parts = new ArrayList<>();

        for (Map.Entry<String, String> filter : regularFilters.entrySet()) {
            String search = filter.getValue() == null ? "" : filter.getValue().trim();
            if (search.isEmpty()) {
                continue;
            }
            Specification<DCC> part = forColumn(rowKeyOf.apply(filter.getKey()), search);
            if (part != null) {
                parts.add(part);
            }
        }

        for (Map.Entry<String, String> bound : rangeFilters.entrySet()) {
            String value = bound.getValue() == null ? "" : bound.getValue().trim();
            if (value.isEmpty()) {
                continue;
            }
            String key = bound.getKey();
            boolean isStart = key.endsWith("Start");
            String base = key.substring(0, key.length() - (isStart ? "Start".length() : "End".length()));
            Specification<DCC> part = forDateBound(rowKeyOf.apply(base), value, isStart);
            if (part != null) {
                parts.add(part);
            }
        }

        if (parts.isEmpty()) {
            return null;
        }
        Specification<DCC> combined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            combined = combined.and(parts.get(i));
        }
        return combined;
    }

    // One predicate per row key whose value comes straight from a table column.
    // Keys not listed are computed in Java and left to matchesFilter.
    private static Specification<DCC> forColumn(String rowKey, String search) {
        switch (rowKey) {
            // matchesFilter: case-insensitive "contains"
            case "poNumber":
            case "poId":            // the PO looked up by the DCC's own poNumber
                return (root, query, cb) -> contains(cb, root.get("poNumber"), search);
            case "vendorName":
            case "vendorEmail":
            case "vendorComment":
                return (root, query, cb) -> contains(cb, root.get(rowKey), search);
            case "projectName":
                return (root, query, cb) -> projectNameContains(root, query, cb, search);
            case "createdBy":
            case "createdByName":
            case "requestedBy":     // all three hold the creator's full name
                return (root, query, cb) -> {
                    Subquery<String> creators = query.subquery(String.class);
                    Root<User> user = creators.from(User.class);
                    creators.select(user.get("username")).where(contains(cb, user.get("fullName"), search));
                    return root.get("createdBy").in(creators);
                };
            case "lnLocationName":
                return (root, query, cb) -> withLineItem(root, query, cb,
                        li -> contains(cb, li.get("locationName"), search));
            case "lnScopeOfWork":
                return (root, query, cb) -> withLineItem(root, query, cb,
                        li -> contains(cb, li.get("scopeOfWork"), search));

            // matchesFilter: case-insensitive exact match on the trimmed value
            case "dccStatus":
                return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("status"))), search.toLowerCase());
            case "dccAcceptanceType":
                return (root, query, cb) -> cb.equal(cb.lower(cb.trim(root.get("acceptanceType"))), search.toLowerCase());
            case "dccId":
                return (root, query, cb) -> dccIdEquals(root, cb, search);

            // matchesFilter: numeric, within 0.001
            case "recordNo":
                return (root, query, cb) -> recordNoNear(root, cb, search);

            // matchesFilter: exact day ("d-MMM-yyyy")
            case "dccCreatedDate":
                return (root, query, cb) -> onDay(cb, root.get("createdDate"), search);
            case "lnInserviceDate":
                return (root, query, cb) -> withLineItem(root, query, cb,
                        li -> onDay(cb, li.get("dateInService"), search));

            default:
                return null;
        }
    }

    // matchesDateRange: the row's day is within [start, end], bounds as ISO dates.
    private static Specification<DCC> forDateBound(String rowKey, String isoDate, boolean isStart) {
        switch (rowKey) {
            case "dccCreatedDate":
                return (root, query, cb) -> dateBound(cb, root.get("createdDate"), isoDate, isStart);
            case "lnInserviceDate":
                return (root, query, cb) -> withLineItem(root, query, cb,
                        li -> dateBound(cb, li.get("dateInService"), isoDate, isStart));
            default:
                return null;
        }
    }

    private static Predicate contains(CriteriaBuilder cb, Path<String> column, String search) {
        String escaped = search.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(column), "%" + escaped + "%", LIKE_ESCAPE);
    }

    // determineProjectName: the PO's newProjectName, else its projectName, else
    // (no PO) the DCC's projectName - so a match is on one of those three.
    private static Predicate projectNameContains(Root<DCC> root, javax.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb, String search) {
        Subquery<String> matchingPos = query.subquery(String.class);
        Root<tbPurchaseOrder> po = matchingPos.from(tbPurchaseOrder.class);
        matchingPos.select(po.get("poNumber")).where(cb.or(
                contains(cb, po.get("newProjectName"), search),
                contains(cb, po.get("projectName"), search)));
        return cb.or(contains(cb, root.get("projectName"), search), root.get("poNumber").in(matchingPos));
    }

    // The row's line-item columns come from one of the DCC's line items; requiring
    // that some line item matches is the necessary condition.
    private static Predicate withLineItem(Root<DCC> root, javax.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb, Function<Root<DCCLineItem>, Predicate> lineCondition) {
        Subquery<String> matchingDccIds = query.subquery(String.class);
        Root<DCCLineItem> li = matchingDccIds.from(DCCLineItem.class);
        matchingDccIds.select(li.get("dccId")).where(lineCondition.apply(li));
        return root.get("recordNo").as(String.class).in(matchingDccIds);
    }

    // Row dccId is the DCC's dccId, or its recordNo when dccId is null.
    private static Predicate dccIdEquals(Root<DCC> root, CriteriaBuilder cb, String search) {
        Predicate byDccId = cb.equal(cb.lower(cb.trim(root.get("dccId"))), search.toLowerCase());
        try {
            long recordNo = Long.parseLong(search);
            return cb.or(byDccId, cb.and(cb.isNull(root.get("dccId")), cb.equal(root.get("recordNo"), recordNo)));
        } catch (NumberFormatException e) {
            return byDccId;
        }
    }

    private static Predicate recordNoNear(Root<DCC> root, CriteriaBuilder cb, String search) {
        double target;
        try {
            target = Double.parseDouble(search);
        } catch (NumberFormatException e) {
            return cb.disjunction();    // matchesFilter rejects every row too
        }
        long low = (long) Math.ceil(target - 0.001);
        long high = (long) Math.floor(target + 0.001);
        return low > high ? cb.disjunction() : cb.between(root.get("recordNo"), low, high);
    }

    // Row dates are formatted from the column in the JVM's time zone, while the
    // database compares in its own. The window is widened by a day on each side
    // so a zone difference can't drop a match; the Java filter trims the extra.
    private static Predicate onDay(CriteriaBuilder cb, Path<Date> column, String search) {
        Date day = parseStrict(search, ROW_DATE_FORMAT);
        if (day == null) {
            return cb.disjunction();    // not a date: matchesFilter rejects every row too
        }
        return cb.and(cb.greaterThanOrEqualTo(column, addDays(day, -1)), cb.lessThan(column, addDays(day, 2)));
    }

    private static Predicate dateBound(CriteriaBuilder cb, Path<Date> column, String isoDate, boolean isStart) {
        // Lenient, like matchesDateRange's own parse of the bound.
        Date day = parse(isoDate, RANGE_DATE_FORMAT, true);
        if (day == null) {
            return cb.disjunction();    // matchesDateRange rejects every row on a bad bound
        }
        return isStart
                ? cb.greaterThanOrEqualTo(column, addDays(day, -1))
                : cb.lessThan(column, addDays(day, 2));
    }

    // Strict, like formatSearchForDateComparison's parse of the search text.
    private static Date parseStrict(String value, String pattern) {
        return parse(value, pattern, false);
    }

    private static Date parse(String value, String pattern, boolean lenient) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(lenient);
            return format.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
