package com.kolofinance.repository;

import com.kolofinance.model.ConversationCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationCommandRepository extends JpaRepository<ConversationCommand, Long> {

    Optional<ConversationCommand> findByOrganizationIdAndIdempotencyKey(Long organizationId, String idempotencyKey);
}
