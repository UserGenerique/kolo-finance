package com.kolofinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String title;
    private String periodLabel;
    private LocalDateTime generatedAt;
    private String summaryText;
    private String whatsappText;
    private String html;
    private List<String> highlights;
    private DashboardAnalytics analytics;
}
