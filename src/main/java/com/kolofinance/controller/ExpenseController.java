package com.kolofinance.controller;
import com.kolofinance.dto.AuthPrincipal;
import com.kolofinance.dto.DashboardFilter;

import com.kolofinance.model.DraftExpense;
import com.kolofinance.model.Expense;
import com.kolofinance.model.enums.Role;
import com.kolofinance.service.DashboardService;
import com.kolofinance.service.ExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{orgId}")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final DashboardService dashboardService;

    @GetMapping("/expenses")
    public ResponseEntity<List<Expense>> getExpenses(
            @PathVariable Long orgId,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return ResponseEntity.ok(dashboardService.findFilteredExpenses(orgId,
                enforceRequesterScope(request, DashboardController.buildFilter(params))));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<DraftExpense>> getDrafts(@PathVariable Long orgId, HttpServletRequest request) {
        List<DraftExpense> drafts = expenseService.findDraftsByOrganization(orgId);
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT && principal.getUser() != null) {
            Long agentId = principal.getUser().getId();
            drafts = drafts.stream()
                    .filter(draft -> draft.getAgent() != null && draft.getAgent().getId().equals(agentId))
                    .toList();
        }
        return ResponseEntity.ok(drafts);
    }

    private DashboardFilter enforceRequesterScope(HttpServletRequest request, DashboardFilter filter) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute("authPrincipal");
        if (principal != null && principal.getRole() == Role.AGENT && principal.getUser() != null) {
            filter.setAgentId(principal.getUser().getId());
        }
        return filter;
    }
}
