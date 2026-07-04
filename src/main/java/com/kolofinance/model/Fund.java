package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kolofinance.model.enums.FundStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "funds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
    private User agent;

    @Column(name = "initial_amount", nullable = false)
    private Long initialAmount;

    @Column(nullable = false)
    private Long balance;

    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FundStatus status = FundStatus.ACTIVE;

    @Column(name = "receipt_confirmed_at")
    private LocalDateTime receiptConfirmedAt;

    @Column(name = "receipt_rejected_at")
    private LocalDateTime receiptRejectedAt;

    @Column(name = "receipt_note")
    private String receiptNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.balance == null) {
            this.balance = this.initialAmount;
        }
    }
}
