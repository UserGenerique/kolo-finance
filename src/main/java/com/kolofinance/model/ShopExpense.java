package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_expenses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization", "passwordHash"})
    private User recordedBy;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 500)
    private String description;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String category = "DIVERS";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "CONFIRMED";

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        if (this.confirmedAt == null) {
            this.confirmedAt = now;
        }
    }
}
