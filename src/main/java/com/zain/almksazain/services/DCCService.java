package com.zain.almksazain.services;

import com.zain.almksazain.repo.*;
import com.zain.almksazain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 *
 * @author mwangudi
 */
@Service
public class DCCService {
    private static final Logger logger = LoggerFactory.getLogger(DCCService.class);
    @Autowired
    private tbPurchaseOrderUPLRepo purchaseOrderUPLRepo;

    @Autowired
    private DccLineRepo dccLineItemRepo;

    public void updatePoAcceptanceQty() {
        logger.info("Updating PO Acceptance Quantities for previous records");

        List<DCCLineItem> dccLines = dccLineItemRepo.findAll(); // or filter if needed

        for (DCCLineItem dccLineItem : dccLines) {
            String poNumber = dccLineItem.getPoId();
            String poLineNumber = dccLineItem.getLineNumber();
            String uplLineNumber = dccLineItem.getUplLineNumber();
            double poAcceptanceQty = 0;
            double delivered = dccLineItem.getDeliveredQty();

            if (uplLineNumber != null && !uplLineNumber.isEmpty()) {
                tb_PurchaseOrderUPL topRecord = purchaseOrderUPLRepo.findTopByPoNumberAndPoLineNumberAndUplLine(poNumber, poLineNumber, uplLineNumber);

                double uplLineUnitPrice = topRecord != null ? topRecord.getUplLineUnitPrice() : 0;
                double poLineUnitPriceCalc = topRecord != null ? topRecord.getPoLineUnitPrice() : 0;
                double lineTotal = uplLineUnitPrice * delivered;

                if (poLineUnitPriceCalc != 0) {
                    poAcceptanceQty = BigDecimal.valueOf(lineTotal).divide(BigDecimal.valueOf(poLineUnitPriceCalc), 20, RoundingMode.HALF_UP).doubleValue();
                } else {
                    poAcceptanceQty = 0;
                }
            }
            else { // None UPL Based
                poAcceptanceQty = delivered;
            }

            dccLineItem.setPoAcceptanceQty(poAcceptanceQty);
            dccLineItemRepo.save(dccLineItem);
        }

        logger.info("PO Acceptance Quantities for previous records update completed");
    }
}