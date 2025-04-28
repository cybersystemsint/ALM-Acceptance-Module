package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DCCRepository extends JpaRepository<DCC, Long> {

    DCC findByRecordNo(long recordNo);

    @Query(value = "SELECT * FROM tb_DCC d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCC findTopByPoNumber(@Param("poNumber") String poNumber);

//    @Query("SELECT d FROM DCC d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC")
//    DCC findTopByPoNumber(@Param("poNumber") String poNumber);
}
