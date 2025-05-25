package com.zain.almksazain.repo;

import com.zain.almksazain.model.TbCategoryApprovalRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TbCategoryApprovalRequestsRepository extends JpaRepository<TbCategoryApprovalRequests, Long> {
    @Query("SELECT r FROM TbCategoryApprovalRequests r WHERE r.acceptanceRequestRecordNo = :acceptanceRequestRecordNo ORDER BY r.recordDateTime DESC")
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(@Param("acceptanceRequestRecordNo") Long acceptanceRequestRecordNo);
}