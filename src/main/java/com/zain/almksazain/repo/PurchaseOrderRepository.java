package com.zain.almksazain.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.zain.almksazain.model.PurchaseOrderTb;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderTb, String> {
    @Query("SELECT DISTINCT p.poNumber FROM PurchaseOrderTb p WHERE (:supplierId = '0' OR p.vendorNumber = :supplierId) AND (:poNumber = '0' OR p.poNumber = :poNumber)")
    Page<String> findDistinctPoNumbers(@Param("supplierId") String supplierId,
                                       @Param("poNumber") String poNumber,
                                       Pageable pageable);

    List<PurchaseOrderTb> findByPoNumberIn(List<String> poNumbers);
}
