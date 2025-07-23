package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

    
public interface DCCRepository extends JpaRepository<DCC, Long>, JpaSpecificationExecutor<DCC> {

    
       DCC findByRecordNo(long recordNo);
    
    @Query(value = "SELECT * FROM tb_DCC d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCC findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_DCC d WHERE d.recordNo = :recordNo ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCC findTopByRecordNo(@Param("recordNo") Integer recordNo);

    @Query("SELECT d.recordNo  FROM DCC  d WHERE d.poNumber  = :poNumber AND LOWER(d.status) IN :statuses")
    List<Integer> findByPoNumberAndStatus(@Param("poNumber") String poNumber, @Param("statuses") List<String> statuses);


}
