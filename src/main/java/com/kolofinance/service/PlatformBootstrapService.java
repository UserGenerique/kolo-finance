package com.kolofinance.service;

import com.kolofinance.model.PlatformAdmin;
import com.kolofinance.repository.PlatformAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformBootstrapService implements ApplicationRunner {

    private final PlatformAdminRepository platformAdminRepository;
    private final AuthService authService;

    @Value("${platform.admin.phone:}")
    private String adminPhone;

    @Value("${platform.admin.name:Super Admin}")
    private String adminName;

    @Value("${platform.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminPhone == null || adminPhone.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        String normalizedPhone = authService.normalizePhone(adminPhone);
        if (platformAdminRepository.existsByPhoneNumber(normalizedPhone)) {
            return;
        }
        platformAdminRepository.save(PlatformAdmin.builder()
                .phoneNumber(normalizedPhone)
                .name(adminName)
                .passwordHash(authService.hashPassword(adminPassword))
                .active(true)
                .build());
        log.info("Super admin Kolo bootstrap créé pour le numéro {}", normalizedPhone);
    }
}
