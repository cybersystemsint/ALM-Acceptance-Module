package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.ApprovalLog;

public interface ApprovalLogRepository extends JpaRepository<ApprovalLog, Long> {

    ApprovalLog findByRecordNo(long recordNo);
}