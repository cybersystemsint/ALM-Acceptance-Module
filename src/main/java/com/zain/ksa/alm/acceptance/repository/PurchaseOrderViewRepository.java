package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrderView;

public interface PurchaseOrderViewRepository extends JpaRepository<PurchaseOrderView, Long> {
	List<PurchaseOrderView> findBySupplierId(String supplierId);
}