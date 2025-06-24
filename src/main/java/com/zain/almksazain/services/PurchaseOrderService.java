package com.zain.almksazain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.zain.almksazain.dto.PurchaseOrderLineItemDTO;
import com.zain.almksazain.dto.PurchaseOrderRequest;
import com.zain.almksazain.dto.PurchaseOrderResponse;
import com.zain.almksazain.dto.PurchaseOrderSummaryDTO;
import com.zain.almksazain.model.PurchaseOrderTb;
import com.zain.almksazain.repo.PurchaseOrderRepository;


@Service
public class PurchaseOrderService {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    public PurchaseOrderResponse getPurchaseOrderResponse(PurchaseOrderRequest request) {
        int page = request.getPage() != null ? Math.max(request.getPage(), 1) - 1 : 0;
        int size = request.getSize() != null ? Math.max(request.getSize(), 1) : 10;

        // 1. Page on unique PO numbers
        Page<String> poNumberPage = purchaseOrderRepository.findDistinctPoNumbers(
                request.getSupplierId(), request.getPoNumber(), PageRequest.of(page, size));
        List<String> poNumbers = poNumberPage.getContent();

        if (poNumbers.isEmpty()) {
            PurchaseOrderResponse response = new PurchaseOrderResponse();
            response.setCurrentPage(page + 1);
            response.setPageSize(size);
            response.setTotalRecords(0L);
            response.setTotalPages(0);
            response.setData(Collections.emptyList());
            return response;
        }

        // 2. Fetch all line items for these PO numbers in one query
        List<PurchaseOrderTb> allLineItems = purchaseOrderRepository.findByPoNumberIn(poNumbers);

        // 3. Group by poNumber and build summaries
        Map<String, List<PurchaseOrderTb>> grouped = allLineItems.stream()
                .collect(Collectors.groupingBy(PurchaseOrderTb::getPoNumber, LinkedHashMap::new, Collectors.toList()));

