package com.kolofinance.service;

import com.kolofinance.dto.DashboardFilter;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReportCommandService {

    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d{1,3})\\s*(jour|jours|j)\\b");
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?.*(?:au|a|à|-).*(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?");
    private static final Pattern EXPENSE_WITH_AMOUNT_PATTERN = Pattern.compile("^depense\\s+(\\d[\\d\\s.]*)\\s+(.+)$");

    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("carburant", "CARBURANT");
        CATEGORY_KEYWORDS.put("essence", "CARBURANT");
        CATEGORY_KEYWORDS.put("gasoil", "CARBURANT");
        CATEGORY_KEYWORDS.put("transport", "TRANSPORT");
        CATEGORY_KEYWORDS.put("taxi", "TRANSPORT");
        CATEGORY_KEYWORDS.put("materiaux", "MATERIAUX");
        CATEGORY_KEYWORDS.put("ciment", "MATERIAUX");
        CATEGORY_KEYWORDS.put("sable", "MATERIAUX");
        CATEGORY_KEYWORDS.put("nourriture", "NOURRITURE");
        CATEGORY_KEYWORDS.put("repas", "NOURRITURE");
        CATEGORY_KEYWORDS.put("pieces", "PIECES");
        CATEGORY_KEYWORDS.put("piece", "PIECES");
        CATEGORY_KEYWORDS.put("divers", "DIVERS");
    }

    private final UserService userService;

    public boolean isHelpCommand(String message) {
        String normalized = commandText(message);
        return normalized.equals("aide")
                || normalized.equals("help")
                || normalized.equals("commandes")
                || normalized.equals("commande")
                || normalized.equals("rapport aide")
                || normalized.equals("aide rapport");
    }

    public boolean isReportCommand(String message) {
        String normalized = commandText(message);
        return normalized.startsWith("rapport")
                || normalized.startsWith("raport")
                || normalized.startsWith("bilan")
                || normalized.startsWith("resume")
                || normalized.startsWith("etat ")
                || normalized.contains(" rapport ")
                || normalized.equals("report")
                || normalized.startsWith("report ");
    }
    public boolean isExpenseListCommand(String message) {
        String normalized = commandText(message);
        if (normalized.startsWith("liste des ")) {
            normalized = normalized.substring("liste des ".length()).trim();
        } else if (normalized.startsWith("liste de ")) {
            normalized = normalized.substring("liste de ".length()).trim();
        } else if (normalized.startsWith("liste ")) {
            normalized = normalized.substring("liste ".length()).trim();
        } else if (normalized.startsWith("detail des ")) {
            normalized = normalized.substring("detail des ".length()).trim();
        } else if (normalized.startsWith("details des ")) {
            normalized = normalized.substring("details des ".length()).trim();
        } else if (normalized.startsWith("detail ")) {
            normalized = normalized.substring("detail ".length()).trim();
        } else if (normalized.startsWith("details ")) {
            normalized = normalized.substring("details ".length()).trim();
        }
        return normalized.equals("depenses")
                || normalized.startsWith("depenses ")
                || normalized.equals("depense")
                || (normalized.startsWith("depense ") && !looksLikeNewExpense(normalized))
                || normalized.startsWith("liste depenses")
                || normalized.startsWith("liste des depenses")
                || normalized.startsWith("export depenses")
                || normalized.startsWith("export depense")
                || normalized.startsWith("journal depenses")
                || normalized.startsWith("journal depense")
                || normalized.startsWith("releve depenses")
                || normalized.startsWith("releve depense");
    }

    public Optional<DashboardFilter> parseReportCommand(User requester, String message) {
        if (!isReportCommand(message)) {
            return Optional.empty();
        }
        return Optional.of(parseFilter(requester, message, true));
    }

    public Optional<DashboardFilter> parseExpenseListCommand(User requester, String message) {
        if (!isExpenseListCommand(message)) {
            return Optional.empty();
        }

        return Optional.of(parseFilter(requester, message, false));
    }

    private DashboardFilter parseFilter(User requester, String message, boolean allowAgentBalances) {

        String normalized = commandText(message);
        LocalDate today = LocalDate.now();
        DashboardFilter.DashboardFilterBuilder builder = DashboardFilter.builder().period("today");

        Matcher rangeMatcher = RANGE_PATTERN.matcher(normalized);
        Matcher daysMatcher = DAYS_PATTERN.matcher(normalized);

        if (rangeMatcher.find()) {
            LocalDate start = parseDate(rangeMatcher.group(1), rangeMatcher.group(2), rangeMatcher.group(3), today);
            LocalDate end = parseDate(rangeMatcher.group(4), rangeMatcher.group(5), rangeMatcher.group(6), today);
            builder.startDate(start).endDate(end).period("custom");
        } else if (normalized.contains("hier")) {
            builder.startDate(today.minusDays(1)).endDate(today.minusDays(1)).period("custom");
        } else if (daysMatcher.find()) {
            int days = Math.max(1, Integer.parseInt(daysMatcher.group(1)));
            builder.startDate(today.minusDays(days - 1L)).endDate(today).period("custom");
        } else if (normalized.contains("semaine") || normalized.contains("hebdo")) {
            builder.period("7d");
        } else if (normalized.contains("mois dernier")) {
            builder.period("last_month");
        } else if (normalized.contains("mois") || normalized.contains("mensuel")) {
            builder.period("month");
        } else if (normalized.contains("30 jours")) {
            builder.period("30d");
        }

        CATEGORY_KEYWORDS.forEach((keyword, category) -> {
            if (normalized.contains(keyword)) {
                builder.category(category);
            }
        });
        if (allowAgentBalances && (normalized.contains("solde") || normalized.contains("soldes"))) {
            builder.includeAgentBalances(true);
        }

        List<OrganizationMembership> memberships = userService.findActiveMembershipsForUser(requester.getId());
        if (memberships.size() == 1) {
            OrganizationMembership membership = memberships.get(0);
            if (membership.getRole() == Role.AGENT) {
                builder.agentId(requester.getId());
            } else {
                userService.findMembershipsByOrganization(membership.getOrganization().getId()).stream()
                        .map(OrganizationMembership::getUser)
                        .filter(user -> normalized.contains(normalize(user.getName()))
                                || normalized.contains(normalize(user.getPhoneNumber())))
                        .findFirst()
                        .ifPresent(user -> builder.agentId(user.getId()));
            }
        }

        return builder.build();
    }

    public String helpMessage() {
        return "🤖 *Commandes Kolo Finance*\n\n"
                + "🧾 Déclarer une dépense :\n"
                + "• depense 15000 carburant\n"
                + "• 25000 réparation voiture\n\n"
                + "📊 Demander un rapport :\n"
                + "• rapport aujourd’hui\n"
                + "• rapport 3 jours\n"
                + "• rapport semaine\n"
                + "• rapport mois\n"
                + "• rapport soldes\n"
                + "• rapport aujourd’hui soldes\n"
                + "• rapport du 01/05 au 05/05\n"
                + "• rapport agent Moussa\n\n"
                + "📎 Exporter les dépenses en Excel :\n"
                + "• depenses aujourd’hui\n"
                + "• depenses semaine\n"
                + "• depenses mois\n"
                + "• depenses du 01/05 au 05/05\n"
                + "• depenses agent Moussa semaine\n\n"
                + "✅ Confirmation :\n"
                + "• oui pour valider\n"
                + "• non pour annuler";
    }

    private LocalDate parseDate(String day, String month, String year, LocalDate fallback) {
        int d = Integer.parseInt(day);
        int m = Integer.parseInt(month);
        int y = fallback.getYear();
        if (year != null && !year.isBlank()) {
            y = Integer.parseInt(year);
            if (y < 100) y += 2000;
        }
        return LocalDate.of(y, m, d);
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized
                .replace('’', '\'')
                .replaceAll("[']", " ")
                .replaceAll("\\s+", " ");
    }

    private String commandText(String value) {
        String normalized = normalize(value);
        normalized = normalized.replaceFirst("^(stp|svp|s il te plait|s il vous plait)\\s+", "");
        normalized = normalized.replaceFirst("^(envoie moi|envoyez moi|envoyer moi|montre moi|montrez moi|donne moi|donnez moi|affiche moi|affichez moi|affiche|voir|afficher)\\s+", "");
        normalized = normalized.replaceFirst("^(je veux|j aimerais|je voudrais|peux tu|pouvez vous)\\s+", "");
        normalized = normalized.replaceFirst("^(le|la|les|mon|ma|mes|un|une|des|du|de la|de l)\\s+", "");
        return normalized.trim();
    }

    private boolean looksLikeNewExpense(String normalized) {
        if (normalized.matches("^depense\\s+\\d[\\d\\s.]*$")) {
            return true;
        }
        Matcher matcher = EXPENSE_WITH_AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        String tail = matcher.group(2).trim();
        return !(tail.startsWith("jour")
                || tail.startsWith("jours")
                || tail.startsWith("j ")
                || tail.startsWith("semaine")
                || tail.startsWith("mois"));
    }
}
