package com.zain.almksazain.service;

import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.ExportPageResult;
import com.zain.almksazain.DTO.request.DccPORequest;

import java.util.concurrent.CompletableFuture;

public interface DccPOV2Service {

    /**
     * Paginated fetch for /combined-view.
     * When pendingApprovers is provided, resolves matching DCC IDs from
     * the approvals table first (2 queries) instead of loading all records
     * and filtering in memory.
     */
    CompletableFuture<DccPOResponseDTO> getCombinedView(DccPORequest request);

    /**
     * Fetches one bounded page of export rows (full detail, same shape as the
     * old unbounded getExportData), so a caller can stream the export to disk
     * page by page instead of holding the entire filtered result set - which
     * can be the whole system's acceptance-request history - in memory at once.
     *
     * @param page 1-based page number
     * @param size max DCC records to fetch for this page (a DCC can still
     *             expand into multiple rows via its line items)
     */
    CompletableFuture<ExportPageResult> getExportDataPage(DccPORequest request, int page, int size);
}