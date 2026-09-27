package com.kolofinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_product_units", uniqueConstraints = @UniqueConstraint(
        name = "uk_shop_product_unit_name",
        columnNames = {"product_id", "normalized_name"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopProductUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ShopProduct product;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 50)
    private String normalizedName;

    @Column(name = "conversion_to_base", nullable = false, precision = 20, scale = 6)
    private BigDecimal conversionToBase;

    @Column(name = "is_base", nullable = false)
    @Builder.Default
    private Boolean base = false;

    @Column(name = "sale_allowed", nullable = false)
    @Builder.Default
    private Boolean saleAllowed = true;

    @Column(name = "purchase_allowed", nullable = false)
    @Builder.Default
    private Boolean purchaseAllowed = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
