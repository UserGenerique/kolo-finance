package com.kolofinance.repository;

import com.kolofinance.model.ShopConversationSession;
import com.kolofinance.model.enums.ShopSessionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ShopConversationSessionRepository extends JpaRepository<ShopConversationSession, Long> {
    Optional<ShopConversationSession> findFirstByUserIdAndSessionTypeAndStateNotAndExpiresAtAfterOrderByUpdatedAtDesc(
            Long userId,
            ShopSessionType sessionType,
            String state,
            LocalDateTime now
    );
}
