package com.kolofinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    private Long totalExpenses;
    private Long totalFunds;
    private Long totalBalance;
    private int agentCount;
    private int expenseCount;
    private Map<String, Long> expensesByCategory;
}
