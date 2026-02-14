package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrderHeader;

public interface PurchaseOrderHeaderRepository extends JpaRepository<PurchaseOrderHeader, Long> {

	List<PurchaseOrderHeader> findByPoIdAndSupplierId(String poId, String supplierId);

	PurchaseOrderHeader findByPoId(String PoId);

	PurchaseOrderHeader findByRecordNo(long recordNo);
}