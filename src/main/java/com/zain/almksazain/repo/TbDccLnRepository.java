package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query; 
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
    
    /**
     * Bulk load all line items for a set of PO numbers.
     * Used by buildQuantityContext to pre-fetch delivered-qty data in a single query
     * instead of one query per (poNumber, poLineNumber, uplLine) combination.
     */
    @Query("SELECT d FROM DCCLineItem d WHERE d.poId IN :poNumbers")
    List<DCCLineItem> findByPoIdIn(@Param("poNumbers") List<String> poNumbers);

    /**
     * Bulk load DCC entities by their record numbers.
     * Used by buildQuantityContext to resolve DCC statuses for line items
     * that belong to DCCs outside the current page.
     */
    @Query("SELECT d FROM DCC d WHERE d.recordNo IN :ids")
    List<DCC> findByRecordNoIn(@Param("ids") List<Long> ids);
}