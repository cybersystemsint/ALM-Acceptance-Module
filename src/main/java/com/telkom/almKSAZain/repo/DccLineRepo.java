package com.telkom.almKSAZain.repo;

import com.telkom.almKSAZain.model.DCCLineItem;
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

    List<DCCLineItem> findByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);

    List<DCCLineItem> findBySerialNumberAndActualItemCode(String serialNumber, String actualItemCode);

    @Query(value = "SELECT * FROM tb_DCC_LN d WHERE d.serialNumber = :serialNumber and d.itemCode = :itemCode ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCCLineItem findTopBySerialNumberAndItemCode(@Param("serialNumber") String serialNumber, @Param("itemCode") String itemCode);

    // New Methods
    List<DCCLineItem> findAllByDccId(String dccId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DCCLineItem d WHERE d.recordNo IN :recordNos")
    void deleteAllByIdInBatch(@Param("recordNos") List<Long> recordNos);
}
