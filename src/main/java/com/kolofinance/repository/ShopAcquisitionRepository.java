package com.kolofinance.repository;

import com.kolofinance.model.ShopAcquisition;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopAcquisitionRepository
    extends JpaRepository<ShopAcquisition, Long>
{
    List<ShopAcquisition> findByOrganizationIdAndSupplierIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(
        Long organizationId,
        Long supplierId,
        Long minDue
    );

    @Query(
        "select coalesce(sum(a.paidAmount), 0) from ShopAcquisition a " +
            "where a.organization.id = :organizationId and a.status = 'CONFIRMED' " +
            "and a.confirmedAt between :from and :to"
    )
    long sumPaidBetween(
        @Param("organizationId") Long organizationId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query(
        "select coalesce(sum(a.paidAmount), 0) from ShopAcquisition a " +
            "where a.organization.id = :organizationId and a.status = 'CONFIRMED' " +
            "and a.confirmedAt < :before"
    )
    long sumPaidBefore(
        @Param("organizationId") Long organizationId,
        @Param("before") LocalDateTime before
    );
}
