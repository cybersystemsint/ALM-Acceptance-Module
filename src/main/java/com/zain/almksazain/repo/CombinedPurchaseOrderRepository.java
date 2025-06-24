package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.zain.almksazain.model.CombinedPurchaseOrder;

@Repository
public interface CombinedPurchaseOrderRepository extends JpaRepository<CombinedPurchaseOrder, Long>, JpaSpecificationExecutor<CombinedPurchaseOrder> {

}

