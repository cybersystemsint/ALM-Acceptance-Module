///*
// * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
// * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
// */
//package com.zain.almksazain.repo;
//
//import com.zain.almksazain.model.tb_Arc_ApprovalRecords;
//import java.util.List;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//
///**
// *
// * @author jgithu
// */
//public interface tbArcApprovalRecordsRepo extends JpaRepository<tb_Arc_ApprovalRecords, Long> {
//
//
//    tb_Arc_ApprovalRecords findByRecordId(String approvalRecordId);
//
//    tb_Arc_ApprovalRecords findByRecordNo(long recordNo);
//
//    @Query(value = "SELECT * FROM tb_Arc_ApprovalRecords d WHERE d.recordId = :recordId ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
//    tb_Arc_ApprovalRecords findTopByRecordId(@Param("recordId") Integer recordId);
//
//}
