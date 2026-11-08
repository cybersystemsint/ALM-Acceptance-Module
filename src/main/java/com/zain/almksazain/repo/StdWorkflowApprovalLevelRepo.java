package com.zain.almksazain.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.StdWorkflowApprovalLevel;

public interface StdWorkflowApprovalLevelRepo extends JpaRepository<StdWorkflowApprovalLevel, Integer> {
    List<StdWorkflowApprovalLevel> findByWorkflowIdAndStatusOrderByApprovalNumberAsc(Integer workflowId, Integer status);
    Optional<StdWorkflowApprovalLevel> findByWorkflowIdAndApprovalNumberAndStatus(Integer workflowId, Integer approvalNumber, Integer status);
    long countByWorkflowIdAndStatus(Integer workflowId, Integer status);
}
