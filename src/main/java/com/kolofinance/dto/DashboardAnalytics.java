package com.kolofinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAnalytics {
    private Long totalExpenses;
    private Long totalFunds;
    private Long totalBalance;
    private int agentCount;
    private int expenseCount;
    private int pendingDraftCount;
    private Long periodExpenses;
    private Long previousPeriodExpenses;
    private Long averageDailyExpense;
    private Long largestExpenseAmount;
    private Double expenseChangePercent;
    private Double fundUsagePercent;
    private String topAgentName;
    private String topCategory;
    private Map<String, Long> expensesByCategory;
    private FilterSummary filter;
    private List<TimeSeriesPoint> dailySeries;
    private List<BreakdownItem> categoryBreakdown;
    private List<BreakdownItem> agentBreakdown;
    private List<AgentBalanceItem> agentBalances;
    private List<BreakdownItem> fundBreakdown;
    private List<FundUtilizationItem> fundUtilization;
    private List<RecentActivityItem> recentExpenses;
    private List<AlertItem> alerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterSummary {
        private String period;
        private String label;
        private String startDate;
        private String endDate;
        private Long agentId;
        private Long fundId;
        private String category;
        private String search;
        private Long minAmount;
        private Long maxAmount;
        private Boolean includeAgentBalances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentBalanceItem {
        private Long agentId;
        private String agentName;
        private Long initialAmount;
        private Long balance;
        private Long usedAmount;
        private int activeFundsCount;
        private Double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String date;
        private String label;
        private Long amount;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItem {
        private String key;
        private String label;
        private Long amount;
        private int count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FundUtilizationItem {
        private Long fundId;
        private String agentName;
        private String description;
        private Long initialAmount;
        private Long balance;
        private Long usedAmount;
        private Double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityItem {
        private Long id;
        private LocalDateTime confirmedAt;
        private String agentName;
        private String fundDescription;
        private String description;
        private String category;
        private Long amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertItem {
        private String type;
        private String title;
        private String message;
        private String level;
    }
}
