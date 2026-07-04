package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.PlatformAdmin;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.OrganizationMembershipRepository;
import com.kolofinance.repository.PlatformAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformWhatsAppAdminService {

    private static final Pattern CREATE_BOUTIQUE_WITH_BOSS_PATTERN = Pattern.compile(
            "^admin\\s+(?:creer|créer|nouvelle?)\\s+boutique\\s+(.+?)\\s+patron\\s+(.+?)\\s+(\\+?\\d[\\d\\s.\\-]{7,})\\s+pass\\s+(\\S+)(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CREATE_BOUTIQUE_PATTERN = Pattern.compile(
            "^admin\\s+(?:creer|créer|nouvelle?)\\s+boutique\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ADD_USER_PATTERN = Pattern.compile(
            "^admin\\s+ajouter\\s+(agent|manager|patron|boss)\\s+boutique\\s+(\\d+)\\s+(.+?)\\s+(\\+?\\d[\\d\\s.\\-]{7,})(?:\\s+pass\\s+(\\S+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SUBSCRIPTION_PATTERN = Pattern.compile(
            "^admin\\s+abonnement\\s+boutique\\s+(\\d+)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern TEAM_PATTERN = Pattern.compile(
            "^admin\\s+(?:equipe|équipe|utilisateurs|users)\\s+boutique\\s+(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PLAN_PATTERN = Pattern.compile("\\bplan\\s+([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern STATUS_PATTERN = Pattern.compile("\\b(?:statut|status)\\s+([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AGENTS_PATTERN = Pattern.compile("\\bagents?\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final PlatformAdminRepository platformAdminRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final OrganizationMembershipRepository membershipRepository;
    private final WhatsAppService whatsAppService;
    private final AuthService authService;

    @Transactional
    public boolean handleMessage(String phoneNumber, String text) {
        String normalized = normalize(text);
        if (!isAdminCommand(normalized)) {
            return false;
        }
        log.info("Commande admin détectée de {}: '{}'", phoneNumber, text);

        Optional<PlatformAdmin> admin = findActiveAdmin(phoneNumber);
        if (admin.isEmpty()) {
            whatsAppService.sendMessage(phoneNumber,
                    "⛔ Commande réservée au super-admin Kolo. Votre numéro n’est pas autorisé pour les commandes *admin*.");
            return true;
        }

        try {
            if (normalized.equals("admin") || normalized.equals("admin aide") || normalized.equals("admin help")
                    || normalized.equals("aide admin") || normalized.equals("help admin")) {
                whatsAppService.sendMessage(phoneNumber, helpMessage());
                return true;
            }
            if (normalized.equals("admin boutiques") || normalized.equals("admin liste boutiques")
                    || normalized.equals("admin organisations") || normalized.equals("admin liste organisations")) {
                sendOrganizations(phoneNumber);
                return true;
            }
            Matcher teamMatcher = TEAM_PATTERN.matcher(text.trim());
            if (teamMatcher.matches()) {
                sendTeam(phoneNumber, Long.parseLong(teamMatcher.group(1)));
                return true;
            }
            Matcher createWithBossMatcher = CREATE_BOUTIQUE_WITH_BOSS_PATTERN.matcher(text.trim());
            if (createWithBossMatcher.matches()) {
                handleCreateBoutiqueWithBoss(phoneNumber, createWithBossMatcher);
                return true;
            }
            Matcher createMatcher = CREATE_BOUTIQUE_PATTERN.matcher(text.trim());
            if (createMatcher.matches()) {
                handleCreateBoutique(phoneNumber, createMatcher);
                return true;
            }
            Matcher addUserMatcher = ADD_USER_PATTERN.matcher(text.trim());
            if (addUserMatcher.matches()) {
                handleAddUser(phoneNumber, addUserMatcher);
                return true;
            }
            Matcher subscriptionMatcher = SUBSCRIPTION_PATTERN.matcher(text.trim());
            if (subscriptionMatcher.matches()) {
                handleSubscription(phoneNumber, subscriptionMatcher);
                return true;
            }

            whatsAppService.sendMessage(phoneNumber,
                    "Commande admin non comprise.\n\n" + helpMessage());
            return true;
        } catch (Exception e) {
            log.error("Erreur commande WhatsApp super-admin: {}", e.getMessage(), e);
            whatsAppService.sendMessage(phoneNumber, "❌ " + e.getMessage());
            return true;
        }
    }

    private boolean isAdminCommand(String normalized) {
        return normalized.equals("admin")
                || normalized.startsWith("admin ")
                || normalized.equals("aide admin")
                || normalized.equals("help admin");
    }

    private Optional<PlatformAdmin> findActiveAdmin(String phoneNumber) {
        try {
            String normalizedPhone = authService.normalizePhone(phoneNumber);
            log.info("Recherche super-admin pour numéro normalisé: {}", normalizedPhone);
            Optional<PlatformAdmin> result = platformAdminRepository.findByPhoneNumber(normalizedPhone)
                    .filter(admin -> Boolean.TRUE.equals(admin.getActive()));
            log.info("Super-admin trouvé: {}", result.isPresent());
            return result;
        } catch (Exception e) {
            log.warn("Erreur recherche super-admin: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void handleCreateBoutiqueWithBoss(String phoneNumber, Matcher matcher) {
        String rawName = matcher.group(1);
        String bossName = requireText(matcher.group(2), "Nom du patron obligatoire.");
        String bossPhone = matcher.group(3);
        String bossPassword = matcher.group(4);
        String tail = matcher.group(5) == null ? "" : matcher.group(5);

        CommandOptions options = parseOptions(tail);
        Organization organization = organizationService.create(
                cleanName(rawName),
                options.plan(),
                options.status(),
                options.maxAgents()
        );
        User boss = userService.create(organization.getId(), bossPhone, bossName, Role.BOSS, bossPassword);

        whatsAppService.sendMessage(phoneNumber,
                "✅ Boutique créée\n"
                        + "ID: *" + organization.getId() + "*\n"
                        + "Nom: *" + organization.getName() + "*\n"
                        + "Plan: " + organization.getSubscriptionPlan() + " — statut " + organization.getSubscriptionStatus() + "\n"
                        + "Max agents: " + organization.getMaxAgents() + "\n\n"
                        + "Patron: *" + boss.getName() + "* — " + boss.getPhoneNumber() + "\n"
                        + "Mot de passe: défini, non affiché.");
    }

    private void handleCreateBoutique(String phoneNumber, Matcher matcher) {
        String tail = matcher.group(1);
        CommandOptions options = parseOptions(tail);
        String name = cleanName(removeOptions(tail));
        Organization organization = organizationService.create(
                name,
                options.plan(),
                options.status(),
                options.maxAgents()
        );

        whatsAppService.sendMessage(phoneNumber,
                "✅ Boutique créée\n"
                        + "ID: *" + organization.getId() + "*\n"
                        + "Nom: *" + organization.getName() + "*\n"
                        + "Plan: " + organization.getSubscriptionPlan() + " — statut " + organization.getSubscriptionStatus() + "\n"
                        + "Max agents: " + organization.getMaxAgents() + "\n\n"
                        + "Ajoutez ensuite un patron ou agent:\n"
                        + "*admin ajouter patron boutique " + organization.getId() + " Awa +22176223344 pass 1234*");
    }

    private void handleAddUser(String phoneNumber, Matcher matcher) {
        Role role = parseRole(matcher.group(1));
        Long organizationId = Long.parseLong(matcher.group(2));
        String name = requireText(matcher.group(3), "Nom utilisateur obligatoire.");
        String userPhone = matcher.group(4);
        String password = matcher.group(5);

        User user = userService.create(organizationId, userPhone, name, role, password);
        Organization organization = organizationService.findById(organizationId);
        whatsAppService.sendMessage(phoneNumber,
                "✅ Utilisateur ajouté\n"
                        + "Boutique: *" + organization.getName() + "* (#" + organization.getId() + ")\n"
                        + "Nom: *" + user.getName() + "*\n"
                        + "Téléphone: " + user.getPhoneNumber() + "\n"
                        + "Rôle: *" + role + "*"
                        + (password != null && !password.isBlank() ? "\nMot de passe: défini, non affiché." : ""));
    }

    private void handleSubscription(String phoneNumber, Matcher matcher) {
        Long organizationId = Long.parseLong(matcher.group(1));
        CommandOptions options = parseOptions(matcher.group(2));
        Organization organization = organizationService.updateSubscription(
                organizationId,
                options.plan(),
                options.status(),
                options.maxAgents()
        );

        whatsAppService.sendMessage(phoneNumber,
                "✅ Abonnement mis à jour\n"
                        + "Boutique: *" + organization.getName() + "* (#" + organization.getId() + ")\n"
                        + "Plan: " + organization.getSubscriptionPlan() + "\n"
                        + "Statut: " + organization.getSubscriptionStatus() + "\n"
                        + "Max agents: " + organization.getMaxAgents());
    }

    private void sendOrganizations(String phoneNumber) {
        List<Organization> organizations = organizationService.findAll();
        if (organizations.isEmpty()) {
            whatsAppService.sendMessage(phoneNumber, "Aucune boutique créée.");
            return;
        }
        StringBuilder sb = new StringBuilder("🏪 *Boutiques Kolo*\n\n");
        organizations.stream().limit(30).forEach(org -> {
            List<OrganizationMembership> memberships = membershipRepository.findByOrganizationId(org.getId());
            long activeUsers = memberships.stream().filter(m -> Boolean.TRUE.equals(m.getActive())).count();
            long activeAgents = memberships.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getActive()) && m.getRole() == Role.AGENT)
                    .count();
            sb.append("#").append(org.getId()).append(" — *").append(org.getName()).append("*\n")
                    .append("Plan: ").append(org.getSubscriptionPlan())
                    .append(" — ").append(org.getSubscriptionStatus())
                    .append(" — agents ").append(activeAgents).append("/").append(org.getMaxAgents())
                    .append(" — users ").append(activeUsers).append("\n\n");
        });
        sb.append("Voir équipe: *admin equipe boutique 1*");
        whatsAppService.sendMessage(phoneNumber, sb.toString());
    }

    private void sendTeam(String phoneNumber, Long organizationId) {
        Organization organization = organizationService.findById(organizationId);
        List<OrganizationMembership> memberships = membershipRepository.findByOrganizationId(organizationId);
        if (memberships.isEmpty()) {
            whatsAppService.sendMessage(phoneNumber, "Aucun utilisateur dans " + organization.getName() + ".");
            return;
        }
        String users = memberships.stream()
                .limit(40)
                .map(m -> "• " + m.getUser().getName()
                        + " — " + m.getUser().getPhoneNumber()
                        + " — " + m.getRole()
                        + (Boolean.TRUE.equals(m.getActive()) ? "" : " — inactif"))
                .collect(Collectors.joining("\n"));
        whatsAppService.sendMessage(phoneNumber,
                "👥 *Équipe " + organization.getName() + "* (#" + organization.getId() + ")\n\n" + users);
    }

    private CommandOptions parseOptions(String text) {
        String source = text == null ? "" : text;
        return new CommandOptions(
                findOption(PLAN_PATTERN, source, "STARTER"),
                findOption(STATUS_PATTERN, source, "TRIAL"),
                findIntegerOption(AGENTS_PATTERN, source, 3)
        );
    }

    private String removeOptions(String text) {
        String cleaned = PLAN_PATTERN.matcher(text == null ? "" : text).replaceAll(" ");
        cleaned = STATUS_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = AGENTS_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String findOption(Pattern pattern, String source, String fallback) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1).trim().toUpperCase() : fallback;
    }

    private Integer findIntegerOption(Pattern pattern, String source, Integer fallback) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return fallback;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Role parseRole(String value) {
        String normalized = normalize(value);
        if (normalized.equals("agent")) {
            return Role.AGENT;
        }
        if (normalized.equals("manager")) {
            return Role.MANAGER;
        }
        if (normalized.equals("patron") || normalized.equals("boss")) {
            return Role.BOSS;
        }
        throw new RuntimeException("Rôle invalide. Utilisez agent, manager ou patron.");
    }

    private String helpMessage() {
        return "🔐 *Commandes super-admin Kolo*\n\n"
                + "• *admin boutiques* — lister les boutiques\n"
                + "• *admin equipe boutique 1* — voir les utilisateurs\n"
                + "• *admin creer boutique Kolo Boutique plan STARTER agents 3*\n"
                + "• *admin creer boutique Kolo Boutique patron Awa +22176223344 pass 1234 plan STARTER agents 3*\n"
                + "• *admin ajouter agent boutique 1 Moussa +22376223344*\n"
                + "• *admin ajouter manager boutique 1 Awa +22176223344*\n"
                + "• *admin ajouter patron boutique 1 Awa +22176223344 pass 1234*\n"
                + "• *admin abonnement boutique 1 plan PRO statut ACTIVE agents 10*";
    }

    private String cleanName(String value) {
        return requireText(value, "Nom de boutique obligatoire.")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record CommandOptions(String plan, String status, Integer maxAgents) {}
}
