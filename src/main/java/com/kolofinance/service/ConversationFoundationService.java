package com.kolofinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.model.ConversationCommand;
import com.kolofinance.model.Organization;
import com.kolofinance.model.User;
import com.kolofinance.model.WhatsAppChannel;
import com.kolofinance.repository.ConversationCommandRepository;
import com.kolofinance.repository.WhatsAppChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationFoundationService {

    private static final String ACTIVE = "ACTIVE";

    private final WhatsAppChannelRepository channelRepository;
    private final ConversationCommandRepository commandRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<WhatsAppChannel> resolveActiveChannel(String provider, String deviceId, String phoneNumber) {
        if (deviceId != null && !deviceId.isBlank()) {
            Optional<WhatsAppChannel> byDevice = channelRepository.findByProviderAndDeviceIdAndStatus(
                    provider, deviceId, ACTIVE);
            if (byDevice.isPresent()) {
                return byDevice;
            }
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return channelRepository.findByProviderAndPhoneNumberAndStatus(provider, phoneNumber, ACTIVE);
    }

    @Transactional
    public ConversationCommand receiveCommand(
            Organization organization,
            WhatsAppChannel channel,
            User actor,
            String sourceMessageId,
            String idempotencyKey,
            String intent,
            Object normalizedPayload
    ) {
        String payload = serialize(normalizedPayload);
        return commandRepository.findByOrganizationIdAndIdempotencyKey(organization.getId(), idempotencyKey)
                .orElseGet(() -> commandRepository.save(ConversationCommand.builder()
                        .organization(organization)
                        .channel(channel)
                        .actorUser(actor)
                        .sourceMessageId(sourceMessageId)
                        .idempotencyKey(idempotencyKey)
                        .intent(intent)
                        .normalizedPayload(payload)
                        .status("RECEIVED")
                        .build()));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload conversationnel invalide", e);
        }
    }
}
