package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.Scope;

public interface ScopeRepository extends JpaRepository<Scope, Long> {
	Scope findByScope(String scope);
}