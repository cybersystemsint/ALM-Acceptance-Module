package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.PurchaseOrderLine;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {
	List<PurchaseOrderLine> findByPoIdAndItemCode(String poId, String itemCode);

	PurchaseOrderLine findByPoId(String PoId);

	PurchaseOrderLine findByRecordNo(long recordNo);
}