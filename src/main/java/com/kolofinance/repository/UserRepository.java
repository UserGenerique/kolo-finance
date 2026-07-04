package com.kolofinance.repository;

import com.kolofinance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByOrganizationId(Long organizationId);

    List<User> findByOrganizationIdAndActiveTrue(Long organizationId);
}
