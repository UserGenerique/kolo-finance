package com.kolofinance.controller;

import com.kolofinance.dto.AuthPrincipal;
import com.kolofinance.model.Fund;
import com.kolofinance.model.enums.Role;
import com.kolofinance.service.FundService;
import jakarta.servlet.http.HttpServletRequest;
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

    @PostMapping("/{id}/top-up")
    public ResponseEntity<Fund> topUp(
            @PathVariable Long orgId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Fund fund = fundService.topUp(
                orgId,
                id,
                Long.valueOf(body.get("amount").toString()),
                (String) body.get("description")
        );
        return ResponseEntity.ok(fund);
    }

    @GetMapping("/pending-receipts")
    public ResponseEntity<List<Fund>> pendingReceipts(@PathVariable Long orgId, HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT) {
            return ResponseEntity.ok(fundService.findPendingReceiptsForAgent(principal.getUser().getId()).stream()
                    .filter(fund -> fund.getOrganization().getId().equals(orgId))
                    .toList());
        }
        return ResponseEntity.ok(fundService.findPendingReceiptsByOrganization(orgId));
    }

    @PostMapping("/{id}/accept-receipt")
    public ResponseEntity<Fund> acceptReceipt(
            @PathVariable Long orgId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Long agentId = agentRestriction(request);
        String note = body != null && body.get("note") != null ? body.get("note").toString() : null;
        return ResponseEntity.ok(fundService.acceptReceipt(orgId, id, agentId, note));
    }

    @PostMapping("/{id}/reject-receipt")
    public ResponseEntity<Fund> rejectReceipt(
            @PathVariable Long orgId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Long agentId = agentRestriction(request);
        String note = body != null && body.get("note") != null ? body.get("note").toString() : null;
        return ResponseEntity.ok(fundService.rejectReceipt(orgId, id, agentId, note));
    }

    @GetMapping
    public ResponseEntity<List<Fund>> findAll(@PathVariable Long orgId, HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT && principal.getUser() != null) {
            Long agentId = principal.getUser().getId();
            return ResponseEntity.ok(fundService.findByOrganization(orgId).stream()
                    .filter(fund -> fund.getAgent() != null && fund.getAgent().getId().equals(agentId))
                    .toList());
        }
        return ResponseEntity.ok(fundService.findByOrganization(orgId));
    }

    private Long agentRestriction(HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT) {
            return principal.getUser().getId();
        }
        return null;
    }
}
