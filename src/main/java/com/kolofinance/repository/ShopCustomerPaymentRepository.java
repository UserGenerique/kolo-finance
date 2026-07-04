package com.kolofinance.repository;

import com.kolofinance.model.ShopCustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShopCustomerPaymentRepository extends JpaRepository<ShopCustomerPayment, Long> {
    List<ShopCustomerPayment> findByCustomerIdOrderByPaidAtDesc(Long customerId);
    List<ShopCustomerPayment> findByOrganizationIdAndPaidAtBetweenOrderByPaidAtDesc(Long organizationId, LocalDateTime start, LocalDateTime end);
    Optional<ShopCustomerPayment> findFirstByCustomerIdOrderByPaidAtDesc(Long customerId);
}
