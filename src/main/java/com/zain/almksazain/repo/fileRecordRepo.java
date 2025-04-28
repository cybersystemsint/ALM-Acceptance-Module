/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.repo;

import com.zain.almksazain.model.FileRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author jgithu
 */
public interface fileRecordRepo extends JpaRepository<FileRecord, Long> {

    FileRecord findByRecordNo(long recordNo);

    List<FileRecord> findByPoNumberAndDccId(String poNumber, int dccId);
}
