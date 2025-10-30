package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.tbCategoryApprovals;

import java.util.Collection;
import java.util.List;

public interface TbCategoryApprovalsRepository extends JpaRepository<tbCategoryApprovals, Integer> {
    List<tbCategoryApprovals> findByApprovalRecordId(int approvalRecordId);
    List<tbCategoryApprovals> findByApprovalRecordIdIn(List<Integer> approvalRecordIds);
      List<tbCategoryApprovals> findByApprovalRecordIdIn(Collection<Integer> approvalRecordIds);
}
