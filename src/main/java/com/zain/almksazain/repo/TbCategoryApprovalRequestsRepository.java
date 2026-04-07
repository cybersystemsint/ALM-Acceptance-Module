package com.zain.almksazain.repo;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.tbCategoryApprovalRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TbCategoryApprovalRequestsRepository extends JpaRepository<tbCategoryApprovalRequests, Integer>, JpaSpecificationExecutor<tbCategoryApprovalRequests> {
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(int acceptanceRequestRecordNo);
    Optional<tbCategoryApprovalRequests> findFirstByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(int acceptanceRequestRecordNo);
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(List<Integer> recordNos);
}
