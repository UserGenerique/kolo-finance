package com.kolofinance.repository;

import com.kolofinance.model.ShopSale;
import com.kolofinance.model.enums.ShopSaleStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopSaleRepository extends JpaRepository<ShopSale, Long> {
    List<ShopSale> findByOrganizationIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(
        Long organizationId,
        LocalDateTime start,
        LocalDateTime end
    );
    List<ShopSale> findByOrganizationIdAndSellerIdAndConfirmedAtBetweenOrderByConfirmedAtDesc(
        Long organizationId,
        Long sellerId,
        LocalDateTime start,
        LocalDateTime end
    );
    List<ShopSale> findByOrganizationIdAndCustomerIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(
        Long organizationId,
        Long customerId,
        Long dueAmount
    );

    @Query(
        "select coalesce(sum(s.paidAmount), 0) from ShopSale s " +
            "where s.organization.id = :organizationId and s.status = :status " +
            "and s.confirmedAt < :before"
    )
    long sumPaidBefore(
        @Param("organizationId") Long organizationId,
        @Param("status") ShopSaleStatus status,
        @Param("before") LocalDateTime before
    );
}
