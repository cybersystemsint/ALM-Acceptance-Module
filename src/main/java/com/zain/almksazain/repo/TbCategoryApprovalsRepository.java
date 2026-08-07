package com.zain.almksazain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zain.almksazain.model.tbCategoryApprovals;

import java.util.Collection;
import java.util.List;

public interface TbCategoryApprovalsRepository extends JpaRepository<tbCategoryApprovals, Integer> {
    List<tbCategoryApprovals> findByApprovalRecordId(int approvalRecordId);
    List<tbCategoryApprovals> findByApprovalRecordIdIn(List<Integer> approvalRecordIds);
    List<tbCategoryApprovals> findByApprovalRecordIdIn(Collection<Integer> approvalRecordIds);

    @Query("SELECT s FROM tbCategoryApprovals s "
            + "WHERE s.approvalRecordId = :recordNo "
            + "AND s.status = 'pending' "
            + "AND s.approvalStatus = 'readyForApproval' "
            + "ORDER BY s.approvalId ASC")
    List<tbCategoryApprovals> findPendingReadyForApprovalByRequestNo(@Param("recordNo") Integer recordNo);
}
