package com.zain.ksa.alm.acceptance.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.zain.ksa.alm.acceptance.dto.ReportFilterDTO;
import com.zain.ksa.alm.acceptance.service.ReportService;

@Service
public class ReportServiceImpl implements ReportService {

	private static final Logger logger = LoggerFactory.getLogger(ReportServiceImpl.class);
	private static final int DEFAULT_PAGE = 1;
	private static final int DEFAULT_SIZE = 20000;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Override
	public Map<String, Object> getAcceptanceReport(ReportFilterDTO filter) {
		logger.info("Generating acceptance report with filters: poNumber={}, columnName={}, searchQuery={}",
				filter.getPoNumber(), filter.getColumnName(), filter.getSearchQuery());

		try {
			disableOnlyFullGroupBy();

			QueryBuilder queryBuilder = buildQuery(filter, "acceptanceReport");
			int totalRecords = getTotalRecords(queryBuilder);

			PaginationParams pagination = calculatePagination(filter.getPage(), filter.getSize(), totalRecords);
			List<Map<String, Object>> data = executeQuery(queryBuilder, pagination);

			addRecordNumbers(data);

			return buildResponse(data, totalRecords, pagination);
		} catch (Exception e) {
			logger.error("Error generating acceptance report", e);
			return buildErrorResponse();
		}
	}

	@Override
	public Map<String, Object> getCapitalizationReport(ReportFilterDTO filter) {
		logger.info("Generating capitalization report with filters: poNumber={}, columnName={}, searchQuery={}",
				filter.getPoNumber(), filter.getColumnName(), filter.getSearchQuery());

		try {
			disableOnlyFullGroupBy();

			QueryBuilder queryBuilder = buildQuery(filter, "capitalizationReport");
			int totalRecords = getTotalRecords(queryBuilder);

			PaginationParams pagination = calculatePagination(filter.getPage(), filter.getSize(), totalRecords);
			List<Map<String, Object>> data = executeQuery(queryBuilder, pagination);

			addRecordNumbers(data);

			return buildResponse(data, totalRecords, pagination);
		} catch (Exception e) {
			logger.error("Error generating capitalization report", e);
			return buildErrorResponse();
		}
	}

	private void disableOnlyFullGroupBy() {
		jdbcTemplate.execute("SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))");
	}

	private QueryBuilder buildQuery(ReportFilterDTO filter, String tableName) {
		StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
		List<Object> params = new ArrayList<>();

		String poNumber = filter.getPoNumber();
		if (poNumber != null && !poNumber.isEmpty() && !poNumber.equalsIgnoreCase("0")) {
			whereClause.append(" AND poNumber = ?");
			params.add(poNumber);
		}

		String columnName = filter.getColumnName();
		String searchQuery = filter.getSearchQuery();
		if (columnName != null && !columnName.isEmpty() && searchQuery != null && !searchQuery.isEmpty()) {
			whereClause.append(" AND ").append(columnName.toLowerCase()).append(" LIKE ?");
			params.add("%" + searchQuery + "%");
		}

		return new QueryBuilder(tableName, whereClause.toString(), params);
	}

	private int getTotalRecords(QueryBuilder queryBuilder) {
		String countSql = "SELECT COUNT(*) FROM " + queryBuilder.tableName + queryBuilder.whereClause;
		return jdbcTemplate.queryForObject(countSql, Integer.class, queryBuilder.params.toArray());
	}

	private PaginationParams calculatePagination(int page, int size, int totalRecords) {
		page = Math.max(page, 0);
		size = Math.max(size, 0);

		if (page == 0 && size == 0) {
			return new PaginationParams(1, totalRecords, "");
		}

		if (page == 1 && size == DEFAULT_SIZE) {
			page = 1;
			size = totalRecords;
		}

		page = Math.max(page, 1);
		size = Math.max(size, 1);
		int offset = (page - 1) * size;

		String paginationSql = " LIMIT " + size + " OFFSET " + offset;
		return new PaginationParams(page, size, paginationSql);
	}

	private List<Map<String, Object>> executeQuery(QueryBuilder queryBuilder, PaginationParams pagination) {
		String sql = "SELECT * FROM `" + queryBuilder.tableName + "` " + queryBuilder.whereClause
				+ pagination.paginationSql;
		return jdbcTemplate.queryForList(sql, queryBuilder.params.toArray());
	}

	private void addRecordNumbers(List<Map<String, Object>> data) {
		AtomicInteger counter = new AtomicInteger(1);
		data.forEach(row -> row.put("recordNo", counter.getAndIncrement()));
	}

	private Map<String, Object> buildResponse(List<Map<String, Object>> data, int totalRecords,
			PaginationParams pagination) {
		Map<String, Object> response = new HashMap<>();
		response.put("data", data);
		response.put("totalRecords", totalRecords);
		response.put("currentPage", pagination.page);
		response.put("pageSize", pagination.size);
		response.put("totalPages", (int) Math.ceil((double) totalRecords / pagination.size));
		return response;
	}

	private Map<String, Object> buildErrorResponse() {
		Map<String, Object> response = new HashMap<>();
		response.put("data", new ArrayList<>());
		response.put("totalRecords", 0);
		response.put("currentPage", 1);
		response.put("pageSize", DEFAULT_SIZE);
		response.put("totalPages", 0);
		return response;
	}

	private static class QueryBuilder {
		String tableName;
		String whereClause;
		List<Object> params;

		QueryBuilder(String tableName, String whereClause, List<Object> params) {
			this.tableName = tableName;
			this.whereClause = whereClause;
			this.params = params;
		}
	}

	private static class PaginationParams {
		int page;
		int size;
		String paginationSql;

		PaginationParams(int page, int size, String paginationSql) {
			this.page = page;
			this.size = size;
			this.paginationSql = paginationSql;
		}
	}
}
