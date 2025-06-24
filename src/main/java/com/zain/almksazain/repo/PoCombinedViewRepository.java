package com.zain.almksazain.repo;

import com.zain.almksazain.model.POCombinedView;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoCombinedViewRepository extends JpaRepository<POCombinedView, Long>, JpaSpecificationExecutor<POCombinedView> {
    List<POCombinedView> findByRecordNoIn(List<Integer> recordNos);
// @Query(value = "SELECT * FROM dccPOCombinedView1 WHERE supplierId = :supplierId ORDER BY dccRecordNo DESC", nativeQuery = true)
// Page<POCombinedView> fetchBySupplierQuickPage(@Param("supplierId") String supplierId, Pageable pageable);

@Query(value = "SELECT * FROM dccPOCombinedCache WHERE supplierId = :supplierId ORDER BY dccRecordNo DESC", nativeQuery = true)
Page<POCombinedView> fetchBySupplierQuickPage(@Param("supplierId") String supplierId, Pageable pageable);
    

}