package com.zain.almksazain.repo;

import com.zain.almksazain.model.DCC;
import com.zain.almksazain.model.DCCLineItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DCCRepository extends JpaRepository<DCC, Long>, JpaSpecificationExecutor<DCC> {

    DCC findByRecordNo(long recordNo);

    @Query(value = "SELECT * FROM tb_DCC d WHERE d.poNumber = :poNumber ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCC findTopByPoNumber(@Param("poNumber") String poNumber);

    @Query(value = "SELECT * FROM tb_DCC d WHERE d.recordNo = :recordNo ORDER BY d.recordNo DESC LIMIT 1", nativeQuery = true)
    DCC findTopByRecordNo(@Param("recordNo") Integer recordNo);

    @Query("SELECT d.recordNo  FROM DCC  d WHERE d.poNumber  = :poNumber AND LOWER(d.status) IN :statuses")
    List<Integer> findByPoNumberAndStatus(@Param("poNumber") String poNumber, @Param("statuses") List<String> statuses);
    List<DCC> findByStatusIgnoreCase(String status);
    Page<DCC> findByStatusIgnoreCase(String status, Pageable pageable);
        @Query("SELECT d FROM DCC d WHERE d.recordNo IN :recordNos")
    List<DCC> findByRecordNoIn(@Param("recordNos") List<Long> recordNos);
       Page<DCC> findAllBySupplierId(String supplierId, Pageable pageable);
           // For pageable queries:
    Page<DCC> findAllByStatus(String status, Pageable pageable);
    Page<DCC> findAllBySupplierIdAndStatus(String supplierId, String status, Pageable pageable);

    // For unpaged / list results (used with Pageable.unpaged())
    List<DCC> findAllByStatus(String status);
    List<DCC> findAllBySupplierIdAndStatus(String supplierId, String status);

    @Query(
      value = "SELECT DISTINCT d.* FROM tb_DCC d " +
              "INNER JOIN tb_PurchaseOrder po ON d.poNumber = po.poNumber " +
              "WHERE LOWER(d.status) = LOWER(:status) AND po.vendorNumber = :supplierId",
      countQuery = "SELECT COUNT(DISTINCT d.recordNo) FROM tb_DCC d " +
                   "INNER JOIN tb_PurchaseOrder po ON d.poNumber = po.poNumber " +
                   "WHERE LOWER(d.status) = LOWER(:status) AND po.vendorNumber = :supplierId",
      nativeQuery = true
    )
    Page<DCC> findAllBySupplierVendorAndStatus(
        @Param("supplierId") String supplierId,
        @Param("status") String status,
        Pageable pageable
    );

    @Query(
        value = "SELECT d FROM DCC d WHERE LOWER(d.status) <> LOWER(:status)",
        countQuery = "SELECT COUNT(d) FROM DCC d WHERE LOWER(d.status) <> LOWER(:status)"
    )
    Page<DCC> findAllByStatusNot(@Param("status") String status, Pageable pageable);

    @Query(
        value = "SELECT DISTINCT d.* FROM tb_DCC d " +
                "INNER JOIN tb_PurchaseOrder po ON d.poNumber = po.poNumber " +
                "WHERE LOWER(d.status) <> LOWER(:status) AND po.vendorNumber = :supplierId",
        countQuery = "SELECT COUNT(DISTINCT d.recordNo) FROM tb_DCC d " +
                     "INNER JOIN tb_PurchaseOrder po ON d.poNumber = po.poNumber " +
                     "WHERE LOWER(d.status) <> LOWER(:status) AND po.vendorNumber = :supplierId",
        nativeQuery = true
    )
    Page<DCC> findAllBySupplierVendorAndStatusNot(
        @Param("supplierId") String supplierId,
        @Param("status") String status,
        Pageable pageable
    );

}
