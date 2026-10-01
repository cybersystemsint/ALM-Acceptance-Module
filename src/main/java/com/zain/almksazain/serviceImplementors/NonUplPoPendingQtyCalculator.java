package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.model.*;
import com.zain.almksazain.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PO Pending Qty for non-UPL export rows, shared by the Actioned Requests export
 * (DccPOApproverExportService) and the vendor Delivery Acceptance Requests export
 * (DccPOExportService). The Requests tab export (DccPOV2ServiceImpl) computes the same value
 * from the line items it already has loaded.
 *
 * Same formula combinedPurchaseOrderView uses for poPendingQuantity on a PO line with no ACTIVE
 * UPL - what the create-acceptance-request page validates against:
 *   quantityDue (quantityDueNew if poQtyNew > 0, else quantityDueOld)
 *   - sum of deliveredQty on that PO + line, excluding incomplete/rejected/returned DCCs.
 * UPL rows keep their own calculation in each export.
 */
@Component
public class NonUplPoPendingQtyCalculator {

    private static final Set<String> EXCLUDED_STATUSES =
            new HashSet<>(Arrays.asList("incomplete", "rejected", "returned"));

    @Autowired
    private TbDccRepository tbDccRepository;

    @Autowired
    private TbDccLnRepository tbDccLnRepository;

    /**
     * @param poNumbers        the POs in this export batch
     * @param purchaseOrderMap every line of those POs, grouped by poNumber
     * @param knownDccs        DCCs already loaded for the batch (their statuses are reused)
     * @return "poNumber|poLineNumber" -> PO Pending Qty, for every line in purchaseOrderMap
     */
    public Map<String, Double> pendingByPoLine(List<String> poNumbers,
            Map<String, List<tbPurchaseOrder>> purchaseOrderMap, Map<Long, DCC> knownDccs) {
        // The sum spans every DCC on these POs, not just the ones in this export, so load all their
        // line items (and the statuses of any DCC not already known) in two bulk queries.
        List<DCCLineItem> allLnForPos = tbDccLnRepository.findByPoIdIn(poNumbers);

        Map<Long, String> statusByDccId = new HashMap<>();
        knownDccs.forEach((id, d) -> statusByDccId.put(id, d.getStatus()));
        List<Long> otherDccIds = allLnForPos.stream()
                .map(ln -> { try { return Long.parseLong(ln.getDccId()); } catch (NumberFormatException e) { return -1L; } })
                .filter(id -> id > 0 && !statusByDccId.containsKey(id))
                .distinct().collect(Collectors.toList());
        if (!otherDccIds.isEmpty()) {
            tbDccRepository.findByRecordNoIn(otherDccIds).forEach(d -> statusByDccId.put(d.getRecordNo(), d.getStatus()));
        }

        Map<String, Double> deliveredByPoLine = new HashMap<>();
        for (DCCLineItem ln : allLnForPos) {
            if (ln.getPoId() == null || ln.getLineNumber() == null || ln.getDeliveredQty() == null) continue;
            long dccId;
            try { dccId = Long.parseLong(ln.getDccId()); } catch (NumberFormatException e) { continue; }
            String status = statusByDccId.get(dccId);
            // A NULL status never satisfies the view's NOT IN, so it isn't counted there either.
            if (status == null || EXCLUDED_STATUSES.contains(status.toLowerCase())) continue;
            deliveredByPoLine.merge(ln.getPoId() + "|" + ln.getLineNumber(), ln.getDeliveredQty(), Double::sum);
        }

        Map<String, Double> pending = new HashMap<>();
        for (List<tbPurchaseOrder> lines : purchaseOrderMap.values()) {
            for (tbPurchaseOrder po : lines) {
                if (po.getPoNumber() == null || po.getLineNumber() == null) continue;
                String key = po.getPoNumber() + "|" + po.getLineNumber();
                Double qtyNew = po.getPoQtyNew();
                Double due = (qtyNew != null && qtyNew > 0) ? po.getQuantityDueNew() : po.getQuantityDueOld();
                pending.putIfAbsent(key, (due != null ? due : 0.0) - deliveredByPoLine.getOrDefault(key, 0.0));
            }
        }
        return pending;
    }
}
