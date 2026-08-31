package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DccLineRepo extends JpaRepository<DCCLineItem, Long> {

    DCCLineItem findByRecordNo(long recordNo);

    List<DCCLineItem> findBySerialNumberAndItemCode(String serialNumber, String itemCode);

    List<DCCLineItem> findBySerialNumber(String serialNumber);

    List<DCCLineItem> findByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);

    List<DCCLineItem> findBySerialNumberAndActualItemCode(String serialNumber, String actualItemCode);

    @Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber and d.itemCode = :itemCode ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCCLineItem findTopBySerialNumberAndItemCode(@Param("serialNumber") String serialNumber, @Param("itemCode") String itemCode);

    @Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber and d.actualItemCode = :actualItemCode ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCCLineItem findTopBySerialNumberAndActualItemCode(@Param("serialNumber") String serialNumber, @Param("actualItemCode") String actualItemCode);

    // New Methods
    List<DCCLineItem> findAllByDccId(String dccId);

    @Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCCLineItem findTopBySerialNumber(@Param("serialNumber") String serialNumber);

//    @Modifying
//    @Transactional
//    @Query("DELETE FROM DCCLineItem d WHERE d.recordNo IN :recordNos")
//    void deleteAllByIdInBatch(@Param("recordNos") List<Long> recordNos);

    @Query("SELECT COALESCE(SUM(d.deliveredQty), 0) FROM DCCLineItem d WHERE d.dccId  IN :dccIds AND d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber  = :uplLineNumber")
    Double sumDeliveredQtyByDccIdsAndPoLineInfo(@Param("dccIds") List<String> dccIds,
            @Param("poId") String poNumber,
            @Param("lineNumber") String lineNumber,
            @Param("uplLineNumber") String upLineNumber);

    @Query("SELECT COALESCE(SUM(d.deliveredQty), 0) FROM DCCLineItem d WHERE d.dccId  IN :dccIds AND d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber  = :uplLineNumber")
    Double sumDeliveredQtyByDccIdsAndPoLine(@Param("dccIds") List<String> dccIds,
            @Param("poId") String poNumber,
            @Param("lineNumber") String lineNumber,
            @Param("uplLineNumber") String upLineNumber);

    List<DCCLineItem> findByDccId(String dccId);
    List<DCCLineItem> findByDccIdIn(List<String> dccIds);
//    //NEW CODE 
//    List<DCCLineItem> findByDcc(DCC dcc);
  List<DCCLineItem> findByDccIdAndDccStatusNotIn(String dccId, List<String> excludedStatuses);
  
    List<DCCLineItem> findByDccIdIn(Collection<String> dccIds);
      
    // Bulk delete by recordNo
    @Modifying
    @Transactional
    @Query("delete from DCCLineItem d where d.recordNo in :recordNos")
    void deleteByRecordNoIn(@Param("recordNos") List<Long> recordNos);

    @Query("SELECT ln FROM DCCLineItem ln JOIN ln.dcc d "
            + "WHERE LOWER(ln.tagNumber) IN :tagNumbers "
            + "AND LOWER(d.status) NOT IN :excludedStatuses")
    List<DCCLineItem> findActiveTagConflicts(
            @Param("tagNumbers") List<String> tagNumbers,
            @Param("excludedStatuses") List<String> excludedStatuses);

    // --- UPL edit/delete approval workflow: PAC lookups ---
    // A "PAC raised" line is a tb_DCC_LN row whose parent tb_DCC is not incomplete/rejected —
    // same definition combinedPurchaseOrderView uses for POAcceptanceQty / poPendingQuantity.
    @Query("SELECT COUNT(d) FROM DCCLineItem d JOIN d.dcc c "
            + "WHERE d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber = :uplLineNumber "
            + "AND c.status NOT IN :excludedStatuses")
    long countActivePacsForUplLine(@Param("poId") String poId,
            @Param("lineNumber") String lineNumber,
            @Param("uplLineNumber") String uplLineNumber,
            @Param("excludedStatuses") List<String> excludedStatuses);

    @Query("SELECT COALESCE(SUM(d.deliveredQty), 0) FROM DCCLineItem d JOIN d.dcc c "
            + "WHERE d.poId = :poId AND d.lineNumber = :lineNumber AND d.uplLineNumber = :uplLineNumber "
            + "AND c.status NOT IN :excludedStatuses")
    Double sumAcceptedQtyForUplLine(@Param("poId") String poId,
            @Param("lineNumber") String lineNumber,
            @Param("uplLineNumber") String uplLineNumber,
            @Param("excludedStatuses") List<String> excludedStatuses);
}
