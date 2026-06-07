package com.zain.almksazain.service;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.request.DccPORequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DccPOV2Service {

    /**
     * Paginated fetch for /combined-view.
     * When pendingApprovers is provided, resolves matching DCC IDs from
     * the approvals table first (2 queries) instead of loading all records
     * and filtering in memory.
     */
    CompletableFuture<DccPOResponseDTO> getCombinedView(DccPORequest request);

    CompletableFuture<List<DccPOCombinedViewDTO>> getExportData(DccPORequest request);
}