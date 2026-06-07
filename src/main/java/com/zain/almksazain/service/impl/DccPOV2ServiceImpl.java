package com.zain.almksazain.service.impl;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOLineItemDTO;
import com.zain.almksazain.DTO.DccPOParentDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.DTO.request.DccPORequest;
import com.zain.almksazain.exception.DccPOProcessingException;
import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;
import com.zain.almksazain.service.DccPOV2Service;
import com.zain.almksazain.serviceImplementors.DccSiteRegionResolver;
import com.zain.almksazain.serviceImplementors.DccSpecification;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.*;

@Service
public class DccPOV2ServiceImpl implements DccPOV2Service {

    private static final Logger logger = LogManager.getLogger(DccPOV2ServiceImpl.class);
    private static final String DATE_FMT = "d-MMM-yyyy";

    @Autowired private TbDccRepository tbDccRepository;
    @Autowired private TbDccLnRepository tbDccLnRepository;
    @Autowired private TbPurchaseOrderRepository tbPurchaseOrderRepository;
    @Autowired private TbPurchaseOrderUplRepository tbPurchaseOrderUplRepository;
    @Autowired private TbCategoryApprovalRequestsRepository approvalRequestsRepository;
    @Autowired private TbCategoryApprovalsRepository approvalsRepository;
    @Autowired private tbSiteRepo tbSiteRepo;

    // ─── PUBLIC API ───────────────────────────────────────────────────────────

@Override
@Async("taskExecutor")
public CompletableFuture<DccPOResponseDTO> getCombinedView(DccPORequest request) {
    // No supplyAsync wrapper needed — @Async wraps this automatically
    int page = Math.max(request.getPage(), 1);
    int size = Math.max(request.getSize(), 1);

    FetchContext ctx = buildFetchContext(request, page, size, false);
    List<DccPOCombinedViewDTO> rows = fetchRows(ctx);
    rows = applyInMemoryFilters(rows, request);

    List<DccPOParentDTO> parents = groupIntoParents(rows, ctx.fetchParentOnly);

    // long totalRecords = ctx.approverFilteredTotal >= 0
    //         ? ctx.approverFilteredTotal
    //         : ctx.totalFiltered;
    long totalRecords = ctx.totalFiltered;

    DccPOResponseDTO resp = new DccPOResponseDTO();
    resp.setTotalRecords(totalRecords);
    resp.setData(parents);
    resp.setTotalPages((int) Math.ceil((double) totalRecords / size));
    resp.setPageSize(size);
    resp.setCurrentPage(page);
    logger.info("getCombinedView — {} parents, totalRecords={}", parents.size(), totalRecords);
    return CompletableFuture.completedFuture(resp);
}

@Override
@Async("taskExecutor")
public CompletableFuture<List<DccPOCombinedViewDTO>> getExportData(DccPORequest request) {
    FetchContext ctx = buildFetchContext(request, 1, Integer.MAX_VALUE, true);
    List<DccPOCombinedViewDTO> rows = fetchRows(ctx);
    rows = applyInMemoryFilters(rows, request);
    logger.info("getExportData — {} rows", rows.size());
    return CompletableFuture.completedFuture(rows);
}

    // ─── FETCH CONTEXT ────────────────────────────────────────────────────────

