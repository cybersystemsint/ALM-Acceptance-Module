package com.zain.ksa.alm.acceptance.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zain.ksa.alm.acceptance.entity.DCCLineItem;

@Repository
public interface DCCLineItemRepository extends JpaRepository<DCCLineItem, Long> {

	DCCLineItem findByRecordNo(long recordNo);

	List<DCCLineItem> findBySerialNumberAndItemCode(String serialNumber, String itemCode);

	List<DCCLineItem> findBySerialNumber(String serialNumber);

	List<DCCLineItem> findByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);

	List<DCCLineItem> findBySerialNumberAndActualItemCode(String serialNumber, String actualItemCode);

	@Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber and d.itemCode = :itemCode ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	DCCLineItem findTopBySerialNumberAndItemCode(@Param("serialNumber") String serialNumber,
			@Param("itemCode") String itemCode);

	@Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber and d.actualItemCode = :actualItemCode ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	DCCLineItem findTopBySerialNumberAndActualItemCode(@Param("serialNumber") String serialNumber,
			@Param("actualItemCode") String actualItemCode);

	List<DCCLineItem> findAllByDccId(String dccId);

	@Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	DCCLineItem findTopBySerialNumber(@Param("serialNumber") String serialNumber);

	@Query("SELECT COALESCE(SUM(d.deliveredQty), 0) FROM DCCLineItem d WHERE d.dccId  IN :dccIds AND d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber  = :uplLineNumber")
	Double sumDeliveredQtyByDccIdsAndPoLineInfo(@Param("dccIds") List<String> dccIds, @Param("poId") String poNumber,
			@Param("lineNumber") String lineNumber, @Param("uplLineNumber") String upLineNumber);

	@Query("SELECT COALESCE(SUM(d.deliveredQty), 0) FROM DCCLineItem d WHERE d.dccId  IN :dccIds AND d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber  = :uplLineNumber")
	Double sumDeliveredQtyByDccIdsAndPoLine(@Param("dccIds") List<String> dccIds, @Param("poId") String poNumber,
			@Param("lineNumber") String lineNumber, @Param("uplLineNumber") String upLineNumber);
}