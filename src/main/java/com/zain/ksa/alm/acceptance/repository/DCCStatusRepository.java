package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.DCCStatus;

public interface DCCStatusRepository extends JpaRepository<DCCStatus, Long> {
	DCCStatus findByRecordNo(long recordno);
}