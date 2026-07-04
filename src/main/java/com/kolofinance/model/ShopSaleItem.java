package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_sale_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ShopSale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
    private ShopProduct product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Builder.Default
    @Column(name = "purchase_price", nullable = false)
    private Long purchasePrice = 0L;

    @Column(name = "line_total", nullable = false)
    private Long lineTotal;

    @Builder.Default
    @Column(name = "line_profit", nullable = false)
    private Long lineProfit = 0L;
}
