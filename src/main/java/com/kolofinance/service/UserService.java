package com.kolofinance.service;

import com.kolofinance.model.Organization;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;

    public User create(Long orgId, String phoneNumber, String name, Role role) {
        Organization org = organizationService.findById(orgId);
        User user = User.builder()
                .organization(org)
                .phoneNumber(phoneNumber)
                .name(name)
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    public Optional<User> findByPhone(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    public List<User> findByOrganization(Long orgId) {
        return userRepository.findByOrganizationId(orgId);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable: " + id));
    }
}
