package com.kolofinance.repository;

import com.kolofinance.model.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

    Optional<PlatformAdmin> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);
}
