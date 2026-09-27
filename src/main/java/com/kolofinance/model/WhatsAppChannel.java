package com.kolofinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_channels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_whatsapp_channel_provider_device", columnNames = {"provider", "device_id"}),
        @UniqueConstraint(name = "uk_whatsapp_channel_provider_phone", columnNames = {"provider", "phone_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = "GOWA";

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "webhook_secret_hash", length = 255)
    private String webhookSecretHash;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
