package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.repository.ShopAcquisitionRepository;
import com.kolofinance.repository.ShopSupplierPaymentRepository;
import com.kolofinance.repository.ShopSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopSupplierService {

    private final ShopSupplierRepository supplierRepository;
    private final ShopSupplierPaymentRepository paymentRepository;
    private final ShopAcquisitionRepository acquisitionRepository;
    private final OrganizationService organizationService;

    public List<ShopSupplier> listActive(Long organizationId) {
        return supplierRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
    }

    public List<ShopSupplier> listDebtors(Long organizationId) {
        return supplierRepository.findByOrganizationIdAndActiveTrueAndOutstandingBalanceGreaterThanOrderByOutstandingBalanceDesc(organizationId, 0L);
    }

    public ShopSupplier findById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fournisseur introuvable."));
    }

    @Transactional
    public ShopSupplier upsertSupplier(Long organizationId, String name, String phoneNumber) {
        Organization organization = organizationService.findById(organizationId);
        String cleanName = requireText(name, "Le nom du fournisseur est obligatoire.");
        String normalizedName = normalize(cleanName);

        Optional<ShopSupplier> existing = supplierRepository.findFirstByOrganizationIdAndActiveTrueAndNormalizedName(organizationId, normalizedName);
        ShopSupplier supplier = existing.orElseGet(() -> ShopSupplier.builder()
                .organization(organization)
                .normalizedName(normalizedName)
                .outstandingBalance(0L)
                .active(true)
                .build());

        supplier.setName(cleanName);
        supplier.setNormalizedName(normalizedName);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            supplier.setPhoneNumber(phoneNumber.replaceAll("[^0-9+]", ""));
        }
        supplier.setActive(true);
        return supplierRepository.save(supplier);
    }

    public ShopSupplier resolveSupplier(Long organizationId, String query) {
        String cleanQuery = requireText(query, "Indiquez le nom du fournisseur.");
        String normalized = normalize(cleanQuery);

        Optional<ShopSupplier> exact = supplierRepository.findFirstByOrganizationIdAndActiveTrueAndNormalizedName(organizationId, normalized);
        if (exact.isPresent()) {
            return exact.get();
        }

        List<ShopSupplier> matches = supplierRepository.findByOrganizationIdAndActiveTrueAndNormalizedNameContainingOrderByNameAsc(organizationId, normalized);
        if (matches.isEmpty()) {
            throw new RuntimeException("Fournisseur introuvable: " + cleanQuery + ". Créez-le avec: *fournisseur " + cleanQuery + " +22376001122*.");
        }
        if (matches.size() > 1) {
            String names = matches.stream().limit(5).map(ShopSupplier::getName).reduce((a, b) -> a + ", " + b).orElse("");
            throw new RuntimeException("Plusieurs fournisseurs correspondent: " + names + ". Précisez le nom.");
        }
        return matches.get(0);
    }

    @Transactional
    public ShopSupplierPayment recordPayment(Long organizationId, User recordedBy, String supplierQuery, Long amount, String note) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Montant paiement invalide.");
        }
        ShopSupplier supplier = resolveSupplier(organizationId, supplierQuery);
        long currentDebt = nullToZero(supplier.getOutstandingBalance());
        if (currentDebt <= 0) {
            throw new RuntimeException(supplier.getName() + " n'a aucune dette fournisseur.");
        }
        if (amount > currentDebt) {
            throw new RuntimeException("Le paiement dépasse la dette de " + supplier.getName() + " (" + currentDebt + " FCFA).");
        }

        // Apply payment to oldest unpaid acquisitions (FIFO)
        long remainingPayment = amount;
        List<ShopAcquisition> unpaidAcquisitions = acquisitionRepository
                .findByOrganizationIdAndSupplierIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(organizationId, supplier.getId(), 0L);
        ShopAcquisition firstTouched = null;
        for (ShopAcquisition acq : unpaidAcquisitions) {
            if (remainingPayment <= 0) break;
            long due = nullToZero(acq.getDueAmount());
            long applied = Math.min(due, remainingPayment);
            acq.setDueAmount(due - applied);
            acq.setPaidAmount(nullToZero(acq.getPaidAmount()) + applied);
            acquisitionRepository.save(acq);
            if (firstTouched == null) firstTouched = acq;
            remainingPayment -= applied;
        }

        supplier.setOutstandingBalance(currentDebt - amount);
        supplierRepository.save(supplier);

        return paymentRepository.save(ShopSupplierPayment.builder()
                .organization(supplier.getOrganization())
                .supplier(supplier)
                .acquisition(firstTouched)
                .recordedBy(recordedBy)
                .amount(amount)
                .paymentMethod("CASH")
                .note(note)
                .build());
    }

    public java.time.LocalDateTime lastPaymentDate(Long supplierId) {
        return paymentRepository.findFirstBySupplierIdOrderByCreatedAtDesc(supplierId)
                .map(ShopSupplierPayment::getCreatedAt)
                .orElse(null);
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

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
