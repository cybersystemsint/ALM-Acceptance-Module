package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.ErrorMessage;

public interface ErrorMessageRepository extends JpaRepository<ErrorMessage, Long> {

	ErrorMessage findByRecordNo(long recordNo);

	ErrorMessage findByModuleAndErrorCodeAndOperation(String module, String errorCode, String operation);
}