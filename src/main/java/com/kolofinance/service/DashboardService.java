package com.kolofinance.service;

import com.kolofinance.dto.DashboardAnalytics;
import com.kolofinance.dto.DashboardFilter;
import com.kolofinance.model.DraftExpense;
import com.kolofinance.model.Expense;
import com.kolofinance.model.Fund;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.enums.DraftStatus;
import com.kolofinance.model.enums.FundStatus;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.DraftExpenseRepository;
import com.kolofinance.repository.ExpenseRepository;
import com.kolofinance.repository.FundRepository;
import com.kolofinance.repository.OrganizationMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.FRANCE);

    private final ExpenseRepository expenseRepository;
    private final DraftExpenseRepository draftExpenseRepository;
    private final FundRepository fundRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    @Transactional(readOnly = true)
    public DashboardAnalytics buildAnalytics(Long orgId, DashboardFilter filter) {
        DashboardFilter safeFilter = filter != null ? filter : DashboardFilter.builder().build();
        PeriodWindow window = resolveWindow(safeFilter);

        List<Expense> expenses = expenseRepository.findByOrganizationIdOrderByConfirmedAtDesc(orgId);
        List<Expense> filteredExpenses = filterExpenses(expenses, safeFilter, window.startDate(), window.endDate());
        List<Expense> previousExpenses = filterExpenses(expenses, safeFilter, window.previousStartDate(), window.previousEndDate());
        List<Fund> funds = fundRepository.findByOrganizationId(orgId);
        List<Fund> scopedActiveFunds = filterFunds(funds, safeFilter);
        List<OrganizationMembership> memberships = organizationMembershipRepository.findByOrganizationId(orgId);
        List<DraftExpense> drafts = draftExpenseRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        List<DraftExpense> scopedDrafts = filterDrafts(drafts, safeFilter);

        long totalExpenses = sum(filteredExpenses);
        long previousTotal = sum(previousExpenses);
        long totalFunds = scopedActiveFunds.stream().mapToLong(f -> nullToZero(f.getInitialAmount())).sum();
        long totalBalance = scopedActiveFunds.stream().mapToLong(f -> nullToZero(f.getBalance())).sum();
        int activeAgents = (int) memberships.stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getActive()))
                .filter(membership -> membership.getRole() == Role.AGENT)
                .count();
        int pendingDraftCount = (int) scopedDrafts.stream().filter(d -> d.getStatus() == DraftStatus.PENDING).count();
        long largestExpense = filteredExpenses.stream().mapToLong(e -> nullToZero(e.getAmount())).max().orElse(0L);
        long averageDaily = averageDaily(totalExpenses, window, filteredExpenses);
        double usagePercent = totalFunds > 0 ? round(((double) (totalFunds - totalBalance) / totalFunds) * 100) : 0;

        List<DashboardAnalytics.BreakdownItem> byCategory = breakdown(filteredExpenses,
                e -> Optional.ofNullable(e.getCategory()).filter(s -> !s.isBlank()).orElse("DIVERS"), totalExpenses);
        List<DashboardAnalytics.BreakdownItem> byAgent = breakdown(filteredExpenses,
                e -> e.getAgent() != null ? e.getAgent().getName() : "Agent inconnu", totalExpenses);
        List<DashboardAnalytics.BreakdownItem> byFund = breakdown(filteredExpenses,
                e -> e.getFund() != null && e.getFund().getDescription() != null && !e.getFund().getDescription().isBlank()
                        ? e.getFund().getDescription()
                        : "Fonds #" + (e.getFund() != null ? e.getFund().getId() : "—"), totalExpenses);

        Map<String, Long> expensesByCategory = byCategory.stream()
                .collect(Collectors.toMap(
                        DashboardAnalytics.BreakdownItem::getLabel,
                        DashboardAnalytics.BreakdownItem::getAmount,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return DashboardAnalytics.builder()
                .totalExpenses(totalExpenses)
                .totalFunds(totalFunds)
                .totalBalance(totalBalance)
                .agentCount(activeAgents)
                .expenseCount(filteredExpenses.size())
                .pendingDraftCount(pendingDraftCount)
                .periodExpenses(totalExpenses)
                .previousPeriodExpenses(previousTotal)
                .averageDailyExpense(averageDaily)
                .largestExpenseAmount(largestExpense)
                .expenseChangePercent(changePercent(totalExpenses, previousTotal))
                .fundUsagePercent(usagePercent)
                .topAgentName(byAgent.isEmpty() ? "—" : byAgent.get(0).getLabel())
                .topCategory(byCategory.isEmpty() ? "—" : byCategory.get(0).getLabel())
                .expensesByCategory(expensesByCategory)
                .filter(filterSummary(safeFilter, window))
                .dailySeries(dailySeries(filteredExpenses, window))
                .categoryBreakdown(byCategory)
                .agentBreakdown(byAgent)
                .agentBalances(agentBalances(scopedActiveFunds))
                .fundBreakdown(byFund)
                .fundUtilization(fundUtilization(scopedActiveFunds))
                .recentExpenses(recentExpenses(filteredExpenses))
                .alerts(alerts(totalExpenses, pendingDraftCount, scopedActiveFunds, largestExpense, averageDaily))
                .build();
    }

    private List<Fund> filterFunds(List<Fund> funds, DashboardFilter filter) {
        return funds.stream()
                .filter(fund -> fund.getStatus() == FundStatus.ACTIVE)
                .filter(fund -> filter.getAgentId() == null || (fund.getAgent() != null && Objects.equals(fund.getAgent().getId(), filter.getAgentId())))
                .filter(fund -> filter.getFundId() == null || Objects.equals(fund.getId(), filter.getFundId()))
                .collect(Collectors.toList());
    }

    private List<DraftExpense> filterDrafts(List<DraftExpense> drafts, DashboardFilter filter) {
        return drafts.stream()
                .filter(draft -> filter.getAgentId() == null || (draft.getAgent() != null && Objects.equals(draft.getAgent().getId(), filter.getAgentId())))
                .filter(draft -> filter.getFundId() == null || (draft.getFund() != null && Objects.equals(draft.getFund().getId(), filter.getFundId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Expense> findFilteredExpenses(Long orgId, DashboardFilter filter) {
        DashboardFilter safeFilter = filter != null ? filter : DashboardFilter.builder().build();
        PeriodWindow window = resolveWindow(safeFilter);
        return filterExpenses(
                expenseRepository.findByOrganizationIdOrderByConfirmedAtDesc(orgId),
                safeFilter,
                window.startDate(),
                window.endDate()
        );
    }

    public String periodLabel(DashboardFilter filter) {
        return resolveWindow(filter != null ? filter : DashboardFilter.builder().build()).label();
    }

    private List<Expense> filterExpenses(List<Expense> expenses, DashboardFilter filter, LocalDate startDate, LocalDate endDate) {
        return expenses.stream()
                .filter(e -> startDate == null || !e.getConfirmedAt().toLocalDate().isBefore(startDate))
                .filter(e -> endDate == null || !e.getConfirmedAt().toLocalDate().isAfter(endDate))
                .filter(e -> filter.getAgentId() == null || (e.getAgent() != null && Objects.equals(e.getAgent().getId(), filter.getAgentId())))
                .filter(e -> filter.getFundId() == null || (e.getFund() != null && Objects.equals(e.getFund().getId(), filter.getFundId())))
                .filter(e -> isBlank(filter.getCategory()) || filter.getCategory().equalsIgnoreCase(Optional.ofNullable(e.getCategory()).orElse("DIVERS")))
                .filter(e -> filter.getMinAmount() == null || nullToZero(e.getAmount()) >= filter.getMinAmount())
                .filter(e -> filter.getMaxAmount() == null || nullToZero(e.getAmount()) <= filter.getMaxAmount())
                .filter(e -> matchesSearch(e, filter.getSearch()))
                .sorted(Comparator.comparing(Expense::getConfirmedAt).reversed())
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(Expense expense, String search) {
        if (isBlank(search)) return true;
        String q = search.toLowerCase(Locale.ROOT).trim();
        return contains(expense.getDescription(), q)
                || contains(expense.getCategory(), q)
                || (expense.getAgent() != null && contains(expense.getAgent().getName(), q))
                || (expense.getFund() != null && contains(expense.getFund().getDescription(), q));
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private PeriodWindow resolveWindow(DashboardFilter filter) {
        LocalDate today = LocalDate.now();
        LocalDate start = filter.getStartDate();
        LocalDate end = filter.getEndDate();
        String period = Optional.ofNullable(filter.getPeriod()).filter(s -> !s.isBlank()).orElse("all").toLowerCase(Locale.ROOT);
        String label;

        if (start != null || end != null) {
            if (start == null) start = end;
            if (end == null) end = start;
            if (end.isBefore(start)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            label = formatWindowLabel(start, end);
        } else {
            switch (period) {
                case "today":
                case "jour":
                case "day":
                    start = today;
                    end = today;
                    label = "Aujourd’hui";
                    break;
                case "yesterday":
                case "hier":
                    start = today.minusDays(1);
                    end = today.minusDays(1);
                    label = "Hier";
                    break;
                case "7d":
                case "week":
                case "semaine":
                    start = today.minusDays(6);
                    end = today;
                    label = "7 derniers jours";
                    break;
                case "30d":
                    start = today.minusDays(29);
                    end = today;
                    label = "30 derniers jours";
                    break;
                case "month":
                case "mois":
                    start = today.withDayOfMonth(1);
                    end = today;
                    label = "Ce mois";
                    break;
                case "lastmonth":
                case "last_month":
                case "mois-dernier":
                    LocalDate previousMonth = today.minusMonths(1);
                    start = previousMonth.withDayOfMonth(1);
                    end = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());
                    label = "Mois dernier";
                    break;
                default:
                    start = null;
                    end = null;
                    label = "Toutes les périodes";
                    period = "all";
            }
        }

        LocalDate previousStart = null;
        LocalDate previousEnd = null;
        if (start != null && end != null) {
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            previousEnd = start.minusDays(1);
            previousStart = previousEnd.minusDays(days - 1);
        }

        return new PeriodWindow(period, start, end, previousStart, previousEnd, label);
    }

    private String formatWindowLabel(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return start.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE));
        }
        return start.format(DateTimeFormatter.ofPattern("dd MMM", Locale.FRANCE))
                + " → "
                + end.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE));
    }

    private DashboardAnalytics.FilterSummary filterSummary(DashboardFilter filter, PeriodWindow window) {
        return DashboardAnalytics.FilterSummary.builder()
                .period(window.period())
                .label(window.label())
                .startDate(window.startDate() != null ? window.startDate().format(ISO_DATE) : null)
                .endDate(window.endDate() != null ? window.endDate().format(ISO_DATE) : null)
                .agentId(filter.getAgentId())
                .fundId(filter.getFundId())
                .category(filter.getCategory())
                .search(filter.getSearch())
                .minAmount(filter.getMinAmount())
                .maxAmount(filter.getMaxAmount())
                .includeAgentBalances(Boolean.TRUE.equals(filter.getIncludeAgentBalances()))
                .build();
    }

    private List<DashboardAnalytics.TimeSeriesPoint> dailySeries(List<Expense> expenses, PeriodWindow window) {
        LocalDate start = window.startDate();
        LocalDate end = window.endDate();

        if (start == null || end == null) {
            Optional<LocalDate> first = expenses.stream()
                    .map(e -> e.getConfirmedAt().toLocalDate())
                    .min(LocalDate::compareTo);
            Optional<LocalDate> last = expenses.stream()
                    .map(e -> e.getConfirmedAt().toLocalDate())
                    .max(LocalDate::compareTo);
            start = first.orElse(LocalDate.now());
            end = last.orElse(LocalDate.now());
        }

        Map<LocalDate, List<Expense>> byDate = expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getConfirmedAt().toLocalDate()));

        List<DashboardAnalytics.TimeSeriesPoint> points = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<Expense> dayExpenses = byDate.getOrDefault(cursor, List.of());
            LocalDate pointDate = cursor;
            points.add(DashboardAnalytics.TimeSeriesPoint.builder()
                    .date(pointDate.format(ISO_DATE))
                    .label(pointDate.format(SHORT_DATE))
                    .amount(sum(dayExpenses))
                    .count(dayExpenses.size())
                    .build());
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    private List<DashboardAnalytics.BreakdownItem> breakdown(List<Expense> expenses, Function<Expense, String> classifier, long total) {
        Map<String, List<Expense>> grouped = expenses.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    long amount = sum(entry.getValue());
                    return DashboardAnalytics.BreakdownItem.builder()
                            .key(entry.getKey())
                            .label(entry.getKey())
                            .amount(amount)
                            .count(entry.getValue().size())
                            .percentage(total > 0 ? round(((double) amount / total) * 100) : 0)
                            .build();
                })
                .sorted(Comparator.comparing(DashboardAnalytics.BreakdownItem::getAmount).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardAnalytics.FundUtilizationItem> fundUtilization(List<Fund> funds) {
        return funds.stream()
                .map(fund -> {
                    long initial = nullToZero(fund.getInitialAmount());
                    long balance = nullToZero(fund.getBalance());
                    long used = Math.max(0, initial - balance);
                    return DashboardAnalytics.FundUtilizationItem.builder()
                            .fundId(fund.getId())
                            .agentName(fund.getAgent() != null ? fund.getAgent().getName() : "—")
                            .description(fund.getDescription())
                            .initialAmount(initial)
                            .balance(balance)
                            .usedAmount(used)
                            .usagePercent(initial > 0 ? round(((double) used / initial) * 100) : 0)
                            .build();
                })
                .sorted(Comparator.comparing(DashboardAnalytics.FundUtilizationItem::getUsagePercent).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardAnalytics.AgentBalanceItem> agentBalances(List<Fund> funds) {
        return funds.stream()
                .collect(Collectors.groupingBy(
                        fund -> fund.getAgent() != null ? fund.getAgent().getId() : -1L,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(agentFunds -> {
                    Fund first = agentFunds.get(0);
                    long initial = agentFunds.stream().mapToLong(fund -> nullToZero(fund.getInitialAmount())).sum();
                    long balance = agentFunds.stream().mapToLong(fund -> nullToZero(fund.getBalance())).sum();
                    long used = Math.max(0, initial - balance);
                    return DashboardAnalytics.AgentBalanceItem.builder()
                            .agentId(first.getAgent() != null ? first.getAgent().getId() : null)
                            .agentName(first.getAgent() != null ? first.getAgent().getName() : "Agent inconnu")
                            .initialAmount(initial)
                            .balance(balance)
                            .usedAmount(used)
                            .activeFundsCount(agentFunds.size())
                            .usagePercent(initial > 0 ? round(((double) used / initial) * 100) : 0)
                            .build();
                })
                .sorted(Comparator.comparing(DashboardAnalytics.AgentBalanceItem::getBalance).reversed())
                .collect(Collectors.toList());
    }

    private List<DashboardAnalytics.RecentActivityItem> recentExpenses(List<Expense> expenses) {
        return expenses.stream()
                .limit(10)
                .map(expense -> DashboardAnalytics.RecentActivityItem.builder()
                        .id(expense.getId())
                        .confirmedAt(expense.getConfirmedAt())
                        .agentName(expense.getAgent() != null ? expense.getAgent().getName() : "—")
                        .fundDescription(expense.getFund() != null ? expense.getFund().getDescription() : null)
                        .description(expense.getDescription())
                        .category(Optional.ofNullable(expense.getCategory()).orElse("DIVERS"))
                        .amount(expense.getAmount())
                        .build())
                .collect(Collectors.toList());
    }

    private List<DashboardAnalytics.AlertItem> alerts(long totalExpenses, int pendingDraftCount, List<Fund> funds, long largestExpense, long averageDaily) {
        List<DashboardAnalytics.AlertItem> alerts = new ArrayList<>();

        if (pendingDraftCount > 0) {
            alerts.add(DashboardAnalytics.AlertItem.builder()
                    .type("DRAFTS")
                    .level("warning")
                    .title("Brouillons en attente")
                    .message(pendingDraftCount + " dépense(s) attendent une confirmation WhatsApp.")
                    .build());
        }

        funds.stream()
                .filter(f -> nullToZero(f.getInitialAmount()) > 0)
                .filter(f -> ((double) (nullToZero(f.getInitialAmount()) - nullToZero(f.getBalance())) / nullToZero(f.getInitialAmount())) >= 0.8)
                .limit(3)
                .forEach(f -> alerts.add(DashboardAnalytics.AlertItem.builder()
                        .type("FUND_LOW")
                        .level("danger")
                        .title("Fonds presque consommé")
                        .message((f.getDescription() != null ? f.getDescription() : "Fonds #" + f.getId()) + " est utilisé à plus de 80%.")
                        .build()));

        if (totalExpenses == 0) {
            alerts.add(DashboardAnalytics.AlertItem.builder()
                    .type("NO_EXPENSE")
                    .level("info")
                    .title("Aucune dépense sur la période")
                    .message("Aucune sortie confirmée ne correspond aux filtres actifs.")
                    .build());
        }

        if (averageDaily > 0 && largestExpense > averageDaily * 3) {
            alerts.add(DashboardAnalytics.AlertItem.builder()
                    .type("UNUSUAL_EXPENSE")
                    .level("warning")
                    .title("Dépense inhabituelle")
                    .message("La plus grosse dépense dépasse trois fois la moyenne journalière.")
                    .build());
        }

        return alerts;
    }

    private long averageDaily(long total, PeriodWindow window, List<Expense> expenses) {
        long days;
        if (window.startDate() != null && window.endDate() != null) {
            days = ChronoUnit.DAYS.between(window.startDate(), window.endDate()) + 1;
        } else if (!expenses.isEmpty()) {
            LocalDate min = expenses.stream().map(e -> e.getConfirmedAt().toLocalDate()).min(LocalDate::compareTo).orElse(LocalDate.now());
            LocalDate max = expenses.stream().map(e -> e.getConfirmedAt().toLocalDate()).max(LocalDate::compareTo).orElse(LocalDate.now());
            days = ChronoUnit.DAYS.between(min, max) + 1;
        } else {
            days = 1;
        }
        return days > 0 ? Math.round((double) total / days) : total;
    }

    private long sum(List<Expense> expenses) {
        return expenses.stream().mapToLong(e -> nullToZero(e.getAmount())).sum();
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private Double changePercent(long current, long previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return round(((double) (current - previous) / previous) * 100);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class PeriodWindow {
        private final String period;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final LocalDate previousStartDate;
        private final LocalDate previousEndDate;
        private final String label;

        private PeriodWindow(String period, LocalDate startDate, LocalDate endDate,
                             LocalDate previousStartDate, LocalDate previousEndDate, String label) {
            this.period = period;
            this.startDate = startDate;
            this.endDate = endDate;
            this.previousStartDate = previousStartDate;
            this.previousEndDate = previousEndDate;
            this.label = label;
        }

        private String period() {
            return period;
        }

        private LocalDate startDate() {
            return startDate;
        }

        private LocalDate endDate() {
            return endDate;
        }

        private LocalDate previousStartDate() {
            return previousStartDate;
        }

        private LocalDate previousEndDate() {
            return previousEndDate;
        }

        private String label() {
            return label;
        }
    }
}
