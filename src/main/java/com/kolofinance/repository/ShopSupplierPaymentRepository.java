package com.kolofinance.repository;

import com.kolofinance.model.ShopSupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopSupplierPaymentRepository extends JpaRepository<ShopSupplierPayment, Long> {
    Optional<ShopSupplierPayment> findFirstBySupplierIdOrderByCreatedAtDesc(Long supplierId);
}
