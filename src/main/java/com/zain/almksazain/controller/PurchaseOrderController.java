package com.zain.almksazain.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zain.almksazain.services.PurchaseOrderService;

@RestController
public class PurchaseOrderController {

    private static final Logger logger = LogManager.getLogger(PurchaseOrderController.class);

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @PatchMapping("/purchase-orders/{recordNo}/favourite")
    @CrossOrigin(origins = "*", allowedHeaders = "*", maxAge = 3600)
    public ResponseEntity<Map<String, Object>> updateFavourite(
            @PathVariable long recordNo,
            @RequestBody Map<String, Boolean> body) {

        Boolean isFavourite = body != null ? body.get("isFavourite") : null;
        if (isFavourite == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "isFavourite is required"));
        }

        try {
            Optional<Map<String, Object>> result = purchaseOrderService.updateFavouriteByRecordNo(recordNo, isFavourite);
            if (result.isEmpty()) {
                Map<String, Object> notFound = new HashMap<>();
                notFound.put("message", "Purchase order record not found");
                notFound.put("recordNo", recordNo);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound);
            }

            Map<String, Object> response = new HashMap<>(result.get());
            response.put("message", "Favourite updated successfully");
            response.put("isFavourite", isFavourite);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating favourite for recordNo={}", recordNo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error: " + e.getMessage()));
        }
    }
}
