package com.zain.ksa.alm.acceptance.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zain.ksa.alm.acceptance.entity.Site;

public interface SiteRepository extends JpaRepository<Site, Long> {

	Site findByrecordNo(long recordNo);

	Site findFirstBySiteId(String siteId);
}