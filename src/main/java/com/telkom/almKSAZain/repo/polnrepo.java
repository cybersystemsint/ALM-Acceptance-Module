package com.telkom.almKSAZain.repo;

import com.telkom.almKSAZain.model.polndata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface polnrepo extends JpaRepository<polndata, Long> {
    List<polndata> findByPoIdAndItemCode(String poId, String itemCode);
    polndata findByPoId(String PoId);
    polndata findByRecordNo(long recordNo);
}
