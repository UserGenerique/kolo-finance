package com.kolofinance.service;

import com.kolofinance.model.WhatsAppChannel;
import com.kolofinance.repository.WhatsAppChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsAppChannelService {

    private static final String GOWA = "GOWA";
    private static final String ACTIVE = "ACTIVE";

    private final WhatsAppChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public Optional<WhatsAppChannel> resolveGowa(String deviceId, String phoneNumber) {
        if (deviceId != null && !deviceId.isBlank()) {
            Optional<WhatsAppChannel> channel = channelRepository
                    .findByProviderAndDeviceIdAndStatus(GOWA, deviceId, ACTIVE);
            if (channel.isPresent()) {
                return channel;
            }
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return channelRepository.findByProviderAndPhoneNumberAndStatus(GOWA, phoneNumber, ACTIVE);
    }
}
