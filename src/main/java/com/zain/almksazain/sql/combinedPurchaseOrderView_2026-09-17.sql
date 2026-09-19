-- Snapshot of ALM_ZAIN_KSA.combinedPurchaseOrderView as it exists LIVE in the database.
-- Captured: 2026-09-17
-- No change was made to this view during this capture -- this is a baseline
-- reference snapshot of its current definition, not a record of a fix.
-- This file is a snapshot for reference only; it is not executed by the application.
-- The view itself lives in the database and must be applied there directly.
--
-- Regenerated 2026-09-17: the previous version of this file was reformatted in a
-- way that lowercased every identifier, which collapsed the view's deliberately
-- case-distinct `dcc`/`DCC` join aliases (used to join tb_DCC_LN and tb_DCC in
-- the same clause) into a duplicate alias, and glued "AS" and "SELECT" together
-- with no separator -- both would break if this file were ever run against the
-- database. This version was regenerated directly from a fresh `SHOW CREATE VIEW`
-- and verified token-for-token identical (case included) to the live definition
-- before being formatted for readability.

CREATE
OR
REPLACE
  ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `combinedPurchaseOrderView` AS
select
  `po`.`recordNo` AS `poRecordNo`,
  `po`.`poNumber` AS `poNumber`,
  `po`.`typeLookUpCode` AS `typeLookUpCode`,
  `po`.`blanketTotalAmount` AS `blanketTotalAmount`,
  `po`.`releaseNum` AS `releaseNum`,
  `po`.`lineNumber` AS `lineNumber`,
  `po`.`prNum` AS `prNum`,
  `po`.`newProjectName` AS `poProjectName`,
  `po`.`newProjectName` AS `newProjectName`,
  `po`.`itemPartNumber` AS `itemPartNumber`,
  `po`.`prSubAllow` AS `prSubAllow`,
  `po`.`countryOfOrigin` AS `poCountryOfOrigin`,
  (
    case
      when (`po`.`poQtyNew` > 0) then `po`.`poQtyNew`
      else `po`.`poOrderQuantity`
    end
  ) AS `poOrderQuantity`,
  (
    case
      when (`upl`.`uplLineQuantity` > 0) then (
        select
          coalesce(sum(`dcc`.`deliveredQty`), 0)
        from
          (
            `tb_DCC_LN` `dcc`
            join `tb_DCC` `DCC` on ((`dcc`.`dccId` = `DCC`.`recordNo`))
          )
        where
          (
            (`dcc`.`uplLineNumber` = `upl`.`uplLine`)
            and (`upl`.`poLineNumber` = `dcc`.`lineNumber`)
            and (`upl`.`poNumber` = `DCC`.`poNumber`)
            and (`DCC`.`status` not in ('incomplete', 'rejected'))
          )
      )
      else 0
    end
  ) AS `UPLACPTRequestValue`,
  (
    case
      when (`upl`.`uplLineQuantity` > 0) then (
        select
          (
            coalesce(sum(`dcc`.`deliveredQty`), 0) / nullif(
              sum(
                (`upl`.`poLineQuantity` * `upl`.`poLineUnitPrice`)
              ),
              0
            )
          )
        from
          (
            `tb_DCC_LN` `dcc`
            join `tb_DCC` `DCC` on ((`dcc`.`dccId` = `DCC`.`recordNo`))
          )
        where
          (
            (`dcc`.`uplLineNumber` = `upl`.`uplLine`)
            and (`upl`.`poLineNumber` = `dcc`.`lineNumber`)
            and (`upl`.`poNumber` = `DCC`.`poNumber`)
            and (`DCC`.`status` not in ('incomplete', 'rejected'))
          )
      )
      else 0
    end
  ) AS `POAcceptanceQty`,
  (
    select
      coalesce(sum(`subquery`.`POAcceptanceQty`), 0)
    from
      (
        select
          `uplInner`.`poNumber` AS `poNumber`,
          `uplInner`.`poLineNumber` AS `poLineNumber`,
          (
            (
              `uplInner`.`uplLineQuantity` * `uplInner`.`uplLineUnitPrice`
            ) / nullif(
              (
                `uplInner`.`poLineQuantity` * `uplInner`.`poLineUnitPrice`
              ),
              0
            )
          ) AS `POAcceptanceQty`
        from
          `tb_PurchaseOrderUPL` `uplInner`
        where
          (`uplInner`.`uplLineQuantity` < 0)
      ) `subquery`
    where
      (
        (`subquery`.`poNumber` = `upl`.`poNumber`)
        and (`subquery`.`poLineNumber` = `upl`.`poLineNumber`)
      )
  ) AS `POLineAcceptanceQty`,
  (
    case
      when (`upl`.`recordNo` is not null) then (
        case
          when (`po`.`poQtyNew` > 0) then `po`.`quantityDueNew`
          else `po`.`quantityDueOld`
        end
      )
      else (
        (
          case
            when (`po`.`poQtyNew` > 0) then `po`.`quantityDueNew`
            else `po`.`quantityDueOld`
          end
        ) - (
          select
            coalesce(sum(`dcc`.`deliveredQty`), 0)
          from
            (
              `tb_DCC_LN` `dcc`
              join `tb_DCC` `DCC` on ((`dcc`.`dccId` = `DCC`.`recordNo`))
            )
          where
            (
              (`DCC`.`poNumber` = `po`.`poNumber`)
              and (`dcc`.`lineNumber` = `po`.`lineNumber`)
              and (
                `DCC`.`status` not in ('incomplete', 'rejected', 'returned')
              )
            )
        )
      )
    end
  ) AS `poPendingQuantity`,
  `po`.`poQtyNew` AS `poQtyNew`,
  `po`.`quantityReceived` AS `quantityReceived`,
  `po`.`currencyCode` AS `poCurrencyCode`,
  `po`.`unitPriceInPoCurrency` AS `unitPriceInPoCurrency`,
  `po`.`unitPriceInSAR` AS `unitPriceInSAR`,
  `po`.`linePriceInPoCurrency` AS `linePriceInPoCurrency`,
  `po`.`linePriceInSAR` AS `linePriceInSAR`,
  `po`.`amountReceived` AS `amountReceived`,
  `po`.`poLineDescription` AS `poLineDescription`,
  `po`.`organizationName` AS `organizationName`,
  `po`.`organizationCode` AS `organizationCode`,
  `po`.`subInventoryCode` AS `subInventoryCode`,
  `po`.`receiptRouting` AS `receiptRouting`,
  `po`.`authorisationStatus` AS `authorisationStatus`,
  `po`.`departmentName` AS `departmentName`,
  `po`.`businessOwner` AS `businessOwner`,
  `po`.`poLineType` AS `poLineType`,
  `po`.`acceptanceType` AS `poAcceptanceType`,
  `po`.`costCenter` AS `costCenter`,
  `po`.`chargeAccount` AS `chargeAccount`,
  `po`.`serialControl` AS `serialControl`,
  `po`.`vendorSerialNumberYN` AS `vendorSerialNumberYN`,
  `po`.`itemType` AS `itemType`,
  `po`.`itemCategoryInventory` AS `itemCategoryInventory`,
  `po`.`inventoryCategoryDescription` AS `inventoryCategoryDescription`,
  `po`.`itemCategoryFA` AS `itemCategoryFA`,
  `po`.`FACategoryDescription` AS `FACategoryDescription`,
  `po`.`itemCategoryPurchasing` AS `itemCategoryPurchasing`,
  `po`.`PurchasingCategoryDescription` AS `PurchasingCategoryDescription`,
  `po`.`vendorName` AS `poVendorName`,
  `po`.`vendorNumber` AS `poVendorNumber`,
  `po`.`approvedDate` AS `poApprovedDate`,
  `po`.`createdDate` AS `poCreatedDate`,
  `po`.`createdBy` AS `poCreatedBy`,
  `po`.`createdByName` AS `poCreatedByName`,
  `upl`.`recordNo` AS `uplRecordNo`,
  `upl`.`manufacturer` AS `uplManufacturer`,
  `upl`.`countryOfOrigin` AS `uplCountryOfOrigin`,
  `upl`.`releaseNumber` AS `uplReleaseNumber`,
  `upl`.`uplLine` AS `uplLine`,
  `upl`.`poLineItemType` AS `uplPoLineItemType`,
  `upl`.`poLineItemCode` AS `uplPoLineItemCode`,
  `upl`.`poLineDescription` AS `uplPoLineDescription`,
  `upl`.`uplLineItemType` AS `uplLineItemType`,
  `upl`.`uplLineItemCode` AS `uplLineItemCode`,
  `upl`.`uplLineDescription` AS `uplLineDescription`,
  `upl`.`zainItemCategoryCode` AS `zainItemCategoryCode`,
  `upl`.`zainItemCategoryDescription` AS `zainItemCategoryDescription`,
  `upl`.`uplItemSerialized` AS `uplItemSerialized`,
  (
    case
      when (`upl`.`recordNo` is not null) then `upl`.`activeOrPassive`
      else `po`.`activeOrPassive`
    end
  ) AS `activeOrPassive`,
  `upl`.`uom` AS `uplUom`,
  `upl`.`currency` AS `uplCurrency`,
  `upl`.`poLineQuantity` AS `uplPoLineQuantity`,
  `upl`.`poLineUnitPrice` AS `uplPoLineUnitPrice`,
  `upl`.`uplLineQuantity` AS `uplLineQuantity`,
  `upl`.`uplLineUnitPrice` AS `uplLineUnitPrice`,
  `upl`.`substituteItemCode` AS `substituteItemCode`,
  `upl`.`remarks` AS `uplRemarks`,
  `cat`.`scope` AS `scopeOfWork`,
  `upl`.`dptApprover1` AS `dptApprover1`,
  `upl`.`dptApprover2` AS `dptApprover2`,
  `upl`.`dptApprover3` AS `dptApprover3`,
  `upl`.`dptApprover4` AS `dptApprover4`,
  `upl`.`regionalApprover` AS `regionalApprover`,
  `upl`.`createdBy` AS `uplCreatedBy`,
  `upl`.`createdByName` AS `uplCreatedByName`,
  (
    case
      when (
        (`po`.`lineCancelFlag` = 0)
        and (`po`.`authorisationStatus` = 'APPROVED')
        and (`po`.`poClosureStatus` = 'OPEN')
      ) then 'YES'
      else 'NO'
    end
  ) AS `canRaiseAcceptance`,
  (
    case
      when (
        (
          `upl`.`uplLineQuantity` - (
            select
              coalesce(sum(`dcc`.`deliveredQty`), 0)
            from
              (
                `tb_DCC_LN` `dcc`
                join `tb_DCC` `DCC` on ((`dcc`.`dccId` = `DCC`.`recordNo`))
              )
            where
              (
                (`dcc`.`uplLineNumber` = `upl`.`uplLine`)
                and (`upl`.`poLineNumber` = `dcc`.`lineNumber`)
                and (`upl`.`poNumber` = `DCC`.`poNumber`)
                and (`DCC`.`status` not in ('incomplete', 'rejected'))
              )
          )
        ) is not null
      ) then (
        `upl`.`uplLineQuantity` - (
          select
            coalesce(sum(`dcc`.`deliveredQty`), 0)
          from
            (
              `tb_DCC_LN` `dcc`
              join `tb_DCC` `DCC` on ((`dcc`.`dccId` = `DCC`.`recordNo`))
            )
          where
            (
              (`dcc`.`uplLineNumber` = `upl`.`uplLine`)
              and (`upl`.`poLineNumber` = `dcc`.`lineNumber`)
              and (`upl`.`poNumber` = `DCC`.`poNumber`)
              and (
                `DCC`.`status` not in ('incomplete', 'rejected', 'returned')
              )
            )
        )
      )
      else `upl`.`uplLineQuantity`
    end
  ) AS `uplPendingQuantity`,
  `cat`.`categoryDescription` AS `categoryDescription`
