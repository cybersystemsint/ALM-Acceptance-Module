package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.ChargeAccount;

public interface ChargeAccountRepository extends JpaRepository<ChargeAccount, Long> {

	ChargeAccount findByRecordNo(long recordNo);

	ChargeAccount findByChargeAccount(String chargeAccount);

}