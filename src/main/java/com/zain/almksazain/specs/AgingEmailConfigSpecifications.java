package com.zain.almksazain.specs;

import com.zain.almksazain.dto.FilterRequestDto;
import com.zain.almksazain.model.AgingEmailConfig;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.*;
import java.util.stream.Collectors;


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
                Expression<String> exp = root.get(field).as(String.class);

                switch (operator) {
                    case "equals":
                        if (value == null) {
                            return cb.isNull(root.get(field));
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
                        return cb.or(cb.isNull(root.get(field)), cb.equal(exp, ""));
                    case "isnotempty":
                    case "isNotEmpty":
                        return cb.and(cb.isNotNull(root.get(field)), cb.notEqual(exp, ""));
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
                            if (value == null) return cb.isNull(root.get(field));
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
}
