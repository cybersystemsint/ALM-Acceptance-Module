package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbPurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for tbPurchaseOrder entity.
 */
@Repository
public interface TbPurchaseOrderRepository extends JpaRepository<tbPurchaseOrder, Long> {
    List<tbPurchaseOrder> findByPoNumberIn(List<String> poNumbers);
}