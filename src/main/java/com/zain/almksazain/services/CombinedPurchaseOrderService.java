package com.zain.almksazain.services;


import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.*;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

@Service
public class CombinedPurchaseOrderService {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private tbPurchaseOrderUPLRepo purchaseOrderUPLRepo;
    @Autowired
    private CategoryRepo categoryRepo;
    @Autowired
    private DccLineRepo dccLineRepo;
    @Autowired
    private DCCRepository dccRepository;

public Map<String, Object> poUplPerSupplierAndPoNumberReport(
            String supplierId, String poID, String columnName, String searchQuery,
            String dateFrom, String dateTo, int page, int size) {

        // Define column mappings: result map column name -> (entity field name, type)
        final Map<String, Map.Entry<String, String>> poColumns = new HashMap<>();
        poColumns.put("poRecordNo", Map.entry("recordNo", "numeric"));
        poColumns.put("poNumber", Map.entry("poNumber", "string"));
        poColumns.put("typeLookUpCode", Map.entry("typeLookUpCode", "string"));
        poColumns.put("blanketTotalAmount", Map.entry("blanketTotalAmount", "numeric"));
        poColumns.put("releaseNum", Map.entry("releaseNum", "numeric"));
        poColumns.put("lineNumber", Map.entry("lineNumber", "numeric"));
        poColumns.put("prNum", Map.entry("prNum", "string"));
        poColumns.put("poProjectName", Map.entry("newProjectName", "string"));
        poColumns.put("newProjectName", Map.entry("newProjectName", "string"));
        poColumns.put("itemPartNumber", Map.entry("itemPartNumber", "string"));
        poColumns.put("poPendingQuantity", Map.entry("quantityDueNew", "numeric")); // or quantityDueOld based on logic
        poColumns.put("prSubAllow", Map.entry("prSubAllow", "boolean"));
        poColumns.put("poCountryOfOrigin", Map.entry("countryOfOrigin", "string"));
        poColumns.put("poOrderQuantity", Map.entry("poQtyNew", "numeric")); // or poOrderQuantity based on logic
        poColumns.put("poQtyNew", Map.entry("poQtyNew", "numeric"));
        poColumns.put("quantityReceived", Map.entry("quantityReceived", "numeric"));
        poColumns.put("poCurrencyCode", Map.entry("currencyCode", "string"));
        poColumns.put("unitPriceInPoCurrency", Map.entry("unitPriceInPoCurrency", "numeric"));
        poColumns.put("unitPriceInSAR", Map.entry("unitPriceInSAR", "numeric"));
        poColumns.put("linePriceInPoCurrency", Map.entry("linePriceInPoCurrency", "numeric"));
        poColumns.put("linePriceInSAR", Map.entry("linePriceInSAR", "numeric"));
        poColumns.put("amountReceived", Map.entry("amountReceived", "numeric"));
        poColumns.put("poLineDescription", Map.entry("poLineDescription", "string"));
        poColumns.put("organizationName", Map.entry("organizationName", "string"));
        poColumns.put("organizationCode", Map.entry("organizationCode", "string"));
        poColumns.put("subInventoryCode", Map.entry("subInventoryCode", "string"));
        poColumns.put("receiptRouting", Map.entry("receiptRouting", "string"));
        poColumns.put("authorisationStatus", Map.entry("authorisationStatus", "string"));
        poColumns.put("departmentName", Map.entry("departmentName", "string"));
        poColumns.put("businessOwner", Map.entry("businessOwner", "string"));
        poColumns.put("poLineType", Map.entry("poLineType", "string"));
        poColumns.put("poAcceptanceType", Map.entry("acceptanceType", "string"));
        poColumns.put("costCenter", Map.entry("costCenter", "string"));
        poColumns.put("chargeAccount", Map.entry("chargeAccount", "string"));
        poColumns.put("serialControl", Map.entry("serialControl", "string"));
        poColumns.put("vendorSerialNumberYN", Map.entry("vendorSerialNumberYN", "string"));
        poColumns.put("itemType", Map.entry("itemType", "string"));
        poColumns.put("itemCategoryInventory", Map.entry("itemCategoryInventory", "string"));
        poColumns.put("inventoryCategoryDescription", Map.entry("inventoryCategoryDescription", "string"));
        poColumns.put("itemCategoryFA", Map.entry("itemCategoryFA", "string"));
        poColumns.put("FACategoryDescription", Map.entry("FACategoryDescription", "string"));
        poColumns.put("itemCategoryPurchasing", Map.entry("itemCategoryPurchasing", "string"));
        poColumns.put("PurchasingCategoryDescription", Map.entry("PurchasingCategoryDescription", "string"));
        poColumns.put("poVendorName", Map.entry("vendorName", "string"));
        poColumns.put("poVendorNumber", Map.entry("vendorNumber", "string"));
        poColumns.put("poApprovedDate", Map.entry("approvedDate", "date"));
        poColumns.put("poCreatedDate", Map.entry("createdDate", "date"));
        poColumns.put("poCreatedBy", Map.entry("createdBy", "string"));
        poColumns.put("poCreatedByName", Map.entry("createdByName", "string"));

        final Map<String, Map.Entry<String, String>> uplColumns = new HashMap<>();
        uplColumns.put("uplRecordNo", Map.entry("recordNo", "numeric"));
        uplColumns.put("uplManufacturer", Map.entry("manufacturer", "string"));
        uplColumns.put("uplCountryOfOrigin", Map.entry("countryOfOrigin", "string"));
        uplColumns.put("uplReleaseNumber", Map.entry("releaseNumber", "numeric"));
        uplColumns.put("uplLine", Map.entry("uplLine", "numeric"));
        uplColumns.put("uplPoLineItemType", Map.entry("poLineItemType", "string"));
        uplColumns.put("uplPoLineItemCode", Map.entry("poLineItemCode", "string"));
        uplColumns.put("uplPoLineDescription", Map.entry("poLineDescription", "string"));
        uplColumns.put("uplLineItemType", Map.entry("uplLineItemType", "string"));
        uplColumns.put("uplLineItemCode", Map.entry("uplLineItemCode", "string"));
        uplColumns.put("uplLineDescription", Map.entry("uplLineDescription", "string"));
        uplColumns.put("zainItemCategoryCode", Map.entry("zainItemCategoryCode", "string"));
        uplColumns.put("zainItemCategoryDescription", Map.entry("zainItemCategoryDescription", "string"));
        uplColumns.put("uplItemSerialized", Map.entry("uplItemSerialized", "string"));
        uplColumns.put("activeOrPassive", Map.entry("activeOrPassive", "string"));
        uplColumns.put("uplUom", Map.entry("uom", "string"));
        uplColumns.put("uplCurrency", Map.entry("currency", "string"));
        uplColumns.put("uplPoLineQuantity", Map.entry("poLineQuantity", "numeric"));
        uplColumns.put("uplPoLineUnitPrice", Map.entry("poLineUnitPrice", "numeric"));
        uplColumns.put("uplLineQuantity", Map.entry("uplLineQuantity", "numeric"));
        uplColumns.put("uplLineUnitPrice", Map.entry("uplLineUnitPrice", "numeric"));
        uplColumns.put("substituteItemCode", Map.entry("substituteItemCode", "string"));
        uplColumns.put("uplRemarks", Map.entry("remarks", "string"));
        uplColumns.put("dptApprover1", Map.entry("dptApprover1", "string"));
        uplColumns.put("dptApprover2", Map.entry("dptApprover2", "string"));
        uplColumns.put("dptApprover3", Map.entry("dptApprover3", "string"));
        uplColumns.put("dptApprover4", Map.entry("dptApprover4", "string"));
        uplColumns.put("regionalApprover", Map.entry("regionalApprover", "string"));
        uplColumns.put("uplCreatedBy", Map.entry("createdBy", "string"));
        uplColumns.put("uplCreatedByName", Map.entry("createdByName", "string"));

        final Map<String, Map.Entry<String, String>> categoryColumns = new HashMap<>();
        categoryColumns.put("scopeOfWork", Map.entry("scope", "string"));
        categoryColumns.put("categoryDescription", Map.entry("categoryDescription", "string"));

        final List<String> calculatedColumns = Arrays.asList(
            "UPLACPTRequestValue", "POAcceptanceQty", "POLineAcceptanceQty",
            "uplPendingQuantity", "canRaiseAcceptance"
        );

        // Build Specification for database filtering
        Specification<PurchaseOrderTb> poSpec = Specification.where(null);

        if (supplierId != null && !"0".equals(supplierId)) {
            poSpec = poSpec.and((root, query, cb) -> cb.equal(root.get("vendorNumber"), supplierId));
        }
        if (poID != null && !"0".equals(poID)) {
            poSpec = poSpec.and((root, query, cb) -> cb.equal(root.get("poNumber"), poID));
        }
        if (dateFrom != null && !dateFrom.isEmpty() && dateTo != null && !dateTo.isEmpty()) {
            poSpec = poSpec.and((root, query, cb) -> cb.between(root.get("createdDate"), dateFrom, dateTo));
        }

        // Database-level filtering for schema columns
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && !calculatedColumns.contains(columnName)) {
            poSpec = poSpec.and((root, query, cb) -> {
                query.distinct(true); // Avoid duplicates from joins
                try {
                    if (poColumns.containsKey(columnName)) {
                        Map.Entry<String, String> entry = poColumns.get(columnName);
                        String fieldName = entry.getKey();
                        String type = entry.getValue();
                        if ("string".equals(type)) {
                            return cb.like(cb.lower(root.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
                        } else if ("numeric".equals(type)) {
                            return cb.equal(root.get(fieldName), Double.parseDouble(searchQuery));
                        } else if ("date".equals(type)) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            Date parsedDate = sdf.parse(searchQuery);
                            return cb.equal(root.get(fieldName), parsedDate);
                        } else if ("boolean".equals(type)) {
                            boolean value = Boolean.parseBoolean(searchQuery) || "1".equals(searchQuery) || "true".equalsIgnoreCase(searchQuery);
                            return cb.equal(root.get(fieldName), value ? "1" : "0");
                        }
                    } else if (uplColumns.containsKey(columnName)) {
                        Join<PurchaseOrderTb, tb_PurchaseOrderUPL> uplJoin = root.join("tbPurchaseOrderUPLs", JoinType.LEFT);
                        Map.Entry<String, String> entry = uplColumns.get(columnName);
                        String fieldName = entry.getKey();
                        String type = entry.getValue();
                        if ("string".equals(type)) {
                            return cb.like(cb.lower(uplJoin.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
                        } else if ("numeric".equals(type)) {
                            return cb.equal(uplJoin.get(fieldName), Double.parseDouble(searchQuery));
                        }
                    } else if (categoryColumns.containsKey(columnName)) {
                        Join<PurchaseOrderTb, tb_PurchaseOrderUPL> uplJoin = root.join("tbPurchaseOrderUPLs", JoinType.LEFT);
                        Join<tb_PurchaseOrderUPL, Category> categoryJoin = uplJoin.join("category", JoinType.LEFT);
                        Map.Entry<String, String> entry = categoryColumns.get(columnName);
                        String fieldName = entry.getKey();
                        return cb.like(cb.lower(categoryJoin.get(fieldName)), "%" + searchQuery.toLowerCase() + "%");
                    }
                } catch (NumberFormatException | ParseException e) {
                    // Invalid number or date format; skip filtering
                    return null;
                }
                return null; // No filtering if columnName is invalid
            });
        }

        // Fetch paginated results
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(size, 1));
        Page<PurchaseOrderTb> poPage = purchaseOrderRepository.findAll(poSpec, pageable);

        List<String> poNumbers = poPage.getContent().stream()
            .map(PurchaseOrderTb::getPoNumber)
            .collect(Collectors.toList());

        // Fetch UPLs in batch
        Map<String, List<tb_PurchaseOrderUPL>> uplMap = purchaseOrderUPLRepo.findByPoNumberIn(poNumbers)
            .stream().collect(Collectors.groupingBy(tb_PurchaseOrderUPL::getPoNumber));

        // Fetch categories in batch
        Set<String> categoryCodes = uplMap.values().stream()
            .flatMap(List::stream)
            .map(tb_PurchaseOrderUPL::getZainItemCategoryCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, Category> categoryMap = categoryRepo.findByItemCategoryCodeIn(categoryCodes)
            .stream().collect(Collectors.toMap(Category::getItemCategoryCode, c -> c));

        // Build result
        List<Map<String, Object>> result = new ArrayList<>();
        for (PurchaseOrderTb po : poPage.getContent()) {
            List<tb_PurchaseOrderUPL> upls = uplMap.getOrDefault(po.getPoNumber(), Collections.emptyList());
            if (upls.isEmpty()) upls = Collections.singletonList(null); // for POs with no UPL
            for (tb_PurchaseOrderUPL upl : upls) {
                Map<String, Object> row = new LinkedHashMap<>();
                // PO columns
                row.put("poRecordNo", po.getRecordNo());
                row.put("poNumber", po.getPoNumber());
                row.put("typeLookUpCode", po.getTypeLookUpCode());
                row.put("blanketTotalAmount", po.getBlanketTotalAmount());
                row.put("releaseNum", po.getReleaseNum());
                row.put("lineNumber", po.getLineNumber());
                row.put("prNum", po.getPrNum());
                row.put("poProjectName", po.getNewProjectName());
                row.put("newProjectName", po.getNewProjectName());
                row.put("itemPartNumber", po.getItemPartNumber());
                Object pendingQuantity = po.getPoQtyNew() != null && po.getPoQtyNew() > 0
                    ? po.getQuantityDueNew()
                    : po.getQuantityDueOld();
                row.put("poPendingQuantity", pendingQuantity != null ? pendingQuantity : 0.0);
                row.put("prSubAllow", "1".equals(po.getPrSubAllow()));
                row.put("poCountryOfOrigin", po.getCountryOfOrigin());
                row.put("poOrderQuantity", po.getPoQtyNew() != null && po.getPoQtyNew() > 0 ? po.getPoQtyNew() : po.getPoOrderQuantity());
                row.put("poQtyNew", po.getPoQtyNew());
                row.put("quantityReceived", po.getQuantityReceived());
                row.put("poCurrencyCode", po.getCurrencyCode());
                row.put("unitPriceInPoCurrency", po.getUnitPriceInPoCurrency());
                row.put("unitPriceInSAR", po.getUnitPriceInSAR());
                row.put("linePriceInPoCurrency", po.getLinePriceInPoCurrency());
                row.put("linePriceInSAR", po.getLinePriceInSAR());
                row.put("amountReceived", po.getAmountReceived());
                row.put("poLineDescription", po.getPoLineDescription());
                row.put("organizationName", po.getOrganizationName());
                row.put("organizationCode", po.getOrganizationCode());
                row.put("subInventoryCode", po.getSubInventoryCode());
                row.put("receiptRouting", po.getReceiptRouting());
                row.put("authorisationStatus", po.getAuthorisationStatus());
                row.put("departmentName", po.getDepartmentName());
                row.put("businessOwner", po.getBusinessOwner());
                row.put("poLineType", po.getPoLineType());
                row.put("poAcceptanceType", po.getAcceptanceType());
                row.put("costCenter", po.getCostCenter());
                row.put("chargeAccount", po.getChargeAccount());
                row.put("serialControl", po.getSerialControl());
                row.put("vendorSerialNumberYN", po.getVendorSerialNumberYN());
                row.put("itemType", po.getItemType());
                row.put("itemCategoryInventory", po.getItemCategoryInventory());
                row.put("inventoryCategoryDescription", po.getInventoryCategoryDescription());
                row.put("itemCategoryFA", po.getItemCategoryFA());
                row.put("FACategoryDescription", po.getFACategoryDescription());
                row.put("itemCategoryPurchasing", po.getItemCategoryPurchasing());
                row.put("PurchasingCategoryDescription", po.getPurchasingCategoryDescription());
                row.put("poVendorName", po.getVendorName());
                row.put("poVendorNumber", po.getVendorNumber());
                row.put("poApprovedDate", po.getApprovedDate());
                row.put("poCreatedDate", po.getCreatedDate());
                row.put("poCreatedBy", po.getCreatedBy());
                row.put("poCreatedByName", po.getCreatedByName());

                // UPL columns (if upl != null)
                if (upl != null) {
                    row.put("uplRecordNo", upl.getRecordNo());
                    row.put("uplManufacturer", upl.getManufacturer());
                    row.put("uplCountryOfOrigin", upl.getCountryOfOrigin());
                    row.put("uplReleaseNumber", upl.getReleaseNumber());
                    row.put("uplLine", upl.getUplLine());
                    row.put("uplPoLineItemType", upl.getPoLineItemType());
                    row.put("uplPoLineItemCode", upl.getPoLineItemCode());
                    row.put("uplPoLineDescription", upl.getPoLineDescription());
                    row.put("uplLineItemType", upl.getUplLineItemType());
                    row.put("uplLineItemCode", upl.getUplLineItemCode());
                    row.put("uplLineDescription", upl.getUplLineDescription());
                    row.put("zainItemCategoryCode", upl.getZainItemCategoryCode());
                    row.put("zainItemCategoryDescription", upl.getZainItemCategoryDescription());
                    row.put("uplItemSerialized", upl.getUplItemSerialized());
                    row.put("activeOrPassive", upl.getActiveOrPassive());
                    row.put("uplUom", upl.getUom());
                    row.put("uplCurrency", upl.getCurrency());
                    row.put("uplPoLineQuantity", upl.getPoLineQuantity());
                    row.put("uplPoLineUnitPrice", upl.getPoLineUnitPrice());
                    row.put("uplLineQuantity", upl.getUplLineQuantity());
                    row.put("uplLineUnitPrice", upl.getUplLineUnitPrice());
                    row.put("substituteItemCode", upl.getSubstituteItemCode());
                    row.put("uplRemarks", upl.getRemarks());
                    row.put("dptApprover1", upl.getDptApprover1());
                    row.put("dptApprover2", upl.getDptApprover2());
                    row.put("dptApprover3", upl.getDptApprover3());
                    row.put("dptApprover4", upl.getDptApprover4());
                    row.put("regionalApprover", upl.getRegionalApprover());
                    row.put("uplCreatedBy", upl.getCreatedBy());
                    row.put("uplCreatedByName", upl.getCreatedByName());

                    // Category columns via UPL
                    Category cat = upl.getZainItemCategoryCode() != null
                        ? categoryMap.get(upl.getZainItemCategoryCode())
                        : null;
                    row.put("scopeOfWork", cat != null ? cat.getScope() : null);
                    row.put("categoryDescription", cat != null ? cat.getCategoryDescription() : null);

                    // Calculated columns
                    Double deliveredQty = dccLineRepo.sumDeliveredQtyByUplLineAndPoLineAndPoNumber(
                        upl.getUplLine(), upl.getPoLineNumber(), po.getPoNumber());
                    row.put("UPLACPTRequestValue", deliveredQty != null ? deliveredQty : 0.0);

                    // POAcceptanceQty
                    Double poLineQty = upl.getPoLineQuantity();
                    Double poLineUnitPrice = upl.getPoLineUnitPrice();
                    Double uplLineQty = upl.getUplLineQuantity();
                    Double poAcceptanceQty = 0.0;
                    if (uplLineQty != null && uplLineQty > 0) {
                        Double denom = (poLineQty != null && poLineUnitPrice != null) ? poLineQty * poLineUnitPrice : 0.0;
                        poAcceptanceQty = (denom != 0.0) ? (deliveredQty / denom) : 0.0;
                    }
                    row.put("POAcceptanceQty", poAcceptanceQty);

                    // POLineAcceptanceQty
                    Double poLineAcceptanceQty = purchaseOrderUPLRepo.sumPOLineAcceptanceQty(po.getPoNumber(), upl.getPoLineNumber());
                    row.put("POLineAcceptanceQty", poLineAcceptanceQty != null ? poLineAcceptanceQty : 0.0);

                    // uplPendingQuantity
                    Double uplPendingQuantity = uplLineQty;
                    if (uplLineQty != null && uplLineQty > 0) {
                        uplPendingQuantity = uplLineQty - (deliveredQty != null ? deliveredQty : 0.0);
                    }
                    row.put("uplPendingQuantity", uplPendingQuantity != null ? uplPendingQuantity : 0.0);
                } else {
                    // Set all UPL and calculated columns to null or 0.0 if upl is null
                    row.put("uplRecordNo", null);
                    row.put("uplManufacturer", null);
                    row.put("uplCountryOfOrigin", null);
                    row.put("uplReleaseNumber", null);
                    row.put("uplLine", null);
                    row.put("uplPoLineItemType", null);
                    row.put("uplPoLineItemCode", null);
                    row.put("uplPoLineDescription", null);
                    row.put("uplLineItemType", null);
                    row.put("uplLineItemCode", null);
                    row.put("uplLineDescription", null);
                    row.put("zainItemCategoryCode", null);
                    row.put("zainItemCategoryDescription", null);
                    row.put("uplItemSerialized", null);
                    row.put("activeOrPassive", null);
                    row.put("uplUom", null);
                    row.put("uplCurrency", null);
                    row.put("uplPoLineQuantity", null);
                    row.put("uplPoLineUnitPrice", null);
                    row.put("uplLineQuantity", null);
                    row.put("uplLineUnitPrice", null);
                    row.put("substituteItemCode", null);
                    row.put("uplRemarks", null);
                    row.put("dptApprover1", null);
                    row.put("dptApprover2", null);
                    row.put("dptApprover3", null);
                    row.put("dptApprover4", null);
                    row.put("regionalApprover", null);
                    row.put("uplCreatedBy", null);
                    row.put("uplCreatedByName", null);
                    row.put("scopeOfWork", null);
                    row.put("categoryDescription", null);
                    row.put("UPLACPTRequestValue", 0.0);
                    row.put("POAcceptanceQty", 0.0);
                    row.put("POLineAcceptanceQty", 0.0);
                    row.put("uplPendingQuantity", null);
                }

                // Logic for canRaiseAcceptance
                row.put("canRaiseAcceptance",
                    (po.getLineCancelFlag() != null && "0".equals(po.getLineCancelFlag())
                            && "APPROVED".equals(po.getAuthorisationStatus())
                            && "OPEN".equals(po.getPoClosureStatus()))
                        ? "YES" : "NO"
                );

                result.add(row);
            }
        }

        // Post-processing for calculated columns
        if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()
                && calculatedColumns.contains(columnName)) {
            String searchPattern = searchQuery.toLowerCase();
            result = result.stream()
                .filter(row -> {
                    Object value = row.get(columnName);
                    if (value == null) return false;
                    String stringValue = String.valueOf(value).toLowerCase();
                    return stringValue.contains(searchPattern);
                })
                .collect(Collectors.toList());
        }

        // Use original pagination for database-filtered results
        Map<String, Object> response = new HashMap<>();
        response.put("data", result);
        response.put("totalRecords", poPage.getTotalElements());
        response.put("currentPage", page);
        response.put("pageSize", size);
        response.put("totalPages", poPage.getTotalPages());
        return response;
    }
}
