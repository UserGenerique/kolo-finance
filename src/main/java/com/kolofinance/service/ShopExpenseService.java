package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.model.enums.ShopSaleStatus;
import com.kolofinance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    public ShopExpense recordExpense(Long organizationId, User recordedBy, long amount, String description, String category) {
        if (amount <= 0) {
            throw new RuntimeException("Montant dépense invalide.");
        }
        Organization organization = organizationService.findById(organizationId);
        return expenseRepository.save(ShopExpense.builder()
                .organization(organization)
                .recordedBy(recordedBy)
                .amount(amount)
                .description(description == null || description.isBlank() ? "Dépense boutique" : description.trim())
                .category(category == null || category.isBlank() ? "DIVERS" : category.trim().toUpperCase())
                .status("CONFIRMED")
                .build());
    }

    public List<ShopExpense> expensesForPeriod(Long organizationId, LocalDate start, LocalDate end) {
        return expenseRepository.findByOrganizationIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(
                organizationId, "CONFIRMED",
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay().minusNanos(1));
    }

    /**
     * Cash register summary for a period.
     */
    public CashRegister cashRegister(Long organizationId, LocalDate start, LocalDate end) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusNanos(1);

        // Income: sales paid amounts + customer payments received
        List<ShopSale> sales = saleRepository.findByOrganizationIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(organizationId, from, to);
        long salesIncome = sales.stream()
                .filter(s -> s.getStatus() == ShopSaleStatus.CONFIRMED)
                .mapToLong(s -> s.getPaidAmount() == null ? 0 : s.getPaidAmount())
                .sum();
        List<ShopCustomerPayment> customerPayments = customerPaymentRepository
                .findByOrganizationIdAndPaidAtBetweenOrderByPaidAtDesc(organizationId, from, to);
        long customerPaymentsTotal = customerPayments.stream()
                .mapToLong(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();

        // Expenses
        List<ShopExpense> expenses = expensesForPeriod(organizationId, start, end);
        long expensesTotal = expenses.stream()
                .mapToLong(e -> e.getAmount() == null ? 0 : e.getAmount())
                .sum();

        // Acquisitions paid (cash outflow)
        // Note: we sum paid_amount from acquisitions confirmed in the period
        // This is simplified - a full implementation would also track supplier payments separately
        long acquisitionsPaid = 0; // Will be enhanced later with proper period queries

        long totalIncome = salesIncome + customerPaymentsTotal;
        long totalExpenses = expensesTotal + acquisitionsPaid;
        long balance = totalIncome - totalExpenses;

        return new CashRegister(salesIncome, customerPaymentsTotal, totalIncome,
                expensesTotal, acquisitionsPaid, totalExpenses, balance,
                sales.size(), expenses.size());
    }

    public record CashRegister(
            long salesIncome, long customerPayments, long totalIncome,
            long expenses, long acquisitionsPaid, long totalExpenses,
            long balance, int salesCount, int expensesCount) {}
}
