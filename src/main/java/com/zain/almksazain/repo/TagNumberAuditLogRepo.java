package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.TagNumberAuditLog;

public interface TagNumberAuditLogRepo extends JpaRepository<TagNumberAuditLog, Long> {
}
