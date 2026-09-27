package com.kolofinance.repository;

import com.kolofinance.model.ShopExpense;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopExpenseRepository
    extends JpaRepository<ShopExpense, Long>
{
    List<ShopExpense> findByOrganizationIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(
        Long organizationId,
        String status,
        LocalDateTime start,
        LocalDateTime end
    );

    @Query(
        "select coalesce(sum(e.amount), 0) from ShopExpense e " +
            "where e.organization.id = :organizationId and e.status = 'CONFIRMED' " +
            "and e.confirmedAt < :before"
    )
    long sumAmountBefore(
        @Param("organizationId") Long organizationId,
        @Param("before") LocalDateTime before
    );
}
