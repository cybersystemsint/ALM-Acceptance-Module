package com.zain.almksazain.repo;

import com.zain.almksazain.model.TbCategoryApprovals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TbCategoryApprovals entity.
 */
@Repository
public interface TbCategoryApprovalsRepository extends JpaRepository<TbCategoryApprovals, Long> {
    List<TbCategoryApprovals> findByApprovalRecordIdAndStatusAndApprovalStatusIn(Long approvalRecordId, String status, List<String> approvalStatuses);

    List<TbCategoryApprovals> findByApprovalRecordIdAndApprovalStatusNotIn(Long approvalRecordId, List<String> approvalStatuses);

    List<TbCategoryApprovals> findByApprovalRecordId(Long approvalRecordId);

    List<TbCategoryApprovals> findByApprovalRecordIdAndStatusAndApprovalStatus(Long approvalRecordId, String status, String approvalStatus);
}