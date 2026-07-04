package com.kolofinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFilter {
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long agentId;
    private Long fundId;
    private String category;
    private String search;
    private Long minAmount;
    private Long maxAmount;
    private Boolean includeAgentBalances;
}
