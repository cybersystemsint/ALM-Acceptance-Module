package com.zain.almksazain.repo;

import com.zain.almksazain.model.TbCategoryApprovals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TbCategoryApprovalsRepository extends JpaRepository<TbCategoryApprovals, Long> {
    // Matches findByApprovalRecordIdAndStatusAndApprovalStatusIn
    List<TbCategoryApprovals> findByApprovalRecordIdAndStatusAndApprovalStatusIn(Long approvalRecordId, String status, List<String> approvalStatuses);

    // Matches findByApprovalRecordIdAndStatusAndApprovalStatus
    List<TbCategoryApprovals> findByApprovalRecordIdAndStatusAndApprovalStatus(Long approvalRecordId, String status, String approvalStatus);

    // Matches findByApprovalRecordIdAndApprovalStatusNotIn
    List<TbCategoryApprovals> findByApprovalRecordIdAndApprovalStatusNotIn(Long approvalRecordId, List<String> approvalStatuses);

    // Matches findByApprovalRecordId
    List<TbCategoryApprovals> findByApprovalRecordId(Long approvalRecordId);

    // Renamed to match the logic of findByApprovalRecordIdAndStatusAndApprovalStatusIn with a custom query
    @Query("SELECT a FROM TbCategoryApprovals a WHERE a.approvalRecordId = :approvalRecordId AND a.approvalStatus IN ('pending', 'readyForApproval', 'request-info') AND a.status = 'pending'")
    List<TbCategoryApprovals> findPendingApprovals(@Param("approvalRecordId") Long approvalRecordId);

    // Renamed to match the logic of findByApprovalRecordIdAndApprovalStatusNotIn with a custom query
    @Query("SELECT a FROM TbCategoryApprovals a WHERE a.approvalRecordId = :approvalRecordId AND a.approvalStatus NOT IN ('pending', 'readyForApproval', 'request-info') AND a.comments IS NOT NULL ORDER BY a.approvalId DESC")
    List<TbCategoryApprovals> findApproverComments(@Param("approvalRecordId") Long approvalRecordId);
}