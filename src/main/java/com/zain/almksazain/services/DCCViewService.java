///*
// * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
// * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
// */
//package com.zain.almksazain.services;
//
//import com.zain.almksazain.model.DCC;
//import com.zain.almksazain.model.DCCLineItem;
//import com.zain.almksazain.model.DCCViewDTO;
//import com.zain.almksazain.model.tbCategoryApprovalRequests;
//import com.zain.almksazain.model.tbPurchaseOrder;
//import com.zain.almksazain.model.tb_PurchaseOrderUPL;
//import com.zain.almksazain.repo.CategoryApprovalRepo;
//import com.zain.almksazain.repo.CategoryApprovalRequestRepo;
//import com.zain.almksazain.repo.DCCRepository;
//import com.zain.almksazain.repo.DccLineRepo;
//import com.zain.almksazain.repo.tbPurchaseOrderRepo;
//import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;
//import java.math.BigDecimal;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
///**
// *
// * @author jgithu
// */
//@Service
//@Transactional
//public class DCCViewService {
//
//    @Autowired
//    private DCCRepository dccRepository;
//
//    @Autowired
//    private DccLineRepo dccLineRepository;
//
//    @Autowired
//    private tbPurchaseOrderRepo purchaseOrderRepository;
//
//    @Autowired
//    private tbPurchaseOrderUPLRepo purchaseOrderUPLRepository;
//
//    @Autowired
//    private CategoryApprovalRequestRepo categoryApprovalRequestRepository;
//
//    @Autowired
//    private CategoryApprovalRepo categoryApprovalRepository;
//
//    public List<DCCViewDTO> getDCCViewData() {
//        List<DCC> dccList = dccRepository.findAllOrdered();
//        List<DCCViewDTO> result = new ArrayList<>();
//
//        // Pre-load all POs and UPLs to minimize database queries
//        Map<String, tbPurchaseOrder> poMap = purchaseOrderRepository.findAll().stream()
//                .collect(Collectors.toMap(tbPurchaseOrder::getPoNumber, Function.identity()));
//
//        Map<String, List<tb_PurchaseOrderUPL>> uplMap = purchaseOrderUPLRepository.findAll().stream()
//                .collect(Collectors.groupingBy(upl -> upl.getPoNumber()));
//
//        for (DCC dcc : dccList) {
//            tbPurchaseOrder po = poMap.get(dcc.getPoNumber());
//            if (po == null) {
//                continue;
//            }
//
//            List<DCCLineItem> lines = dccLineRepository.findByDcc(dcc);
//            List<tb_PurchaseOrderUPL> upls = uplMap.getOrDefault(dcc.getPoNumber(), Collections.emptyList());
//
//            // Get latest approval request
//            List<tbCategoryApprovalRequests> approvalRequests = categoryApprovalRequestRepository.findLatestByDccId(dcc.getRecordNo());
//            tbCategoryApprovalRequests latestApprovalRequest = approvalRequests.isEmpty() ? null : approvalRequests.get(0);
//
//            for (DCCLineItem line : lines) {
//                DCCViewDTO dto = new DCCViewDTO();
//                mapBaseDCCData(dcc, dto);
//                mapPurchaseOrderData(po, dto);
//                mapDCCLineData(line, dto);
//
//                // Find matching UPL if exists
//                tb_PurchaseOrderUPL upl = findMatchingUPL(upls, line, dcc.getPoNumber());
//                if (upl != null) {
//                    mapUPLData(upl, dto);
//                }
//
//                // Find matching PO line item (for cases with same PO number but different line numbers)
//                mapPOLineItemData(po, line, dto);
//
//                // Calculate derived fields
//                calculateDerivedFields(dto, dcc, line, upl, latestApprovalRequest, po);
//
//                result.add(dto);
//            }
//        }
//
//        return result;
//    }
//
//    private void mapPOLineItemData(tbPurchaseOrder po, DCCLineItem line, DCCViewDTO dto) {
//        // This assumes PurchaseOrder has a collection of line items
//        // You might need to adjust based on your actual entity structure
//        if (po.getLineItems() != null) {
//            po.getLineItems().stream()
//                    .filter(item -> item.getLineNumber().equals(line.getLineNumber()))
//                    .findFirst()
//                    .ifPresent(item -> {
//                        dto.setPoLineDescription(item.getDescription());
//                        dto.setPoOrderQuantity(item.getQuantity());
//                        // Map other PO line item fields as needed
//                    });
//        }
//    }
//
//    private tb_PurchaseOrderUPL findMatchingUPL(List<tb_PurchaseOrderUPL> upls, DCCLineItem line, String poNumber) {
//        if (line.getUplLineNumber() != null && !line.getUplLineNumber().isEmpty()) {
//            return upls.stream()
//                    .filter(u -> u.getUplLine().equals(line.getUplLineNumber())
//                    && u.getPoLineNumber().equals(line.getLineNumber())
//                    && u.getPurchaseOrder().getPoNumber().equals(poNumber))
//                    .findFirst()
//                    .orElse(null);
//        }
//        return null;
//    }
//
//    private void mapBaseDCCData(DCC dcc, DCCViewDTO dto) {
//        dto.setDccRecordNo(dcc.getRecordNo());
//        dto.setDccPoNumber(dcc.getPoNumber());
//        dto.setDccVendorName(dcc.getVendorName());
//        dto.setDccVendorEmail(dcc.getVendorEmail());
//        dto.setDccAcceptanceType(dcc.getAcceptanceType());
//        dto.setDccStatus(dcc.getStatus());
//        dto.setDccCreatedDate(formatDate(dcc.getCreatedDate()));
//        dto.setVendorComment(dcc.getVendorComment());
//        dto.setDccId(dcc.getDccId());
//        dto.setDccCurrency(dcc.getCurrency());
//        dto.setCreatedBy(dcc.getCreatedBy());
//    }
//
//    private void mapPurchaseOrderData(PurchaseOrder po, DCCViewDTO dto) {
//        dto.setDccProjectName(po.getProjectName());
//        dto.setNewProjectName(po.getNewProjectName());
//        dto.setProjectName(po.getProjectName());
//        dto.setSupplierId(po.getVendorNumber());
//        dto.setVendorName(po.getVendorName());
//        dto.setPoId(po.getPoNumber());
//        dto.setCreatedByName(po.getCreatedByName());
//    }
//
//    private void mapDCCLineData(DCCLine line, DCCViewDTO dto) {
//        dto.setLnRecordNo(line.getRecordNo());
//        dto.setLnProductName(line.getProductName());
//        dto.setLnProductSerialNo(line.getSerialNumber());
//        dto.setLnDeliveredQty(line.getDeliveredQty());
//        dto.setLnLocationName(line.getLocationName());
//        dto.setLnInserviceDate(formatDate(line.getDateInService()));
//        dto.setLnUnitPrice(line.getUnitPrice());
//        dto.setLnScopeOfWork(line.getScopeOfWork());
//        dto.setLnRemarks(line.getRemarks());
//        dto.setLinkId(line.getLinkId());
//        dto.setTagNumber(line.getTagNumber());
//        dto.setLineNumber(line.getLineNumber());
//        dto.setActualItemCode(line.getActualItemCode());
//        dto.setUplLineNumber(line.getUplLineNumber());
//        dto.setPoId(line.getPoId());
//    }
//
//    private void mapUPLData(PurchaseOrderUPL upl, DCCViewDTO dto) {
//        dto.setLnItemCode(upl.getPoLineItemCode());
//        dto.setUplLineQuantity(upl.getUplLineQuantity());
//        dto.setPoLineQuantity(upl.getPoLineQuantity());
//        dto.setUplLineItemCode(upl.getUplLineItemCode());
//        dto.setUplLineDescription(upl.getUplLineDescription());
//        dto.setUnitOfMeasure(upl.getUom());
//        dto.setActiveOrPassive(upl.getActiveOrPassive());
//        dto.setPoLineDescription(upl.getPoLineDescription());
//        dto.setPoLineItemCode(upl.getPoLineItemCode());
//    }
//
//    private void calculateDerivedFields(DCCViewDTO dto, DCC dcc, DCCLine line, PurchaseOrderUPL upl,
//            CategoryApprovalRequest approvalRequest, PurchaseOrder po) {
//        // Calculate itemPartNumber
//        if (upl != null) {
//            dto.setItemPartNumber(upl.getPoLineItemCode());
//        } else {
//            // Try to get from PO line item if available
//            Optional<POLineItem> poLineItem = po.getLineItems().stream()
//                    .filter(item -> item.getLineNumber().equals(line.getLineNumber()))
//                    .findFirst();
//
//            dto.setItemPartNumber(poLineItem.map(POLineItem::getItemPartNumber)
//                    .orElse(po.getItemPartNumber()));
//        }
//
//        // Calculate poOrderQuantity
//        if (upl != null) {
//            dto.setPoOrderQuantity(upl.getPoLineQuantity());
//        } else {
//            // Try to get from PO line item if available
//            Optional<POLineItem> poLineItem = po.getLineItems().stream()
//                    .filter(item -> item.getLineNumber().equals(line.getLineNumber()))
//                    .findFirst();
//
//            if (poLineItem.isPresent()) {
//                dto.setPoOrderQuantity(poLineItem.get().getQuantity());
//            } else if (po.getPoQtyNew() != null) {
//                dto.setPoOrderQuantity(po.getPoQtyNew());
//            } else {
//                dto.setPoOrderQuantity(po.getPoOrderQuantity());
//            }
//        }
//
//        // Calculate poLineDescription
//        if (upl != null) {
//            dto.setPoLineDescription(upl.getPoLineDescription());
//        } else {
//            // Try to get from PO line item if available
//            Optional<POLineItem> poLineItem = po.getLineItems().stream()
//                    .filter(item -> item.getLineNumber().equals(line.getLineNumber()))
//                    .findFirst();
//
//            dto.setPoLineDescription(poLineItem.map(POLineItem::getDescription)
//                    .orElse(po.getPoLineDescription()));
//        }
//
//        // Calculate UPLACPTRequestValue, POAcceptanceQty, POLineAcceptanceQty
//        if (upl != null && upl.getUplLineQuantity() > 0) {
//            BigDecimal totalDeliveredQty = calculateTotalDeliveredQty(upl, dcc.getPoNumber(), line.getLineNumber());
//            dto.setUPLACPTRequestValue(totalDeliveredQty);
//
//            BigDecimal poAcceptanceQty = calculatePOAcceptanceQty(upl, dcc.getPoNumber(), line.getLineNumber());
//            dto.setPOAcceptanceQty(poAcceptanceQty);
//
//            BigDecimal poLineAcceptanceQty = calculatePOLineAcceptanceQty(upl, dcc.getPoNumber(), line.getLineNumber());
//            dto.setPOLineAcceptanceQty(poLineAcceptanceQty);
//        } else {
//            dto.setUPLACPTRequestValue(BigDecimal.ZERO);
//            dto.setPOAcceptanceQty(BigDecimal.ZERO);
//            dto.setPOLineAcceptanceQty(BigDecimal.ZERO);
//        }
//
//        dto.setPoPendingQuantity(0); // As per the SQL
//
//        // Calculate uplPendingQuantity
//        if (upl != null) {
//            BigDecimal pendingQty = calculateUPLPendingQuantity(upl, dcc, line.getLineNumber());
//            dto.setUplPendingQuantity(pendingQty.intValue());
//        }
//
//        // Approval related fields
//        if (approvalRequest != null) {
//            dto.setDateApproved(formatDate(approvalRequest.getApprovedDate()));
//
//            List<CategoryApproval> pendingApprovals = categoryApprovalRepository.findPendingApprovalsByRecordId(approvalRequest.getRecordNo());
//            dto.setApprovalCount(pendingApprovals.size());
//
//            // Pending approvers
//            List<CategoryApproval> readyForApproval = categoryApprovalRepository.findReadyForApprovalByRecordId(approvalRequest.getRecordNo());
//            if (!readyForApproval.isEmpty()) {
//                dto.setPendingApprovers(readyForApproval.get(0).getApproverName());
//            } else if (!pendingApprovals.isEmpty()) {
//                dto.setPendingApprovers(pendingApprovals.get(0).getApproverName());
//            }
//
//            // Approver comment
//            List<String> comments = categoryApprovalRepository.findApproverComments(approvalRequest.getRecordNo());
//            if (!comments.isEmpty()) {
//                dto.setApproverComment(comments.get(0));
//            }
//
//            // User aging
//            dto.setUserAging(calculateUserAging(approvalRequest.getRecordNo()));
//
//            // Total aging
//            dto.setTotalAging(calculateTotalAging(approvalRequest.getRecordNo()));
//        }
//    }
//
//    private BigDecimal calculateTotalDeliveredQty(PurchaseOrderUPL upl, String poNumber, String lineNumber) {
//        // Implement logic to calculate total delivered quantity for the UPL line
//        // This should consider both the UPL line and PO line number
//        return dccLineRepository.sumDeliveredQtyByPoAndLine(poNumber, lineNumber, upl.getUplLine());
//    }
//
//    private BigDecimal calculatePOAcceptanceQty(PurchaseOrderUPL upl, String poNumber, String lineNumber) {
//        // Implement logic to calculate PO acceptance quantity
//        // Should consider both the UPL line and PO line number
//        BigDecimal totalDelivered = dccLineRepository.sumDeliveredQtyByPoAndLine(poNumber, lineNumber, upl.getUplLine());
//        BigDecimal totalValue = upl.getPoLineQuantity().multiply(upl.getPoLineUnitPrice());
//
//        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
//            return BigDecimal.ZERO;
//        }
//
//        return totalDelivered.divide(totalValue, 4, RoundingMode.HALF_UP);
//    }
//
//    private BigDecimal calculatePOLineAcceptanceQty(PurchaseOrderUPL upl, String poNumber, String lineNumber) {
//        // Implement logic to calculate PO line acceptance quantity
//        // Should consider both the UPL line and PO line number
//        List<PurchaseOrderUPL> allUplsForLine = purchaseOrderUPLRepository.findByPoNumberAndPoLineNumber(poNumber, lineNumber);
//
//        BigDecimal totalQuantity = allUplsForLine.stream()
//                .map(u -> u.getUplLineQuantity().multiply(u.getPoLineQuantity()))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        BigDecimal totalValue = allUplsForLine.stream()
//                .map(u -> u.getPoLineQuantity().multiply(u.getPoLineUnitPrice()))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
//            return BigDecimal.ZERO;
//        }
//
//        return totalQuantity.divide(totalValue, 4, RoundingMode.HALF_UP);
//    }
//
//    private BigDecimal calculateUPLPendingQuantity(PurchaseOrderUPL upl, DCC dcc, String lineNumber) {
//        BigDecimal totalDelivered = dccLineRepository.sumDeliveredQtyByPoAndLineAndStatus(
//                dcc.getPoNumber(),
//                lineNumber,
//                upl.getUplLine(),
//                Arrays.asList("incomplete", "rejected")
//        );
//
//        return BigDecimal.valueOf(upl.getUplLineQuantity()).subtract(totalDelivered);
//    }
//
//    private String calculateUserAging(Long recordNo) {
//        List<CategoryApproval> pendingApprovals = categoryApprovalRepository.findPendingApprovalsByRecordId(recordNo);
//
//        if (pendingApprovals.isEmpty()) {
//            LocalDateTime maxRecordDateTime = categoryApprovalRepository.findMaxRecordDateTimeForPending(recordNo);
//            if (maxRecordDateTime == null) {
//                return "0 days 0 hrs 0 mins";
//            }
//
//            Duration duration = Duration.between(maxRecordDateTime, LocalDateTime.now());
//            return formatDuration(duration);
//        } else {
//            LocalDate maxApprovedDate = categoryApprovalRepository.findMaxApprovedDateForPending(recordNo);
//            LocalDateTime maxRecordDateTime = categoryApprovalRepository.findMaxRecordDateTimeForPending(recordNo);
//
//            LocalDateTime startDateTime = maxApprovedDate != null
//                    ? maxApprovedDate.atStartOfDay()
//                    : (maxRecordDateTime != null ? maxRecordDateTime : LocalDateTime.now());
//
//            Duration duration = Duration.between(startDateTime, LocalDateTime.now());
//            return formatDuration(duration);
//        }
//    }
//
//    private String calculateTotalAging(Long recordNo) {
//        List<CategoryApproval> pendingApprovals = categoryApprovalRepository.findByApprovalRecordId(recordNo);
//
//        if (pendingApprovals.stream().noneMatch(a -> "pending".equals(a.getApprovalStatus()))) {
//            LocalDateTime minRecordDateTime = categoryApprovalRepository.findMinRecordDateTime(recordNo);
//            LocalDate maxApprovedDate = categoryApprovalRepository.findMaxApprovedDate(recordNo);
//
//            if (minRecordDateTime == null || maxApprovedDate == null) {
//                return "0 days 0 hrs 0 mins";
//            }
//
//            Duration duration = Duration.between(minRecordDateTime, maxApprovedDate.atStartOfDay());
//            return formatDuration(duration);
//        } else {
//            List<CategoryApproval> firstPending = categoryApprovalRepository.findPendingApprovalsByRecordId(recordNo);
//            if (firstPending.isEmpty()) {
//                return "0 days 0 hrs 0 mins";
//            }
//
//            Duration duration = Duration.between(firstPending.get(0).getRecordDateTime(), LocalDateTime.now());
//            return formatDuration(duration);
//        }
//    }
//
//    private String formatDuration(Duration duration) {
//        long days = duration.toDays();
//        long hours = duration.toHours() % 24;
//        long minutes = duration.toMinutes() % 60;
//
//        return String.format("%d days %d hrs %d mins", days, hours, minutes);
//    }
//
//    private String formatDate(LocalDate date) {
//        if (date == null) {
//            return null;
//        }
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMM-yyyy");
//        return date.format(formatter);
//    }
//
//    private String formatDate(LocalDateTime dateTime) {
//        if (dateTime == null) {
//            return null;
//        }
//        return formatDate(dateTime.toLocalDate());
//    }
//
//}
