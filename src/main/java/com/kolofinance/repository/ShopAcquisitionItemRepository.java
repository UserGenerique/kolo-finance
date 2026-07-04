package com.kolofinance.repository;

import com.kolofinance.model.ShopAcquisitionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopAcquisitionItemRepository extends JpaRepository<ShopAcquisitionItem, Long> {
    List<ShopAcquisitionItem> findByAcquisitionId(Long acquisitionId);
}
