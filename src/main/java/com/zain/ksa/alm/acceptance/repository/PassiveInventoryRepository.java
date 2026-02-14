package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.PassiveInventory;

public interface PassiveInventoryRepository extends JpaRepository<PassiveInventory, Long> {

	List<PassiveInventory> findByItemCodeAndSerialNumber(String itemCode, String serialNumber);
}