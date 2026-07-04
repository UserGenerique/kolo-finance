package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_acquisition_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopAcquisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acquisition_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ShopAcquisition acquisition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
    private ShopProduct product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "unit_cost", nullable = false)
    private Long unitCost;

    @Column(name = "line_total", nullable = false)
    private Long lineTotal;
}
