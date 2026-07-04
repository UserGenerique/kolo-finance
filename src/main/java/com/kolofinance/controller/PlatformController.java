package com.kolofinance.controller;

import com.kolofinance.dto.PlatformOrganizationResponse;
import com.kolofinance.model.Organization;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.OrganizationMembershipRepository;
import com.kolofinance.service.OrganizationService;
import com.kolofinance.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final OrganizationMembershipRepository membershipRepository;

    @GetMapping("/organizations")
    public ResponseEntity<List<PlatformOrganizationResponse>> organizations() {
        return ResponseEntity.ok(organizationService.findAll().stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping("/organizations")
    public ResponseEntity<PlatformOrganizationResponse> createOrganization(@RequestBody Map<String, Object> body) {
        Organization org = organizationService.create(
                (String) body.get("name"),
                (String) body.get("subscriptionPlan"),
                (String) body.get("subscriptionStatus"),
                intValue(body.get("maxAgents"))
        );

        if (body.get("bossPhoneNumber") != null && body.get("bossPassword") != null) {
            userService.create(
                    org.getId(),
                    body.get("bossPhoneNumber").toString(),
                    body.getOrDefault("bossName", "Patron").toString(),
                    Role.BOSS,
                    body.get("bossPassword").toString()
            );
        }
        return ResponseEntity.ok(toResponse(org));
    }

    @PutMapping("/organizations/{id}/subscription")
    public ResponseEntity<PlatformOrganizationResponse> updateSubscription(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Organization org = organizationService.updateSubscription(
                id,
                (String) body.get("subscriptionPlan"),
                (String) body.get("subscriptionStatus"),
                intValue(body.get("maxAgents"))
        );
        return ResponseEntity.ok(toResponse(org));
    }

    private PlatformOrganizationResponse toResponse(Organization org) {
        List<OrganizationMembership> memberships = membershipRepository.findByOrganizationId(org.getId());
        int activeAgents = (int) memberships.stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()) && m.getRole() == Role.AGENT)
                .count();
        return PlatformOrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .subscriptionPlan(org.getSubscriptionPlan())
                .subscriptionStatus(org.getSubscriptionStatus())
                .subscriptionEndsAt(org.getSubscriptionEndsAt())
                .maxAgents(org.getMaxAgents())
                .usersCount(memberships.size())
                .activeAgentsCount(activeAgents)
                .createdAt(org.getCreatedAt())
                .build();
    }

    private Integer intValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }
}
