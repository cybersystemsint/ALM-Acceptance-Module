package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.ScopeApprovalLevel;

public interface ScopeApprovalLevelRepository extends JpaRepository<ScopeApprovalLevel, Long> {
	List<ScopeApprovalLevel> findByScope(Integer scope);
}