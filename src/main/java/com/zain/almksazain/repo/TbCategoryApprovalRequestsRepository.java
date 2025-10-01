package com.zain.almksazain.repo;

import com.zain.almksazain.model.TbCategoryApprovalRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TbCategoryApprovalRequests entity.
 */
@Repository
public interface TbCategoryApprovalRequestsRepository extends JpaRepository<TbCategoryApprovalRequests, Long> {
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoOrderByRecordDateTimeDesc(Long recordNo);
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoIn(List<Long> recordNos);
    List<TbCategoryApprovalRequests> findByRecordNoIn(List<Long> recordNos);
    @Query("SELECT r FROM TbCategoryApprovalRequests r WHERE r.acceptanceRequestRecordNo IN :dccIds ORDER BY r.acceptanceRequestRecordNo DESC, r.recordDateTime DESC")
    List<TbCategoryApprovalRequests> findByAcceptanceRequestRecordNoInOrderByAcceptanceRequestRecordNoAscRecordDateTimeDesc(@Param("dccIds") List<Long> dccIds);

    @Query(value = "SELECT * FROM `tb_Category_Approval_Requests` r " +
            "INNER JOIN `tb_Category_Approvals` a ON a.`approvalRecordId` = r.`recordNo` " +
            "WHERE a.`approverName` = :approverName AND a.`status` != 'pending' " +
            "ORDER BY r.`recordDateTime` DESC", nativeQuery = true)
    List<TbCategoryApprovalRequests> findRequestsByApproverActioned(@Param("approverName") String approverName);
}