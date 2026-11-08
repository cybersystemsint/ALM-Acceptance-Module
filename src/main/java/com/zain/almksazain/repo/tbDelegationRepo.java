package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbDelegation;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface tbDelegationRepo extends JpaRepository<tbDelegation, Integer> {
    @Query("SELECT d FROM tbDelegation d WHERE d.delegatorId = :delegatorId "
            + "AND :now BETWEEN d.startDateTime AND d.endDateTime AND d.isActive = true")
    Optional<tbDelegation> findActiveDelegation(@Param("delegatorId") int delegatorId, @Param("now") LocalDateTime now);
}
