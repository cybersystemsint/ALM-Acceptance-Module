package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbScopeApprovalSuppliers;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface tbScopeApprovalSuppliersRepo extends JpaRepository<tbScopeApprovalSuppliers, Integer> {
    // Any supplier-specific approver override configured anywhere within this
    // scope (not limited to a single level) - mirrors
    // ScopeApprovalSupplierRepository.findFirstBySupplierAndScopeApprovalLevel_ScopeOrderByRecordNoAsc.
    @Query("SELECT s FROM tbScopeApprovalSuppliers s WHERE s.supplierId = :supplierId "
            + "AND s.approvalLevelId IN (SELECT l.recordNo FROM tbScopeApprovalLevels l WHERE l.scope = :scopeId) "
            + "ORDER BY s.recordNo ASC")
    List<tbScopeApprovalSuppliers> findBySupplierAndScopeOrderByRecordNoAsc(@Param("supplierId") int supplierId, @Param("scopeId") int scopeId);
}
