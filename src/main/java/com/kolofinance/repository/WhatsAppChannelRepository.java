package com.kolofinance.repository;

import com.kolofinance.model.WhatsAppChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsAppChannelRepository extends JpaRepository<WhatsAppChannel, Long> {

    Optional<WhatsAppChannel> findByProviderAndDeviceIdAndStatus(String provider, String deviceId, String status);

    Optional<WhatsAppChannel> findByProviderAndPhoneNumberAndStatus(String provider, String phoneNumber, String status);

    List<WhatsAppChannel> findByOrganizationIdAndStatusOrderByCreatedAtAsc(Long organizationId, String status);
}
