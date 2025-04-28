package com.zain.almksazain.repo;

import com.zain.almksazain.model.DccPoCombinedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DccCombinedViewrepo extends JpaRepository<DccPoCombinedView,Long> {
    List<DccPoCombinedView> findBySupplierId(String supplierID);
    List<DccPoCombinedView> findBySupplierIdAndDccStatus(String supplierID,String dccstatus);
}
