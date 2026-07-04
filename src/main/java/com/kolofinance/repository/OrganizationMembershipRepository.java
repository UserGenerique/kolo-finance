package com.kolofinance.repository;

import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {

    List<OrganizationMembership> findByUserId(Long userId);

    List<OrganizationMembership> findByUserIdAndActiveTrue(Long userId);

    List<OrganizationMembership> findByOrganizationId(Long organizationId);

    List<OrganizationMembership> findByOrganizationIdAndActiveTrue(Long organizationId);

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    long countByOrganizationIdAndRoleAndActiveTrue(Long organizationId, Role role);
}
