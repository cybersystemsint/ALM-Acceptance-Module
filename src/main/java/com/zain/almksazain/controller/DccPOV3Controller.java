package com.zain.almksazain.controller;

import com.zain.almksazain.DTO.DccPORequestDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.serviceImplementors.DccPOCombinedViewResponseMapper;
import com.zain.almksazain.serviceImplementors.DccPOService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;

/**
 * Optimised DCC PO combined view based on POST /dcc-po/combined-view (v1).
 * Same request payload and response shape; faster DB and approval loading.
 */
@RestController
@RequestMapping("/dcc-po/v3")
public class DccPOV3Controller {

    private static final Logger logger = LogManager.getLogger(DccPOV3Controller.class);
    private static final long TIMEOUT_MS = 120_000L;

    @Autowired
    private DccPOService dccPOService;

    @PostMapping("/combined-view")
    public DeferredResult<ResponseEntity<DccPOResponseDTO>> getDccPOCombinedView(
            @RequestBody DccPORequestDTO request) {

        DeferredResult<ResponseEntity<DccPOResponseDTO>> deferredResult = new DeferredResult<>(TIMEOUT_MS);

        int page = Math.max(request.getPage(), 1);
        int size = Math.max(request.getSize(), 1);

        CompletableFuture<DccPOService.DccPOFetchResult> future = dccPOService.getDccPOCombinedViewV3(
                request.getSupplierId(),
                request.getPendingApprovers(),
                page,
                size,
                request.getColumnName(),
                request.getSearchQuery(),
                request.getExporting() != null ? request.getExporting() : false,
                request.getOperator());

        future.thenAccept(result -> {
            DccPOResponseDTO responseDTO = DccPOCombinedViewResponseMapper.toResponse(result, page, size);
            logger.info("V3 combined-view — {} parent records (page={}, size={}, supplierId={}, pendingApprovers={})",
                    responseDTO.getData() != null ? responseDTO.getData().size() : 0,
                    page, size, request.getSupplierId(), request.getPendingApprovers());
            deferredResult.setResult(ResponseEntity.ok(responseDTO));
        }).exceptionally(throwable -> {
            logger.error("Error processing V3 DCC PO Combined View request", throwable);
            if (throwable.getCause() instanceof DccPOProcessingException) {
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error: " + throwable.getCause().getMessage()));
            } else {
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Unexpected error occurred"));
            }
            return null;
        });

        return deferredResult;
    }
}
