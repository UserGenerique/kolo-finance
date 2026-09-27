package com.kolofinance.repository;

import com.kolofinance.model.ShopProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopProductUnitRepository extends JpaRepository<ShopProductUnit, Long> {

    List<ShopProductUnit> findByOrganizationIdAndProductIdOrderByBaseDescNameAsc(Long organizationId, Long productId);

    Optional<ShopProductUnit> findByOrganizationIdAndProductIdAndNormalizedName(
            Long organizationId, Long productId, String normalizedName);
}
