package com.zain.almksazain.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.StdWorkflowModule;

public interface StdWorkflowModuleRepo extends JpaRepository<StdWorkflowModule, Integer> {
    Optional<StdWorkflowModule> findByModuleNameAndStatus(String moduleName, Integer status);
}
