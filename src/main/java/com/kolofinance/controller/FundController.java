package com.kolofinance.controller;

import com.kolofinance.model.Fund;
import com.kolofinance.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @PostMapping
    public ResponseEntity<Fund> create(
            @PathVariable Long orgId,
            @RequestBody Map<String, Object> body) {
        Fund fund = fundService.create(
                orgId,
                Long.valueOf(body.get("agentId").toString()),
                Long.valueOf(body.get("amount").toString()),
                (String) body.get("description")
        );
        return ResponseEntity.ok(fund);
    }

    @GetMapping
    public ResponseEntity<List<Fund>> findAll(@PathVariable Long orgId) {
        return ResponseEntity.ok(fundService.findByOrganization(orgId));
    }
}
