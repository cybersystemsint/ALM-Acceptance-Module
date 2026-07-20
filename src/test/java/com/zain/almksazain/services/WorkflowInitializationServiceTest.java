package com.zain.almksazain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zain.almksazain.dto.WorkflowInitResult;
import com.zain.almksazain.model.User;
import com.zain.almksazain.model.tbCategoryApprovalRequests;
import com.zain.almksazain.model.tbCategoryApprovals;
import com.zain.almksazain.model.tbScope;
import com.zain.almksazain.model.tbScopeApprovalLevelRegion;
import com.zain.almksazain.model.tbScopeApprovalLevels;
import com.zain.almksazain.model.tb_Region;
import com.zain.almksazain.repo.TbCategoryApprovalRequestsRepository;
import com.zain.almksazain.repo.TbCategoryApprovalsRepository;
import com.zain.almksazain.repo.UserRepository;
import com.zain.almksazain.repo.supplierrepo;
import com.zain.almksazain.repo.tbDelegationRepo;
import com.zain.almksazain.repo.tbRegionRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelRegionRepo;
import com.zain.almksazain.repo.tbScopeApprovalLevelsRepo;
import com.zain.almksazain.repo.tbScopeApprovalSupplierRepo;
import com.zain.almksazain.repo.tbScopeRepo;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowInitializationServiceTest {

    @Mock private TbCategoryApprovalRequestsRepository approvalRequestRepository;
    @Mock private TbCategoryApprovalsRepository categoryApprovalsRepository;
    @Mock private tbScopeRepo scopeRepository;
    @Mock private tbScopeApprovalLevelsRepo scopeApprovalLevelsRepo;
    @Mock private tbScopeApprovalLevelRegionRepo scopeApprovalLevelRegionRepo;
    @Mock private tbScopeApprovalSupplierRepo scopeApprovalSupplierRepo;
    @Mock private tbRegionRepo regionRepo;
    @Mock private supplierrepo supplierRepository;
    @Mock private UserRepository userRepository;
    @Mock private tbDelegationRepo delegationRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private WorkflowInitializationService service;

    private tbScope radioScope;
    private tbScopeApprovalLevels levelOne;
    private tb_Region regionR4;
    private User approver;

    @BeforeEach
    void setUp() {
        radioScope = new tbScope();
        radioScope.setRecordNo(10L);
        radioScope.setScope("Radio");

        levelOne = new tbScopeApprovalLevels();
        levelOne.setRecordNo(100L);
        levelOne.setLevel(1);
        levelOne.setScope(10);
        levelOne.setApproverUserId(55);

        regionR4 = new tb_Region();
        regionR4.setRecordNo(4L);
        regionR4.setRegionName("R4");

        approver = new User();
        approver.setUserId(55);
        approver.setUsername("radio.approver");
        approver.setEmailAddress("radio.approver@example.com");
        approver.setFullName("Radio Approver");
    }

    @Test
    void initializeApproval_objectAHappyPath_createsRequestAndApproval() {
        when(approvalRequestRepository.findByAcceptanceRequestRecordNoStatuses(9001))
                .thenReturn(Collections.emptyList());
        when(scopeRepository.findByScope("Radio")).thenReturn(radioScope);
        when(approvalRequestRepository.save(any(tbCategoryApprovalRequests.class))).thenAnswer(invocation -> {
            tbCategoryApprovalRequests req = invocation.getArgument(0);
            req.setRecordNo(501);
            return req;
        });
        when(scopeApprovalLevelsRepo.findByScopeOrderByLevelAsc(10)).thenReturn(List.of(levelOne));
        when(scopeApprovalSupplierRepo.findByScopeApprovalLevelRecordNoIn(anyList()))
                .thenReturn(Collections.emptyList());

        tbScopeApprovalLevelRegion mapping = new tbScopeApprovalLevelRegion();
        mapping.setScope(radioScope);
        mapping.setScopeApprovalLevel(levelOne);
        mapping.setRegion(regionR4);
        when(scopeApprovalLevelRegionRepo.findByScopeAndScopeApprovalLevel(radioScope, levelOne))
                .thenReturn(List.of(mapping));
        when(regionRepo.findByRegionName("R4")).thenReturn(regionR4);
        when(userRepository.findByUserId(55)).thenReturn(Optional.of(approver));
        when(delegationRepository.findActiveDelegation(eq(55), any())).thenReturn(Optional.empty());
        when(categoryApprovalsRepository.save(any(tbCategoryApprovals.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryApprovalsRepository.findPendingReadyForApprovalByRequestNo(501))
                .thenAnswer(invocation -> {
                    tbCategoryApprovals approval = new tbCategoryApprovals();
                    approval.setApproverId(55);
                    approval.setStatus("pending");
                    approval.setApprovalStatus("readyForApproval");
                    return List.of(approval);
                });

        WorkflowInitResult result = service.initializeApproval(
                9001, "VendorA", "requester", "Radio", "42",
                List.of("R4"), "tb_DCC", "PO line desc", "PO-OBJECT-A");

        assertNotNull(result);
        assertEquals(9001, result.getDccId());
        assertEquals(501, result.getApprovalRequestRecordNo());
        assertEquals("radio.approver@example.com", result.getFirstApproverEmail());

        ArgumentCaptor<tbCategoryApprovalRequests> requestCaptor =
                ArgumentCaptor.forClass(tbCategoryApprovalRequests.class);
        verify(approvalRequestRepository).save(requestCaptor.capture());
        assertEquals("pending", requestCaptor.getValue().getStatus());
        assertEquals(10, requestCaptor.getValue().getScope());
        assertEquals("PO-OBJECT-A", requestCaptor.getValue().getPoNumber());

        ArgumentCaptor<tbCategoryApprovals> approvalCaptor =
                ArgumentCaptor.forClass(tbCategoryApprovals.class);
        verify(categoryApprovalsRepository).save(approvalCaptor.capture());
        assertEquals("pending", approvalCaptor.getValue().getStatus());
        assertEquals("readyForApproval", approvalCaptor.getValue().getApprovalStatus());
        assertEquals(4, approvalCaptor.getValue().getRegionId());
        assertEquals(55, approvalCaptor.getValue().getApproverId());
    }

    @Test
    void initializeApproval_missingRegionMapping_throwsAndDoesNotCreateApprovals() {
        when(approvalRequestRepository.findByAcceptanceRequestRecordNoStatuses(9002))
                .thenReturn(Collections.emptyList());
        when(scopeRepository.findByScope("Radio")).thenReturn(radioScope);
        when(approvalRequestRepository.save(any(tbCategoryApprovalRequests.class))).thenAnswer(invocation -> {
            tbCategoryApprovalRequests req = invocation.getArgument(0);
            req.setRecordNo(502);
            return req;
        });
        when(scopeApprovalLevelsRepo.findByScopeOrderByLevelAsc(10)).thenReturn(List.of(levelOne));
        when(scopeApprovalSupplierRepo.findByScopeApprovalLevelRecordNoIn(anyList()))
                .thenReturn(Collections.emptyList());
        when(scopeApprovalLevelRegionRepo.findByScopeAndScopeApprovalLevel(radioScope, levelOne))
                .thenReturn(Collections.emptyList());

        try {
            service.initializeApproval(
                    9002, "VendorA", "requester", "Radio", "42",
                    List.of("R4"), "tb_DCC", "PO line desc", "PO-FAIL");
            fail("Expected RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("No generic level-1 approval config")
                    || ex.getMessage().contains("no approval levels are configured"));
        }

        verify(categoryApprovalsRepository, never()).save(any(tbCategoryApprovals.class));
    }

    @Test
    void initializeApproval_duplicatePending_blocksReinitialization() {
        tbCategoryApprovalRequests existing = new tbCategoryApprovalRequests();
        existing.setStatus("pending");
        existing.setAcceptanceRequestRecordNo(9003);
        when(approvalRequestRepository.findByAcceptanceRequestRecordNoStatuses(9003))
                .thenReturn(List.of(existing));

        try {
            service.initializeApproval(
                    9003, "VendorA", "requester", "Radio", "42",
                    List.of("R4"), "tb_DCC", "PO line desc", "PO-DUP");
            fail("Expected RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("already initialized"));
        }

        verify(approvalRequestRepository, never()).save(any(tbCategoryApprovalRequests.class));
        verify(categoryApprovalsRepository, never()).save(any(tbCategoryApprovals.class));
    }
}
