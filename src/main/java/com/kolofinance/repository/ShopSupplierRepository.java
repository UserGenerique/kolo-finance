package com.kolofinance.repository;

import com.kolofinance.model.ShopSupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopSupplierRepository extends JpaRepository<ShopSupplier, Long> {
    List<ShopSupplier> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);
    List<ShopSupplier> findByOrganizationIdAndActiveTrueAndOutstandingBalanceGreaterThanOrderByOutstandingBalanceDesc(Long organizationId, Long minBalance);
    Optional<ShopSupplier> findFirstByOrganizationIdAndActiveTrueAndNormalizedName(Long organizationId, String normalizedName);
    List<ShopSupplier> findByOrganizationIdAndActiveTrueAndNormalizedNameContainingOrderByNameAsc(Long organizationId, String normalizedName);
}
