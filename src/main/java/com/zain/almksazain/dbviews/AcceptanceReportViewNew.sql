SELECT DISTINCT `DCC`.`recordNo` AS `requestId`
	,`DCC`.`status` AS `requestStatus`
	,`DCC`.`acceptanceType` AS `acceptanceType`
	,`DCC`.`poNumber` AS `poNumber`
	,`LN2`.`lineNumber` AS `poLineNumber`
	,(
		CASE
			WHEN (length(`LN2`.`uplLineNumber`) > 0)
				THEN `upl`.`poLineItemCode`
			ELSE max(`HD`.`itemPartNumber`)
			END
		) AS `poPartNumber`
	,(
		CASE
			WHEN (length(`LN2`.`uplLineNumber`) > 0)
				THEN `upl`.`poLineDescription`
			ELSE `HD`.`poLineDescription`
			END
		) AS `poLineDescription`
	,(
		CASE
			WHEN (`HD`.`serialControl` = 'NO CONTROL')
				THEN 'NO'
			ELSE 'YES'
			END
		) AS `poItemSerializedStatus`
	,'SAR' AS `currency`
	,`upl`.`poLineUnitPrice` AS `unitPrice`
	,`LN2`.`recordNo` AS `dccLnRecordNo`
	,`LN2`.`locationName` AS `siteId`
	,`siteType`.`siteTypeName` AS `siteTypeName`
	,date_format(cast(`LN2`.`dateInService` AS DATE), '%e-%b-%Y') AS `inServiceDate`
	,`rg`.`regionName` AS `region`
	,`HD`.`typeLookUpCode` AS `typeLookUpCode`
	,`HD`.`releaseNum` AS `releaseNumber`
	,`HD`.`newProjectName` AS `dccProjectName`
	,`HD`.`newProjectName` AS `newProjectName`
	,`LN2`.`uplLineNumber` AS `uplLineNumber`
	,`upl`.`uplLineItemCode` AS `uplPartNumber`
	,`upl`.`uplLineDescription` AS `uplItemDescription`
	,`LN2`.`actualItemCode` AS `actualPartNumber`
	,`upl`.`uplItemSerialized` AS `uplItemSerializedStatus`
	,`LN2`.`serialNumber` AS `serialNumber`
	,`upl`.`zainItemCategoryCode` AS `uplItemCategoryCode`
	,`upl`.`zainItemCategoryDescription` AS `uplItemCategoryCodeDescription`
	,`upl`.`uplLineUnitPrice` AS `uplLineUnitPrice`
	,`LN2`.`deliveredQty` AS `acceptanceUplQty`
	,`LN2`.`poAcceptanceQty` AS `acceptancePoQty`
	,(`upl`.`uplLineUnitPrice` * `LN2`.`deliveredQty`) AS `totalAcceptanceAmount`
	,`HD`.`vendorName` AS `vendorName`
FROM (
	(
		(
			(
				(
					(
						(
							`ALM_ZAIN_KSA`.`tb_DCC` `DCC` JOIN `ALM_ZAIN_KSA`.`tb_PurchaseOrder` `HD` ON ((`DCC`.`poNumber` = `HD`.`poNumber`))
							) JOIN `ALM_ZAIN_KSA`.`tb_Category_Approval_Requests` `AR` ON ((`DCC`.`recordNo` = `AR`.`acceptanceRequestRecordNo`))
						) JOIN `ALM_ZAIN_KSA`.`tb_DCC_LN` `LN2` ON ((`DCC`.`recordNo` = `LN2`.`dccId`))
					) LEFT JOIN `ALM_ZAIN_KSA`.`tb_PurchaseOrderUPL` `upl` ON (
						(
							(`DCC`.`poNumber` = `upl`.`poNumber`)
							AND (`LN2`.`uplLineNumber` = `upl`.`uplLine`)
							AND (`upl`.`poLineNumber` = `LN2`.`lineNumber`)
							)
						)
				) LEFT JOIN `ALM_ZAIN_KSA`.`tb_Site` `site` ON (((`LN2`.`locationName` collate utf8mb4_general_ci) = (`site`.`siteId` collate utf8mb4_general_ci)))
			) LEFT JOIN `ALM_ZAIN_KSA`.`tb_Site_Type` `siteType` ON (((`site`.`siteTypeId` collate utf8mb4_general_ci) = (`siteType`.`recordNo` collate utf8mb4_general_ci)))
		) LEFT JOIN `ALM_ZAIN_KSA`.`tb_Region` `rg` ON (((`site`.`regionId` collate utf8mb4_general_ci) = (`rg`.`recordNo` collate utf8mb4_general_ci)))
	)
WHERE (
		0 <> (
			CASE
				WHEN (length(`LN2`.`uplLineNumber`) > 0)
					THEN (
							(`LN2`.`uplLineNumber` = `upl`.`uplLine`)
							AND (`upl`.`poLineNumber` = `LN2`.`lineNumber`)
							AND (`upl`.`poNumber` = `DCC`.`poNumber`)
							)
				ELSE (
						(`HD`.`lineNumber` = `LN2`.`lineNumber`)
						AND (`HD`.`poNumber` = `DCC`.`poNumber`)
						)
				END
			)
		)
GROUP BY `DCC`.`recordNo`
	,`DCC`.`status`
	,`DCC`.`acceptanceType`
	,`DCC`.`poNumber`
	,`LN2`.`recordNo`
	,`LN2`.`lineNumber`
	,`LN2`.`uplLineNumber`
	,`upl`.`poLineItemCode`
	,`upl`.`poLineDescription`
	,`HD`.`poLineDescription`
	,`HD`.`serialControl`
	,`HD`.`unitPriceInSAR`
	,`LN2`.`locationName`
	,`LN2`.`dateInService`
	,`HD`.`typeLookUpCode`
	,`HD`.`projectName`
	,`HD`.`newProjectName`
	,`upl`.`uplLineItemCode`
	,`upl`.`uplLineDescription`
	,`LN2`.`actualItemCode`
	,`upl`.`uplItemSerialized`
	,`LN2`.`serialNumber`
	,`upl`.`zainItemCategoryCode`
	,`upl`.`uplLineUnitPrice`
	,`LN2`.`deliveredQty`
	,`HD`.`vendorName`
	,`rg`.`regionName`
	,`HD`.`unitPriceInPoCurrency`