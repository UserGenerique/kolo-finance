package com.kolofinance.repository;

import com.kolofinance.model.DraftExpense;
import com.kolofinance.model.enums.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DraftExpenseRepository extends JpaRepository<DraftExpense, Long> {

    Optional<DraftExpense> findFirstByAgentIdAndStatusOrderByCreatedAtDesc(Long agentId, DraftStatus status);

    List<DraftExpense> findByAgentIdAndStatusOrderByCreatedAtDesc(Long agentId, DraftStatus status);

    List<DraftExpense> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
