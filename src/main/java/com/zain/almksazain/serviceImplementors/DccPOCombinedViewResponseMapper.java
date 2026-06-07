package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.DTO.DccPOCombinedViewDTO;
import com.zain.almksazain.DTO.DccPOLineItemDTO;
import com.zain.almksazain.DTO.DccPOParentDTO;
import com.zain.almksazain.DTO.DccPOResponseDTO;
import com.zain.almksazain.serviceImplementors.DccPOService.DccPOFetchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps flat combined-view rows to the hierarchical response used by /dcc-po/combined-view.
 */
public final class DccPOCombinedViewResponseMapper {

    private DccPOCombinedViewResponseMapper() {}

    public static DccPOResponseDTO toResponse(DccPOFetchResult result, int page, int size) {
        List<DccPOCombinedViewDTO> data = result.getData();
        Long totalFilteredRecords = result.getTotalFilteredRecords();

        Map<Long, List<DccPOCombinedViewDTO>> groupedByDccRecordNo = data.stream()
                .collect(Collectors.groupingBy(DccPOCombinedViewDTO::getDccRecordNo));

        List<DccPOParentDTO> parentDTOs = groupedByDccRecordNo.entrySet().stream()
                .map(entry -> toParentDto(entry.getValue()))
                .sorted((a, b) -> b.getRecordNo().compareTo(a.getRecordNo()))
                .collect(Collectors.toList());

        DccPOResponseDTO responseDTO = new DccPOResponseDTO();
        responseDTO.setTotalRecords(totalFilteredRecords);
        responseDTO.setData(parentDTOs);
        responseDTO.setTotalPages((int) Math.ceil((double) totalFilteredRecords / size));
        responseDTO.setPageSize(size);
        responseDTO.setCurrentPage(page);
        return responseDTO;
    }

    private static DccPOParentDTO toParentDto(List<DccPOCombinedViewDTO> records) {
        DccPOCombinedViewDTO firstRecord = records.get(0);
        DccPOParentDTO parentDTO = new DccPOParentDTO();
        parentDTO.setRecordNo(firstRecord.getDccRecordNo());
        parentDTO.setDccPoNumber(firstRecord.getDccPoNumber());
        parentDTO.setNewProjectName(firstRecord.getNewProjectName());
        parentDTO.setDccAcceptanceType(firstRecord.getDccAcceptanceType());
        parentDTO.setDccStatus(firstRecord.getDccStatus());
        parentDTO.setDccCreatedDate(firstRecord.getDccCreatedDate());
        parentDTO.setDateApproved(firstRecord.getDateApproved());
        parentDTO.setVendorComment(firstRecord.getVendorComment());
        parentDTO.setDccId(firstRecord.getDccId());
        parentDTO.setPoId(firstRecord.getPoId());
        parentDTO.setProjectName(firstRecord.getProjectName());
        parentDTO.setSupplierId(firstRecord.getSupplierId());
        parentDTO.setVendorNumber(firstRecord.getVendorNumber());
        parentDTO.setVendorName(firstRecord.getVendorName());
        parentDTO.setCreatedBy(firstRecord.getCreatedBy());
        parentDTO.setCreatedByName(firstRecord.getCreatedByName());
        parentDTO.setApprovalCount(firstRecord.getApprovalCount());
        parentDTO.setPendingApprovers(firstRecord.getPendingApprovers());
        parentDTO.setApproverComment(firstRecord.getApproverComment());
        parentDTO.setUserAging(firstRecord.getUserAging());
        parentDTO.setTotalAging(firstRecord.getTotalAging());
        parentDTO.setVendorEmail(firstRecord.getDccVendorEmail());
        parentDTO.setDccCurrency(firstRecord.getDccCurrency());

        List<DccPOLineItemDTO> lineItems = new ArrayList<>();
        if (firstRecord.getLnRecordNo() != null) {
            lineItems = records.stream()
                    .map(DccPOCombinedViewResponseMapper::toLineItemDto)
                    .collect(Collectors.toList());
        }
        parentDTO.setLineItems(lineItems);
        return parentDTO;
    }

    private static DccPOLineItemDTO toLineItemDto(DccPOCombinedViewDTO dto) {
        DccPOLineItemDTO lineItem = new DccPOLineItemDTO();
        lineItem.setRecordNo(dto.getLnRecordNo());
        lineItem.setLnProductName(dto.getLnProductName());
        lineItem.setSerialNumber(dto.getLnProductSerialNo());
        lineItem.setDeliveredQty(dto.getLnDeliveredQty());
        lineItem.setLocationName(dto.getLnLocationName());
        lineItem.setRegion(dto.getRegion());
        lineItem.setDateInService(dto.getLnInserviceDate());
        lineItem.setLnUnitPrice(dto.getLnUnitPrice());
        lineItem.setScopeOfWork(dto.getLnScopeOfWork());
        lineItem.setRemarks(dto.getLnRemarks());
        lineItem.setItemCode(dto.getUplLineItemCode());
        lineItem.setLinkId(dto.getLinkId() != null ? String.valueOf(dto.getLinkId()) : "");
        lineItem.setTagNumber(dto.getTagNumber());
        lineItem.setPoLineNumber(dto.getLineNumber());
        lineItem.setActualItemCode(dto.getActualItemCode());
        lineItem.setUplLineNumber(dto.getUplLineNumber());
        lineItem.setCurrency(dto.getDccCurrency());
        lineItem.setPoId(dto.getPoId());
        lineItem.setUPLACPTRequestValue(dto.getUPLACPTRequestValue());
        lineItem.setpoAcceptanceQty(dto.getpoAcceptanceQty());
        lineItem.setPOLineAcceptanceQty(dto.getPOLineAcceptanceQty());
        lineItem.setPoPendingQuantity(dto.getPoPendingQuantity());
        lineItem.setPoOrderQuantity(dto.getPoOrderQuantity());
        lineItem.setItemPartNumber(dto.getItemPartNumber());
        lineItem.setPoLineDescription(dto.getPoLineDescription());
        lineItem.setUplLineQuantity(dto.getUplLineQuantity());
        lineItem.setPoLineQuantity(dto.getPoLineQuantity());
        lineItem.setUplLineItemCode(dto.getUplLineItemCode());
        lineItem.setUplLineDescription(dto.getUplLineDescription());
        lineItem.setUom(dto.getUnitOfMeasure());
        lineItem.setActiveOrPassive(dto.getActiveOrPassive());
        lineItem.setUplPendingQuantity(dto.getUplPendingQuantity());
        return lineItem;
    }
}
