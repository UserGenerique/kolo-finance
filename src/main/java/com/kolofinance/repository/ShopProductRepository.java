package com.kolofinance.repository;

import com.kolofinance.model.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopProductRepository extends JpaRepository<ShopProduct, Long> {
    List<ShopProduct> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);
    Optional<ShopProduct> findByOrganizationIdAndNormalizedName(Long organizationId, String normalizedName);
}
