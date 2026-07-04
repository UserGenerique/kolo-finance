package com.kolofinance.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlatformOrganizationResponse {
    private Long id;
    private String name;
    private String subscriptionPlan;
    private String subscriptionStatus;
    private LocalDateTime subscriptionEndsAt;
    private Integer maxAgents;
    private Integer usersCount;
    private Integer activeAgentsCount;
    private LocalDateTime createdAt;
}
