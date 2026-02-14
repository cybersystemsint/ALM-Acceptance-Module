package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrderUPL;

public interface PurchaseOrderUPLRepository extends JpaRepository<PurchaseOrderUPL, Long> {

	// List<PurchaseOrderUPL> findByPoNumberAndVendorNumber(String poId, String
	// supplierId);
	// PurchaseOrderUPL findByPoNumber(String PoNumber);

	PurchaseOrderUPL findByRecordNo(long recordNo);

	List<PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndUplLine(String PoNumber, String PoLineNumber,
			String UplLine);

	@Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrderUPL findTopByPoNumber(@Param("poNumber") String poNumber);

	@Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrderUPL findTopByPoNumberAndUplLine(@Param("poNumber") String poNumber, @Param("uplLine") String uplLine);

	@Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND  d.poLineNumber = :poLineNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	PurchaseOrderUPL findTopByPoNumberAndPoLineNumberAndUplLine(@Param("poNumber") String poNumber,
			@Param("poLineNumber") String poLineNumber, @Param("uplLine") String uplLine);

//    //NEW CODE 
//    List<PurchaseOrderUPL> findByPoNumber(String poNumber);

}