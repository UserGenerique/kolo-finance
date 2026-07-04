package com.kolofinance.controller;
import com.kolofinance.dto.AuthPrincipal;

import com.kolofinance.dto.UserResponse;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @PathVariable Long orgId,
            @RequestBody Map<String, String> body) {
        User user = userService.create(
                orgId,
                body.get("phoneNumber"),
                body.get("name"),
                Role.valueOf(body.get("role").toUpperCase()),
                body.get("password")
        );
        return ResponseEntity.ok(toResponse(orgId, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long orgId,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = userService.update(
                orgId,
                id,
                body.get("phoneNumber"),
                body.get("name"),
                Role.valueOf(body.get("role").toUpperCase()),
                body.get("password")
        );
        return ResponseEntity.ok(toResponse(orgId, user));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(@PathVariable Long orgId, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orgId, userService.setActive(orgId, id, true)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivate(@PathVariable Long orgId, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orgId, userService.setActive(orgId, id, false)));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll(@PathVariable Long orgId, HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT && principal.getUser() != null) {
            return ResponseEntity.ok(List.of(UserResponse.of(
                    principal.getUser(),
                    principal.getRole(),
                    principal.getMembership() == null || Boolean.TRUE.equals(principal.getMembership().getActive())
            )));
        }
        return ResponseEntity.ok(userService.findMembershipsByOrganization(orgId).stream()
                .map(m -> UserResponse.of(m.getUser(), m.getRole(), m.getActive()))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long orgId, @PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orgId, userService.findById(id)));
    }

    private UserResponse toResponse(Long orgId, User user) {
        OrganizationMembership membership = userService.findMembership(orgId, user.getId())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable dans cette organisation"));
        return UserResponse.of(user, membership.getRole(), membership.getActive());
    }
}
