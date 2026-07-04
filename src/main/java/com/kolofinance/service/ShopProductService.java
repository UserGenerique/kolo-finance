package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.model.ShopProduct;
import com.kolofinance.repository.ShopProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ShopProductService {

    private final ShopProductRepository productRepository;
    private final OrganizationService organizationService;

    public List<ShopProduct> listActive(Long organizationId) {
        return productRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
    }

    @Transactional
    public ShopProduct upsertProduct(Long organizationId, String name, Long purchasePrice, Long salePrice, Long stockQuantity, Long minStockQuantity) {
        return upsertProduct(organizationId, name, purchasePrice, salePrice, null, stockQuantity, minStockQuantity);
    }

    @Transactional
    public ShopProduct upsertProduct(Long organizationId, String name, Long purchasePrice, Long salePrice, Long wholesalePrice, Long stockQuantity, Long minStockQuantity) {
        if (salePrice == null || salePrice <= 0) {
            throw new RuntimeException("Le prix de vente est obligatoire et doit être supérieur à zéro.");
        }
        if (stockQuantity == null || stockQuantity < 0) {
            throw new RuntimeException("Le stock doit être supérieur ou égal à zéro.");
        }

        Organization organization = organizationService.findById(organizationId);
        String cleanName = requireText(name, "Le nom du produit est obligatoire.");
        String normalizedName = normalize(cleanName);
        ShopProduct product = productRepository.findByOrganizationIdAndNormalizedName(organizationId, normalizedName)
                .orElseGet(() -> ShopProduct.builder()
                        .organization(organization)
                        .normalizedName(normalizedName)
                        .active(true)
                        .build());

        product.setName(cleanName);
        product.setPurchasePrice(purchasePrice != null && purchasePrice >= 0 ? purchasePrice : product.getPurchasePrice());
        product.setSalePrice(salePrice);
        product.setStockQuantity(stockQuantity);
        product.setMinStockQuantity(minStockQuantity != null && minStockQuantity >= 0 ? minStockQuantity : product.getMinStockQuantity());
        product.setWholesalePrice(wholesalePrice != null && wholesalePrice > 0 ? wholesalePrice : product.getWholesalePrice());
        product.setUnit(product.getUnit() == null || product.getUnit().isBlank() ? "piece" : product.getUnit());
        product.setActive(true);
        return productRepository.save(product);
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
}
