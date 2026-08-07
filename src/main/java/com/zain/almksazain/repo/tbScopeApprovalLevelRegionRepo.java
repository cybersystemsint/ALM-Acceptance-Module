package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbScope;
import com.zain.almksazain.model.tbScopeApprovalLevelRegion;
import com.zain.almksazain.model.tbScopeApprovalLevels;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface tbScopeApprovalLevelRegionRepo extends JpaRepository<tbScopeApprovalLevelRegion, Integer> {

    @Query("SELECT sar FROM tbScopeApprovalLevelRegion sar "
            + "LEFT JOIN FETCH sar.region "
            + "WHERE sar.scope = :scope AND sar.scopeApprovalLevel = :scopeApprovalLevel")
    List<tbScopeApprovalLevelRegion> findByScopeAndScopeApprovalLevel(
            @Param("scope") tbScope scope,
            @Param("scopeApprovalLevel") tbScopeApprovalLevels scopeApprovalLevel);
}
