package com.zain.almksazain.repo;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.zain.almksazain.model.DCCLineItem;

public interface PoUplAcceptanceStatsRepo extends JpaRepository<DCCLineItem, Long> {

    @Query("SELECT d.poNumber, ln.lineNumber, ln.uplLineNumber, COALESCE(SUM(ln.deliveredQty), 0) "
            + "FROM DCCLineItem ln, DCC d "
            + "WHERE ln.dccId = CAST(d.recordNo AS string) "
            + "AND d.poNumber IN :poNumbers "
            + "AND LOWER(d.status) NOT IN :excludedStatuses "
            + "GROUP BY d.poNumber, ln.lineNumber, ln.uplLineNumber")
    List<Object[]> sumDeliveredQtyByPoLineUpl(
            @Param("poNumbers") Collection<String> poNumbers,
            @Param("excludedStatuses") Collection<String> excludedStatuses);

    @Query("SELECT upl.poNumber, upl.poLineNumber, "
            + "COALESCE(SUM((upl.uplLineQuantity * upl.uplLineUnitPrice) / "
            + "NULLIF(upl.poLineQuantity * upl.poLineUnitPrice, 0)), 0) "
            + "FROM tb_PurchaseOrderUPL upl "
            + "WHERE upl.poNumber IN :poNumbers AND upl.uplLineQuantity < 0 "
            + "GROUP BY upl.poNumber, upl.poLineNumber")
    List<Object[]> sumNegativeLineAcceptanceByPo(@Param("poNumbers") Collection<String> poNumbers);
}
