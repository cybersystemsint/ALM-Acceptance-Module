package com.zain.ksa.alm.acceptance.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.Node;

public interface NodeRepository extends JpaRepository<Node, Long> {

	List<Node> findByPartNumberAndSerialNumber(String partNumber, String serialNumber);
}