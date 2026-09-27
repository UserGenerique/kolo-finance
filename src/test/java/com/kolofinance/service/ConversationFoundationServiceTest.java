package com.kolofinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.model.ConversationCommand;
import com.kolofinance.model.Organization;
import com.kolofinance.model.User;
import com.kolofinance.model.WhatsAppChannel;
import com.kolofinance.repository.ConversationCommandRepository;
import com.kolofinance.repository.WhatsAppChannelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationFoundationServiceTest {

    @Mock
    WhatsAppChannelRepository channelRepository;

    @Mock
    ConversationCommandRepository commandRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    ConversationFoundationService service;

    @Test
    void resolvesByDeviceBeforePhoneNumber() {
        WhatsAppChannel channel = WhatsAppChannel.builder().id(7L).build();
        when(channelRepository.findByProviderAndDeviceIdAndStatus("GOWA", "device-a", "ACTIVE"))
                .thenReturn(Optional.of(channel));

        assertThat(service.resolveActiveChannel("GOWA", "device-a", "22370000000"))
                .contains(channel);
        verify(channelRepository, never())
                .findByProviderAndPhoneNumberAndStatus(any(), any(), any());
    }

    @Test
    void fallsBackToPhoneNumberWhenDeviceIsUnknown() {
        WhatsAppChannel channel = WhatsAppChannel.builder().id(8L).build();
        when(channelRepository.findByProviderAndDeviceIdAndStatus("GOWA", "unknown", "ACTIVE"))
                .thenReturn(Optional.empty());
        when(channelRepository.findByProviderAndPhoneNumberAndStatus("GOWA", "22370000000", "ACTIVE"))
                .thenReturn(Optional.of(channel));

        assertThat(service.resolveActiveChannel("GOWA", "unknown", "22370000000"))
                .contains(channel);
    }

    @Test
    void returnsExistingCommandForSameTenantAndIdempotencyKey() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"items\":1}");
        Organization organization = Organization.builder().id(3L).name("Boutique").build();
        ConversationCommand existing = ConversationCommand.builder()
                .id(10L)
                .organization(organization)
                .idempotencyKey("gowa:device-a:message-1")
                .intent("SALE")
                .normalizedPayload("{}")
                .build();
        when(commandRepository.findByOrganizationIdAndIdempotencyKey(3L, "gowa:device-a:message-1"))
                .thenReturn(Optional.of(existing));

        ConversationCommand result = service.receiveCommand(
                organization,
                null,
                null,
                "message-1",
                "gowa:device-a:message-1",
                "SALE",
                Map.of("items", 1)
        );

        assertThat(result).isSameAs(existing);
        verify(commandRepository, never()).save(any());
    }

    @Test
    void createsCommandWhenIdempotencyKeyIsNew() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"items\":2}");
        Organization organization = Organization.builder().id(3L).name("Boutique").build();
        when(commandRepository.findByOrganizationIdAndIdempotencyKey(3L, "gowa:device-a:message-2"))
                .thenReturn(Optional.empty());
        when(commandRepository.save(any(ConversationCommand.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversationCommand result = service.receiveCommand(
                organization,
                null,
                User.builder().id(9L).name("Agent").build(),
                "message-2",
                "gowa:device-a:message-2",
                "PURCHASE",
                Map.of("items", 2)
        );

        assertThat(result.getIntent()).isEqualTo("PURCHASE");
        assertThat(result.getNormalizedPayload()).contains("items");
        verify(commandRepository).save(any(ConversationCommand.class));
    }
}
