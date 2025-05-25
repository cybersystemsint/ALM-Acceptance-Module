package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface TbPurchaseOrderUplRepository extends JpaRepository<tb_PurchaseOrderUPL, Long> {
    List<tb_PurchaseOrderUPL> findByPoNumber(String poNumber);
    List<tb_PurchaseOrderUPL> findByPoNumberIn(Set<String> poNumbers);

    List<tb_PurchaseOrderUPL> findByUplLineQuantityGreaterThan(double uplLineQuantity);
}