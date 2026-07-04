package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.model.enums.ShopStockMovementType;
import com.kolofinance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShopAcquisitionService {

    private final ShopAcquisitionRepository acquisitionRepository;
    private final ShopAcquisitionItemRepository itemRepository;
    private final ShopProductRepository productRepository;
    private final ShopStockMovementRepository movementRepository;
    private final ShopSupplierRepository supplierRepository;
    private final OrganizationService organizationService;

    /**
     * Confirm a procurement/acquisition.
     * @param items map of productId -> quantity
     * @param unitCosts map of productId -> unit cost (purchase price for this batch)
     */
    @Transactional
    public ShopAcquisition confirmAcquisition(
            Long organizationId, User recordedBy, ShopSupplier supplier,
            Map<Long, Long> items, Map<Long, Long> unitCosts,
            long paidAmount, LocalDate dueDate) {

        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Aucun produit sélectionné pour l'approvisionnement.");
        }
        Organization organization = organizationService.findById(organizationId);
        List<ShopProduct> products = productRepository.findAllById(items.keySet());
        if (products.size() != items.size()) {
            throw new RuntimeException("Un produit sélectionné est introuvable.");
        }

        long totalAmount = 0;
        for (ShopProduct product : products) {
            if (!Objects.equals(product.getOrganization().getId(), organizationId) || !Boolean.TRUE.equals(product.getActive())) {
                throw new RuntimeException("Produit non disponible: " + product.getName());
            }
            long quantity = positiveQuantity(items.get(product.getId()));
            long cost = unitCosts.getOrDefault(product.getId(), nullToZero(product.getPurchasePrice()));
            if (cost <= 0) {
                throw new RuntimeException("Prix d'achat manquant pour " + product.getName() + ". Précisez le prix: *achat 8500*.");
            }
            totalAmount += quantity * cost;
        }

        long safePaid = Math.max(0, Math.min(paidAmount, totalAmount));
        long dueAmount = totalAmount - safePaid;
        String acquisitionType = dueAmount > 0 ? "CREDIT" : "CASH";

        ShopAcquisition acquisition = ShopAcquisition.builder()
                .organization(organization)
                .supplier(supplier)
                .recordedBy(recordedBy)
                .acquisitionType(acquisitionType)
                .totalAmount(totalAmount)
                .paidAmount(safePaid)
                .dueAmount(dueAmount)
                .dueDate(dueDate)
                .status("CONFIRMED")
                .build();
        acquisition = acquisitionRepository.save(acquisition);

        for (ShopProduct product : products) {
            long quantity = positiveQuantity(items.get(product.getId()));
            long cost = unitCosts.getOrDefault(product.getId(), nullToZero(product.getPurchasePrice()));

            // Save acquisition item
            itemRepository.save(ShopAcquisitionItem.builder()
                    .acquisition(acquisition)
                    .product(product)
                    .productName(product.getName())
                    .quantity(quantity)
                    .unitCost(cost)
                    .lineTotal(quantity * cost)
                    .build());

            // Update stock
            long previousStock = product.getStockQuantity();
            long newStock = previousStock + quantity;
            product.setStockQuantity(newStock);

            // Recalculate weighted average cost (CMP)
            long oldCmp = nullToZero(product.getAverageCost());
            if (oldCmp <= 0 && previousStock == 0) {
                product.setAverageCost(cost);
            } else {
                long newCmp = (previousStock * oldCmp + quantity * cost) / (previousStock + quantity);
                product.setAverageCost(newCmp);
            }

            // Update reference purchase price to latest cost
            product.setPurchasePrice(cost);
            productRepository.save(product);

            // Stock movement
            movementRepository.save(ShopStockMovement.builder()
                    .organization(organization)
                    .product(product)
                    .user(recordedBy)
                    .movementType(ShopStockMovementType.PURCHASE)
                    .quantityDelta(quantity)
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .referenceType("SHOP_ACQUISITION")
                    .referenceId(acquisition.getId())
                    .note("Appro" + (supplier != null ? " " + supplier.getName() : "") + " — " + cost + " F/unité")
                    .build());
        }

        // Update supplier debt
        if (supplier != null && dueAmount > 0) {
            supplier.setOutstandingBalance(nullToZero(supplier.getOutstandingBalance()) + dueAmount);
            supplierRepository.save(supplier);
        }

        return acquisition;
    }

    @Transactional
    public ShopAcquisition cancelAcquisition(Long organizationId, Long acquisitionId, User cancelledBy) {
        ShopAcquisition acquisition = acquisitionRepository.findById(acquisitionId)
                .orElseThrow(() -> new RuntimeException("Approvisionnement #" + acquisitionId + " introuvable."));
        if (!Objects.equals(acquisition.getOrganization().getId(), organizationId)) {
            throw new RuntimeException("Approvisionnement #" + acquisitionId + " n'appartient pas à cette boutique.");
        }
        if ("CANCELLED".equals(acquisition.getStatus())) {
            throw new RuntimeException("Approvisionnement #" + acquisitionId + " est déjà annulé.");
        }

        List<ShopAcquisitionItem> acqItems = itemRepository.findByAcquisitionId(acquisitionId);
        for (ShopAcquisitionItem item : acqItems) {
            ShopProduct product = item.getProduct();
            long previousStock = product.getStockQuantity();
            long newStock = Math.max(0, previousStock - item.getQuantity());
            product.setStockQuantity(newStock);
            // Note: CMP is not reverted on cancellation (too complex for edge cases)
            productRepository.save(product);

            movementRepository.save(ShopStockMovement.builder()
                    .organization(acquisition.getOrganization())
                    .product(product)
                    .user(cancelledBy)
                    .movementType(ShopStockMovementType.ADJUSTMENT)
                    .quantityDelta(-item.getQuantity())
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .referenceType("SHOP_ACQUISITION_CANCEL")
                    .referenceId(acquisition.getId())
                    .note("Annulation appro #" + acquisition.getId())
                    .build());
        }

        // Reverse supplier debt
        if (acquisition.getSupplier() != null && nullToZero(acquisition.getDueAmount()) > 0) {
            ShopSupplier supplier = acquisition.getSupplier();
            supplier.setOutstandingBalance(Math.max(0, nullToZero(supplier.getOutstandingBalance()) - nullToZero(acquisition.getDueAmount())));
            supplierRepository.save(supplier);
        }

        acquisition.setStatus("CANCELLED");
        acquisition.setNote((acquisition.getNote() == null ? "" : acquisition.getNote() + " | ") + "Annulé par " + cancelledBy.getName());
        return acquisitionRepository.save(acquisition);
    }

    public List<ShopAcquisitionItem> items(Long acquisitionId) {
        return itemRepository.findByAcquisitionId(acquisitionId);
    }

    private long positiveQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantité invalide.");
        }
        return quantity;
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }
}
