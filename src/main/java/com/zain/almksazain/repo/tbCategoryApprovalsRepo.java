package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.tbCategoryApprovals;


public interface tbCategoryApprovalsRepo extends JpaRepository<tbCategoryApprovals, Integer> {

        List<tbCategoryApprovals> findByApprovalRecordId(int approvalRecordId);

    // Bulk fetch (for faster mapping in service)
    List<tbCategoryApprovals> findByApprovalRecordIdIn(Collection<Integer> approvalRecordIds);
}
