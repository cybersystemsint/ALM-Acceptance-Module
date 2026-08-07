package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbScopeApprovalSupplier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface tbScopeApprovalSupplierRepo extends JpaRepository<tbScopeApprovalSupplier, Integer> {

    @Query("SELECT sas FROM tbScopeApprovalSupplier sas "
            + "LEFT JOIN FETCH sas.supplier "
            + "LEFT JOIN FETCH sas.scopeApprovalLevel "
            + "WHERE sas.scopeApprovalLevel.recordNo IN :levelIds")
    List<tbScopeApprovalSupplier> findByScopeApprovalLevelRecordNoIn(@Param("levelIds") List<Long> levelIds);
}
