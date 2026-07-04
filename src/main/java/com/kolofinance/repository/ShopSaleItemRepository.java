package com.kolofinance.repository;

import com.kolofinance.model.ShopSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopSaleItemRepository extends JpaRepository<ShopSaleItem, Long> {
    List<ShopSaleItem> findBySaleId(Long saleId);
}
