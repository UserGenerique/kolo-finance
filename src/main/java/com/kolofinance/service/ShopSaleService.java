package com.kolofinance.service;

import com.kolofinance.model.*;
import com.kolofinance.model.enums.ShopSaleStatus;
import com.kolofinance.model.enums.ShopStockMovementType;
import com.kolofinance.repository.ShopProductRepository;
import com.kolofinance.repository.ShopSaleItemRepository;
import com.kolofinance.repository.ShopSaleRepository;
import com.kolofinance.repository.ShopStockMovementRepository;
import com.kolofinance.repository.ShopCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShopSaleService {

    private final ShopSaleRepository saleRepository;
    private final ShopSaleItemRepository saleItemRepository;
    private final ShopProductRepository productRepository;
    private final ShopStockMovementRepository movementRepository;
    private final ShopCustomerRepository customerRepository;
    private final OrganizationService organizationService;

    @Transactional
    public ShopSale confirmQuickSale(Long organizationId, User seller, Map<Long, Long> quantities, long discountAmount) {
        return confirmSale(organizationId, seller, null, quantities, discountAmount, 0L, null, false);
    }

    @Transactional
    public ShopSale confirmQuickSale(Long organizationId, User seller, Map<Long, Long> quantities, long discountAmount, boolean wholesale) {
        return confirmSale(organizationId, seller, null, quantities, discountAmount, 0L, null, wholesale);
    }

    @Transactional
    public ShopSale confirmCreditSale(Long organizationId, User seller, Long customerId, Map<Long, Long> quantities, long discountAmount, long paidAmount, LocalDate dueDate) {
        return confirmCreditSale(organizationId, seller, customerId, quantities, discountAmount, paidAmount, dueDate, false);
    }

    @Transactional
    public ShopSale confirmCreditSale(Long organizationId, User seller, Long customerId, Map<Long, Long> quantities, long discountAmount, long paidAmount, LocalDate dueDate, boolean wholesale) {
        ShopCustomer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Client introuvable."));
        if (customer.getOrganization() == null || !Objects.equals(customer.getOrganization().getId(), organizationId) || !Boolean.TRUE.equals(customer.getActive())) {
            throw new RuntimeException("Client non disponible dans cette boutique.");
        }
        return confirmSale(organizationId, seller, customer, quantities, discountAmount, paidAmount, dueDate, wholesale);
    }

    private ShopSale confirmSale(Long organizationId, User seller, ShopCustomer customer, Map<Long, Long> quantities, long discountAmount, long paidAmount, LocalDate dueDate, boolean wholesale) {
        if (quantities == null || quantities.isEmpty()) {
            throw new RuntimeException("Aucun produit sélectionné.");
        }
        Organization organization = organizationService.findById(organizationId);
        List<ShopProduct> products = productRepository.findAllById(quantities.keySet());
        if (products.size() != quantities.size()) {
            throw new RuntimeException("Un produit sélectionné est introuvable.");
        }

        long subtotal = 0;
        long grossProfit = 0;
        for (ShopProduct product : products) {
            if (product.getOrganization() == null || !Objects.equals(product.getOrganization().getId(), organizationId) || !Boolean.TRUE.equals(product.getActive())) {
                throw new RuntimeException("Produit non disponible: " + product.getName());
            }
            long quantity = positiveQuantity(quantities.get(product.getId()));
            if (product.getStockQuantity() < quantity) {
                throw new RuntimeException("Stock insuffisant pour " + product.getName() + ". Stock: " + product.getStockQuantity());
            }
            long unitPrice = wholesale && product.getWholesalePrice() != null && product.getWholesalePrice() > 0
                    ? product.getWholesalePrice() : product.getSalePrice();
            subtotal += quantity * unitPrice;
            long costBasis = nullToZero(product.getAverageCost()) > 0
                    ? product.getAverageCost() : nullToZero(product.getPurchasePrice());
            grossProfit += quantity * (unitPrice - costBasis);
        }

        long safeDiscount = Math.max(0, Math.min(discountAmount, subtotal));
        long total = subtotal - safeDiscount;
        long safePaid = customer == null ? total : Math.max(0, Math.min(paidAmount, total));
        long dueAmount = total - safePaid;
        ShopSale sale = ShopSale.builder()
                .organization(organization)
                .seller(seller)
                .customer(customer)
                .status(ShopSaleStatus.CONFIRMED)
                .saleType(customer == null || dueAmount == 0 ? "QUICK" : "CREDIT")
                .paymentMethod(customer == null || dueAmount == 0 ? "CASH" : (safePaid > 0 ? "PARTIAL" : "CREDIT"))
                .subtotalAmount(subtotal)
                .discountAmount(safeDiscount)
                .totalAmount(total)
                .profitAmount(grossProfit - safeDiscount)
                .paidAmount(safePaid)
                .dueAmount(dueAmount)
                .dueDate(dueDate)
                .build();
        sale = saleRepository.save(sale);

        for (ShopProduct product : products) {
            long quantity = positiveQuantity(quantities.get(product.getId()));
            long previousStock = product.getStockQuantity();
            long newStock = previousStock - quantity;
            product.setStockQuantity(newStock);
            productRepository.save(product);

            long saleUnitPrice = wholesale && product.getWholesalePrice() != null && product.getWholesalePrice() > 0
                    ? product.getWholesalePrice() : product.getSalePrice();
            saleItemRepository.save(ShopSaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .quantity(quantity)
                    .unitPrice(saleUnitPrice)
                    .purchasePrice(nullToZero(product.getAverageCost()) > 0 ? product.getAverageCost() : nullToZero(product.getPurchasePrice()))
                    .lineTotal(quantity * saleUnitPrice)
                    .lineProfit(quantity * (saleUnitPrice - (nullToZero(product.getAverageCost()) > 0 ? product.getAverageCost() : nullToZero(product.getPurchasePrice()))))
                    .build());

            movementRepository.save(ShopStockMovement.builder()
                    .organization(organization)
                    .product(product)
                    .user(seller)
                    .movementType(ShopStockMovementType.SALE)
                    .quantityDelta(-quantity)
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .referenceType("SHOP_SALE")
                    .referenceId(sale.getId())
                    .note(customer == null || dueAmount == 0 ? "Vente rapide WhatsApp" : "Vente à crédit WhatsApp")
                    .build());
        }

        if (customer != null && dueAmount > 0) {
            customer.setOutstandingBalance(nullToZero(customer.getOutstandingBalance()) + dueAmount);
            customerRepository.save(customer);
        }

        return sale;
    }

    @Transactional
    public ShopSale cancelSale(Long organizationId, Long saleId, User cancelledBy) {
        ShopSale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Vente #" + saleId + " introuvable."));
        if (!Objects.equals(sale.getOrganization().getId(), organizationId)) {
            throw new RuntimeException("Vente #" + saleId + " n'appartient pas à cette boutique.");
        }
        if (sale.getStatus() == ShopSaleStatus.CANCELLED) {
            throw new RuntimeException("Vente #" + saleId + " est déjà annulée.");
        }

        List<ShopSaleItem> saleItems = saleItemRepository.findBySaleId(saleId);
        for (ShopSaleItem item : saleItems) {
            ShopProduct product = item.getProduct();
            long previousStock = product.getStockQuantity();
            long newStock = previousStock + item.getQuantity();
            product.setStockQuantity(newStock);
            productRepository.save(product);

            movementRepository.save(ShopStockMovement.builder()
                    .organization(sale.getOrganization())
                    .product(product)
                    .user(cancelledBy)
                    .movementType(ShopStockMovementType.ADJUSTMENT)
                    .quantityDelta(item.getQuantity())
                    .previousStock(previousStock)
                    .newStock(newStock)
                    .referenceType("SHOP_SALE_CANCEL")
                    .referenceId(sale.getId())
                    .note("Annulation vente #" + sale.getId())
                    .build());
        }

        if (sale.getCustomer() != null && nullToZero(sale.getDueAmount()) > 0) {
            ShopCustomer customer = sale.getCustomer();
            customer.setOutstandingBalance(Math.max(0, nullToZero(customer.getOutstandingBalance()) - nullToZero(sale.getDueAmount())));
            customerRepository.save(customer);
        }

        sale.setStatus(ShopSaleStatus.CANCELLED);
        sale.setNote((sale.getNote() == null ? "" : sale.getNote() + " | ") + "Annulée par " + cancelledBy.getName());
        return saleRepository.save(sale);
    }

    public List<ShopSale> salesForPeriod(Long organizationId, Long sellerId, LocalDate start, LocalDate end) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusNanos(1);
        if (sellerId != null) {
            return saleRepository.findByOrganizationIdAndSellerIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(organizationId, sellerId, from, to);
        }
        return saleRepository.findByOrganizationIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(organizationId, from, to);
    }

    public List<ShopSaleItem> items(Long saleId) {
        return saleItemRepository.findBySaleId(saleId);
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
