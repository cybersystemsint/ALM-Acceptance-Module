package com.zain.almksazain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zain.almksazain.dto.AcceptanceCreateResult;
import com.zain.almksazain.dto.WorkflowInitResult;
import com.zain.almksazain.model.DCC;
import com.zain.almksazain.repo.DCCRepository;
import com.zain.almksazain.repo.DccLineRepo;
import com.zain.almksazain.repo.fileRecordRepo;
import com.zain.almksazain.repo.tbPurchaseOrderUPLRepo;
import com.zain.almksazain.repo.tbRegionRepo;
import com.zain.almksazain.repo.tbSiteRepo;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcceptanceRequestServiceTest {

    @Mock private DCCRepository dccrepo;
    @Mock private DccLineRepo dcclnrepo;
    @Mock private fileRecordRepo fileRepo;
    @Mock private tbPurchaseOrderUPLRepo purchaseOrderUPLRepo;
    @Mock private tbSiteRepo siteRepo;
    @Mock private tbRegionRepo regionRepo;
    @Mock private WorkflowInitializationService workflowInitializationService;

    @InjectMocks
    private AcceptanceRequestService acceptanceRequestService;

    @Test
    void createAcceptanceRequest_workflowFailure_propagatesForRollback() throws Exception {
        JSONObject payload = basePayload("Radio", "R4");

        DCC saved = new DCC();
        saved.setRecordNo(7001L);
        when(dccrepo.findByRecordNo(0L)).thenReturn(null);
        when(dccrepo.saveAndFlush(any(DCC.class))).thenReturn(saved);
        when(dcclnrepo.findByDccId("7001")).thenReturn(Collections.emptyList());
        when(dcclnrepo.findByRecordNo(0L)).thenReturn(null);
        when(dcclnrepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new RuntimeException(
                "Error! No generic level-1 approval config exists for scope Radio, region(s) R4 without vendor-specific suppliers."))
                .when(workflowInitializationService)
                .initializeApproval(anyInt(), anyString(), anyString(), eq("Radio"), anyString(),
                        anyList(), eq("tb_DCC"), anyString(), anyString());

        try {
            acceptanceRequestService.createAcceptanceRequest(payload, null, "/tmp/");
            fail("Expected RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("No generic level-1"));
        }

        verify(workflowInitializationService).initializeApproval(
                eq(7001), anyString(), anyString(), eq("Radio"), anyString(),
                anyList(), eq("tb_DCC"), anyString(), anyString());
    }

    @Test
    void createAcceptanceRequest_objectASuccess_returnsWorkflowInitResult() throws Exception {
        JSONObject payload = basePayload("Radio", "R4");

        DCC saved = new DCC();
        saved.setRecordNo(7002L);
        when(dccrepo.findByRecordNo(0L)).thenReturn(null);
        when(dccrepo.saveAndFlush(any(DCC.class))).thenReturn(saved);
        when(dcclnrepo.findByDccId("7002")).thenReturn(Collections.emptyList());
        when(dcclnrepo.findByRecordNo(0L)).thenReturn(null);
        when(dcclnrepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowInitResult initResult = new WorkflowInitResult(
                7002, 88, "PO-1", "VendorA", "requester", "Radio",
                "approver@example.com", "Approver");
        when(workflowInitializationService.initializeApproval(
                eq(7002), anyString(), anyString(), eq("Radio"), anyString(),
                anyList(), eq("tb_DCC"), anyString(), anyString()))
                .thenReturn(initResult);

        AcceptanceCreateResult result =
                acceptanceRequestService.createAcceptanceRequest(payload, null, "/tmp/");

        assertEquals(7002L, result.getDccId());
        assertEquals(1, result.getWorkflowInitResults().size());
        assertEquals("approver@example.com", result.getWorkflowInitResults().get(0).getFirstApproverEmail());
        verify(fileRepo, never()).save(any());
    }

    private JSONObject basePayload(String scope, String region) throws Exception {
        JSONObject line = new JSONObject();
        line.put("recordNo", "0");
        line.put("poLineNumber", "1");
        line.put("uplLineNumber", "");
        line.put("itemCode", "ITEM1");
        line.put("serialNumber", "");
        line.put("deliveredQty", "1");
        line.put("locationName", "SITE1");
        line.put("dateInService", "2026-01-15");
        line.put("scopeOfWork", scope);
        line.put("remarks", "");
        line.put("linkId", "");
        line.put("tagNumber", "");
        line.put("poLineItemDescription", "ObjectA line");

        JSONArray lines = new JSONArray();
        lines.put(line);

        JSONObject payload = new JSONObject();
        payload.put("recordNo", "0");
        payload.put("poNumber", "PO-1");
        payload.put("vendorName", "VendorA");
        payload.put("vendorNumber", "V1");
        payload.put("projectName", "Proj");
        payload.put("acceptanceType", "FA");
        payload.put("status", "inprocess");
        payload.put("createdById", 42);
        payload.put("createdByName", "requester");
        payload.put("region", region);
        payload.put("lineItems", lines);
        return payload;
    }
}
