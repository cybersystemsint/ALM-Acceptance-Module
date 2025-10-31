package com.zain.almksazain.repo;

import com.zain.almksazain.model.AcceptanceRequestReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcceptanceRequestReceiptRepository extends JpaRepository<AcceptanceRequestReceipt, Integer> {

    List<AcceptanceRequestReceipt> findByCategoryApprovalRequestIdIn(List<Integer> categoryApprovalRequestIds);
      //Find all receipts by a specific user
    List<AcceptanceRequestReceipt> findByApprovedBy(Integer approvedBy);
}