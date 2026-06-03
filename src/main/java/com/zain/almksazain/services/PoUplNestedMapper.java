package com.zain.almksazain.services;

import com.zain.almksazain.model.dto.CombinedPurchaseOrderRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class PoUplNestedMapper {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private PoUplNestedMapper() {
    }

    static List<Map<String, Object>> toNestedPoList(List<CombinedPurchaseOrderRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<CombinedPurchaseOrderRow>> rowsByPo = new LinkedHashMap<>();
        for (CombinedPurchaseOrderRow row : dedupeRows(rows)) {
            rowsByPo.computeIfAbsent(row.getPoNumber(), key -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> nestedPos = new ArrayList<>();
        int poIndex = 1;
        for (Map.Entry<String, List<CombinedPurchaseOrderRow>> entry : rowsByPo.entrySet()) {
            nestedPos.add(buildNestedPo(entry.getValue(), poIndex++));
        }
        return nestedPos;
    }

    private static List<CombinedPurchaseOrderRow> dedupeRows(List<CombinedPurchaseOrderRow> rows) {
        Set<String> seen = new LinkedHashSet<>();
        List<CombinedPurchaseOrderRow> deduped = new ArrayList<>();
        for (CombinedPurchaseOrderRow row : rows) {
            String key = row.getPoNumber() + "|" + row.getLineNumber() + "|" + row.getUplLine();
            if (seen.add(key)) {
                deduped.add(row);
            }
        }
        return deduped;
    }

    private static Map<String, Object> buildNestedPo(List<CombinedPurchaseOrderRow> poRows, int poIndex) {
        CombinedPurchaseOrderRow header = poRows.get(0);
        Map<String, Object> po = new LinkedHashMap<>();
        po.put("poNumber", header.getPoNumber());
        po.put("poId", header.getPoNumber());
        po.put("poDate", formatDateTime(header.getPoCreatedDate()));
        po.put("poApprovedDate", formatDateTime(header.getPoApprovedDate()));
        po.put("supplierId", header.getPoVendorNumber());
        po.put("vendorName", header.getPoVendorName());
        po.put("vendorNumber", header.getPoVendorNumber());
        po.put("projectNumber", header.getPrNum());
        po.put("currency", header.getPoCurrencyCode());
        po.put("partNumber", header.getItemPartNumber());
        po.put("costCenter", header.getCostCenter());
        po.put("status", header.getAuthorisationStatus());
        po.put("id", poIndex);
        po.put("acceptanceType", nullToEmpty(header.getPoAcceptanceType()));
        po.put("polineItems", buildPoLineItems(poRows));
        return po;
    }

    private static List<Map<String, Object>> buildPoLineItems(List<CombinedPurchaseOrderRow> poRows) {
        Map<Integer, CombinedPurchaseOrderRow> lineHeaders = new LinkedHashMap<>();
        Map<Integer, List<CombinedPurchaseOrderRow>> uplRowsByLine = new LinkedHashMap<>();

        for (CombinedPurchaseOrderRow row : poRows) {
            Integer lineNumber = row.getLineNumber();
            lineHeaders.putIfAbsent(lineNumber, row);
            if (row.getUplLine() != null && !row.getUplLine().isEmpty()) {
                uplRowsByLine.computeIfAbsent(lineNumber, key -> new ArrayList<>()).add(row);
            }
        }

        List<Map<String, Object>> polineItems = new ArrayList<>();
        AtomicInteger uplId = new AtomicInteger(1);
        for (Map.Entry<Integer, CombinedPurchaseOrderRow> entry : lineHeaders.entrySet()) {
            Integer lineNumber = entry.getKey();
            CombinedPurchaseOrderRow lineRow = entry.getValue();
            List<CombinedPurchaseOrderRow> uplRows = uplRowsByLine.getOrDefault(lineNumber, Collections.emptyList());
            polineItems.add(buildPoLineItem(lineRow, uplRows, uplId));
        }
        return polineItems;
    }

    private static Map<String, Object> buildPoLineItem(
            CombinedPurchaseOrderRow lineRow,
            List<CombinedPurchaseOrderRow> uplRows,
            AtomicInteger uplId) {

        CombinedPurchaseOrderRow firstUpl = uplRows.isEmpty() ? null : uplRows.get(0);
        Map<String, Object> poline = new LinkedHashMap<>();
        poline.put("recordNo", "0");
        poline.put("poId", lineRow.getPoNumber());
        poline.put("lineNumber", lineRow.getLineNumber());
        poline.put("itemPartNumber", lineRow.getItemPartNumber());
        poline.put("poLineDescription", lineRow.getPoLineDescription());
        poline.put("itemCode", firstUpl != null ? nullToEmpty(firstUpl.getUplLineItemCode()) : "");
        poline.put("unitOfMeasure", firstUpl != null ? nullToEmpty(firstUpl.getUplUom()) : "");
        poline.put("UoM", firstUpl != null ? nullToEmpty(firstUpl.getUplUom()) : "");
        poline.put("poOrderQuantity", lineRow.getPoOrderQuantity());
        poline.put("unitPrice", firstUpl != null ? firstUpl.getUplLineUnitPrice() : lineRow.getUnitPriceInPoCurrency());
        poline.put("VAT", 0);
        poline.put("linePrice", lineRow.getUnitPriceInPoCurrency());
        poline.put("currency", lineRow.getPoCurrencyCode());
        poline.put("modelNumber", firstUpl != null ? nullToEmpty(firstUpl.getUplLineItemCode()) : nullToEmpty(lineRow.getItemPartNumber()));
        poline.put("qtyPerSite", 0);
        poline.put("accDepreciation", 0);
        poline.put("lifeYears", 0);
        poline.put("DeliveredQuantity", 0);
        poline.put("serialNumber", "");
        poline.put("serialized", nullToEmpty(lineRow.getVendorSerialNumberYN()));
        poline.put("dateInService", Instant.now().toString());
        poline.put("isPOLineSerialized", isSerialized(lineRow.getVendorSerialNumberYN()));
        poline.put("poApprovedDate", formatDateTime(lineRow.getPoApprovedDate()));
        poline.put("locationId", "");
        poline.put("remarks", "");
        poline.put("scopeOfWork", firstUpl != null ? nullToEmpty(firstUpl.getScopeOfWork()) : "");
        poline.put("poPendingQuantity", lineRow.getPoPendingQuantity());
        poline.put("activeOrPassive", firstUpl != null ? nullToEmpty(firstUpl.getActiveOrPassive()) : "");
        poline.put("uplListNew", Collections.emptyList());
        poline.put("uplListFiltered", Collections.emptyList());
        poline.put("uplList", buildUplList(lineRow, uplRows, uplId));
        return poline;
    }

    private static List<Map<String, Object>> buildUplList(
            CombinedPurchaseOrderRow lineRow,
            List<CombinedPurchaseOrderRow> uplRows,
            AtomicInteger uplId) {

        List<Map<String, Object>> uplList = new ArrayList<>();
        for (CombinedPurchaseOrderRow uplRow : uplRows) {
            Map<String, Object> upl = new LinkedHashMap<>();
            upl.put("recordNo", "0");
            upl.put("id", uplId.getAndIncrement());
            upl.put("poId", lineRow.getPoNumber());
            upl.put("lineNumber", lineRow.getLineNumber());
            upl.put("itemPartNumber", lineRow.getItemPartNumber());
            upl.put("poLineDescription", lineRow.getPoLineDescription());
            upl.put("itemCode", nullToEmpty(uplRow.getUplLineItemCode()));
            upl.put("unitOfMeasure", nullToEmpty(uplRow.getUplUom()));
            upl.put("UoM", nullToEmpty(uplRow.getUplUom()));
            upl.put("unitPrice", uplRow.getUplLineUnitPrice());
            upl.put("VAT", 0);
            upl.put("linePrice", lineRow.getUnitPriceInPoCurrency());
            upl.put("currency", uplRow.getUplCurrency() != null ? uplRow.getUplCurrency() : lineRow.getPoCurrencyCode());
            upl.put("modelNumber", nullToEmpty(uplRow.getUplLineItemCode()));
            upl.put("qtyPerSite", 0);
            upl.put("accDepreciation", 0);
            upl.put("lifeYears", 0);
            upl.put("DeliveredQuantity", 0);
            upl.put("uplLine", uplRow.getUplLine());
            upl.put("uplLineItemCode", nullToEmpty(uplRow.getUplLineItemCode()));
            upl.put("uplLineDescription", nullToEmpty(uplRow.getUplLineDescription()));
            upl.put("uplLineQuantity", uplRow.getUplLineQuantity());
            upl.put("uplPendingQuantity", uplRow.getUplPendingQuantity());
            upl.put("serialNumber", "");
            upl.put("serialized", nullToEmpty(uplRow.getUplItemSerialized()));
            upl.put("dateInService", Instant.now().toString());
            upl.put("isPOLineSerialized", isSerialized(lineRow.getVendorSerialNumberYN()));
            upl.put("uplSerialized", nullToEmpty(uplRow.getUplItemSerialized()));
            upl.put("isUPLSerialized", isSerialized(uplRow.getUplItemSerialized()));
            upl.put("poApprovedDate", formatDateTime(lineRow.getPoApprovedDate()));
            upl.put("locationId", "");
            upl.put("regionName", "");
            upl.put("remarks", nullToEmpty(uplRow.getUplRemarks()));
            upl.put("scopeOfWork", nullToEmpty(uplRow.getScopeOfWork()));
            upl.put("activeOrPassive", nullToEmpty(uplRow.getActiveOrPassive()));
            upl.put("locationSearching", false);
            upl.put("openLocationDropdwn", false);
            uplList.add(upl);
        }
        return uplList;
    }

    private static boolean isSerialized(String value) {
        return value != null && value.trim().equalsIgnoreCase("YES");
    }

    private static String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate;
        if (date instanceof java.sql.Date) {
            localDate = ((java.sql.Date) date).toLocalDate();
        } else {
            localDate = date.toInstant().atZone(ZONE).toLocalDate();
        }
        return localDate.atStartOfDay().format(DATE_TIME);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
