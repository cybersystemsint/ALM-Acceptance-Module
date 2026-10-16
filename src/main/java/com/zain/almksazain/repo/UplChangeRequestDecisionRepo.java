package com.zain.almksazain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.UplChangeRequestDecision;

public interface UplChangeRequestDecisionRepo extends JpaRepository<UplChangeRequestDecision, Long> {
    List<UplChangeRequestDecision> findByChangeRequestIdOrderByLevelNoAsc(Long changeRequestId);
}
