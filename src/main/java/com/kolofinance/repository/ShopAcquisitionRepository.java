package com.kolofinance.repository;

import com.kolofinance.model.ShopAcquisition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopAcquisitionRepository extends JpaRepository<ShopAcquisition, Long> {
    List<ShopAcquisition> findByOrganizationIdAndSupplierIdAndDueAmountGreaterThanOrderByConfirmedAtAsc(Long organizationId, Long supplierId, Long minDue);
}
