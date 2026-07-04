package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public Organization create(String name) {
        Organization org = Organization.builder().name(name).build();
        return organizationRepository.save(org);
    }

    public Organization create(String name, String plan, String status, Integer maxAgents) {
        Organization org = Organization.builder()
                .name(requireText(name, "Le nom de l'organisation est obligatoire"))
                .subscriptionPlan(defaultText(plan, "STARTER"))
                .subscriptionStatus(defaultText(status, "TRIAL"))
                .maxAgents(maxAgents != null && maxAgents > 0 ? maxAgents : 3)
                .build();
        return organizationRepository.save(org);
    }

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Organization findById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organisation introuvable: " + id));
    }

    public Organization updateSubscription(Long orgId, String plan, String status, Integer maxAgents) {
        Organization org = findById(orgId);
        org.setSubscriptionPlan(defaultText(plan, org.getSubscriptionPlan()));
        org.setSubscriptionStatus(defaultText(status, org.getSubscriptionStatus()));
        if (maxAgents != null && maxAgents > 0) {
            org.setMaxAgents(maxAgents);
        }
        return organizationRepository.save(org);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
