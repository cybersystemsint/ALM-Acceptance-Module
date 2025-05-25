package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCCLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbDccLnRepository extends JpaRepository<DCCLineItem, Long> {
    List<DCCLineItem> findByDccId(String dccId);
    List<DCCLineItem> findByDccIdIn(List<String> dccIds);

    List<DCCLineItem> findByUplLineNumberAndLineNumberAndPoId(String uplLineNumber, String lineNumber, String poId);

    boolean existsByPoIdAndLineNumberAndUplLineNumber(String poId, String lineNumber, String uplLineNumber);
}