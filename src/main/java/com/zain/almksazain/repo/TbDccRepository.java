package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DCC entity.
 */
@Repository
public interface TbDccRepository extends JpaRepository<DCC, Long>, JpaSpecificationExecutor<DCC> {
    List<DCC> findByRecordNoIn(List<Long> recordNos);
    Optional<DCC> findByRecordNo(Long recordNo);
    long countByPoNumberIsNotNull();
}