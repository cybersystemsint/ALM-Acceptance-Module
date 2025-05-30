package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCCLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DCCLineItem entity.
 */
@Repository
public interface TbDccLnRepository extends JpaRepository<DCCLineItem, Long> {
    List<DCCLineItem> findByDccIdIn(List<String> dccIds);

    List<DCCLineItem> findByUplLineNumberAndLineNumberAndPoId(String uplLineNumber, String lineNumber, String poId);
    List<DCCLineItem> findByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);
    boolean existsByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);
}