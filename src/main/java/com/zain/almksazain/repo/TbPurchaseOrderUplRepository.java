package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for tb_PurchaseOrderUPL entity.
 */
@Repository
public interface TbPurchaseOrderUplRepository extends JpaRepository<tb_PurchaseOrderUPL, Long> {
    List<tb_PurchaseOrderUPL> findByPoNumberIn(List<String> poNumbers);

    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumber(String poNumber, String poLineNumber);

    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndUplLine(String poNumber, String poLineNumber, String uplLine);
}