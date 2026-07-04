package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.model.ShopConversationSession;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.ShopSessionType;
import com.kolofinance.repository.ShopConversationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopConversationService {

    private static final String CLOSED = "CLOSED";
    private final ShopConversationSessionRepository sessionRepository;

    public Optional<ShopConversationSession> findActiveSaleSession(Long userId) {
        return sessionRepository.findFirstByUserIdAndSessionTypeAndStateNotAndExpiresAtAfterOrderByUpdatedAtDesc(
                userId,
                ShopSessionType.SALE,
                CLOSED,
                LocalDateTime.now()
        );
    }

    @Transactional
    public ShopConversationSession startSaleSession(Organization organization, User user, String payload) {
        findActiveSaleSession(user.getId()).ifPresent(this::close);
        ShopConversationSession session = ShopConversationSession.builder()
                .organization(organization)
                .user(user)
                .sessionType(ShopSessionType.SALE)
                .state("SELECTING")
                .payload(payload)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();
        return sessionRepository.save(session);
    }

    @Transactional
    public ShopConversationSession save(ShopConversationSession session) {
        session.setExpiresAt(LocalDateTime.now().plusMinutes(20));
        return sessionRepository.save(session);
    }

    @Transactional
    public void close(ShopConversationSession session) {
        session.setState(CLOSED);
        sessionRepository.save(session);
    }
}
