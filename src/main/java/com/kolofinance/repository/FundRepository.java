package com.kolofinance.repository;

import com.kolofinance.model.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FundRepository extends JpaRepository<Fund, Long> {

    List<Fund> findByOrganizationId(Long organizationId);

    List<Fund> findByAgentId(Long agentId);

    Optional<Fund> findFirstByAgentIdAndBalanceGreaterThanOrderByCreatedAtDesc(Long agentId, Long minBalance);
}
