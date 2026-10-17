package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbScopeApprovalLevelRegions;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface tbScopeApprovalLevelRegionsRepo extends JpaRepository<tbScopeApprovalLevelRegions, Integer> {
    List<tbScopeApprovalLevelRegions> findByScopeIdAndApprovalLevelId(int scopeId, int approvalLevelId);
}
