/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.tb_Approval_Log;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author jgithu
 */
public interface tbApprovalLogRepo extends JpaRepository<tb_Approval_Log, Long> {

 //   List<tb_Approval_Log> findByRecordTypeAndApprovalRecordId(String RecordType, String approvalRecordId);

   // tb_Approval_Log findByRecordId(String approvalRecordId);

    tb_Approval_Log findByRecordNo(long recordNo);

}
