package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.model.enums.ShopSaleStatus;
import com.kolofinance.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopExpenseService {

    private final ShopExpenseRepository expenseRepository;
    private final ShopSaleRepository saleRepository;
    private final ShopCustomerPaymentRepository customerPaymentRepository;
    private final ShopAcquisitionRepository acquisitionRepository;
    private final ShopSupplierPaymentRepository supplierPaymentRepository;
    private final OrganizationService organizationService;

    @Transactional
    public ShopExpense recordExpense(
        Long organizationId,
        User recordedBy,
        long amount,
        String description,
        String category
    ) {
        if (amount <= 0) {
            throw new RuntimeException("Montant dépense invalide.");
        }
        Organization organization = organizationService.findById(
            organizationId
        );
        return expenseRepository.save(
            ShopExpense.builder()
                .organization(organization)
                .recordedBy(recordedBy)
                .amount(amount)
                .description(
                    description == null || description.isBlank()
                        ? "Dépense boutique"
                        : description.trim()
                )
                .category(
                    category == null || category.isBlank()
                        ? "DIVERS"
                        : category.trim().toUpperCase()
                )
                .status("CONFIRMED")
                .build()
        );
    }

    public List<ShopExpense> expensesForPeriod(
        Long organizationId,
        LocalDate start,
        LocalDate end
    ) {
        return expenseRepository.findByOrganizationIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(
            organizationId,
            "CONFIRMED",
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay().minusNanos(1)
        );
    }

    /**
     * Cash register summary for a period.
     */
    public CashRegister cashRegister(
        Long organizationId,
        LocalDate start,
        LocalDate end
    ) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusNanos(1);

        // Income: sales paid amounts + customer payments received
        List<ShopSale> sales =
            saleRepository.findByOrganizationIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(
                organizationId,
                from,
                to
            );
        long salesIncome = sales
            .stream()
            .filter(s -> s.getStatus() == ShopSaleStatus.CONFIRMED)
            .mapToLong(s -> s.getPaidAmount() == null ? 0 : s.getPaidAmount())
            .sum();
        List<ShopCustomerPayment> customerPayments =
            customerPaymentRepository.findByOrganizationIdAndPaidAtBetweenOrderByPaidAtDesc(
                organizationId,
                from,
                to
            );
        long customerPaymentsTotal = customerPayments
            .stream()
            .mapToLong(p -> p.getAmount() == null ? 0 : p.getAmount())
            .sum();

        // Expenses
        List<ShopExpense> expenses = expensesForPeriod(
            organizationId,
            start,
            end
        );
        long expensesTotal = expenses
            .stream()
            .mapToLong(e -> e.getAmount() == null ? 0 : e.getAmount())
            .sum();

        // Achats/approvisionnements payés en cash sur la période :
        //  - paidAmount des acquisitions confirmées (paiement au moment de l'achat)
        //  - paiements de dettes fournisseurs (règlements ultérieurs)
        // Les deux sont disjoints, donc pas de double comptage.
        long acquisitionsPaid =
            acquisitionRepository.sumPaidBetween(organizationId, from, to) +
            supplierPaymentRepository.sumAmountBetween(
                organizationId,
                from,
                to
            );

        long totalIncome = salesIncome + customerPaymentsTotal;
        long totalExpenses = expensesTotal + acquisitionsPaid;
        long balance = totalIncome - totalExpenses;

        // Solde d'ouverture : cumul net de TOUT ce qui s'est passé avant le début
        // de la période (report de caisse).
        long openingBalance = openingBalance(organizationId, from);
        long closingBalance = openingBalance + balance;

        return new CashRegister(
            salesIncome,
            customerPaymentsTotal,
            totalIncome,
            expensesTotal,
            acquisitionsPaid,
            totalExpenses,
            balance,
            openingBalance,
            closingBalance,
            sales.size(),
            expenses.size()
        );
    }

    /**
     * Solde net cumulé (report de caisse) de tous les mouvements antérieurs à {@code before}.
     */
    private long openingBalance(Long organizationId, LocalDateTime before) {
        long incomeBefore =
            saleRepository.sumPaidBefore(
                organizationId,
                ShopSaleStatus.CONFIRMED,
                before
            ) +
            customerPaymentRepository.sumAmountBefore(organizationId, before);
        long outflowBefore =
            expenseRepository.sumAmountBefore(organizationId, before) +
            acquisitionRepository.sumPaidBefore(organizationId, before) +
            supplierPaymentRepository.sumAmountBefore(organizationId, before);
        return incomeBefore - outflowBefore;
    }

    public record CashRegister(
        long salesIncome,
        long customerPayments,
        long totalIncome,
        long expenses,
        long acquisitionsPaid,
        long totalExpenses,
        long balance,
        long openingBalance,
        long closingBalance,
        int salesCount,
        int expensesCount
    ) {}
}
