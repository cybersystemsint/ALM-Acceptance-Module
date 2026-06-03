package com.zain.almksazain.model.dto;

import com.zain.almksazain.model.tbPurchaseOrder;
import com.zain.almksazain.model.tb_PurchaseOrderUPL;

public class PoUplCombinedPair {

    private final tbPurchaseOrder purchaseOrder;
    private final tb_PurchaseOrderUPL upl;

    public PoUplCombinedPair(tbPurchaseOrder purchaseOrder, tb_PurchaseOrderUPL upl) {
        this.purchaseOrder = purchaseOrder;
        this.upl = upl;
    }

    public tbPurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public tb_PurchaseOrderUPL getUpl() {
        return upl;
    }
}
