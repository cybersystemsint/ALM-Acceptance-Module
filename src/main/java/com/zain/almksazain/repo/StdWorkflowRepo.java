package com.zain.almksazain.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.StdWorkflow;

public interface StdWorkflowRepo extends JpaRepository<StdWorkflow, Integer> {
    Optional<StdWorkflow> findByModuleIdAndActionTypeAndStatus(Integer moduleId, String actionType, Integer status);
}
