package com.zain.almksazain.specs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.zain.almksazain.dto.FilterRequestDto;
import com.zain.almksazain.model.AgingEmailConfig;


public final class AgingEmailConfigSpecifications {

    private AgingEmailConfigSpecifications() {}

    public static Specification<AgingEmailConfig> buildFromRequest(FilterRequestDto req) {
        Specification<AgingEmailConfig> spec = Specification.where(null);

        // quick single-column search
        if (req.getColumnName() != null && req.getSearchQuery() != null && !req.getSearchQuery().isBlank()) {
            String operator = Optional.ofNullable(req.getSearchOperator()).orElse("contains");
            spec = spec.and(singleFilterSpec(req.getColumnName(), operator, req.getSearchQuery()));
        }

        // multi-filters
        if (req.getFilterBy() != null && !req.getFilterBy().isEmpty()) {
            for (Map.Entry<String, FilterRequestDto.FilterDto> e : req.getFilterBy().entrySet()) {
                String field = e.getKey();
                FilterRequestDto.FilterDto dto = e.getValue();
                if (dto == null) continue;
                String operator = Optional.ofNullable(dto.getOperator()).orElse("contains");
                Object value = dto.getValue();
                spec = spec.and(singleFilterSpec(field, operator, value));
            }
        }

        return spec;
    }

