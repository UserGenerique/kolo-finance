package com.kolofinance.repository;

import com.kolofinance.model.ShopSupplierPayment;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopSupplierPaymentRepository
    extends JpaRepository<ShopSupplierPayment, Long>
{
    Optional<ShopSupplierPayment> findFirstBySupplierIdOrderByCreatedAtDesc(
        Long supplierId
    );

    @Query(
        "select coalesce(sum(p.amount), 0) from ShopSupplierPayment p " +
            "where p.organization.id = :organizationId and p.createdAt between :from and :to"
    )
    long sumAmountBetween(
        @Param("organizationId") Long organizationId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query(
        "select coalesce(sum(p.amount), 0) from ShopSupplierPayment p " +
            "where p.organization.id = :organizationId and p.createdAt < :before"
    )
    long sumAmountBefore(
        @Param("organizationId") Long organizationId,
        @Param("before") LocalDateTime before
    );
}
