package com.kolofinance.repository;

import com.kolofinance.model.ShopCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopCustomerRepository extends JpaRepository<ShopCustomer, Long> {
    List<ShopCustomer> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);
    List<ShopCustomer> findByOrganizationIdAndActiveTrueAndNormalizedNameContainingOrderByNameAsc(Long organizationId, String normalizedName);
    List<ShopCustomer> findByOrganizationIdAndActiveTrueAndOutstandingBalanceGreaterThanOrderByOutstandingBalanceDesc(Long organizationId, Long amount);
    Optional<ShopCustomer> findFirstByOrganizationIdAndActiveTrueAndNormalizedName(Long organizationId, String normalizedName);
    Optional<ShopCustomer> findFirstByOrganizationIdAndActiveTrueAndPhoneNumber(Long organizationId, String phoneNumber);
}
