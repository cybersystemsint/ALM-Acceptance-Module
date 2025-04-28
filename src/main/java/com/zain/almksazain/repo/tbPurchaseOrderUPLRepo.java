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

/**
 *
 * @author jgithu
 */
public interface tbPurchaseOrderUPLRepo extends JpaRepository<tb_PurchaseOrderUPL, Long> {

    //  List<tb_PurchaseOrderUPL> findByPoNumberAndVendorNumber(String poId, String supplierId);
    tb_PurchaseOrderUPL findByPoNumber(String PoNumber);

    tb_PurchaseOrderUPL findByRecordNo(long recordNo);

    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndUplLine(String PoNumber, String PoLineNumber, String UplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndUplLine(@Param("poNumber") String poNumber, @Param("uplLine") String uplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND  d.poLineNumber = :poLineNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndPoLineNumberAndUplLine(@Param("poNumber") String poNumber, @Param("poLineNumber") String poLineNumber, @Param("uplLine") String uplLine);

}
