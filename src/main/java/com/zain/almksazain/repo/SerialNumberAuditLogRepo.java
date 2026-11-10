package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.SerialNumberAuditLog;

public interface SerialNumberAuditLogRepo extends JpaRepository<SerialNumberAuditLog, Long> {
}
