package com.zain.almksazain.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.tbCategoryApprovalRequests;

public interface TbCategoryApprovalRequestsRepository extends JpaRepository<tbCategoryApprovalRequests, Integer>, JpaSpecificationExecutor<tbCategoryApprovalRequests> {
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(int acceptanceRequestRecordNo);
    Optional<tbCategoryApprovalRequests> findFirstByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(int acceptanceRequestRecordNo);
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(List<Integer> recordNos);

    @Query("SELECT ar FROM tbCategoryApprovalRequests ar WHERE ar.acceptanceRequestRecordNo = :acceptanceRequestRecordNo")
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoStatuses(
            @Param("acceptanceRequestRecordNo") Integer acceptanceRequestRecordNo);
}
