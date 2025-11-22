package com.zain.almksazain.repo;

import com.zain.almksazain.model.AgingEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AgingEmailConfigRepository extends JpaRepository<AgingEmailConfig, Long>, JpaSpecificationExecutor<AgingEmailConfig> {
       Optional<AgingEmailConfig> findByJobName(String jobName);

    boolean existsByTargetTypeAndCronExpression(String targetType, String cronExpression);
    boolean existsByTargetTypeAndCronExpressionAndIdNot(String targetType, String cronExpression, Long id);

    Optional<AgingEmailConfig> findByTargetTypeAndCronExpression(String targetType, String cronExpression);
    

}