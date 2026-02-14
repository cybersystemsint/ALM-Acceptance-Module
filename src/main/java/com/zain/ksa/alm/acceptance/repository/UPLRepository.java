package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.ksa.alm.acceptance.entity.UPL;

public interface UPLRepository extends JpaRepository<UPL, Long> {

	UPL findByUplLine(long uplLine);

	UPL findByRecordNo(long recordno);

	List<UPL> findByPoId(String poid);

	@Override
	List<UPL> findAll();

	@Query(value = "SELECT * FROM tb_UPL d WHERE d.poId = :poId ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
	UPL findTopByPoNumber(@Param("poId") String poNumber);
}