    private static Specification<AgingEmailConfig> singleFilterSpec(String field, String operatorRaw, Object value) {
        String operator = operatorRaw == null ? "contains" : operatorRaw.trim().toLowerCase();

        return (Root<AgingEmailConfig> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            try {
                String normalized = normalizeFieldName(field);

                // Special handling for userAging numeric column
                if ("userAging".equalsIgnoreCase(normalized)) {
                    Path<?> path = root.get(normalized);

                    switch (operator) {
                        case "equals":
                            if (value == null) {
                                return cb.isNull(path);
                            } else {
                                Number n = tryParseNumber(value);
                                if (n != null) {
                                    return cb.equal(path.as(Number.class), n);
                                } else {
                                    // fallback to string comparison if value not numeric
                                    Expression<String> exp = path.as(String.class);
                                    return cb.equal(cb.lower(exp), value.toString().toLowerCase());
                                }
                            }
                        case "isanyof":
                        case "isAnyOf":
                            if (value instanceof Collection) {
                                Collection<?> col = (Collection<?>) value;
                                List<Predicate> ors = col.stream()
                                        .filter(Objects::nonNull)
                                        .map(v -> {
                                            Number n = tryParseNumber(v);
                                            if (n != null) return cb.equal(path.as(Number.class), n);
                                            // fallback to string equality
                                            return cb.equal(cb.lower(path.as(String.class)), v.toString().toLowerCase());
                                        })
                                        .collect(Collectors.toList());
                                if (ors.isEmpty()) return cb.disjunction();
                                return cb.or(ors.toArray(new Predicate[0]));
                            } else if (value != null && value.getClass().isArray()) {
                                Object[] arr = (Object[]) value;
                                List<Predicate> ors = Arrays.stream(arr)
                                        .filter(Objects::nonNull)
                                        .map(v -> {
                                            Number n = tryParseNumber(v);
                                            if (n != null) return cb.equal(path.as(Number.class), n);
                                            return cb.equal(cb.lower(path.as(String.class)), v.toString().toLowerCase());
                                        })
                                        .collect(Collectors.toList());
                                if (ors.isEmpty()) return cb.disjunction();
                                return cb.or(ors.toArray(new Predicate[0]));
                            } else {
                                if (value == null) return cb.isNull(path);
                                Number n = tryParseNumber(value);
                                if (n != null) return cb.equal(path.as(Number.class), n);
                                return cb.equal(cb.lower(path.as(String.class)), value.toString().toLowerCase());
                            }
                        case "isempty":
                        case "isEmpty":
                            return cb.or(cb.isNull(path), cb.equal(path.as(String.class), ""));
                        case "isnotempty":
                        case "isNotEmpty":
                            return cb.and(cb.isNotNull(path), cb.notEqual(path.as(String.class), ""));
                        case "startswith":
                        case "startsWith":
                            if (value == null) return cb.disjunction();
                            return cb.like(cb.lower(path.as(String.class)), escapeLike(value.toString().toLowerCase()) + "%");
                        case "endswith":
                        case "endsWith":
                            if (value == null) return cb.disjunction();
                            return cb.like(cb.lower(path.as(String.class)), "%" + escapeLike(value.toString().toLowerCase()));
                        case "contains":
                        default:
                            if (value == null) return cb.disjunction();
                            return cb.like(cb.lower(path.as(String.class)), "%" + escapeLike(value.toString().toLowerCase()) + "%");
                    }
                }

                Expression<String> exp = root.get(normalized).as(String.class);

                // Special handling for department column which may store:
                if ("department".equalsIgnoreCase(normalized)) {
                    Path<String> path = root.get("department");
                    java.util.function.Function<Collection<String>, Predicate> buildPredicateForCollection = (coll) -> {
                        List<Predicate> topOrs = new ArrayList<>();
                        for (String rawVal : coll) {
                            if (rawVal == null) continue;
                            String vtrim = rawVal.trim();
                            if (vtrim.isEmpty()) continue;

                            // NOTE: removed special-case for "ALL" here so "ALL" is treated
                            // like a normal value and only matches rows that actually contain "ALL".

                            String v = vtrim.toLowerCase();

                            List<Predicate> checks = new ArrayList<>();
                            checks.add(cb.equal(cb.lower(path), v));
                            checks.add(cb.like(cb.lower(path), "%\"" + escapeLike(v) + "\"%"));
                            Expression<String> withCommas = cb.concat(",", cb.concat(cb.lower(path), ","));
                            checks.add(cb.like(withCommas, "%," + escapeLike(v) + ",%"));
                            checks.add(cb.like(cb.lower(path), "%" + escapeLike(v) + "%"));

                            topOrs.add(cb.or(checks.toArray(new Predicate[0])));
                        }
                        if (topOrs.isEmpty()) return cb.disjunction();
                        return cb.or(topOrs.toArray(new Predicate[0]));
                    };

                    switch (operator) {
                        case "equals":
                            if (value == null) return cb.isNull(path);
                            return buildPredicateForCollection.apply(List.of(value.toString()));
                        case "isanyof":
                        case "isAnyOf":
                            if (value instanceof Collection) {
                                @SuppressWarnings("unchecked")
                                Collection<Object> col = (Collection<Object>) value;
                                List<String> vals = col.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList());
                                return buildPredicateForCollection.apply(vals);
                            } else if (value != null && value.getClass().isArray()) {
                                Object[] arr = (Object[]) value;
                                List<String> vals = Arrays.stream(arr).filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList());
                                return buildPredicateForCollection.apply(vals);
                            } else {
                                if (value == null) return cb.isNull(path);
                                return buildPredicateForCollection.apply(List.of(value.toString()));
                            }
                        case "isempty":
                        case "isEmpty":
                            return cb.or(cb.isNull(path), cb.equal(cb.trim(path), ""));
                        case "isnotempty":
                        case "isNotEmpty":
                            return cb.and(cb.isNotNull(path), cb.notEqual(cb.trim(path), ""));
                        case "contains":
                        default:
                            if (value == null) return cb.disjunction();
                            return buildPredicateForCollection.apply(List.of(value.toString()));
                    }
                }

                switch (operator) {
                    case "equals":
                        if (value == null) {
                            return cb.isNull(root.get(normalized));
                        } else {
                            return cb.equal(cb.lower(exp), value.toString().toLowerCase());
                        }
                    case "startswith":
                    case "startsWith":
                        if (value == null) return cb.disjunction();
                        return cb.like(cb.lower(exp), escapeLike(value.toString().toLowerCase()) + "%");
                    case "endswith":
                    case "endsWith":
                        if (value == null) return cb.disjunction();
                        return cb.like(cb.lower(exp), "%" + escapeLike(value.toString().toLowerCase()));
                    case "isempty":
                    case "isEmpty":
                        return cb.or(cb.isNull(root.get(normalized)), cb.equal(exp, ""));
                    case "isnotempty":
                    case "isNotEmpty":
                        return cb.and(cb.isNotNull(root.get(normalized)), cb.notEqual(exp, ""));
                    case "isanyof":
                    case "isAnyOf":
                        if (value instanceof Collection) {
                            Collection<?> col = (Collection<?>) value;
                            List<Predicate> ors = col.stream()
                                    .filter(Objects::nonNull)
                                    .map(v -> cb.equal(cb.lower(exp), v.toString().toLowerCase()))
                                    .collect(Collectors.toList());
                            if (ors.isEmpty()) return cb.disjunction();
                            return cb.or(ors.toArray(new Predicate[0]));
                        } else if (value != null && value.getClass().isArray()) {
                            Object[] arr = (Object[]) value;
                            List<Predicate> ors = Arrays.stream(arr)
                                    .filter(Objects::nonNull)
                                    .map(v -> cb.equal(cb.lower(exp), v.toString().toLowerCase()))
                                    .collect(Collectors.toList());
                            if (ors.isEmpty()) return cb.disjunction();
                            return cb.or(ors.toArray(new Predicate[0]));
                        } else {
                            if (value == null) return cb.isNull(root.get(normalized));
                            return cb.equal(cb.lower(exp), value.toString().toLowerCase());
                        }
                    case "contains":
                    default:
                        if (value == null) return cb.disjunction();
                        return cb.like(cb.lower(exp), "%" + escapeLike(value.toString().toLowerCase()) + "%");
                }
            } catch (IllegalArgumentException iae) {
                return cb.conjunction();
            } catch (Exception ex) {
                return cb.conjunction();
            }
        };
    }

    // Escape SQL LIKE special characters
    private static String escapeLike(String input) {
        if (input == null) return null;
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Number tryParseNumber(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return (Number) v;
        try {
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            if (s.contains(".")) return Double.parseDouble(s);
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeFieldName(String incoming) {
        if (incoming == null) return "";
        String key = incoming.trim();
        // Accept multiple aliases that should map to the 'department' DB column
        if ("departmentName".equalsIgnoreCase(key) || "deptName".equalsIgnoreCase(key)
                || "departmentsList".equalsIgnoreCase(key) || "departments".equalsIgnoreCase(key)
                || "department_list".equalsIgnoreCase(key)) return "department";
        if ("userAgingInDays".equalsIgnoreCase(key) || "user_aging_in_days".equalsIgnoreCase(key) || "user_aging".equalsIgnoreCase(key))
            return "userAging";
        return key;
    }
}