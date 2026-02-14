package com.zain.ksa.alm.acceptance.service;

import java.util.Map;

import com.zain.ksa.alm.acceptance.dto.ReportFilterDTO;

public interface ReportService {
	Map<String, Object> getAcceptanceReport(ReportFilterDTO filter);
	Map<String, Object> getCapitalizationReport(ReportFilterDTO filter);
}
