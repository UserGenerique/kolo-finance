package com.kolofinance.repository;

import com.kolofinance.model.Fund;
import com.kolofinance.model.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FundRepository extends JpaRepository<Fund, Long> {

    List<Fund> findByOrganizationId(Long organizationId);

    List<Fund> findByAgentId(Long agentId);

    Optional<Fund> findFirstByAgentIdAndBalanceGreaterThanOrderByCreatedAtDesc(Long agentId, Long minBalance);

    Optional<Fund> findFirstByAgentIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(Long agentId, FundStatus status, Long minBalance);

    List<Fund> findByAgentIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(Long agentId, FundStatus status, Long minBalance);

    Optional<Fund> findFirstByAgentIdAndOrganizationIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(Long agentId, Long organizationId, FundStatus status, Long minBalance);

    Optional<Fund> findByIdAndOrganizationId(Long id, Long organizationId);

    List<Fund> findByAgentIdAndStatusOrderByCreatedAtAsc(Long agentId, FundStatus status);

    List<Fund> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, FundStatus status);
}
