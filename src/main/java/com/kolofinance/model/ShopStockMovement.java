package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kolofinance.model.enums.ShopStockMovementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_stock_movements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
    private ShopProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization", "passwordHash"})
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private ShopStockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private Long quantityDelta;

    @Column(name = "previous_stock", nullable = false)
    private Long previousStock;

    @Column(name = "new_stock", nullable = false)
    private Long newStock;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
