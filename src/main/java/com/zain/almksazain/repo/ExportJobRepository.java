package com.zain.almksazain.repo;

import com.zain.almksazain.model.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, String> {

    List<ExportJob> findByStatusInAndCompletedAtBefore(Collection<String> statuses, LocalDateTime cutoff);

    List<ExportJob> findByStatusInAndCreatedAtBefore(Collection<String> statuses, LocalDateTime cutoff);
}
