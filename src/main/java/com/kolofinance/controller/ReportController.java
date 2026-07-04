package com.kolofinance.controller;

import com.kolofinance.dto.DashboardFilter;
import com.kolofinance.dto.ReportResponse;
import com.kolofinance.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/generate")
    public ResponseEntity<ReportResponse> generate(
            @PathVariable Long orgId,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(reportService.generate(orgId, DashboardController.buildFilter(params)));
    }

    @PostMapping("/send")
    public ResponseEntity<ReportResponse> send(
            @PathVariable Long orgId,
            @RequestBody Map<String, Object> body) {
        Object to = body.get("to");
        if (to == null || to.toString().isBlank()) {
            throw new IllegalArgumentException("Le numéro destinataire est obligatoire");
        }
        return ResponseEntity.ok(reportService.sendReport(orgId, buildFilter(body), to.toString()));
    }

    private DashboardFilter buildFilter(Map<String, Object> body) {
        return DashboardFilter.builder()
                .period(asString(body.get("period")))
                .startDate(parseDate(body.get("startDate")))
                .endDate(parseDate(body.get("endDate")))
                .agentId(parseLong(body.get("agentId")))
                .fundId(parseLong(body.get("fundId")))
                .category(asString(body.get("category")))
                .search(asString(body.get("search")))
                .minAmount(parseLong(body.get("minAmount")))
                .maxAmount(parseLong(body.get("maxAmount")))
                .includeAgentBalances(parseBoolean(body.get("includeAgentBalances")))
                .build();
    }

    private String asString(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Long.valueOf(value.toString());
    }

    private LocalDate parseDate(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return LocalDate.parse(value.toString());
    }

    private Boolean parseBoolean(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Boolean.valueOf(value.toString());
    }
}
