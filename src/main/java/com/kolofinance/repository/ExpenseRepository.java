package com.kolofinance.repository;

import com.kolofinance.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByOrganizationIdOrderByConfirmedAtDesc(Long organizationId);

    List<Expense> findByAgentIdOrderByConfirmedAtDesc(Long agentId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.organization.id = :orgId")
    Long sumAmountByOrganizationId(Long orgId);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e WHERE e.organization.id = :orgId GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<Object[]> sumByCategory(Long orgId);
}
