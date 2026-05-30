package com.kolofinance.controller;

import com.kolofinance.dto.DashboardSummary;
import com.kolofinance.model.Fund;
import com.kolofinance.repository.ExpenseRepository;
import com.kolofinance.repository.UserRepository;
import com.kolofinance.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final FundService fundService;

    @GetMapping
    public ResponseEntity<DashboardSummary> getDashboard(@PathVariable Long orgId) {
        Long totalExpenses = expenseRepository.sumAmountByOrganizationId(orgId);
        List<Fund> funds = fundService.findByOrganization(orgId);
        long totalFunds = funds.stream().mapToLong(Fund::getInitialAmount).sum();
        long totalBalance = funds.stream().mapToLong(Fund::getBalance).sum();
        int agentCount = userRepository.findByOrganizationIdAndActiveTrue(orgId).size();
        int expenseCount = expenseRepository.findByOrganizationIdOrderByConfirmedAtDesc(orgId).size();

        // Dépenses par catégorie
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Object[] row : expenseRepository.sumByCategory(orgId)) {
            String cat = row[0] != null ? row[0].toString() : "DIVERS";
            Long sum = ((Number) row[1]).longValue();
            byCategory.put(cat, sum);
        }

        DashboardSummary summary = DashboardSummary.builder()
                .totalExpenses(totalExpenses)
                .totalFunds(totalFunds)
                .totalBalance(totalBalance)
                .agentCount(agentCount)
                .expenseCount(expenseCount)
                .expensesByCategory(byCategory)
                .build();

        return ResponseEntity.ok(summary);
    }
}
