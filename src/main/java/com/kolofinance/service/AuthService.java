package com.kolofinance.service;

import com.kolofinance.dto.AuthOrganization;
import com.kolofinance.dto.AuthPrincipal;
import com.kolofinance.dto.AuthResponse;
import com.kolofinance.model.AuthSession;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.PlatformAdmin;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.SessionUserType;
import com.kolofinance.repository.AuthSessionRepository;
import com.kolofinance.repository.OrganizationMembershipRepository;
import com.kolofinance.repository.PlatformAdminRepository;
import com.kolofinance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformAdminRepository platformAdminRepository;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(String phoneNumber, String password) {
        String normalizedPhone = normalizePhone(phoneNumber);
        String rawPassword = requireText(password, "Mot de passe obligatoire");

        Optional<PlatformAdmin> adminOpt = platformAdminRepository.findByPhoneNumber(normalizedPhone)
                .filter(admin -> Boolean.TRUE.equals(admin.getActive()))
                .filter(admin -> passwordEncoder.matches(rawPassword, admin.getPasswordHash()));
        if (adminOpt.isPresent()) {
            PlatformAdmin admin = adminOpt.get();
            admin.setLastLoginAt(LocalDateTime.now());
            String token = createSession(SessionUserType.PLATFORM_ADMIN, admin, null, null);
            return AuthResponse.builder()
                    .token(token)
                    .userType(SessionUserType.PLATFORM_ADMIN.name())
                    .userId(admin.getId())
                    .name(admin.getName())
                    .phoneNumber(admin.getPhoneNumber())
                    .organizations(List.of())
                    .build();
        }

        User user = userRepository.findByPhoneNumber(normalizedPhone)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(() -> new RuntimeException("Téléphone ou mot de passe invalide"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("Téléphone ou mot de passe invalide");
        }

        List<OrganizationMembership> memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        if (memberships.isEmpty()) {
            throw new RuntimeException("Aucune organisation active pour ce compte");
        }

        user.setLastLoginAt(LocalDateTime.now());
        String token = createSession(SessionUserType.ORG_USER, null, user, null);
        return AuthResponse.builder()
                .token(token)
                .userType(SessionUserType.ORG_USER.name())
                .userId(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .organizations(memberships.stream()
                        .map(m -> AuthOrganization.builder()
                                .id(m.getOrganization().getId())
                                .name(m.getOrganization().getName())
                                .role(m.getRole())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<AuthPrincipal> resolve(String rawToken, Long requestedOrgId) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return authSessionRepository.findByTokenHashAndExpiresAtAfter(hashToken(rawToken), LocalDateTime.now())
                .map(session -> toPrincipal(session, requestedOrgId));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            authSessionRepository.deleteByTokenHash(hashToken(rawToken));
        }
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(requireText(rawPassword, "Mot de passe obligatoire"));
    }

    public boolean hasValidPassword(User user) {
        return user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
    }

    public String normalizePhone(String phoneNumber) {
        String normalized = requireText(phoneNumber, "Numéro obligatoire").replaceAll("[^0-9]", "");
        if (normalized.length() < 8) {
            throw new RuntimeException("Numéro invalide");
        }
        return normalized;
    }

    private AuthPrincipal toPrincipal(AuthSession session, Long requestedOrgId) {
        if (session.getUserType() == SessionUserType.PLATFORM_ADMIN) {
            return AuthPrincipal.builder()
                    .session(session)
                    .userType(SessionUserType.PLATFORM_ADMIN)
                    .platformAdmin(session.getPlatformAdmin())
                    .build();
        }

        OrganizationMembership membership = null;
        if (requestedOrgId != null) {
            membership = membershipRepository.findByOrganizationIdAndUserId(requestedOrgId, session.getUser().getId())
                    .filter(m -> Boolean.TRUE.equals(m.getActive()))
                    .orElse(null);
        }

        return AuthPrincipal.builder()
                .session(session)
                .userType(SessionUserType.ORG_USER)
                .user(session.getUser())
                .organization(membership != null ? membership.getOrganization() : null)
                .membership(membership)
                .role(membership != null ? membership.getRole() : null)
                .build();
    }

    private String createSession(SessionUserType type, PlatformAdmin admin, User user, com.kolofinance.model.Organization org) {
        String token = randomToken();
        AuthSession session = AuthSession.builder()
                .tokenHash(hashToken(token))
                .userType(type)
                .platformAdmin(admin)
                .user(user)
                .organization(org)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        authSessionRepository.save(session);
        return token;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erreur hash session", e);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }
}
