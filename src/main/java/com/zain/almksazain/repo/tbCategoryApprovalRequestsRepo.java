package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.tbCategoryApprovalRequests;

public interface tbCategoryApprovalRequestsRepo extends JpaRepository<tbCategoryApprovalRequests, Integer> {

List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(Integer acceptanceRequestRecordNo);
   List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(int acceptanceRequestRecordNo);

    // Bulk fetch (for faster mapping in service)
    List<tbCategoryApprovalRequests> findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(Collection<Integer> acceptanceRequestRecordNos);
}
