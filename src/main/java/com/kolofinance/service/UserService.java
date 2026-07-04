package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.OrganizationMembershipRepository;
import com.kolofinance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final AuthService authService;

    public User create(Long orgId, String phoneNumber, String name, Role role) {
        return create(orgId, phoneNumber, name, role, null);
    }

    @Transactional
    public User create(Long orgId, String phoneNumber, String name, Role role, String password) {
        Organization org = organizationService.findById(orgId);
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String cleanName = requireText(name, "Le nom est obligatoire");

        User user = userRepository.findByPhoneNumber(normalizedPhone)
                .orElseGet(() -> User.builder()
                        .organization(org)
                        .phoneNumber(normalizedPhone)
                        .name(cleanName)
                        .role(role)
                        .active(true)
                        .build());
        user.setName(cleanName);
        user.setPhoneNumber(normalizedPhone);
        user.setActive(true);
        if (user.getOrganization() == null) {
            user.setOrganization(org);
        }
        if (user.getRole() == null) {
            user.setRole(role);
        }
        if (password != null && !password.trim().isEmpty()) {
            user.setPasswordHash(authService.hashPassword(password));
            user.setPasswordSetAt(LocalDateTime.now());
        }
        user = userRepository.save(user);

        Optional<OrganizationMembership> membershipOpt = membershipRepository.findByOrganizationIdAndUserId(orgId, user.getId());
        OrganizationMembership membership = membershipOpt.orElse(null);
        if (role == Role.AGENT && (membership == null || membership.getRole() != Role.AGENT || !Boolean.TRUE.equals(membership.getActive()))) {
            enforceAgentLimit(org, role);
        }
        if (membership == null) {
            membership = OrganizationMembership.builder()
                    .organization(org)
                    .user(user)
                    .build();
        }
        membership.setRole(role);
        membership.setActive(true);
        membershipRepository.save(membership);
        return user;
    }

    public Optional<User> findByPhone(String phoneNumber) {
        return userRepository.findByPhoneNumber(normalizePhoneNumber(phoneNumber))
                .filter(user -> Boolean.TRUE.equals(user.getActive()));
    }

    public List<User> findByOrganization(Long orgId) {
        return membershipRepository.findByOrganizationId(orgId).stream()
                .map(OrganizationMembership::getUser)
                .collect(Collectors.toList());
    }

    public List<OrganizationMembership> findMembershipsByOrganization(Long orgId) {
        return membershipRepository.findByOrganizationId(orgId);
    }

    public List<OrganizationMembership> findActiveMembershipsForUser(Long userId) {
        return membershipRepository.findByUserIdAndActiveTrue(userId);
    }

    public Optional<OrganizationMembership> findMembership(Long orgId, Long userId) {
        return membershipRepository.findByOrganizationIdAndUserId(orgId, userId);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
    }

    @Transactional
    public User update(Long orgId, Long userId, String phoneNumber, String name, Role role) {
        return update(orgId, userId, phoneNumber, name, role, null);
    }

    @Transactional
    public User update(Long orgId, Long userId, String phoneNumber, String name, Role role, String password) {
        User user = findById(userId);
        OrganizationMembership membership = requireMembership(orgId, userId);
        user.setPhoneNumber(normalizePhoneNumber(phoneNumber));
        user.setName(requireText(name, "Le nom est obligatoire"));
        if (password != null && !password.trim().isEmpty()) {
            user.setPasswordHash(authService.hashPassword(password));
            user.setPasswordSetAt(LocalDateTime.now());
        }
        user = userRepository.save(user);
        if (role == Role.AGENT && (membership.getRole() != Role.AGENT || !Boolean.TRUE.equals(membership.getActive()))) {
            enforceAgentLimit(membership.getOrganization(), role);
        }
        membership.setRole(role);
        membershipRepository.save(membership);
        return user;
    }

    @Transactional
    public User setActive(Long orgId, Long userId, boolean active) {
        User user = findById(userId);
        OrganizationMembership membership = requireMembership(orgId, userId);
        if (active && membership.getRole() == Role.AGENT && !Boolean.TRUE.equals(membership.getActive())) {
            enforceAgentLimit(membership.getOrganization(), membership.getRole());
        }
        membership.setActive(active);
        membershipRepository.save(membership);
        return user;
    }

    private OrganizationMembership requireMembership(Long orgId, Long userId) {
        return membershipRepository.findByOrganizationIdAndUserId(orgId, userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable dans cette organisation"));
    }

    public void ensureMembership(Long orgId, Long userId) {
        requireMembership(orgId, userId);
    }

    public void enforceAgentLimit(Organization org, Role role) {
        if (role != Role.AGENT) {
            return;
        }
        long activeAgents = membershipRepository.countByOrganizationIdAndRoleAndActiveTrue(org.getId(), Role.AGENT);
        Integer maxAgents = org.getMaxAgents() != null ? org.getMaxAgents() : 3;
        if (activeAgents >= maxAgents) {
            throw new RuntimeException("Limite d'agents atteinte pour le plan actuel (" + maxAgents + "). Passez au plan supérieur.");
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = requireText(phoneNumber, "Le numéro WhatsApp est obligatoire")
                .replaceAll("[^0-9]", "");
        if (normalized.length() < 8) {
            throw new RuntimeException("Numéro WhatsApp invalide");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }
}
