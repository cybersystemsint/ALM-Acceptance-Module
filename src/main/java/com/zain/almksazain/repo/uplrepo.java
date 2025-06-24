package com.zain.almksazain.repo;

import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.upldata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface uplrepo extends JpaRepository<upldata, Long> {

    upldata findByUplLine(long uplLine);

    upldata findByRecordNo(long recordno);

    List<upldata> findByPoId(String poid);

    @Override
    List<upldata> findAll();

    @Query(value = "SELECT * FROM tb_UPL d WHERE d.poId = :poId ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    upldata findTopByPoNumber(@Param("poId") String poNumber);
}
