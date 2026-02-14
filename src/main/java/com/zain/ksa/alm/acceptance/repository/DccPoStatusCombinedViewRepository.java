package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.DccPoStatusCombinedView;

import java.util.List;

public interface DccPoStatusCombinedViewRepository extends JpaRepository<DccPoStatusCombinedView, Long> {
	List<DccPoStatusCombinedView> findBySupplierId(String supplierID);

	List<DccPoStatusCombinedView> findBySupplierIdAndDccStatus(String supplierID, String dccstatus);
}