package com.kolofinance.controller;

import com.kolofinance.model.Organization;
import com.kolofinance.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<Organization> create(@RequestBody Map<String, String> body) {
        Organization org = organizationService.create(body.get("name"));
        return ResponseEntity.ok(org);
    }

    @GetMapping
    public ResponseEntity<List<Organization>> findAll() {
        return ResponseEntity.ok(organizationService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organization> findById(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.findById(id));
    }
}
