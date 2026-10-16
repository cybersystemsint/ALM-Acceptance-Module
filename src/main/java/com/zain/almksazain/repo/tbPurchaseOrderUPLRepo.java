/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author jgithu
 */
public interface tbPurchaseOrderUPLRepo extends JpaRepository<tb_PurchaseOrderUPL, Long> {

    //  List<tb_PurchaseOrderUPL> findByPoNumberAndVendorNumber(String poId, String supplierId);
   // tb_PurchaseOrderUPL findByPoNumber(String PoNumber);

    tb_PurchaseOrderUPL findByRecordNo(long recordNo);

    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndUplLine(String PoNumber, String PoLineNumber, String UplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndUplLine(@Param("poNumber") String poNumber, @Param("uplLine") String uplLine);

    @Query(value = "SELECT * FROM tb_PurchaseOrderUPL d WHERE d.poNumber = :poNumber AND  d.poLineNumber = :poLineNumber AND d.uplLine = :uplLine ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    tb_PurchaseOrderUPL findTopByPoNumberAndPoLineNumberAndUplLine(@Param("poNumber") String poNumber, @Param("poLineNumber") String poLineNumber, @Param("uplLine") String uplLine);
     
    tb_PurchaseOrderUPL findFirstByPoNumberAndPoLineNumberAndUplLine(String poNumber, String poLineNumber, String uplLine);

    // "Does this line already exist" for the UPL creation flow's duplicate check - excludes
    // DELETED rows (a soft-deleted line, or the placeholder row left behind by a rejected CREATE
    // request) so re-submitting the same PO+line+UPL-line combo after a rejection/deletion isn't
    // permanently blocked. Still matches ACTIVE and PENDING rows, so a genuinely live line or one
    // still awaiting its own decision correctly continues to block a duplicate submission.
    tb_PurchaseOrderUPL findFirstByPoNumberAndPoLineNumberAndUplLineAndStatusNot(
            String poNumber, String poLineNumber, String uplLine, String status);
    List<tb_PurchaseOrderUPL> findByPoNumberInAndPoLineNumberInAndUplLineIn(
        List<String> poNumbers,
        List<String> poLineNumbers,
        List<String> uplLines
    );
    List<tb_PurchaseOrderUPL> findByPoNumberInAndPoLineNumberInAndUplLineIn(
    Set<String> poNumbers, Set<String> lineNumbers, Set<String> uplLines);
//    //NEW CODE
//    List<tb_PurchaseOrderUPL> findByPoNumber(String poNumber);

    // UPL edit/delete approval workflow: every other active line under the same PO line,
    // for the line-total-vs-PO-line-total ceiling check.
    List<tb_PurchaseOrderUPL> findByPoNumberAndPoLineNumberAndStatus(String poNumber, String poLineNumber, String status);

}
