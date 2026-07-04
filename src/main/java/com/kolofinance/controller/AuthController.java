package com.kolofinance.controller;

import com.kolofinance.dto.AuthPrincipal;
import com.kolofinance.dto.AuthResponse;
import com.kolofinance.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.login(body.get("phoneNumber"), body.get("password")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(extractBearer(request));
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Session invalide"));
        }
        if (principal.isPlatformAdmin()) {
            return ResponseEntity.ok(Map.of(
                    "userType", "PLATFORM_ADMIN",
                    "name", principal.getPlatformAdmin().getName(),
                    "phoneNumber", principal.getPlatformAdmin().getPhoneNumber()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "userType", "ORG_USER",
                "name", principal.getUser().getName(),
                "phoneNumber", principal.getUser().getPhoneNumber()
        ));
    }

    private String extractBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
