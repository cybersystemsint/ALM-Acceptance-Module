package com.zain.almksazain.serviceImplementors;

import com.zain.almksazain.model.DCCLineItem;
import com.zain.almksazain.model.tb_Site;
import com.zain.almksazain.repo.tbSiteRepo;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class DccSiteRegionResolver {

    private DccSiteRegionResolver() {
    }

    public static Map<String, tb_Site> loadSiteBySiteIdMap(Map<Long, List<DCCLineItem>> dccLnMap, tbSiteRepo tbSiteRepo) {
        List<String> siteIds = dccLnMap.values().stream()
                .flatMap(List::stream)
                .map(DCCLineItem::getLocationName)
                .filter(locationName -> locationName != null && !locationName.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        return loadSitesByIds(siteIds, tbSiteRepo);
    }

    public static String resolveRegion(Map<String, tb_Site> siteBySiteId, String locationName) {
        return resolveRegion(siteBySiteId, locationName, null);
    }

    public static String resolveRegion(Map<String, tb_Site> siteBySiteId, String locationName, tbSiteRepo tbSiteRepo) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }
        String normalizedLocation = normalizeSiteKey(locationName);
        tb_Site site = siteBySiteId != null ? siteBySiteId.get(normalizedLocation) : null;
        if (site == null && tbSiteRepo != null) {
            site = tbSiteRepo.findFirstBySiteIdIgnoreCase(locationName.trim());
        }
        if (site == null || site.getRegionId() == null) {
            return null;
        }
        return "R" + site.getRegionId();
    }

    private static Map<String, tb_Site> loadSitesByIds(List<String> siteIds, tbSiteRepo tbSiteRepo) {
        if (siteIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return tbSiteRepo.findBySiteIdIn(siteIds).stream()
                .filter(site -> site.getSiteId() != null && !site.getSiteId().trim().isEmpty())
                .collect(Collectors.toMap(
                        site -> normalizeSiteKey(site.getSiteId()),
                        site -> site,
                        (first, duplicate) -> first));
    }

    private static String normalizeSiteKey(String siteId) {
        return siteId.trim().toUpperCase(Locale.ROOT);
    }
}
