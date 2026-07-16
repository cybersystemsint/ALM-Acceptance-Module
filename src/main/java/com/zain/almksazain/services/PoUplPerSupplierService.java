package com.zain.almksazain.services;

import com.zain.almksazain.model.dto.CombinedPurchaseOrderRow;
import com.zain.almksazain.model.dto.PoUplCombinedPair;
import com.zain.almksazain.model.tbCategory;
import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;
import com.zain.almksazain.repo.PoUplAcceptanceStatsRepo;
import com.zain.almksazain.repo.PoUplCombinedQueryRepository;
import com.zain.almksazain.repo.tbCategoryRepo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PoUplPerSupplierService {

    private static final Logger logger = LogManager.getLogger(PoUplPerSupplierService.class);

    private static final List<String> ACCEPTANCE_EXCLUDED = Arrays.asList("incomplete", "rejected");
    private static final List<String> PENDING_EXCLUDED = Arrays.asList("incomplete", "rejected", "returned");

    @Autowired
    private PoUplCombinedQueryRepository combinedQueryRepository;

    @Autowired
    private PoUplAcceptanceStatsRepo acceptanceStatsRepo;

    @Autowired
    private tbCategoryRepo categoryRepo;

    public Map<String, Object> getPoUplPerSupplierAndPoNumber(
            String supplierId,
            String poId,
            String columnName,
            String searchQuery,
            String dateFrom,
            String dateTo,
            int page,
            int size) {

        page = Math.max(page, 0);
        size = Math.max(size, 0);
        boolean fetchAll = page == 1 && size == 20000;

        List<PoUplCombinedPair> pairs = combinedQueryRepository.findCombinedRows(
                supplierId, poId, dateFrom, dateTo,
                combinedQueryRepository.isEntityBackedSearchColumn(columnName) ? columnName : "",
                combinedQueryRepository.isEntityBackedSearchColumn(columnName) ? searchQuery : "",
                0, 0);

        List<CombinedPurchaseOrderRow> rows = assembleRows(pairs);
        if (columnName != null && !columnName.isEmpty()
                && searchQuery != null && !searchQuery.isEmpty()) {
            rows = applyPostFilter(rows, columnName, searchQuery);
        }

        List<Map<String, Object>> nestedPos = PoUplNestedMapper.toNestedPoList(rows);
        int totalRecords = nestedPos.size();

        if (fetchAll) {
            page = 1;
            size = Math.max(totalRecords, 1);
        } else if (page == 0 && size == 0) {
            page = 1;
            size = Math.max(totalRecords, 1);
        } else {
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            nestedPos = paginateNestedPos(nestedPos, page, size, false);
        }

        logger.info("PoUplPerSupplierService V2 fetch supplierId={} poId={} totalRecords={}", supplierId, poId, totalRecords);
        return buildResponse(nestedPos, totalRecords, page, size);
    }

    private List<CombinedPurchaseOrderRow> assembleRows(List<PoUplCombinedPair> pairs) {
        if (pairs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> poNumbers = pairs.stream()
                .map(p -> p.getPurchaseOrder().getPoNumber())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> categoryCodes = pairs.stream()
                .map(PoUplCombinedPair::getUpl)
                .filter(Objects::nonNull)
                .map(tb_PurchaseOrderUPL::getZainItemCategoryCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Double> acceptanceTotals = loadAcceptanceMap(poNumbers, ACCEPTANCE_EXCLUDED);
        Map<String, Double> pendingTotals = loadAcceptanceMap(poNumbers, PENDING_EXCLUDED);
        Map<String, Double> negativeLineAcceptance = loadNegativeLineAcceptanceMap(poNumbers);
        Map<String, tbCategory> categoryMap = categoryRepo.findByItemCategoryCodeIn(categoryCodes).stream()
                .collect(Collectors.toMap(tbCategory::getItemCategoryCode, c -> c, (a, b) -> a));

        List<CombinedPurchaseOrderRow> rows = new ArrayList<>(pairs.size());
        for (PoUplCombinedPair pair : pairs) {
            rows.add(buildRow(
                    pair.getPurchaseOrder(),
                    pair.getUpl(),
                    categoryMap,
                    acceptanceTotals,
                    pendingTotals,
                    negativeLineAcceptance));
        }
        return rows;
    }

    private Map<String, Double> loadAcceptanceMap(Set<String> poNumbers, List<String> excludedStatuses) {
        Map<String, Double> map = new HashMap<>();
        if (poNumbers.isEmpty()) {
            return map;
        }
        List<Object[]> stats = acceptanceStatsRepo.sumDeliveredQtyByPoLineUpl(poNumbers, excludedStatuses);
        for (Object[] stat : stats) {
            String key = acceptanceKey((String) stat[0], (String) stat[1], (String) stat[2]);
            map.put(key, ((Number) stat[3]).doubleValue());
        }
        return map;
    }

    private Map<String, Double> loadNegativeLineAcceptanceMap(Set<String> poNumbers) {
        Map<String, Double> map = new HashMap<>();
        if (poNumbers.isEmpty()) {
            return map;
        }
        List<Object[]> stats = acceptanceStatsRepo.sumNegativeLineAcceptanceByPo(poNumbers);
        for (Object[] stat : stats) {
            map.put(lineKey((String) stat[0], (String) stat[1]), ((Number) stat[2]).doubleValue());
        }
        return map;
    }

    private CombinedPurchaseOrderRow buildRow(
            tbPurchaseOrder po,
            tb_PurchaseOrderUPL upl,
            Map<String, tbCategory> categoryMap,
            Map<String, Double> acceptanceTotals,
            Map<String, Double> pendingTotals,
            Map<String, Double> negativeLineAcceptance) {

        CombinedPurchaseOrderRow row = new CombinedPurchaseOrderRow();
        row.setPoRecordNo(po.getRecordNo());
        row.setPoNumber(po.getPoNumber());
        row.setTypeLookUpCode(po.getTypeLookUpCode());
        row.setBlanketTotalAmount(po.getBlanketTotalAmount());
        row.setReleaseNum(po.getReleaseNum());
        row.setLineNumber(po.getLineNumber());
        row.setPrNum(po.getPrNum());
        row.setPoProjectName(po.getNewProjectName());
        row.setNewProjectName(po.getNewProjectName());
        row.setItemPartNumber(po.getItemPartNumber());
        row.setPrSubAllow(po.isPrSubAllow());
        row.setPoCountryOfOrigin(po.getCountryOfOrigin());
        row.setPoOrderQuantity(po.getPoQtyNew() > 0 ? po.getPoQtyNew() : po.getPoOrderQuantity());
        row.setPoPendingQuantity(po.getPoQtyNew() > 0 ? po.getQuantityDueNew() : po.getQuantityDueOld());
        row.setPoQtyNew(po.getPoQtyNew());
        row.setQuantityReceived(po.getQuantityReceived());
        row.setPoCurrencyCode(po.getCurrencyCode());
        row.setUnitPriceInPoCurrency(po.getUnitPriceInPoCurrency());
        row.setUnitPriceInSAR(po.getUnitPriceInSAR());
        row.setLinePriceInPoCurrency(po.getLinePriceInPoCurrency());
        row.setLinePriceInSAR(po.getLinePriceInSAR());
        row.setAmountReceived(po.getAmountReceived());
        row.setPoLineDescription(po.getPoLineDescription());
        row.setOrganizationName(po.getOrganizationName());
        row.setOrganizationCode(po.getOrganizationCode());
        row.setSubInventoryCode(po.getSubInventoryCode());
        row.setReceiptRouting(po.getReceiptRouting());
        row.setAuthorisationStatus(po.getAuthorisationStatus());
        row.setDepartmentName(po.getDepartmentName());
        row.setBusinessOwner(po.getBusinessOwner());
        row.setPoLineType(po.getPoLineType());
        row.setPoAcceptanceType(po.getAcceptanceType());
        row.setCostCenter(po.getCostCenter());
        row.setChargeAccount(po.getChargeAccount());
        row.setSerialControl(po.getSerialControl());
        row.setVendorSerialNumberYN(po.getVendorSerialNumberYN());
        row.setItemType(po.getItemType());
        row.setItemCategoryInventory(po.getItemCategoryInventory());
        row.setInventoryCategoryDescription(po.getInventoryCategoryDescription());
        row.setItemCategoryFA(po.getItemCategoryFA());
        row.setFaCategoryDescription(po.getFACategoryDescription());
        row.setItemCategoryPurchasing(po.getItemCategoryPurchasing());
        row.setPurchasingCategoryDescription(po.getPurchasingCategoryDescription());
        row.setPoVendorName(po.getVendorName());
        row.setPoVendorNumber(po.getVendorNumber());
        row.setPoApprovedDate(po.getApprovedDate());
        row.setPoCreatedDate(po.getCreatedDate());
        row.setPoCreatedBy(po.getCreatedBy());
        row.setPoCreatedByName(po.getCreatedByName());
        row.setCanRaiseAcceptance(canRaiseAcceptance(po));

        if (upl != null) {
            tbCategory category = categoryMap.get(upl.getZainItemCategoryCode());
            row.setUplRecordNo(upl.getRecordNo());
            row.setUplManufacturer(upl.getManufacturer());
            row.setUplCountryOfOrigin(upl.getCountryOfOrigin());
            row.setUplReleaseNumber(upl.getReleaseNumber());
            row.setUplLine(upl.getUplLine());
            row.setUplPoLineItemType(upl.getPoLineItemType());
            row.setUplPoLineItemCode(upl.getPoLineItemCode());
            row.setUplPoLineDescription(upl.getPoLineDescription());
            row.setUplLineItemType(upl.getUplLineItemType());
            row.setUplLineItemCode(upl.getUplLineItemCode());
            row.setUplLineDescription(upl.getUplLineDescription());
            row.setZainItemCategoryCode(upl.getZainItemCategoryCode());
            row.setZainItemCategoryDescription(upl.getZainItemCategoryDescription());
            row.setUplItemSerialized(upl.getUplItemSerialized());
            row.setActiveOrPassive(upl.getActiveOrPassive());
            row.setUplUom(upl.getUom());
            row.setUplCurrency(upl.getCurrency());
            row.setUplPoLineQuantity(upl.getPoLineQuantity());
            row.setUplPoLineUnitPrice(upl.getPoLineUnitPrice());
            row.setUplLineQuantity(upl.getUplLineQuantity());
            row.setUplLineUnitPrice(upl.getUplLineUnitPrice());
            row.setSubstituteItemCode(upl.getSubstituteItemCode());
            row.setUplRemarks(upl.getRemarks());
            row.setDptApprover1(upl.getDptApprover1());
            row.setDptApprover2(upl.getDptApprover2());
            row.setDptApprover3(upl.getDptApprover3());
            row.setDptApprover4(upl.getDptApprover4());
            row.setRegionalApprover(upl.getRegionalApprover());
            row.setUplCreatedBy(upl.getCreatedBy());
            row.setUplCreatedByName(upl.getCreatedByName());
            if (category != null) {
                row.setScopeOfWork(category.getScope());
                row.setCategoryDescription(category.getCategoryDescription());
            }

            String acceptanceKey = acceptanceKey(upl.getPoNumber(), upl.getPoLineNumber(), upl.getUplLine());
            String lineKey = lineKey(upl.getPoNumber(), upl.getPoLineNumber());
            double deliveredQty = acceptanceTotals.getOrDefault(acceptanceKey, 0.0);
            double pendingDeliveredQty = pendingTotals.getOrDefault(acceptanceKey, 0.0);
            double lineDenominator = upl.getPoLineQuantity() * upl.getPoLineUnitPrice();

            if (upl.getUplLineQuantity() > 0) {
                row.setUplAcptRequestValue(deliveredQty);
                row.setPoAcceptanceQty(lineDenominator == 0 ? 0.0 : deliveredQty / lineDenominator);
            } else {
                row.setUplAcptRequestValue(0.0);
                row.setPoAcceptanceQty(0.0);
            }
            row.setPoLineAcceptanceQty(negativeLineAcceptance.getOrDefault(lineKey, 0.0));
            row.setUplPendingQuantity(upl.getUplLineQuantity() - pendingDeliveredQty);
        }

        return row;
    }

    private String canRaiseAcceptance(tbPurchaseOrder po) {
        if (!po.isLineCancelFlag()
                && "APPROVED".equalsIgnoreCase(po.getAuthorisationStatus())
                && "OPEN".equalsIgnoreCase(po.getPoClosureStatus())) {
            return "YES";
        }
        return "NO";
    }

    private String acceptanceKey(String poNumber, String poLineNumber, String uplLine) {
        return poNumber + "|" + poLineNumber + "|" + uplLine;
    }

    private String lineKey(String poNumber, String poLineNumber) {
        return poNumber + "|" + poLineNumber;
    }

    private List<CombinedPurchaseOrderRow> applyPostFilter(
            List<CombinedPurchaseOrderRow> rows, String columnName, String searchQuery) {
        String needle = searchQuery.toLowerCase();
        return rows.stream()
                .filter(row -> {
                    Object value = row.toMap().get(columnName);
                    return value != null && value.toString().toLowerCase().contains(needle);
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> paginateNestedPos(
            List<Map<String, Object>> nestedPos, int page, int size, boolean fetchAll) {
        if (fetchAll || (page == 0 && size == 0)) {
            return nestedPos;
        }
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(fromIndex + size, nestedPos.size());
        if (fromIndex >= toIndex) {
            return new ArrayList<>();
        }
        return nestedPos.subList(fromIndex, toIndex);
    }

    private Map<String, Object> buildResponse(
            List<Map<String, Object>> nestedPos, int totalRecords, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", nestedPos);
        response.put("totalRecords", totalRecords);
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", size == 0 ? 0 : (int) Math.ceil((double) totalRecords / size));
        return response;
    }
}
