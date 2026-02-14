package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.DccPoCombinedView;

import java.util.List;

public interface DccPoCombinedViewRepository extends JpaRepository<DccPoCombinedView, Long> {

	List<DccPoCombinedView> findBySupplierId(String supplierId);

	List<DccPoCombinedView> findAll();
}