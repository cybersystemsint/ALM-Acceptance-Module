package com.zain.ksa.alm.acceptance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
	Department findByRecordNo(long recordno);

	List<Department> findBySysStatus(boolean active);
}