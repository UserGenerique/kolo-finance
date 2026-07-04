package com.kolofinance.repository;

import com.kolofinance.model.ShopExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopExpenseRepository extends JpaRepository<ShopExpense, Long> {
    List<ShopExpense> findByOrganizationIdAndStatusAndConfirmedAtBetweenOrderByConfirmedAtDesc(
            Long organizationId, String status, LocalDateTime start, LocalDateTime end);
}
