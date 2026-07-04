package com.kolofinance.repository;

import com.kolofinance.model.ShopStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopStockMovementRepository extends JpaRepository<ShopStockMovement, Long> {
}
