package com.kolofinance.repository;

import com.kolofinance.model.ShopSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopSaleRepository extends JpaRepository<ShopSale, Long> {
    List<ShopSale> findByOrganizationIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(Long organizationId, LocalDateTime start, LocalDateTime end);
    List<ShopSale> findByOrganizationIdAndSellerIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(Long organizationId, Long sellerId, LocalDateTime start, LocalDateTime end);
    List<ShopSale> findByOrganizationIdAndCustomerIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(Long organizationId, Long customerId, Long dueAmount);
}