 private FetchContext buildFetchContext(DccPORequest req, int page, int size, boolean exporting) {

    // ── Approver pre-filter ────────────────────────────────────────────────
    Set<Long> allowedIds = null;
    long approverFilteredTotal = -1L;

    if (hasValue(req.getPendingApprovers())) {
        List<Long> matched = approvalsRepository
                .findDccIdsByPendingApproverName(req.getPendingApprovers().trim());
        if (matched.isEmpty()) {
            logger.info("No DCCs for pending approver '{}'", req.getPendingApprovers());
            return FetchContext.empty();
        }
        allowedIds = new HashSet<>(matched);
        approverFilteredTotal = matched.size();  // exact count, not a separate DB call
        logger.info("Approver pre-filter matched {} DCC IDs", allowedIds.size());
    }

    // ── Build filterMap ────────────────────────────────────────────────────
    Map<String, String> filterMap = new HashMap<>();
    if (req.getFilterBy() != null) {
        for (DccPORequest.FilterCriteria f : req.getFilterBy()) {
            if (f.getColumn() != null && f.getValue() != null && !f.getValue().trim().isEmpty()) {
                filterMap.put(f.getColumn(), f.getValue());
            }
        }
    }

    // ── Build spec ────────────────────────────────────────────────────────
    // Pass allowedIds always — DccSpecification must add "record_no IN (...)"
    // when allowedIds is non-null and non-empty.
    DccSpecification spec = new DccSpecification(
            req.getSupplierId(),
            null,                      // pendingApprovers handled above
            req.getColumnName(),
            req.getSearchQuery(),
            req.getOperator(),
            allowedIds,                // null == no restriction; non-null == hard IN filter
            filterMap,
            req.getCreatedDateStart(),
            req.getCreatedDateEnd());

    // ── Fetch DCCs ────────────────────────────────────────────────────────
    Page<DCC> dccPage;
    if (exporting) {
        List<DCC> all = tbDccRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "recordNo"));
        dccPage = new org.springframework.data.domain.PageImpl<>(all);
    } else {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "recordNo"));
        dccPage = tbDccRepository.findAll(spec, pageable);
    }

    List<DCC> dccList = dccPage.getContent();
    // When allowedIds is active, totalFiltered = allowedIds.size() (already exact).
    // Otherwise use the DB count.
    long totalFiltered = (approverFilteredTotal >= 0)
            ? approverFilteredTotal
            : (exporting ? dccList.size() : dccPage.getTotalElements());

    logger.info("DB returned {} DCC records (totalFiltered={})", dccList.size(), totalFiltered);

    if (dccList.isEmpty()) return FetchContext.empty();
    validatePoNumbers(dccList);

    boolean fetchParentOnly = !exporting
            && !(hasValue(req.getColumnName())
                    && req.getColumnName().equalsIgnoreCase("recordNo")
                    && hasValue(req.getSearchQuery()));

    List<Long>   dccIds    = dccList.stream().map(DCC::getRecordNo).collect(Collectors.toList());
    List<String> poNumbers = dccList.stream().map(DCC::getPoNumber).distinct().collect(Collectors.toList());

    Map<String, List<tbPurchaseOrder>>            poMap      = batchLoadPurchaseOrders(poNumbers);
    Map<Long,   TbCategoryApprovalRequests>       latestReqs = batchLoadLatestApprovalRequests(dccIds);
    Map<Long,   List<TbCategoryApprovalRequests>> allReqs    = batchLoadAllRequestsByDcc(dccIds);
    Map<Long,   List<TbCategoryApprovals>>        approvMap  = batchLoadApprovals(allReqs);

    Map<String, List<tb_PurchaseOrderUPL>> uplMap      = null;
    Map<Long,   List<DCCLineItem>>         lnMap       = null;
    Map<String, tb_Site>                   siteBySiteId = null;
    QuantityContext                         qtyCtx      = null;

    if (!fetchParentOnly) {
        uplMap       = batchLoadUplMap(poNumbers);
        lnMap        = batchLoadDccLineItems(dccIds);
        siteBySiteId = DccSiteRegionResolver.loadSiteBySiteIdMap(lnMap, tbSiteRepo);
        qtyCtx       = buildQuantityContext(poNumbers, dccList, uplMap, lnMap);
    }

    return new FetchContext(dccList, totalFiltered, approverFilteredTotal, fetchParentOnly,
            poMap, latestReqs, allReqs, approvMap, uplMap, lnMap, siteBySiteId, qtyCtx);
}

    // ─── QUANTITY CONTEXT (eliminates N+1) ────────────────────────────────────

    /**
     * Pre-loads and pre-computes all data needed by {@link #calculateQuantities}.
     * Previously that method issued 4 DB queries per line item per UPL; now it's zero.
     *
     * Keys use the pattern "poNumber|poLineNumber|uplLine" or "poNumber|poLineNumber".
     */
    private QuantityContext buildQuantityContext(
            List<String> poNumbers,
            List<DCC> dccList,
            Map<String, List<tb_PurchaseOrderUPL>> uplMap,
            Map<Long,   List<DCCLineItem>>          lnMap) {

        // ── 1. Index DCC status by recordNo for O(1) lookup ───────────────────
        // (dccList already contains all DCCs for this page/export)
        Map<Long, String> dccStatusByRecordNo = dccList.stream()
                .collect(Collectors.toMap(DCC::getRecordNo, DCC::getStatus, (a, b) -> a));

        // ── 2. Load ALL line items for the PO numbers in bulk ─────────────────
        // We need items beyond just the current page's DCCs because the delivered-qty
        // sum includes items from OTHER DCCs sharing the same PO/line/upl.
        List<DCCLineItem> allLnForPos = tbDccLnRepository.findByPoIdIn(poNumbers);

        // ── 3. Load ALL DCC statuses for those extra dccIds (not on current page) ──
        Set<Long> extraDccIds = allLnForPos.stream()
                .map(ln -> {
                    try { return Long.parseLong(ln.getDccId()); }
                    catch (NumberFormatException e) { return -1L; }
                })
                .filter(id -> id > 0 && !dccStatusByRecordNo.containsKey(id))
                .collect(Collectors.toSet());

        if (!extraDccIds.isEmpty()) {
            tbDccRepository.findByRecordNoIn(new ArrayList<>(extraDccIds))
                    .forEach(d -> dccStatusByRecordNo.put(d.getRecordNo(), d.getStatus()));
        }

        // ── 4. Build delivered-qty map: "poNumber|poLineNumber|uplLine" → sum ─
        // Mirrors the filter in the old calculateQuantities:
        //   status NOT IN ('incomplete','rejected') AND uplLineNumber present
        Set<String> excludedStatuses = new HashSet<>(Arrays.asList("incomplete", "rejected"));

        Map<String, Double> deliveredByUplKey = new HashMap<>();
        for (DCCLineItem ln : allLnForPos) {
            if (!hasValue(ln.getUplLineNumber())) continue;
            if (ln.getDeliveredQty() == null) continue;
            long dccId;
            try { dccId = Long.parseLong(ln.getDccId()); } catch (NumberFormatException e) { continue; }
            String status = dccStatusByRecordNo.get(dccId);
            if (status != null && excludedStatuses.contains(status.toLowerCase())) continue;
            String key = ln.getPoId() + "|" + ln.getLineNumber() + "|" + ln.getUplLineNumber();
            deliveredByUplKey.merge(key, ln.getDeliveredQty(), Double::sum);
        }

        // ── 5. Build exists map: "poNumber|poLineNumber|uplLine" → boolean ────
        Map<String, Boolean> dccLnExistsByKey = new HashMap<>();
        for (DCCLineItem ln : allLnForPos) {
            if (!hasValue(ln.getPoId()) || !hasValue(ln.getLineNumber())) continue;
            String key = ln.getPoId() + "|" + ln.getLineNumber() + "|"
                    + (hasValue(ln.getUplLineNumber()) ? ln.getUplLineNumber() : "");
            dccLnExistsByKey.put(key, true);
        }

        // ── 6. Build UPL-by-PO-line map: "poNumber|poLineNumber" → List<UPL> ─
        Map<String, List<tb_PurchaseOrderUPL>> uplByPoLine = new HashMap<>();
        for (List<tb_PurchaseOrderUPL> upls : uplMap.values()) {
            for (tb_PurchaseOrderUPL upl : upls) {
                if (!hasValue(upl.getPoNumber()) || !hasValue(upl.getPoLineNumber())) continue;
                String key = upl.getPoNumber() + "|" + upl.getPoLineNumber();
                uplByPoLine.computeIfAbsent(key, k -> new ArrayList<>()).add(upl);
            }
        }

        logger.info("QuantityContext built: {} deliveredKeys, {} uplByPoLineKeys, {} existsKeys",
                deliveredByUplKey.size(), uplByPoLine.size(), dccLnExistsByKey.size());

        return new QuantityContext(deliveredByUplKey, uplByPoLine, dccLnExistsByKey);
    }

    private List<DccPOCombinedViewDTO> fetchRows(FetchContext ctx) {
        if (ctx.isEmpty) return List.of();
        SimpleDateFormat fmt = new SimpleDateFormat(DATE_FMT);
        return ctx.fetchParentOnly ? buildParentOnlyRows(ctx, fmt) : buildFullRows(ctx, fmt);
    }

    // ─── IN-MEMORY FILTERS ────────────────────────────────────────────────────

 private List<DccPOCombinedViewDTO> applyInMemoryFilters(
        List<DccPOCombinedViewDTO> rows, DccPORequest req) {

    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FMT, Locale.ENGLISH);

    // NOTE: pendingApprovers is now handled at the DB level via allowedIds in
    // DccSpecification — do NOT filter it again here, or valid rows get dropped.

    if (req.getFilterBy() != null) {
        for (DccPORequest.FilterCriteria fc : req.getFilterBy()) {
            if (fc.getColumn() == null || !hasValue(fc.getValue())) continue;
            String col = fc.getColumn().toLowerCase();
            String val = fc.getValue().trim();
            String op  = fc.getOperator();

            switch (col) {
                case "pendingapprovers":
                case "pendingapprover":
                    // Only apply in-memory if an explicit filterBy criterion was sent
                    // (not the top-level pendingApprovers field — that's already DB-filtered)
                    rows = rows.stream()
                            .filter(dto -> dto.getPendingApprovers() != null
                                    && applyOp(dto.getPendingApprovers(), val, op))
                            .collect(Collectors.toList());
                    break;
                case "approvalcount":
                    try {
                        Long expected = Long.parseLong(val);
                        rows = rows.stream()
                                .filter(dto -> expected.equals(dto.getApprovalCount()))
                                .collect(Collectors.toList());
                    } catch (NumberFormatException ignored) {}
                    break;
                case "supplierid":
                    rows = rows.stream()
                            .filter(dto -> dto.getSupplierId() != null
                                    && applyOp(dto.getSupplierId(), val, op))
                            .collect(Collectors.toList());
                    break;
                case "dateapproved":
                    rows = rows.stream()
                            .filter(dto -> dto.getDateApproved() != null
                                    && applyOp(dto.getDateApproved(), val, op))
                            .collect(Collectors.toList());
                    break;
                default:
                    break;
            }
        }
    }

    String aStart = req.getApprovedDateStart();
    String aEnd   = req.getApprovedDateEnd();
    if (hasValue(aStart) || hasValue(aEnd)) {
        rows = rows.stream().filter(dto -> {
            try {
                if (!hasValue(dto.getDateApproved())) return false;
                Date d = sdf.parse(dto.getDateApproved());
                if (hasValue(aStart) && d.before(sdf.parse(aStart))) return false;
                if (hasValue(aEnd)   && d.after(sdf.parse(aEnd)))    return false;
                return true;
            } catch (ParseException e) { return true; }
        }).collect(Collectors.toList());
    }

    return rows;
}
    private boolean applyOp(String fieldValue, String query, String op) {
        if (fieldValue == null || query == null) return false;
        String fv = fieldValue.toLowerCase();
        String q  = query.toLowerCase();
        String normalizedOp = op != null ? op.toUpperCase() : "CONTAINS";
        switch (normalizedOp) {
            case "EQUALS":
            case "EQ":          return fv.equals(q);
            case "STARTS_WITH":
            case "STARTSWITH":  return fv.startsWith(q);
            case "ENDS_WITH":
            case "ENDSWITH":    return fv.endsWith(q);
            default:            return fv.contains(q);
        }
    }

    // ─── ROW BUILDERS ─────────────────────────────────────────────────────────

 private List<DccPOCombinedViewDTO> buildParentOnlyRows(FetchContext ctx, SimpleDateFormat fmt) {
    List<DccPOCombinedViewDTO> result = new ArrayList<>(ctx.dccList.size());
    for (DCC dcc : ctx.dccList) {
        List<tbPurchaseOrder> pos = ctx.poMap.getOrDefault(dcc.getPoNumber(), List.of());
        if (pos.isEmpty()) {
            // Matches original service behaviour: log and skip, don't throw
            logger.error("No Purchase Order found for poNumber: {} in DCC record: {}. Skipping.",
                    dcc.getPoNumber(), dcc.getRecordNo());
            continue;
        }

        TbCategoryApprovalRequests latestReq = ctx.latestReqs.get(dcc.getRecordNo());
        DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();
        populateDccFields(dto, dcc, fmt, latestReq);
        populatePoFields(dto, pos.get(0));

        if (latestReq != null) {
            List<TbCategoryApprovalRequests> all = ctx.allReqs.getOrDefault(dcc.getRecordNo(), List.of());
            calculateApprovalFieldsBatched(dto, latestReq, all, collectApprovals(all, ctx.approvMap));
        } else {
            setDefaultApprovalFields(dto);
        }
        result.add(dto);
    }
    return result;
}
    private List<DccPOCombinedViewDTO> buildFullRows(FetchContext ctx, SimpleDateFormat fmt) {

        List<DccPOCombinedViewDTO> result = new ArrayList<>();
        Set<Long> processed = new HashSet<>();

        for (DCC dcc : ctx.dccList) {
            if (processed.contains(dcc.getRecordNo())) continue;

            List<tbPurchaseOrder> pos = ctx.poMap.getOrDefault(dcc.getPoNumber(), List.of());
            if (pos.isEmpty()) throw new DccPOProcessingException(
                    "Missing PO for DCC record: " + dcc.getRecordNo());

            List<tb_PurchaseOrderUPL> uplList = ctx.uplMap.getOrDefault(dcc.getPoNumber(), List.of());
            List<DCCLineItem>         lnList  = ctx.lnMap.getOrDefault(dcc.getRecordNo(), List.of());

            if (lnList.isEmpty() || uplList.isEmpty()) {
                processed.add(dcc.getRecordNo());
                continue;
            }

            TbCategoryApprovalRequests latestReq = ctx.latestReqs.get(dcc.getRecordNo());
            List<TbCategoryApprovalRequests> allR = ctx.allReqs.getOrDefault(dcc.getRecordNo(), List.of());
            List<TbCategoryApprovals> allA = collectApprovals(allR, ctx.approvMap);

            result.addAll(buildLineRows(dcc, pos.get(0), uplList, lnList,
                    latestReq, allR, allA, fmt, ctx.qtyCtx, ctx.siteBySiteId));
            processed.add(dcc.getRecordNo());
        }
        return result;
    }

    private List<DccPOCombinedViewDTO> buildLineRows(
            DCC dcc, tbPurchaseOrder po,
            List<tb_PurchaseOrderUPL> uplList, List<DCCLineItem> lnList,
            TbCategoryApprovalRequests latestReq,
            List<TbCategoryApprovalRequests> allReqs, List<TbCategoryApprovals> allApprovals,
            SimpleDateFormat fmt, QuantityContext qtyCtx, Map<String, tb_Site> siteBySiteId) {

        List<DccPOCombinedViewDTO> dtos = new ArrayList<>();

        for (DCCLineItem ln : lnList) {
            for (tb_PurchaseOrderUPL upl : uplList) {
                boolean matches = hasValue(ln.getUplLineNumber())
                        ? (ln.getUplLineNumber().equals(upl.getUplLine())
                           && upl.getPoLineNumber().equals(ln.getLineNumber())
                           && upl.getPoNumber().equals(dcc.getPoNumber()))
                        : (po.getLineNumber().equals(ln.getLineNumber())
                           && po.getPoNumber().equals(dcc.getPoNumber()));
                if (!matches) continue;

                DccPOCombinedViewDTO dto = new DccPOCombinedViewDTO();
                populateDccFields(dto, dcc, fmt, latestReq);
                populateLineItemFields(dto, ln, siteBySiteId, fmt);
                populatePoAndUplFields(dto, ln, po, upl);
                // ── Zero DB calls — uses pre-computed QuantityContext ──────────
                calculateQuantitiesFromContext(dto, upl, qtyCtx);

                if (latestReq != null) {
                    calculateApprovalFieldsBatched(dto, latestReq, allReqs, allApprovals);
                } else {
                    setDefaultApprovalFields(dto);
                }
                dtos.add(dto);
            }
        }
        return dtos;
    }

    // ─── RESPONSE GROUPING ────────────────────────────────────────────────────

    private List<DccPOParentDTO> groupIntoParents(List<DccPOCombinedViewDTO> rows, boolean parentOnly) {
        Map<Long, List<DccPOCombinedViewDTO>> grouped = new LinkedHashMap<>();
        for (DccPOCombinedViewDTO r : rows)
            grouped.computeIfAbsent(r.getDccRecordNo(), k -> new ArrayList<>()).add(r);

        return grouped.entrySet().stream().map(entry -> {
            DccPOCombinedViewDTO first = entry.getValue().get(0);
            DccPOParentDTO p = new DccPOParentDTO();
            p.setRecordNo(first.getDccRecordNo());
            p.setDccPoNumber(first.getDccPoNumber());
            p.setNewProjectName(first.getNewProjectName());
            p.setDccAcceptanceType(first.getDccAcceptanceType());
            p.setDccStatus(first.getDccStatus());
            p.setDccCreatedDate(first.getDccCreatedDate());
            p.setDateApproved(first.getDateApproved());
            p.setVendorComment(first.getVendorComment());
            p.setDccId(first.getDccId());
            p.setPoId(first.getPoId());
            p.setProjectName(first.getProjectName());
            p.setSupplierId(first.getSupplierId());
            p.setVendorNumber(first.getVendorNumber());
            p.setVendorName(first.getVendorName());
            p.setCreatedBy(first.getCreatedBy());
            p.setCreatedByName(first.getCreatedByName());
            p.setApprovalCount(first.getApprovalCount());
            p.setPendingApprovers(first.getPendingApprovers());
            p.setApproverComment(first.getApproverComment());
            p.setUserAging(first.getUserAging());
            p.setTotalAging(first.getTotalAging());
            p.setVendorEmail(first.getDccVendorEmail());
            p.setDccCurrency(first.getDccCurrency());

            List<DccPOLineItemDTO> lineItems = new ArrayList<>();
            if (!parentOnly && first.getLnRecordNo() != null) {
                lineItems = entry.getValue().stream()
                        .map(this::toLineItemDto).collect(Collectors.toList());
            }
            p.setLineItems(lineItems);
            return p;
        }).collect(Collectors.toList());
    }

    private DccPOLineItemDTO toLineItemDto(DccPOCombinedViewDTO dto) {
        DccPOLineItemDTO li = new DccPOLineItemDTO();
        li.setRecordNo(dto.getLnRecordNo());
        li.setLnProductName(dto.getLnProductName());
        li.setSerialNumber(dto.getLnProductSerialNo());
        li.setDeliveredQty(dto.getLnDeliveredQty());
        li.setLocationName(dto.getLnLocationName());
        li.setRegion(dto.getRegion());
        li.setDateInService(dto.getLnInserviceDate());
        li.setLnUnitPrice(dto.getLnUnitPrice());
        li.setScopeOfWork(dto.getLnScopeOfWork());
        li.setRemarks(dto.getLnRemarks());
        li.setItemCode(dto.getUplLineItemCode());
        li.setLinkId(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
        li.setTagNumber(dto.getTagNumber());
        li.setPoLineNumber(dto.getLineNumber());
        li.setActualItemCode(dto.getActualItemCode());
        li.setUplLineNumber(dto.getUplLineNumber());
        li.setCurrency(dto.getDccCurrency());
        li.setPoId(dto.getPoId());
        li.setUPLACPTRequestValue(dto.getUPLACPTRequestValue());
        li.setpoAcceptanceQty(dto.getpoAcceptanceQty());
        li.setPOLineAcceptanceQty(dto.getPOLineAcceptanceQty());
        li.setPoPendingQuantity(dto.getPoPendingQuantity());
        li.setPoOrderQuantity(dto.getPoOrderQuantity());
        li.setItemPartNumber(dto.getItemPartNumber());
        li.setPoLineDescription(dto.getPoLineDescription());
        li.setUplLineQuantity(dto.getUplLineQuantity());
        li.setPoLineQuantity(dto.getPoLineQuantity());
        li.setUplLineItemCode(dto.getUplLineItemCode());
        li.setUplLineDescription(dto.getUplLineDescription());
        li.setUom(dto.getUnitOfMeasure());
        li.setActiveOrPassive(dto.getActiveOrPassive());
        li.setUplPendingQuantity(dto.getUplPendingQuantity());
        return li;
    }

    // ─── BATCH LOADERS ────────────────────────────────────────────────────────

    private Map<String, List<tbPurchaseOrder>> batchLoadPurchaseOrders(List<String> poNums) {
        return tbPurchaseOrderRepository.findByPoNumberIn(poNums)
                .stream().collect(Collectors.groupingBy(tbPurchaseOrder::getPoNumber));
    }

    private Map<String, List<tb_PurchaseOrderUPL>> batchLoadUplMap(List<String> poNums) {
        return tbPurchaseOrderUplRepository.findByPoNumberIn(poNums)
                .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));
    }

    private Map<Long, List<DCCLineItem>> batchLoadDccLineItems(List<Long> ids) {
        return tbDccLnRepository.findByDccIdIn(
                ids.stream().map(String::valueOf).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(ln -> Long.parseLong(ln.getDccId())));
    }

    private Map<Long, TbCategoryApprovalRequests> batchLoadLatestApprovalRequests(List<Long> ids) {
        Map<Long, TbCategoryApprovalRequests> latest = new LinkedHashMap<>();
        for (TbCategoryApprovalRequests r :
                approvalRequestsRepository.findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(ids))
            latest.putIfAbsent(r.getAcceptanceRequestRecordNo(), r);
        return latest;
    }

    private Map<Long, List<TbCategoryApprovalRequests>> batchLoadAllRequestsByDcc(List<Long> ids) {
        return approvalRequestsRepository
                .findByAcceptanceRequestRecordNoInOrderByRecordDateTimeDesc(ids)
                .stream()
                .collect(Collectors.groupingBy(TbCategoryApprovalRequests::getAcceptanceRequestRecordNo));
    }

    private Map<Long, List<TbCategoryApprovals>> batchLoadApprovals(
            Map<Long, List<TbCategoryApprovalRequests>> byDcc) {
        List<Long> ids = byDcc.values().stream().flatMap(List::stream)
                .map(TbCategoryApprovalRequests::getRecordNo).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        return approvalsRepository.findByApprovalRecordIdIn(ids)
                .stream().collect(Collectors.groupingBy(TbCategoryApprovals::getApprovalRecordId));
    }

    private List<TbCategoryApprovals> collectApprovals(List<TbCategoryApprovalRequests> reqs,
                                                        Map<Long, List<TbCategoryApprovals>> map) {
        return reqs.stream().map(TbCategoryApprovalRequests::getRecordNo)
                .flatMap(id -> map.getOrDefault(id, List.of()).stream())
                .collect(Collectors.toList());
    }

    // ─── DTO POPULATORS ───────────────────────────────────────────────────────

    private void populateDccFields(DccPOCombinedViewDTO dto, DCC dcc,
                                   SimpleDateFormat fmt, TbCategoryApprovalRequests latestReq) {
        dto.setDccRecordNo(dcc.getRecordNo());
        dto.setDccPoNumber(dcc.getPoNumber());
        dto.setDccVendorName(dcc.getVendorName());
        dto.setDccVendorEmail(dcc.getVendorEmail());
        dto.setDccProjectName(dcc.getProjectName());
        dto.setDccAcceptanceType(dcc.getAcceptanceType());
        dto.setDccStatus(dcc.getStatus());
        dto.setDccCreatedDate(dcc.getCreatedDate() != null ? fmt.format(dcc.getCreatedDate()) : null);
        dto.setVendorComment(dcc.getVendorComment());
        dto.setDccId(dcc.getDccId());
        dto.setDccCurrency(dcc.getCurrency());
        dto.setCreatedBy(dcc.getCreatedBy());
        dto.setCreatedByName(dcc.getCreatedBy());
        if (latestReq != null && latestReq.getApprovedDate() != null) {
            dto.setDateApproved(fmt.format(Date.from(
                    latestReq.getApprovedDate().atZone(ZoneId.of("Africa/Nairobi")).toInstant())));
        }
    }

    private void populatePoFields(DccPOCombinedViewDTO dto, tbPurchaseOrder po) {
        dto.setPoId(po.getPoNumber());
        dto.setProjectName(resolveProjectName(po));
        dto.setNewProjectName(po.getNewProjectName());
        dto.setSupplierId(po.getVendorNumber());
        dto.setVendorNumber(po.getVendorNumber());
        dto.setVendorName(po.getVendorName());
    }

    private void populateLineItemFields(DccPOCombinedViewDTO dto, DCCLineItem ln,
                                        Map<String, tb_Site> siteBySiteId, SimpleDateFormat fmt) {
        dto.setLnRecordNo(ln.getRecordNo());
        dto.setLnProductName(ln.getProductName());
        dto.setLnProductSerialNo(ln.getSerialNumber());
        dto.setLnDeliveredQty(ln.getDeliveredQty());
        dto.setLnLocationName(ln.getLocationName());
        dto.setRegion(DccSiteRegionResolver.resolveRegion(siteBySiteId, ln.getLocationName(), tbSiteRepo));
        dto.setLnInserviceDate(ln.getDateInService() != null ? fmt.format(ln.getDateInService()) : null);
        dto.setLnUnitPrice(ln.getUnitPrice() != null ? ln.getUnitPrice() : 0.0);
        dto.setLnScopeOfWork(ln.getScopeOfWork());
        dto.setLnRemarks(ln.getRemarks());
        dto.setLinkId(ln.getLinkId());
        dto.setTagNumber(ln.getTagNumber());
        dto.setLineNumber(ln.getLineNumber());
        dto.setActualItemCode(ln.getActualItemCode());
        dto.setUplLineNumber(ln.getUplLineNumber());
        dto.setpoAcceptanceQty(ln.getpoAcceptanceQty());
    }

    private void populatePoAndUplFields(DccPOCombinedViewDTO dto, DCCLineItem ln,
                                        tbPurchaseOrder po, tb_PurchaseOrderUPL upl) {
        dto.setPoId(po.getPoNumber());
        dto.setProjectName(resolveProjectName(po));
        dto.setNewProjectName(po.getNewProjectName());
        dto.setSupplierId(po.getVendorNumber());
        dto.setVendorNumber(po.getVendorNumber());
        dto.setVendorName(po.getVendorName());
        double orderQty = hasValue(ln.getUplLineNumber()) ? upl.getPoLineQuantity() : parsePoQty(po);
        dto.setPoLineQuantity(orderQty);
        dto.setPoOrderQuantity(orderQty);
        dto.setPoLineDescription(upl.getPoLineDescription());
        dto.setUplLineQuantity(upl.getUplLineQuantity());
        dto.setUplLineItemCode(upl.getUplLineItemCode());
        dto.setUplLineDescription(upl.getUplLineDescription());
        dto.setUnitOfMeasure(upl.getUom());
        dto.setActiveOrPassive(upl.getActiveOrPassive());
        dto.setItemCode(upl.getUplLineItemCode());
        dto.setItemPartNumber(upl.getPoLineItemCode());
    }

    /**
     * Replaces the old {@code calculateQuantities} method.
     * Zero DB queries — reads everything from the pre-built {@link QuantityContext}.
     */
    private void calculateQuantitiesFromContext(DccPOCombinedViewDTO dto,
                                                tb_PurchaseOrderUPL upl,
                                                QuantityContext qtyCtx) {
        // ── totalDelivered (was: N queries per row) ───────────────────────────
        double totalDelivered = 0.0;
        if (hasValue(upl.getUplLine())) {
            String key = upl.getPoNumber() + "|" + upl.getPoLineNumber() + "|" + upl.getUplLine();
            totalDelivered = qtyCtx.deliveredByUplKey.getOrDefault(key, 0.0);
        }
        dto.setUPLACPTRequestValue(totalDelivered);

        // ── poLineAccQty (was: 1 query per row returning a list) ──────────────
        String poLineKey = upl.getPoNumber() + "|" + upl.getPoLineNumber();
        List<tb_PurchaseOrderUPL> siblingUpls =
                qtyCtx.uplByPoLine.getOrDefault(poLineKey, List.of());

        double poLineAccQty = siblingUpls.stream()
                .filter(u -> u.getUplLineQuantity() != null && u.getUplLineQuantity() > 0
                        && u.getPoLineQuantity() != null && u.getPoLineUnitPrice() != null)
                .mapToDouble(u -> {
                    double denom = u.getPoLineQuantity() * u.getPoLineUnitPrice();
                    return denom == 0 ? 0.0 : (u.getUplLineQuantity() * u.getPoLineQuantity()) / denom;
                }).sum();
        dto.setPOLineAcceptanceQty(poLineAccQty);

        // ── exists check (was: 1 exists query per row) ────────────────────────
        String existsKey = upl.getPoNumber() + "|" + upl.getPoLineNumber() + "|"
                + (hasValue(upl.getUplLine()) ? upl.getUplLine() : "");
        boolean exists = qtyCtx.dccLnExistsByKey.getOrDefault(existsKey, false);

        dto.setPoPendingQuantity(exists ? poLineAccQty
                : (upl.getPoLineQuantity() != null ? upl.getPoLineQuantity() : 0.0));
        dto.setUplPendingQuantity(Math.max(
                upl.getUplLineQuantity() != null ? upl.getUplLineQuantity() - totalDelivered : 0.0, 0.0));
    }

    // ─── APPROVAL FIELDS ──────────────────────────────────────────────────────

    private void calculateApprovalFieldsBatched(DccPOCombinedViewDTO dto,
            TbCategoryApprovalRequests latestReq,
            List<TbCategoryApprovalRequests> allReqs,
            List<TbCategoryApprovals> allApprovals) {

        LocalDateTime now = LocalDateTime.now();
        String totalAging  = computeTotalAging(allApprovals, latestReq, now);
        String lastComment = getLatestComment(allApprovals);
        String status      = latestReq.getStatus();

        if ("approved".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status)
                || "returned".equalsIgnoreCase(status)) {
            dto.setApprovalCount(0L);
            dto.setPendingApprovers(null);
            dto.setUserAging("0 days 0 hrs 0 mins");
            dto.setTotalAging(totalAging);
            dto.setApproverComment(lastComment);
            return;
        }

        String currentApprover = allApprovals.stream()
                .filter(a -> a.getApprovalRecordId().equals(latestReq.getRecordNo()))
                .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .min(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .map(TbCategoryApprovals::getApproverName).orElse(null);

        if ("request-info".equalsIgnoreCase(status)) {
            List<TbCategoryApprovals> filtered = allApprovals.stream()
                    .filter(a -> "pending".equals(a.getStatus())
                            && List.of("pending", "request-info").contains(a.getApprovalStatus()))
                    .filter(a -> allReqs.stream().anyMatch(r -> "request-info".equals(r.getStatus())
                            && r.getRecordNo().equals(a.getApprovalRecordId())))
                    .collect(Collectors.toList());
            dto.setApprovalCount((long) filtered.size());
            dto.setPendingApprovers(currentApprover);
            long aging1 = allApprovals.stream()
                    .filter(a -> a.getApprovalRecordId().equals(latestReq.getRecordNo()))
                    .filter(a -> "pending".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                    .filter(a -> currentApprover != null && currentApprover.equals(a.getApproverName()))
                    .filter(a -> a.getRecordDateTime() != null && a.getApprovedDate() != null)
                    .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                    .sum();
            dto.setUserAging(fmtMins(aging1 + pausedMins(allApprovals, currentApprover)));
            dto.setTotalAging(totalAging);
            dto.setApproverComment(lastComment);
            return;
        }

        List<TbCategoryApprovals> filtered = allApprovals.stream()
                .filter(a -> "pending".equals(a.getStatus())
                        && List.of("pending", "readyForApproval").contains(a.getApprovalStatus()))
                .filter(a -> allReqs.stream().anyMatch(r -> "pending".equals(r.getStatus())
                        && r.getRecordNo().equals(a.getApprovalRecordId())))
                .collect(Collectors.toList());
        dto.setApprovalCount((long) filtered.size());

        String pendingApprover = filtered.stream()
                .filter(a -> "readyForApproval".equals(a.getApprovalStatus()))
                .min(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .map(TbCategoryApprovals::getApproverName)
                .orElseGet(() -> filtered.stream()
                        .min(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                        .map(TbCategoryApprovals::getApproverName).orElse(null));
        dto.setPendingApprovers(pendingApprover);

        long currentMins = 0;
        if (pendingApprover != null) {
            Optional<LocalDateTime> latestRfa = filtered.stream()
                    .filter(a -> "readyForApproval".equals(a.getApprovalStatus())
                            && pendingApprover.equals(a.getApproverName()))
                    .map(TbCategoryApprovals::getRecordDateTime).filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo);
            currentMins = Math.max(latestRfa.map(d -> Duration.between(d, now).toMinutes())
                    .orElseGet(() -> latestReq.getRecordDateTime() != null
                            ? Duration.between(latestReq.getRecordDateTime(), now).toMinutes() : 0L), 0);
        }
        dto.setUserAging(fmtMins(pausedMins(allApprovals, pendingApprover) + currentMins));
        dto.setTotalAging(totalAging);
        dto.setApproverComment(lastComment);
    }

    // ─── UTILITIES ────────────────────────────────────────────────────────────

    private long pausedMins(List<TbCategoryApprovals> approvals, String name) {
        if (name == null) return 0;
        return approvals.stream()
                .filter(a -> "request-info".equals(a.getStatus()) && "request-info".equals(a.getApprovalStatus()))
                .filter(a -> name.equals(a.getApproverName()))
                .filter(a -> a.getApprovedDate() != null && a.getRecordDateTime() != null)
                .mapToLong(a -> Math.max(Duration.between(a.getRecordDateTime(), a.getApprovedDate()).toMinutes(), 0))
                .sum();
    }

    private String computeTotalAging(List<TbCategoryApprovals> approvals,
                                     TbCategoryApprovalRequests req, LocalDateTime now) {
        LocalDateTime start = approvals.stream().map(TbCategoryApprovals::getRecordDateTime)
                .filter(Objects::nonNull).min(LocalDateTime::compareTo)
                .orElse(req.getRecordDateTime() != null ? req.getRecordDateTime() : now);
        LocalDateTime end = approvals.stream()
                .filter(a -> "pending".equals(a.getStatus()) && "pending".equals(a.getApprovalStatus()))
                .findAny().map(a -> now)
                .orElseGet(() -> approvals.stream().map(TbCategoryApprovals::getApprovedDate)
                        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(now));
        return fmtMins(Math.max(Duration.between(start, end).toMinutes(), 0));
    }

    private String getLatestComment(List<TbCategoryApprovals> approvals) {
        return approvals.stream()
                .filter(a -> !"pending".equals(a.getApprovalStatus()) && !"readyForApproval".equals(a.getApprovalStatus()))
                .filter(a -> a.getComments() != null)
                .max(Comparator.comparing(TbCategoryApprovals::getApprovalId))
                .map(TbCategoryApprovals::getComments).orElse(null);
    }

    private String fmtMins(long m) {
        return String.format("%d days %d hrs %d mins", m / 1440, (m / 60) % 24, m % 60);
    }

    private String resolveProjectName(tbPurchaseOrder po) {
        return hasValue(po.getNewProjectName()) ? po.getNewProjectName() : po.getProjectName();
    }

    private double parsePoQty(tbPurchaseOrder po) {
        String v = String.valueOf(po.getPoQtyNew());
        return hasValue(v) ? Double.parseDouble(v) : po.getAmountDueLine();
    }

    private void setDefaultApprovalFields(DccPOCombinedViewDTO dto) {
        dto.setApprovalCount(0L);
        dto.setPendingApprovers(null);
        dto.setApproverComment(null);
        dto.setUserAging("0 days 0 hrs 0 mins");
        dto.setTotalAging("0 days 0 hrs 0 mins");
    }

    private void validatePoNumbers(List<DCC> list) {
        List<Long> bad = list.stream()
                .filter(d -> !hasValue(d.getPoNumber())).map(DCC::getRecordNo).collect(Collectors.toList());
        if (!bad.isEmpty()) throw new DccPOProcessingException("Missing poNumber for DCC records: " + bad);
    }

    private boolean hasValue(String s) { return s != null && !s.trim().isEmpty(); }

    // ─── FETCH CONTEXT ────────────────────────────────────────────────────────

    private static class FetchContext {
        final boolean isEmpty;
        final List<DCC> dccList;
        final long totalFiltered;
        final long approverFilteredTotal;
        final boolean fetchParentOnly;
        final Map<String, List<tbPurchaseOrder>>            poMap;
        final Map<Long,   TbCategoryApprovalRequests>       latestReqs;
        final Map<Long,   List<TbCategoryApprovalRequests>> allReqs;
        final Map<Long,   List<TbCategoryApprovals>>        approvMap;
        final Map<String, List<tb_PurchaseOrderUPL>>        uplMap;
        final Map<Long,   List<DCCLineItem>>                lnMap;
        final Map<String, tb_Site>                          siteBySiteId;
        final QuantityContext                               qtyCtx;  // null for parent-only

        FetchContext(List<DCC> dccList, long totalFiltered, long approverFilteredTotal,
                     boolean fetchParentOnly,
                     Map<String, List<tbPurchaseOrder>> poMap,
                     Map<Long, TbCategoryApprovalRequests> latestReqs,
                     Map<Long, List<TbCategoryApprovalRequests>> allReqs,
                     Map<Long, List<TbCategoryApprovals>> approvMap,
                     Map<String, List<tb_PurchaseOrderUPL>> uplMap,
                     Map<Long, List<DCCLineItem>> lnMap,
                     Map<String, tb_Site> siteBySiteId,
                     QuantityContext qtyCtx) {
            this.isEmpty               = false;
            this.dccList               = dccList;
            this.totalFiltered         = totalFiltered;
            this.approverFilteredTotal = approverFilteredTotal;
            this.fetchParentOnly       = fetchParentOnly;
            this.poMap                 = poMap;
            this.latestReqs            = latestReqs;
            this.allReqs               = allReqs;
            this.approvMap             = approvMap;
            this.uplMap                = uplMap;
            this.lnMap                 = lnMap;
            this.siteBySiteId          = siteBySiteId;
            this.qtyCtx                = qtyCtx;
        }

        static FetchContext empty() {
            return new FetchContext(List.of(), 0L, 0L, true,
                    Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null) {
                @Override boolean isEmpty() { return true; }
            };
        }
        boolean isEmpty() { return isEmpty; }
    }

    // ─── QUANTITY CONTEXT ─────────────────────────────────────────────────────

    private static class QuantityContext {
        /** "poNumber|poLineNumber|uplLine" → sum of delivered qty (non-incomplete/rejected DCCs) */
        final Map<String, Double> deliveredByUplKey;
        /** "poNumber|poLineNumber" → all UPLs for that PO line (for poLineAccQty calc) */
        final Map<String, List<tb_PurchaseOrderUPL>> uplByPoLine;
        /** "poNumber|poLineNumber|uplLine" → true if any DCCLineItem exists for that key */
        final Map<String, Boolean> dccLnExistsByKey;

        QuantityContext(Map<String, Double> deliveredByUplKey,
                        Map<String, List<tb_PurchaseOrderUPL>> uplByPoLine,
                        Map<String, Boolean> dccLnExistsByKey) {
            this.deliveredByUplKey = deliveredByUplKey;
            this.uplByPoLine       = uplByPoLine;
            this.dccLnExistsByKey  = dccLnExistsByKey;
        }
    }
}