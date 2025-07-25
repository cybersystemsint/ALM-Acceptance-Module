package com.zain.almksazain.repo;
import com.zain.almksazain.model.DCCLineItem;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DccLineRepo extends JpaRepository<DCCLineItem, Long> {
        List<DCCLineItem> findByDccIdAndDccStatusNotIn(String dccId, List<String> excludedStatuses);

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

    @Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCCLineItem findTopBySerialNumber(@Param("serialNumber") String serialNumber);

        List<DCCLineItem> findByDccIdAndLineNumberAndUplLineNumberAndDccStatusNotIn(
        String dccId,
        String lineNumber,
        String uplLineNumber,
        List<String> excludedStatuses
    );


@Query("SELECT COALESCE(SUM(dl.deliveredQty), 0) " +
       "FROM DCCLineItem dl " +
       "JOIN dl.dcc dcc " +
       "WHERE dl.uplLineNumber = :uplLine " +
       "AND dl.lineNumber = :poLineNumber " +
       "AND dcc.poNumber = :poNumber " +
       "AND dcc.status NOT IN ('incomplete', 'rejected')")
Double sumDeliveredQtyByUplLineAndPoLineAndPoNumber(
        @Param("uplLine") String uplLine,
        @Param("poLineNumber") String poLineNumber,
        @Param("poNumber") String poNumber
);


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


  List<DCCLineItem> findAllByDccId(String dccId);
    List<DCCLineItem> findByDccIdIn(Collection<String> dccIds);
    List<DCCLineItem> findByPoIdAndLineNumberAndUplLineNumberAndDccStatusNotIn(String poId, String lineNumber, String uplLineNumber, List<String> dccStatus);



}
