package com.kolofinance.repository;

import com.kolofinance.model.ShopCustomerPayment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopCustomerPaymentRepository
    extends JpaRepository<ShopCustomerPayment, Long>
{
    List<ShopCustomerPayment> findByCustomerIdOrderByPaidAtDesc(
        Long customerId
    );
    List<ShopCustomerPayment> findByOrganizationIdAndPaidAtBetweenOrderByPaidAtDesc(
        Long organizationId,
        LocalDateTime start,
        LocalDateTime end
    );
    Optional<ShopCustomerPayment> findFirstByCustomerIdOrderByPaidAtDesc(
        Long customerId
    );

    @Query(
        "select coalesce(sum(p.amount), 0) from ShopCustomerPayment p " +
            "where p.organization.id = :organizationId and p.paidAt < :before"
    )
    long sumAmountBefore(
        @Param("organizationId") Long organizationId,
        @Param("before") LocalDateTime before
    );
}
