package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "normalized_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    private String aliases;

    private String category;

    @Builder.Default
    @Column(nullable = false)
    private String unit = "piece";

    @Builder.Default
    @Column(name = "purchase_price", nullable = false)
    private Long purchasePrice = 0L;

    @Column(name = "sale_price", nullable = false)
    private Long salePrice;

    @Builder.Default
    @Column(name = "wholesale_price", nullable = false)
    private Long wholesalePrice = 0L;

    @Builder.Default
    @Column(name = "stock_quantity", nullable = false)
    private Long stockQuantity = 0L;

    @Builder.Default
    @Column(name = "min_stock_quantity", nullable = false)
    private Long minStockQuantity = 0L;

    @Builder.Default
    @Column(name = "average_cost", nullable = false)
    private Long averageCost = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
