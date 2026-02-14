package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.ksa.alm.acceptance.entity.DCC;

public interface DCCRepository extends JpaRepository<DCC, Long> {

	DCC findByRecordNo(long recordNo);

	@Query(value = "SELECT * FROM tb_DCC d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	DCC findTopByPoNumber(@Param("poNumber") String poNumber);

	@Query(value = "SELECT * FROM tb_DCC d WHERE d.recordNo = :recordNo ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	DCC findTopByRecordNo(@Param("recordNo") Integer recordNo);

	@Query("SELECT d.recordNo  FROM DCC  d WHERE d.poNumber  = :poNumber AND LOWER(d.status) IN :statuses")
	List<Integer> findByPoNumberAndStatus(@Param("poNumber") String poNumber, @Param("statuses") List<String> statuses);

}
