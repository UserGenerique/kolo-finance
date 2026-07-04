package com.kolofinance.controller;

import com.kolofinance.dto.DashboardAnalytics;
import com.kolofinance.dto.DashboardFilter;
import com.kolofinance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardAnalytics> getDashboard(
            @PathVariable Long orgId,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(dashboardService.buildAnalytics(orgId, buildFilter(params)));
    }

    static DashboardFilter buildFilter(Map<String, String> params) {
        return DashboardFilter.builder()
                .period(blankToNull(params.get("period")))
                .startDate(parseDate(params.get("startDate")))
                .endDate(parseDate(params.get("endDate")))
                .agentId(parseLong(params.get("agentId")))
                .fundId(parseLong(params.get("fundId")))
                .category(blankToNull(params.get("category")))
                .search(blankToNull(params.get("search")))
                .minAmount(parseLong(params.get("minAmount")))
                .maxAmount(parseLong(params.get("maxAmount")))
                .includeAgentBalances(parseBoolean(params.get("includeAgentBalances")))
                .build();
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        return Long.valueOf(value);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return null;
        return Boolean.valueOf(value);
    }
}
