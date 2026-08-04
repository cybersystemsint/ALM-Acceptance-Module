package com.zain.almksazain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.almksazain.model.UplActionType;
import com.zain.almksazain.model.UplChangeRequest;
import com.zain.almksazain.model.UplChangeRequestStatus;

public interface UplChangeRequestRepo extends JpaRepository<UplChangeRequest, Long> {
    List<UplChangeRequest> findByStatusOrderByRequestedAtDesc(UplChangeRequestStatus status);
    List<UplChangeRequest> findByBatchIdOrderByRecordIdAsc(String batchId);
    List<UplChangeRequest> findByRequestedByOrderByRequestedAtDesc(Integer requestedBy);
    List<UplChangeRequest> findByUplRecordNoAndStatus(Long uplRecordNo, UplChangeRequestStatus status);
    List<UplChangeRequest> findByUplRecordNoAndStatusAndChangeType(Long uplRecordNo, UplChangeRequestStatus status, UplActionType changeType);
}
