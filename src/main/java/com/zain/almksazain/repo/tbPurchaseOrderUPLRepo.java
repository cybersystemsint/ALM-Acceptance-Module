/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface tbPurchaseOrderUPLRepo extends JpaRepository<tb_PurchaseOrderUPL, Long> {
      // Batch fetch for UPLs by PO numbers
    List<tb_PurchaseOrderUPL> findByPoNumberIn(List<String> poNumbers);

        // For POLineAcceptanceQty subquery (negative uplLineQuantity only)
    @Query("SELECT COALESCE(SUM( (upl.uplLineQuantity * upl.uplLineUnitPrice) / NULLIF((upl.poLineQuantity * upl.poLineUnitPrice),0) ),0) " +
           "FROM tb_PurchaseOrderUPL upl " +
           "WHERE upl.uplLineQuantity < 0 AND upl.poNumber = :poNumber AND upl.poLineNumber = :poLineNumber")
    Double sumPOLineAcceptanceQty(@Param("poNumber") String poNumber, @Param("poLineNumber") String poLineNumber);

    tb_PurchaseOrderUPL findByRecordNo(long recordNo);

    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndUplLine(String PoNumber, String PoLineNumber, String UplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndUplLine(@Param("poNumber") String poNumber, @Param("uplLine") String uplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND  d.poLineNumber = :poLineNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndPoLineNumberAndUplLine(@Param("poNumber") String poNumber, @Param("poLineNumber") String poLineNumber, @Param("uplLine") String uplLine);

    List<tb_PurchaseOrderUPL> findByPoNumber(String poNumber);

}
