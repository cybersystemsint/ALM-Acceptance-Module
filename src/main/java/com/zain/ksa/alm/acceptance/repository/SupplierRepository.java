package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
	List<Supplier> findBySupplierIdAndSupplierPhone(String supplierId, String supplierPhone);

	Supplier findBySupplierId(String SupplierId);

	Supplier findByRecordNo(long recordNo);
}