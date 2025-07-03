package com.zain.almksazain.repo;

import com.zain.almksazain.model.TbCategoryApprovalRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TbCategoryApprovalRequests entity.
 */
@Repository
public interface TbCategoryApprovalRequestsRepository extends JpaRepository<TbCategoryApprovalRequests, Long> {
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(Long recordNo);
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoIn(List<Long> recordNos);

}