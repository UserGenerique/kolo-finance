package com.kolofinance.controller;

import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import com.kolofinance.service.UserService;
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
    public ResponseEntity<User> create(
            @PathVariable Long orgId,
            @RequestBody Map<String, String> body) {
        User user = userService.create(
                orgId,
                body.get("phoneNumber"),
                body.get("name"),
                Role.valueOf(body.get("role").toUpperCase())
        );
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<List<User>> findAll(@PathVariable Long orgId) {
        return ResponseEntity.ok(userService.findByOrganization(orgId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> findById(@PathVariable Long orgId, @PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }
}
