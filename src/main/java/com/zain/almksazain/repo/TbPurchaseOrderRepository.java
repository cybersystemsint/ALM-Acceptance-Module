package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbPurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface TbPurchaseOrderRepository extends JpaRepository<tbPurchaseOrder, Long> {
    List<tbPurchaseOrder> findByPoNumber(String poNumber);
    List<tbPurchaseOrder> findByPoNumberIn(Set<String> poNumbers);
}