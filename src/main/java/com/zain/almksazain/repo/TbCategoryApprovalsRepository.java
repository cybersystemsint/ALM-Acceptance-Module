package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.Query; 
import com.zain.almksazain.model.TbCategoryApprovals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
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

    List<TbCategoryApprovals> findByApproverName(String approverName);
    List<TbCategoryApprovals> findByApprovedBy(Integer approvedBy);
    List<TbCategoryApprovals> findByApprovalRecordIdAndStatusAndApprovalStatus(Long approvalRecordId, String status, String approvalStatus);
    List<TbCategoryApprovals> findByApprovalRecordIdIn(List<Long> recordIds);

@Query("""
    SELECT DISTINCT r.acceptanceRequestRecordNo
    FROM TbCategoryApprovalRequests r
    JOIN TbCategoryApprovals a ON a.approvalRecordId = r.recordNo
    WHERE LOWER(a.approverName) = LOWER(:approverName)
      AND a.approvalStatus = 'readyForApproval'
      AND r.status = 'pending'
      AND r.recordDateTime = (
          SELECT MAX(r2.recordDateTime)
          FROM TbCategoryApprovalRequests r2
          WHERE r2.acceptanceRequestRecordNo = r.acceptanceRequestRecordNo
            AND r2.status = 'pending'
      )
    """)
List<Long> findDccIdsByPendingApproverName(@Param("approverName") String approverName);
}