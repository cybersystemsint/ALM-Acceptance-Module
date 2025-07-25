/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbPurchaseOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author jgithu
 */
public interface tbPurchaseOrderRepo extends JpaRepository<tbPurchaseOrder, Long> {
     

    List<tbPurchaseOrder> findByPoNumberAndVendorNumber(String poId, String supplierId);

    List<tbPurchaseOrder> findByPoNumber(String PoNumber);

    tbPurchaseOrder findByRecordNo(long recordNo);

    @Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tbPurchaseOrder findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber AND d.lineNumber = :lineNumber  ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tbPurchaseOrder findTopByPoNumberAndLineNumber(@Param("poNumber") String poNumber, @Param("lineNumber") String lineNumber);

    @Query(value = "SELECT * FROM tb_PurchaseOrder d WHERE d.poNumber = :poNumber AND d.lineNumber = :lineNumber AND d.releaseNum = :releaseNum  ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tbPurchaseOrder findTopByPoNumberAndLineNumberAndReleaseNum(@Param("poNumber") String poNumber, @Param("lineNumber") String lineNumber, @Param("releaseNum") String releaseNum);

}
