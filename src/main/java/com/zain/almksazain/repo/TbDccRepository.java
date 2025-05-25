package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TbDccRepository extends JpaRepository<DCC, Long> {
    @Query("SELECT d FROM DCC d WHERE d.vendorNumber = :vendorNumber")
    Page<DCC> findByVendorNumber(@Param("vendorNumber") String vendorNumber, Pageable pageable);

    @Query("SELECT d FROM DCC d")
    Page<DCC> findAll(Pageable pageable);

    @Query("SELECT COUNT(d) FROM DCC d WHERE d.vendorNumber = :vendorNumber")
    long countByVendorNumber(@Param("vendorNumber") String vendorNumber);
    List<DCC> findByRecordNoIn(List<Long> recordNos);
}