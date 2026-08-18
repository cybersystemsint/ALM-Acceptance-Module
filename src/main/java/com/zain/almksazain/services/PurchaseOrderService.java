package com.zain.almksazain.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.repo.tbPurchaseOrderRepo;

@Service
public class PurchaseOrderService {

    private static final Logger logger = LogManager.getLogger(PurchaseOrderService.class);

    @Autowired
    private tbPurchaseOrderRepo purchaseOrderRepo;

    @Transactional
    public Optional<Map<String, Object>> updateFavouriteByRecordNo(long recordNo, boolean isFavourite) {
        tbPurchaseOrder record = purchaseOrderRepo.findByRecordNo(recordNo);
        if (record == null) {
            return Optional.empty();
        }

        List<tbPurchaseOrder> lines = purchaseOrderRepo.findByPoNumber(record.getPoNumber());
        for (tbPurchaseOrder line : lines) {
            line.setFavourite(isFavourite);
        }
        purchaseOrderRepo.saveAll(lines);

        logger.info("Updated isFavourite={} for recordNo={}, poNumber={}, linesUpdated={}",
                isFavourite, recordNo, record.getPoNumber(), lines.size());

        Map<String, Object> result = new HashMap<>();
        result.put("recordNo", recordNo);
        result.put("poNumber", record.getPoNumber());
        result.put("linesUpdated", lines.size());
        return Optional.of(result);
    }
}
