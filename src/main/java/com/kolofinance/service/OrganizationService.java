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

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Organization findById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organisation introuvable: " + id));
    }
}
