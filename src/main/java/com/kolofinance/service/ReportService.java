package com.kolofinance.service;

import com.kolofinance.dto.DashboardAnalytics;
import com.kolofinance.dto.DashboardFilter;
import com.kolofinance.dto.ReportResponse;
import com.kolofinance.model.Expense;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final NumberFormat AMOUNT_FORMATTER = NumberFormat.getNumberInstance(Locale.FRANCE);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private final DashboardService dashboardService;
    private final WhatsAppService whatsAppService;
    private final UserService userService;
    private final ReportImageService reportImageService;
    private final ExpenseExportService expenseExportService;

    @Value("${app.public-url:https://kolo-finance.duckdns.org}")
    private String appPublicUrl;

    @Transactional(readOnly = true)
    public ReportResponse generate(Long orgId, DashboardFilter filter) {
        DashboardAnalytics analytics = dashboardService.buildAnalytics(orgId, filter);
        List<Expense> expenses = dashboardService.findFilteredExpenses(orgId, filter);
        return buildReport(analytics, expenses);
    }

    @Transactional(readOnly = true)
    public ReportResponse generateForRequester(User requester, DashboardFilter filter) {
        OrganizationMembership membership = resolveSingleMembership(requester);
        DashboardFilter scopedFilter = enforceRequesterScope(membership.getRole(), requester, filter);
        return generate(membership.getOrganization().getId(), scopedFilter);
    }

    public ReportResponse sendReport(Long orgId, DashboardFilter filter, String phoneNumber) {
        ReportResponse report = generate(orgId, filter);
        sendReportMessage(phoneNumber, report);
        return report;
    }

    private void sendReportMessage(String phoneNumber, ReportResponse report) {
        byte[] image = reportImageService.render(report);
        String caption = report.getTitle() + "\n" + report.getSummaryText()
                + "\n\n🔎 Détails filtrés : " + detailsUrl(report.getAnalytics());
        boolean sent = whatsAppService.sendImage(phoneNumber, caption, image, "rapport-kolo.png");
        if (!sent) {
            whatsAppService.sendMessage(phoneNumber, report.getWhatsappText());
        }
    }

    public ReportResponse sendReportToRequester(User requester, DashboardFilter filter) {
        ReportResponse report = generateForRequester(requester, filter);
        sendReportMessage(requester.getPhoneNumber(), report);
        return report;
    }

    @Transactional(readOnly = true)
    public void sendExpensesExportToRequester(User requester, DashboardFilter filter) {
        OrganizationMembership membership = resolveSingleMembership(requester);
        DashboardFilter scopedFilter = enforceRequesterScope(membership.getRole(), requester, filter);
        DashboardAnalytics analytics = dashboardService.buildAnalytics(membership.getOrganization().getId(), scopedFilter);
        List<Expense> expenses = dashboardService.findFilteredExpenses(membership.getOrganization().getId(), scopedFilter);

        if (expenses.isEmpty()) {
            whatsAppService.sendMessage(requester.getPhoneNumber(),
                    "Aucune dépense trouvée pour " + analytics.getFilter().getLabel() + ".");
            return;
        }

        byte[] file = expenseExportService.renderXlsx(analytics, expenses);
        String filename = expenseExportService.filename(analytics);
        String caption = "📎 *Liste des dépenses* — " + analytics.getFilter().getLabel()
                + "\n" + expenses.size() + " dépense(s), total "
                + amount(analytics.getPeriodExpenses()) + " FCFA.";
        boolean sent = whatsAppService.sendFile(requester.getPhoneNumber(), caption, file, filename);
        if (!sent) {
            whatsAppService.sendMessage(requester.getPhoneNumber(), expenseExportFallbackText(analytics, expenses));
        }
    }

    private ReportResponse buildReport(DashboardAnalytics analytics, List<Expense> expenses) {
        String periodLabel = Optional.ofNullable(analytics.getFilter())
                .map(DashboardAnalytics.FilterSummary::getLabel)
                .orElse("Période sélectionnée");
        String title = "Rapport Kolo Finance — " + periodLabel;
        List<String> highlights = highlights(analytics);
        String summary = summaryText(analytics);
        String whatsapp = whatsappText(analytics, expenses, highlights);
        String html = htmlReport(title, analytics, expenses, highlights);

        return ReportResponse.builder()
                .title(title)
                .periodLabel(periodLabel)
                .generatedAt(LocalDateTime.now())
                .summaryText(summary)
                .whatsappText(whatsapp)
                .html(html)
                .highlights(highlights)
                .analytics(analytics)
                .build();
    }

    private DashboardFilter enforceRequesterScope(Role requesterRole, User requester, DashboardFilter filter) {
        DashboardFilter source = filter != null ? filter : DashboardFilter.builder().build();
        DashboardFilter scoped = DashboardFilter.builder()
                .period(source.getPeriod())
                .startDate(source.getStartDate())
                .endDate(source.getEndDate())
                .agentId(source.getAgentId())
                .fundId(source.getFundId())
                .category(source.getCategory())
                .search(source.getSearch())
                .minAmount(source.getMinAmount())
                .maxAmount(source.getMaxAmount())
                .includeAgentBalances(source.getIncludeAgentBalances())
                .build();

        if (requesterRole == Role.AGENT) {
            scoped.setAgentId(requester.getId());
            scoped.setIncludeAgentBalances(false);
        }
        return scoped;
    }

    private OrganizationMembership resolveSingleMembership(User requester) {
        List<OrganizationMembership> memberships = userService.findActiveMembershipsForUser(requester.getId());
        if (memberships.isEmpty()) {
            throw new RuntimeException("Votre compte n'est actif dans aucune organisation.");
        }
        if (memberships.size() > 1) {
            throw new RuntimeException("Votre numéro est lié à plusieurs organisations. Connectez-vous à Kolo pour choisir l'organisation du rapport.");
        }
        return memberships.get(0);
    }

    private List<String> highlights(DashboardAnalytics analytics) {
        List<String> highlights = new ArrayList<>();
        highlights.add("Dépenses : " + amount(analytics.getPeriodExpenses()) + " FCFA");
        highlights.add(balanceLabel(analytics) + " : " + amount(analytics.getTotalBalance()) + " FCFA");

        if (analytics.getTopCategory() != null && !"—".equals(analytics.getTopCategory())) {
            highlights.add("Catégorie principale : " + analytics.getTopCategory());
        }
        if (analytics.getTopAgentName() != null && !"—".equals(analytics.getTopAgentName())) {
            highlights.add("Agent le plus actif : " + analytics.getTopAgentName());
        }
        if (analytics.getPendingDraftCount() > 0) {
            highlights.add(analytics.getPendingDraftCount() + " brouillon(s) à confirmer");
        }
        if (analytics.getAlerts() != null && !analytics.getAlerts().isEmpty()) {
            highlights.add("Alertes : " + analytics.getAlerts().size());
        }
        return highlights;
    }

    private String summaryText(DashboardAnalytics analytics) {
        return "Sur " + analytics.getFilter().getLabel()
                + ", " + analytics.getExpenseCount() + " dépense(s) confirmée(s) totalisent "
                + amount(analytics.getPeriodExpenses()) + " FCFA. " + balanceLabel(analytics) + " : "
                + amount(analytics.getTotalBalance()) + " FCFA, avec "
                + amount(analytics.getAverageDailyExpense()) + " FCFA de moyenne journalière.";
    }

    private String whatsappText(DashboardAnalytics analytics, List<Expense> expenses, List<String> highlights) {
        StringBuilder sb = new StringBuilder();
        String nl = "\n";
        sb.append("📊 *Rapport Kolo Finance*").append(nl);
        sb.append("Période : *").append(analytics.getFilter().getLabel()).append("*").append(nl);
        sb.append("Généré le : ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append(nl).append(nl);

        sb.append("💰 *Résumé*").append(nl);
        sb.append("• Dépenses : *").append(amount(analytics.getPeriodExpenses())).append(" FCFA*").append(nl);
        sb.append("• ").append(balanceLabel(analytics)).append(" : *").append(amount(analytics.getTotalBalance())).append(" FCFA*").append(nl);
        sb.append("• Dépenses confirmées : ").append(analytics.getExpenseCount()).append(nl);
        sb.append("• Moyenne/jour : ").append(amount(analytics.getAverageDailyExpense())).append(" FCFA").append(nl);
        sb.append("• Utilisation fonds : ").append(analytics.getFundUsagePercent()).append("%").append(nl).append(nl);

        appendBreakdown(sb, "🏷️ Par catégorie", analytics.getCategoryBreakdown(), 5);
        appendBreakdown(sb, "👷 Par agent", analytics.getAgentBreakdown(), 5);
        if (shouldIncludeAgentBalances(analytics)) {
            appendAgentBalances(sb, analytics.getAgentBalances(), 10);
        } else if (analytics.getFilter() != null && analytics.getFilter().getAgentId() == null) {
            sb.append("💼 Pour voir les soldes par agent, envoyez *rapport soldes* ou *rapport aujourd’hui soldes*.").append(nl).append(nl);
        }

        if (analytics.getPendingDraftCount() > 0 || (analytics.getAlerts() != null && !analytics.getAlerts().isEmpty())) {
            sb.append("⚠️ *Points d’attention*").append(nl);
            if (analytics.getPendingDraftCount() > 0) {
                sb.append("• ").append(analytics.getPendingDraftCount()).append(" brouillon(s) en attente").append(nl);
            }
            analytics.getAlerts().stream().limit(3).forEach(alert ->
                    sb.append("• ").append(alert.getTitle()).append(" : ").append(alert.getMessage()).append(nl));
            sb.append(nl);
        }

        sb.append("🧾 *Dernières dépenses*").append(nl);
        if (expenses.isEmpty()) {
            sb.append("Aucune dépense sur cette période.").append(nl);
        } else {
            expenses.stream().limit(5).forEach(expense ->
                    sb.append("• ").append(amount(expense.getAmount())).append(" FCFA — ")
                            .append(expense.getDescription()).append(" (")
                            .append(expense.getAgent() != null ? expense.getAgent().getName() : "—")
                            .append(")").append(nl));
        }

        sb.append(nl).append("🔎 *Voir les détails filtrés* : ").append(detailsUrl(analytics)).append(nl);
        sb.append(nl).append("Commandes utiles : *rapport aujourd’hui*, *rapport 3 jours*, *rapport semaine*, *rapport mois*, *depense aujourd’hui*, *depenses semaine*.");
        return sb.toString();
    }

    private String expenseExportFallbackText(DashboardAnalytics analytics, List<Expense> expenses) {
        StringBuilder sb = new StringBuilder();
        String nl = "\n";
        sb.append("📎 *Liste des dépenses — ").append(analytics.getFilter().getLabel()).append("*").append(nl);
        sb.append(expenses.size()).append(" dépense(s), total ")
                .append(amount(analytics.getPeriodExpenses())).append(" FCFA.").append(nl).append(nl);
        expenses.stream().limit(20).forEach(expense ->
                sb.append("• ").append(expense.getConfirmedAt() != null ? expense.getConfirmedAt().format(DATE_TIME_FORMATTER) : "—")
                        .append(" — ").append(amount(expense.getAmount())).append(" FCFA — ")
                        .append(expense.getDescription()).append(" (")
                        .append(expense.getAgent() != null ? expense.getAgent().getName() : "—")
                        .append(")").append(nl));
        if (expenses.size() > 20) {
            sb.append("… ").append(expenses.size() - 20).append(" autre(s) dépense(s).").append(nl);
        }
        return sb.toString();
    }

    private void appendBreakdown(StringBuilder sb, String title, List<DashboardAnalytics.BreakdownItem> items, int limit) {
        String nl = "\n";
        sb.append(title).append(nl);
        if (items == null || items.isEmpty()) {
            sb.append("Aucune donnée.").append(nl).append(nl);
            return;
        }
        items.stream().limit(limit).forEach(item ->
                sb.append("• ").append(item.getLabel()).append(" : ")
                        .append(amount(item.getAmount())).append(" FCFA (")
                        .append(item.getPercentage()).append("%)").append(nl));
        sb.append(nl);
    }

    private String htmlReport(String title, DashboardAnalytics analytics, List<Expense> expenses, List<String> highlights) {
        StringBuilder rows = new StringBuilder();
        expenses.stream().limit(50).forEach(expense -> rows.append("<tr>")
                .append("<td>").append(escape(expense.getConfirmedAt().format(DATE_TIME_FORMATTER))).append("</td>")
                .append("<td>").append(escape(expense.getAgent() != null ? expense.getAgent().getName() : "—")).append("</td>")
                .append("<td>").append(escape(expense.getDescription())).append("</td>")
                .append("<td>").append(escape(Optional.ofNullable(expense.getCategory()).orElse("DIVERS"))).append("</td>")
                .append("<td class=\"amount\">").append(amount(expense.getAmount())).append(" FCFA</td>")
                .append("</tr>"));

        StringBuilder highlightHtml = new StringBuilder();
        highlights.forEach(item -> highlightHtml.append("<li>").append(escape(item)).append("</li>"));

        String template = "<article class=\"report-document\">"
                + "<header class=\"report-cover\">"
                + "<p class=\"eyebrow\">Kolo Finance</p>"
                + "<h1>%s</h1>"
                + "<p>%s</p>"
                + "</header>"
                + "<section class=\"report-grid\">"
                + "<div><span>Dépenses</span><strong>%s FCFA</strong></div>"
                + "<div><span>Solde restant</span><strong>%s FCFA</strong></div>"
                + "<div><span>Moyenne/jour</span><strong>%s FCFA</strong></div>"
                + "<div><span>Utilisation fonds</span><strong>%s%%</strong></div>"
                + "</section>"
                + "<section>"
                + "<h2>Résumé exécutif</h2>"
                + "<p>%s</p>"
                + "<ul>%s</ul>"
                + "</section>"
                + "<section>"
                + "<h2>Dépenses détaillées</h2>"
                + "<table>"
                + "<thead><tr><th>Date</th><th>Agent</th><th>Description</th><th>Catégorie</th><th>Montant</th></tr></thead>"
                + "<tbody>%s</tbody>"
                + "</table>"
                + "</section>"
                + "</article>";

        return String.format(template,
                escape(title),
                escape("Généré le " + LocalDateTime.now().format(DATE_TIME_FORMATTER)),
                amount(analytics.getPeriodExpenses()),
                amount(analytics.getTotalBalance()),
                amount(analytics.getAverageDailyExpense()),
                analytics.getFundUsagePercent(),
                escape(summaryText(analytics)),
                highlightHtml,
                rows
        );
    }

    private String balanceLabel(DashboardAnalytics analytics) {
        if (analytics.getFilter() != null && analytics.getFilter().getAgentId() != null) {
            return "Solde agent restant";
        }
        if (analytics.getFilter() != null && analytics.getFilter().getFundId() != null) {
            return "Solde fonds restant";
        }
        return "Solde global restant";
    }

    private boolean shouldIncludeAgentBalances(DashboardAnalytics analytics) {
        return analytics.getFilter() != null
                && Boolean.TRUE.equals(analytics.getFilter().getIncludeAgentBalances())
                && analytics.getFilter().getAgentId() == null
                && analytics.getAgentBalances() != null
                && !analytics.getAgentBalances().isEmpty();
    }

    private void appendAgentBalances(StringBuilder sb, List<DashboardAnalytics.AgentBalanceItem> items, int limit) {
        String nl = "\n";
        sb.append("💼 *Soldes par agent*").append(nl);
        items.stream().limit(limit).forEach(item ->
                sb.append("• ").append(item.getAgentName()).append(" : ")
                        .append(amount(item.getBalance())).append(" FCFA restant / ")
                        .append(amount(item.getInitialAmount())).append(" FCFA confié")
                        .append(nl));
        sb.append(nl);
    }

    private String detailsUrl(DashboardAnalytics analytics) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("view", "expenses");
        DashboardAnalytics.FilterSummary filter = analytics.getFilter();
        if (filter != null) {
            put(params, "period", filter.getPeriod());
            put(params, "startDate", filter.getStartDate());
            put(params, "endDate", filter.getEndDate());
            put(params, "agentId", filter.getAgentId());
            put(params, "fundId", filter.getFundId());
            put(params, "category", filter.getCategory());
            put(params, "search", filter.getSearch());
            put(params, "minAmount", filter.getMinAmount());
            put(params, "maxAmount", filter.getMaxAmount());
        }

        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("view=expenses");
        return appPublicUrl.replaceAll("/+$", "") + "/app.html?" + query;
    }

    private void put(Map<String, String> params, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            params.put(key, value.toString());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String amount(Long value) {
        return AMOUNT_FORMATTER.format(value == null ? 0 : value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
