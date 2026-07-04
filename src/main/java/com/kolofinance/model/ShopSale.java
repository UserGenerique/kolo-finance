package com.kolofinance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kolofinance.model.enums.ShopSaleStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_sales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShopSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization", "passwordHash"})
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
    private ShopCustomer customer;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopSaleStatus status = ShopSaleStatus.CONFIRMED;

    @Builder.Default
    @Column(name = "sale_type", nullable = false)
    private String saleType = "QUICK";

    @Builder.Default
    @Column(name = "payment_method", nullable = false)
    private String paymentMethod = "CASH";

    @Builder.Default
    @Column(name = "subtotal_amount", nullable = false)
    private Long subtotalAmount = 0L;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount = 0L;

    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Builder.Default
    @Column(name = "profit_amount", nullable = false)
    private Long profitAmount = 0L;
    @Builder.Default
    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount = 0L;

    @Builder.Default
    @Column(name = "due_amount", nullable = false)
    private Long dueAmount = 0L;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 500)
    private String note;

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
