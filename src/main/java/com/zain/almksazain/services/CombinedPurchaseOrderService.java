package com.zain.almksazain.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.zain.almksazain.dto.CombinedPurchaseOrderRequest;
import com.zain.almksazain.dto.CombinedPurchaseOrderResponse;
import com.zain.almksazain.model.CombinedPurchaseOrder;
import com.zain.almksazain.repo.CombinedPurchaseOrderRepository;
import com.zain.almksazain.spec.CombinedPurchaseOrderSpecification;

@Service
public class CombinedPurchaseOrderService {


    @Autowired
    private CombinedPurchaseOrderRepository repository;

    public CombinedPurchaseOrderResponse search(CombinedPurchaseOrderRequest request) {
        
        int page = request.getPage() != null ? Math.max(request.getPage(), 1) - 1 : 0;
        int size = request.getSize() != null ? Math.max(request.getSize(), 1) : 1;
        Pageable pageable = PageRequest.of(page, size, Sort.by("poRecordNo").ascending());

        Specification<CombinedPurchaseOrder> spec = CombinedPurchaseOrderSpecification.filter(
                request.getSupplierId(),
                request.getPoId(),
                request.getColumnName(),
                request.getSearchQuery(),
                request.getDateFrom(),
                request.getDateTo()
        );

        Page<CombinedPurchaseOrder> result = repository.findAll(spec, pageable);
        return new CombinedPurchaseOrderResponse(
                result.getTotalElements(),
                result.getContent(),
                result.getTotalPages(),
                result.getSize(),
                result.getNumber() + 1
        );
    }
}
