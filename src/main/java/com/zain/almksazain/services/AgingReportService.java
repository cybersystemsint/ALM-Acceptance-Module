package com.zain.almksazain.services;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import com.zain.almksazain.dto.AgingReportItem;
import com.zain.almksazain.dto.AgingReportRequest;
import com.zain.almksazain.dto.AgingReportResponse;
import com.zain.almksazain.model.POCombinedView;
import com.zain.almksazain.repo.PoCombinedViewRepository;



@Service
public class AgingReportService {
    
    @Autowired
    private PoCombinedViewRepository poCombinedViewRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    
    
public AgingReportResponse getAgingReport(AgingReportRequest request) {
    int page = Math.max(request.getPage(), 1);
    int size = Math.max(request.getSize(), 1);

    boolean hasSearch = StringUtils.hasText(request.getColumnName()) && StringUtils.hasText(request.getSearchQuery());
    boolean hasSupplier = StringUtils.hasText(request.getSupplierId()) && !"0".equals(request.getSupplierId());

    if (hasSupplier && !hasSearch) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("dccRecordNo").descending());
        Page<POCombinedView> results = poCombinedViewRepository.fetchBySupplierQuickPage(
            request.getSupplierId(), pageable
        );
        List<AgingReportItem> items = groupAndConvertToItems(results.getContent());

