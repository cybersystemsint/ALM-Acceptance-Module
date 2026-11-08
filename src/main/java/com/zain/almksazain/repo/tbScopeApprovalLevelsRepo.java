/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbScopeApprovalLevels;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


/**
 *
 * @author jgithu
 */

// Config reads filter to acceptanceRequestRecordNo IS NULL: rows with it set are
// one request's approver-defined chain (Others) and must never route another
// request or count as the scope's config.
public interface tbScopeApprovalLevelsRepo extends JpaRepository<tbScopeApprovalLevels, Long> {

    @Query("SELECT l FROM tbScopeApprovalLevels l WHERE l.scope = :scope AND l.acceptanceRequestRecordNo IS NULL")
    List<tbScopeApprovalLevels> findByScope(@Param("scope") Integer scope);

    @Query("SELECT l FROM tbScopeApprovalLevels l WHERE l.scope = :scope AND l.acceptanceRequestRecordNo IS NULL ORDER BY l.level ASC")
    List<tbScopeApprovalLevels> findByScopeOrderByLevelAsc(@Param("scope") Integer scope);

    // status = 0 marks a soft-deleted level, which must never be assigned to a new request.
    @Query("SELECT l FROM tbScopeApprovalLevels l WHERE l.scope = :scope AND l.status = :status "
            + "AND l.acceptanceRequestRecordNo IS NULL ORDER BY l.level ASC")
    List<tbScopeApprovalLevels> findByScopeAndStatusOrderByLevelAsc(@Param("scope") Integer scope, @Param("status") Integer status);

    // The approver-defined chain of one Acceptance Request (status 1 = active, 0 = retired).
    List<tbScopeApprovalLevels> findByAcceptanceRequestRecordNoAndStatusOrderByLevelAsc(Integer acceptanceRequestRecordNo, Integer status);

}
