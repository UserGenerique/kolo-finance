package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.repository.ShopCustomerPaymentRepository;
import com.kolofinance.repository.ShopCustomerRepository;
import com.kolofinance.repository.ShopSaleRepository;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopCustomerService {

    private final ShopCustomerRepository customerRepository;
    private final ShopCustomerPaymentRepository paymentRepository;
    private final ShopSaleRepository saleRepository;
    private final OrganizationService organizationService;

    public List<ShopCustomer> listActive(Long organizationId) {
        return customerRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
    }

    public List<ShopCustomer> listDebtors(Long organizationId) {
        return customerRepository.findByOrganizationIdAndActiveTrueAndOutstandingBalanceGreaterThanOrderByOutstandingBalanceDesc(organizationId, 0L);
    }

    public ShopCustomer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client introuvable."));
    }

    @Transactional
    public ShopCustomer upsertCustomer(Long organizationId, String name, String phoneNumber, Long creditLimit) {
        Organization organization = organizationService.findById(organizationId);
        String cleanName = requireText(name, "Le nom du client est obligatoire.");
        String normalizedName = normalize(cleanName);
        String cleanPhone = requireInternationalPhone(phoneNumber);

        Optional<ShopCustomer> existing = customerRepository.findFirstByOrganizationIdAndActiveTrueAndPhoneNumber(organizationId, cleanPhone);

        ShopCustomer customer = existing.orElseGet(() -> ShopCustomer.builder()
                .organization(organization)
                .normalizedName(normalizedName)
                .outstandingBalance(0L)
                .active(true)
                .build());

        customer.setName(cleanName);
        customer.setNormalizedName(normalizedName);
        customer.setPhoneNumber(cleanPhone);
        if (creditLimit != null && creditLimit >= 0) {
            customer.setCreditLimit(creditLimit);
        }
        customer.setActive(true);
        return customerRepository.save(customer);
    }

    public ShopCustomer resolveCustomer(Long organizationId, String query) {
        String cleanQuery = requireText(query, "Indiquez le nom ou le numéro du client.");
        String phone = normalizePhone(cleanQuery);
        if (phone != null) {
            Optional<ShopCustomer> byPhone = customerRepository.findFirstByOrganizationIdAndActiveTrueAndPhoneNumber(organizationId, phone);
            if (byPhone.isPresent()) {
                return byPhone.get();
            }
        }

        String normalized = normalize(cleanQuery);
        Optional<ShopCustomer> exact = customerRepository.findFirstByOrganizationIdAndActiveTrueAndNormalizedName(organizationId, normalized);
        if (exact.isPresent()) {
            return exact.get();
        }

        List<ShopCustomer> matches = customerRepository.findByOrganizationIdAndActiveTrueAndNormalizedNameContainingOrderByNameAsc(organizationId, normalized);
        if (matches.isEmpty()) {
            throw new RuntimeException("Client introuvable: " + cleanQuery + ". Créez-le avec numéro international: *client Awa +22176223344*.");
        }
        if (matches.size() > 1) {
            String names = matches.stream().limit(5).map(ShopCustomer::getName).reduce((a, b) -> a + ", " + b).orElse("");
            throw new RuntimeException("Plusieurs clients correspondent: " + names + ". Précisez le nom ou le numéro.");
        }
        return matches.get(0);
    }

    @Transactional
    public ShopCustomerPayment recordPayment(Long organizationId, User recordedBy, String customerQuery, Long amount, String note) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Montant paiement invalide.");
        }
        ShopCustomer customer = resolveCustomer(organizationId, customerQuery);
        long currentDebt = nullToZero(customer.getOutstandingBalance());
        if (currentDebt <= 0) {
            throw new RuntimeException(customer.getName() + " n'a aucune dette client.");
        }
        if (amount > currentDebt) {
            throw new RuntimeException("Le paiement dépasse la dette de " + customer.getName() + " (" + currentDebt + " FCFA).");
        }

        long remainingPayment = amount;
        List<ShopSale> unpaidSales = saleRepository.findByOrganizationIdAndCustomerIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(
                organizationId,
                customer.getId(),
                0L
        );
        ShopSale firstTouchedSale = null;
        for (ShopSale sale : unpaidSales) {
            if (remainingPayment <= 0) {
                break;
            }
            long due = nullToZero(sale.getDueAmount());
            long applied = Math.min(due, remainingPayment);
            sale.setDueAmount(due - applied);
            sale.setPaidAmount(nullToZero(sale.getPaidAmount()) + applied);
            saleRepository.save(sale);
            if (firstTouchedSale == null) {
                firstTouchedSale = sale;
            }
            remainingPayment -= applied;
        }

        customer.setOutstandingBalance(currentDebt - amount);
        customerRepository.save(customer);

        return paymentRepository.save(ShopCustomerPayment.builder()
                .organization(customer.getOrganization())
                .customer(customer)
                .sale(firstTouchedSale)
                .recordedBy(recordedBy)
                .amount(amount)
                .paymentMethod("CASH")
                .note(note)
                .build());
    }

    public String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        return normalized.length() >= 6 ? normalized : null;
    }

    public boolean isInternationalPhone(String phoneNumber) {
        String normalized = normalizePhone(phoneNumber);
        return normalized != null
                && normalized.length() >= 10
                && normalized.length() <= 15
                && !normalized.startsWith("0");
    }

    private String requireInternationalPhone(String phoneNumber) {
        String normalized = normalizePhone(phoneNumber);
        if (normalized == null) {
            throw new RuntimeException("Le numéro du client est obligatoire. Ajoutez l’indicatif pays, exemple: *client Awa +22176223344*.");
        }
        if (!isInternationalPhone(normalized)) {
            throw new RuntimeException("Ajoutez l’indicatif pays au numéro client, exemple: *+22176223344* ou *+22376223344*.");
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    public LocalDateTime lastPaymentDate(Long customerId) {
        return paymentRepository.findFirstByCustomerIdOrderByPaidAtDesc(customerId)
                .map(ShopCustomerPayment::getPaidAt)
                .orElse(null);
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
