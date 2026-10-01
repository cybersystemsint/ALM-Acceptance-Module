package com.zain.almksazain.DTO;

import java.util.List;

/**
 * One page of export rows, plus whether more pages are likely to follow.
 *
 * "hasMore" reflects the underlying DCC fetch for this page, not the row count
 * in {@link #getRows()} - a page's DCCs can all be dropped by in-memory row
 * filters and still leave more DCCs to fetch on the next page, so callers must
 * keep paging on hasMore rather than stopping when rows happens to be empty.
 */
public class ExportPageResult {

    private final List<DccPOCombinedViewDTO> rows;
    private final boolean hasMore;

    public ExportPageResult(List<DccPOCombinedViewDTO> rows, boolean hasMore) {
        this.rows = rows;
        this.hasMore = hasMore;
    }

    public List<DccPOCombinedViewDTO> getRows() {
        return rows;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
