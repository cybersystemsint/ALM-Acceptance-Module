package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	List<PurchaseOrder> findByPoNumberAndVendorNumber(String poId, String supplierId);

	List<PurchaseOrder> findByPoNumber(String PoNumber);

	PurchaseOrder findByRecordNo(long recordNo);

	@Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrder findTopByPoNumber(@Param("poNumber") String poNumber);

	@Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber AND d.lineNumber = :lineNumber  ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrder findTopByPoNumberAndLineNumber(@Param("poNumber") String poNumber,
			@Param("lineNumber") String lineNumber);

	@Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber AND d.lineNumber = :lineNumber AND d.releaseNum = :releaseNum  ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrder findTopByPoNumberAndLineNumberAndReleaseNum(@Param("poNumber") String poNumber,
			@Param("lineNumber") String lineNumber, @Param("releaseNum") String releaseNum);

}