from
  (
    (
      `tb_PurchaseOrder` `po`
      left join `tb_PurchaseOrderUPL` `upl` on (
        (
          (`po`.`poNumber` = `upl`.`poNumber`)
          and (`po`.`lineNumber` = `upl`.`poLineNumber`)
        )
      )
    )
    left join `tb_Category` `cat` on (
      (
        (
          (
            case
              when (`upl`.`recordNo` is not null) then `upl`.`zainItemCategoryCode`
              else `po`.`itemCategoryInventory`
            end
          ) = `cat`.`itemCategoryCode`
        )
        and (`cat`.`status` = 1)
      )
    )
  )
group by
  `po`.`recordNo`,
  `po`.`poNumber`,
  `po`.`typeLookUpCode`,
  `po`.`blanketTotalAmount`,
  `po`.`releaseNum`,
  `po`.`lineNumber`,
  `po`.`prNum`,
  `po`.`newProjectName`,
  `po`.`newProjectName`,
  `po`.`itemPartNumber`,
  `po`.`prSubAllow`,
  `po`.`countryOfOrigin`,
  `po`.`poQtyNew`,
  `po`.`quantityReceived`,
  `po`.`currencyCode`,
  `po`.`unitPriceInPoCurrency`,
  `po`.`unitPriceInSAR`,
  `po`.`linePriceInPoCurrency`,
  `po`.`linePriceInSAR`,
  `po`.`amountReceived`,
  `po`.`poLineDescription`,
  `po`.`organizationName`,
  `po`.`organizationCode`,
  `po`.`subInventoryCode`,
  `po`.`receiptRouting`,
  `po`.`authorisationStatus`,
  `po`.`departmentName`,
  `po`.`businessOwner`,
  `po`.`poLineType`,
  `po`.`acceptanceType`,
  `po`.`costCenter`,
  `po`.`chargeAccount`,
  `po`.`serialControl`,
  `po`.`vendorSerialNumberYN`,
  `po`.`itemType`,
  `po`.`itemCategoryInventory`,
  `po`.`inventoryCategoryDescription`,
  `po`.`itemCategoryFA`,
  `po`.`FACategoryDescription`,
  `po`.`itemCategoryPurchasing`,
  `po`.`PurchasingCategoryDescription`,
  `po`.`vendorName`,
  `po`.`vendorNumber`,
  `po`.`approvedDate`,
  `po`.`createdDate`,
  `po`.`createdBy`,
  `po`.`createdByName`,
  `po`.`activeOrPassive`,
  `upl`.`recordNo`,
  `upl`.`manufacturer`,
  `upl`.`countryOfOrigin`,
  `upl`.`releaseNumber`,
  `upl`.`uplLine`,
  `upl`.`poLineItemType`,
  `upl`.`poLineItemCode`,
  `upl`.`poLineDescription`,
  `upl`.`uplLineItemType`,
  `upl`.`uplLineItemCode`,
  `upl`.`uplLineDescription`,
  `upl`.`zainItemCategoryCode`,
  `upl`.`zainItemCategoryDescription`,
  `upl`.`uplItemSerialized`,
  `upl`.`activeOrPassive`,
  `upl`.`uom`,
  `upl`.`currency`,
  `upl`.`poLineQuantity`,
  `upl`.`poLineUnitPrice`,
  `upl`.`uplLineQuantity`,
  `upl`.`uplLineUnitPrice`,
  `upl`.`substituteItemCode`,
  `upl`.`remarks`,
  `cat`.`scope`,
  `upl`.`dptApprover1`,
  `upl`.`dptApprover2`,
  `upl`.`dptApprover3`,
  `upl`.`dptApprover4`,
  `upl`.`regionalApprover`,
  `upl`.`createdBy`,
  `upl`.`createdByName`,
  `cat`.`categoryDescription`;