        List<PurchaseOrderSummaryDTO> summaryList = new ArrayList<>();
        for (Map.Entry<String, List<PurchaseOrderTb>> entry : grouped.entrySet()) {
            List<PurchaseOrderTb> items = entry.getValue();
            if (items.isEmpty()) continue;
            PurchaseOrderTb first = items.get(0);

            PurchaseOrderSummaryDTO summary = new PurchaseOrderSummaryDTO();
            summary.setPoNumber(first.getPoNumber());
            summary.setTypeLookUpCode(first.getTypeLookUpCode());
            summary.setBlanketTotalAmount(first.getBlanketTotalAmount());
            summary.setReleaseNum(first.getReleaseNum());
            summary.setPrNum(first.getPrNum());
            summary.setProjectName(first.getProjectName());
            summary.setNewProjectName(first.getNewProjectName());
            summary.setLineCancelFlag(first.getLineCancelFlag());
            summary.setCancelReason(first.getCancelReason());
            summary.setItemPartNumber(first.getItemPartNumber());
            summary.setPrSubAllow(first.getPrSubAllow());
            summary.setCurrencyCode(first.getCurrencyCode());
            summary.setOrganizationName(first.getOrganizationName());
            summary.setOrganizationCode(first.getOrganizationCode());
            summary.setSubInventoryCode(first.getSubInventoryCode());
            summary.setReceiptRouting(first.getReceiptRouting());
            summary.setAuthorisationStatus(first.getAuthorisationStatus());
            summary.setPoClosureStatus(first.getPoClosureStatus());
            summary.setDepartmentName(first.getDepartmentName());
            summary.setBusinessOwner(first.getBusinessOwner());
            summary.setPoLineType(first.getPoLineType());
            summary.setAcceptanceType(first.getAcceptanceType());
            summary.setCostCenter(first.getCostCenter());
            summary.setChargeAccount(first.getChargeAccount());
            summary.setSerialControl(first.getSerialControl());
            summary.setItemType(first.getItemType());
            summary.setItemCategoryPurchasing(first.getItemCategoryPurchasing());
            summary.setPurchasingCategoryDescription(first.getPurchasingCategoryDescription());
            summary.setVendorName(first.getVendorName());
            summary.setVendorNumber(first.getVendorNumber());
            summary.setApprovedDate(first.getApprovedDate());
            summary.setCreatedDate(first.getCreatedDate());
            summary.setCreatedBy(first.getCreatedBy());
            summary.setCreatedByName(first.getCreatedByName());

            // Totals (sum over line items)
            summary.setTotalPoQtyNew(items.stream().mapToDouble(x -> Optional.ofNullable(x.getPoQtyNew()).orElse(0.0)).sum());
            summary.setTotalQuantityReceived(items.stream().mapToDouble(x -> Optional.ofNullable(x.getQuantityReceived()).orElse(0.0)).sum());
            summary.setTotalQuantityDueOld(items.stream().mapToDouble(x -> Optional.ofNullable(x.getQuantityDueOld()).orElse(0.0)).sum());
            summary.setTotalQuantityDueNew(items.stream().mapToDouble(x -> Optional.ofNullable(x.getQuantityDueNew()).orElse(0.0)).sum());
            summary.setTotalQuantityBilled(items.stream().mapToDouble(x -> Optional.ofNullable(x.getQuantityBilled()).orElse(0.0)).sum());
            summary.setTotalpoOrderQuantity(items.stream().mapToDouble(x -> Optional.ofNullable(x.getPoOrderQuantity()).orElse(0.0)).sum());
            summary.setTotalunitPriceInPoCurrency(items.stream().mapToDouble(x -> Optional.ofNullable(x.getUnitPriceInPoCurrency()).orElse(0.0)).sum());
            summary.setTotalunitPriceInSAR(items.stream().mapToDouble(x -> Optional.ofNullable(x.getUnitPriceInSAR()).orElse(0.0)).sum());
            summary.setTotallinePriceInPoCurrency(items.stream().mapToDouble(x -> Optional.ofNullable(x.getLinePriceInPoCurrency()).orElse(0.0)).sum());
            summary.setTotallinePriceInSAR(items.stream().mapToDouble(x -> Optional.ofNullable(x.getLinePriceInSAR()).orElse(0.0)).sum());
            summary.setTotalamountReceived(items.stream().mapToDouble(x -> Optional.ofNullable(x.getAmountReceived()).orElse(0.0)).sum());
            summary.setTotalamountDue(items.stream().mapToDouble(x -> Optional.ofNullable(x.getAmountDue()).orElse(0.0)).sum());
            summary.setTotalamountDueNew(items.stream().mapToDouble(x -> Optional.ofNullable(x.getAmountDueNew()).orElse(0.0)).sum());
            summary.setTotalamountBilled(items.stream().mapToDouble(x -> Optional.ofNullable(x.getAmountBilled()).orElse(0.0)).sum());
            summary.setTotalDescopedLinePriceInPoCurrency(items.stream().mapToDouble(x -> Optional.ofNullable(x.getDescopedLinePriceInPoCurrency()).orElse(0.0)).sum());
            summary.setTotalNewLinePriceInPoCurrency(items.stream().mapToDouble(x -> Optional.ofNullable(x.getNewLinePriceInPoCurrency()).orElse(0.0)).sum());

            // Line items
            List<PurchaseOrderLineItemDTO> lineItemDTOs = items.stream().map(line -> {
                PurchaseOrderLineItemDTO dto = new PurchaseOrderLineItemDTO();
                dto.setRecordNo(line.getRecordNo());
                dto.setPoNumber(line.getPoNumber());
                dto.setLineNumber(line.getLineNumber());
                dto.setItemPartNumber(line.getItemPartNumber());
                dto.setCountryOfOrigin(line.getCountryOfOrigin());
                dto.setPoOrderQuantity(line.getPoOrderQuantity());
                dto.setPoQtyNew(line.getPoQtyNew());
                dto.setQuantityReceived(line.getQuantityReceived());
                dto.setQuantityDueOld(line.getQuantityDueOld());
                dto.setQuantityDueNew(line.getQuantityDueNew());
                dto.setQuantityBilled(line.getQuantityBilled());
                dto.setUnitPriceInPoCurrency(line.getUnitPriceInPoCurrency());
                dto.setUnitPriceInSAR(line.getUnitPriceInSAR());
                dto.setLinePriceInPoCurrency(line.getLinePriceInPoCurrency());
                dto.setLinePriceInSAR(line.getLinePriceInSAR());
                dto.setAmountReceived(line.getAmountReceived());
                dto.setAmountDue(line.getAmountDue());
                dto.setAmountDueNew(line.getAmountDueNew());
                dto.setAmountBilled(line.getAmountBilled());
                dto.setPoLineDescription(line.getPoLineDescription());
                dto.setVendorSerialNumberYN(line.getVendorSerialNumberYN());
                dto.setItemCategoryInventory(line.getItemCategoryInventory());
                dto.setInventoryCategoryDescription(line.getInventoryCategoryDescription());
                dto.setItemCategoryFA(line.getItemCategoryFA());
                dto.setDescopedLinePriceInPoCurrency(line.getDescopedLinePriceInPoCurrency());
                dto.setNewLinePriceInPoCurrency(line.getNewLinePriceInPoCurrency());
                dto.setFacategoryDescription(line.getFACategoryDescription());
                return dto;
            }).collect(Collectors.toList());
            summary.setPolineItems(lineItemDTOs);

            summaryList.add(summary);
        }

        PurchaseOrderResponse response = new PurchaseOrderResponse();
        response.setCurrentPage(page + 1);
        response.setPageSize(size);
        response.setTotalRecords(poNumberPage.getTotalElements());
        response.setTotalPages(poNumberPage.getTotalPages());
        response.setData(summaryList);
        return response;
    }
}