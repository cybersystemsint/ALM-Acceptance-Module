package com.zain.almksazain.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.CombinedPurchaseOrder;

public interface CombinedPurchaseOrderRepository extends JpaRepository<CombinedPurchaseOrder, Long> {
@Query(value = "SELECT * FROM vw_CombinedPurchaseOrder WHERE " +
            "(:supplierId = '0' OR poVendorNumber = :supplierId) " +
            "AND (:poId = '0' OR poNumber = :poId) " +
            "AND (:dateFrom IS NULL OR :dateTo IS NULL OR poCreatedDate BETWEEN :dateFrom AND :dateTo) " +
            "AND (:columnName IS NULL OR :searchQuery IS NULL OR " +
            "(:columnName = 'poNumber' AND poNumber LIKE CONCAT('%', :searchQuery, '%')) OR " +
            "(:columnName = 'poVendorName' AND poVendorName LIKE CONCAT('%', :searchQuery, '%')))",
            countQuery = "SELECT COUNT(*) FROM vw_CombinedPurchaseOrder WHERE " +
                    "(:supplierId = '0' OR poVendorNumber = :supplierId) " +
                    "AND (:poId = '0' OR poNumber = :poId) " +
                    "AND (:dateFrom IS NULL OR :dateTo IS NULL OR poCreatedDate BETWEEN :dateFrom AND :dateTo) " +
                    "AND (:columnName IS NULL OR :searchQuery IS NULL OR " +
                    "(:columnName = 'poNumber' AND poNumber LIKE CONCAT('%', :searchQuery, '%')) OR " +
                    "(:columnName = 'poVendorName' AND poVendorName LIKE CONCAT('%', :searchQuery, '%')))",
            nativeQuery = true)
    Page<CombinedPurchaseOrder> findPurchaseOrders(
            @Param("supplierId") String supplierId,
            @Param("poId") String poId,
            @Param("dateFrom") String dateFrom,
            @Param("dateTo") String dateTo,
            @Param("columnName") String columnName,
            @Param("searchQuery") String searchQuery,
            Pageable pageable);

            
}