        AgingReportResponse response = new AgingReportResponse();
        response.setData(items);
        response.setCurrentPage(page);
        response.setPageSize(size);
        response.setTotalRecords(results.getTotalElements());
        response.setTotalPages(results.getTotalPages());
        return response;
    }

    // Fallback to dynamic Criteria + Specification
    Specification<POCombinedView> spec = buildSpecification(request);
    long totalUniqueRecords = countUniqueRecords(spec);
    int totalPages = (int) Math.ceil((double) totalUniqueRecords / size);
    List<String> uniqueRecordNumbers = getUniqueRecordNumbers(spec, request);

    if (uniqueRecordNumbers.isEmpty()) {
        return createEmptyResponse(request, totalUniqueRecords, totalPages);
    }

    List<POCombinedView> allRowsForUniqueRecords = fetchRowsForUniqueRecords(uniqueRecordNumbers, spec);
    List<AgingReportItem> items = groupAndConvertToItems(allRowsForUniqueRecords);

    AgingReportResponse response = new AgingReportResponse();
    response.setData(items);
    response.setCurrentPage(page);
    response.setPageSize(size);
    response.setTotalRecords(totalUniqueRecords);
    response.setTotalPages(totalPages);
    return response;
}


    
    private Long countUniqueRecords(Specification<POCombinedView> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<POCombinedView> root = countQuery.from(POCombinedView.class);
                countQuery.select(cb.countDistinct(root.get("recordNo")));
                if (spec != null) {
            Predicate predicate = spec.toPredicate(root, countQuery, cb);
            if (predicate != null) {
                countQuery.where(predicate);
            }
        }
        
        return entityManager.createQuery(countQuery).getSingleResult();
    }
    
    private List<String> getUniqueRecordNumbers(Specification<POCombinedView> spec, AgingReportRequest request) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<POCombinedView> root = query.from(POCombinedView.class);
        query.select(root.get("recordNo")).distinct(true);
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }
        TypedQuery<String> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((request.getPage() - 1) * request.getSize());
        typedQuery.setMaxResults(request.getSize());
        
        return typedQuery.getResultList();
    }
    
    private List<POCombinedView> fetchRowsForUniqueRecords(List<String> uniqueRecordNumbers, Specification<POCombinedView> originalSpec) {
        if (uniqueRecordNumbers.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Create specification to fetch all rows for the unique record numbers
        Specification<POCombinedView> recordNumberSpec = (root, query, criteriaBuilder) -> 
            root.get("recordNo").in(uniqueRecordNumbers);
        
        // Combine with original specification
        Specification<POCombinedView> combinedSpec = originalSpec != null ? 
            Specification.where(originalSpec).and(recordNumberSpec) : recordNumberSpec;
        
        return poCombinedViewRepository.findAll(combinedSpec);
    }
    
    private Specification<POCombinedView> buildSpecification(AgingReportRequest request) {
        return Specification.where(buildSupplierSpec(request.getSupplierId()))
                            .and(buildSearchSpec(request.getColumnName(), request.getSearchQuery()));
    }

 private Specification<POCombinedView> buildSupplierSpec(String supplierId) {
    return (root, query, criteriaBuilder) -> {
        if (supplierId == null || "0".equalsIgnoreCase(supplierId)) {
            // Fallback condition that limits full table scan and helps with performance
            return criteriaBuilder.gt(root.get("recordNo"), 0);
        }
        return criteriaBuilder.equal(root.get("supplierId"), supplierId);
    };
}

    private Specification<POCombinedView> buildSearchSpec(String columnName, String searchQuery) {
        return (root, query, criteriaBuilder) -> {
            if (columnName == null || columnName.isEmpty() || 
                searchQuery == null || searchQuery.isEmpty()) {
                return criteriaBuilder.conjunction(); 
            }
            
            String fixedColumnName = mapColumnName(columnName);
            String searchPattern = "%" + searchQuery + "%";
            
            return criteriaBuilder.like(
                criteriaBuilder.lower(root.get(fixedColumnName).as(String.class)), 
                searchPattern.toLowerCase()
            );
        };
    }

private String mapColumnName(String columnName) {
    switch (columnName.toLowerCase()) {
        case "recordno":
            return "recordNo"; 
        case "projectname":
            return "projectName";
        case "vendorname":
            return "vendorName"; 
        default:
            return columnName;
    }
}

    
  private List<AgingReportItem> groupAndConvertToItems(List<POCombinedView> allRows) {
    Map<Long, List<POCombinedView>> groupedByRecordNo = allRows.stream()
        .collect(Collectors.groupingBy(
            view -> view.getRecordNo(),      
            LinkedHashMap::new,             
            Collectors.toList()
        ));
        return groupedByRecordNo.values().stream()
        .map(group -> convertToAgingReportItem(group.get(0)))
        .collect(Collectors.toList());
}

    
    private AgingReportResponse createEmptyResponse(AgingReportRequest request, long totalRecords, int totalPages) {
        AgingReportResponse response = new AgingReportResponse();
        response.setData(new ArrayList<>());
        response.setCurrentPage(request.getPage());
        response.setPageSize(request.getSize());
        response.setTotalRecords(totalRecords);
        response.setTotalPages(totalPages);
        return response;
    }
    

private AgingReportItem convertToAgingReportItem(POCombinedView po) {
    AgingReportItem item = new AgingReportItem();

    // Prefer newProjectName if available
  String effectiveProjectName = StringUtils.hasText(po.getNewProjectName()) 
    ? po.getNewProjectName() 
    : po.getProjectName();

item.setPoNumber(po.getPoNumber());
item.setNewProjectName(po.getNewProjectName());         
item.setProjectName(effectiveProjectName);  
    item.setDccAcceptanceType(po.getDccAcceptanceType());
    item.setDccStatus(po.getDccStatus());
    item.setDccCreatedDate(po.getDccCreatedDate());
    item.setDateApproved(po.getDateApproved());
    item.setLnLocationName(po.getLnLocationName());
    item.setLnInserviceDate(po.getLnInserviceDate());
    item.setLnScopeOfWork(po.getLnScopeOfWork());
    item.setPoId(po.getPoId());
    item.setUplacptRequestValue(po.getUplacptRequestValue());
    item.setSupplierId(po.getSupplierId());
    item.setVendorName(po.getVendorName());
    item.setVendorEmail(po.getVendorEmail());
    item.setVendorNumber(po.getSupplierId()); 
    item.setCreatedBy(po.getCreatedBy());
    item.setCreatedByName(po.getCreatedByName());
    item.setApprovalCount(po.getApprovalCount());
    item.setPendingApprovers(po.getPendingApprovers());
    item.setApproverComment(po.getApproverComment());
    item.setUserAging(po.getUserAging());
    item.setTotalAging(po.getTotalAging());
    item.setRecordNo(po.getRecordNo());
    // item.setDepartmentName(po.getDepartmentName());
    item.setVendorComment(po.getVendorComment());
    item.setUserAgingInDays(extractDaysFromAging(po.getUserAging()));
    item.setTotalAgingInDays(extractDaysFromAging(po.getTotalAging()));

    return item;
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

}