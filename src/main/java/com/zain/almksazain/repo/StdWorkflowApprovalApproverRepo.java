package com.zain.almksazain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.StdWorkflowApprovalApprover;

public interface StdWorkflowApprovalApproverRepo extends JpaRepository<StdWorkflowApprovalApprover, Integer> {
    List<StdWorkflowApprovalApprover> findByApprovalLevelIdAndStatus(Integer approvalLevelId, Integer status);
    boolean existsByApprovalLevelIdAndApproverIdAndStatus(Integer approvalLevelId, Integer approverId, Integer status);
}
