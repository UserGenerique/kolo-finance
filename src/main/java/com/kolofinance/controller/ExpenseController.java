package com.kolofinance.controller;

import com.kolofinance.model.DraftExpense;
import com.kolofinance.model.Expense;
import com.kolofinance.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations/{orgId}")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/expenses")
    public ResponseEntity<List<Expense>> getExpenses(@PathVariable Long orgId) {
        return ResponseEntity.ok(expenseService.findByOrganization(orgId));
    }

    @GetMapping("/drafts")
    public ResponseEntity<List<DraftExpense>> getDrafts(@PathVariable Long orgId) {
        return ResponseEntity.ok(expenseService.findDraftsByOrganization(orgId));
    }
}
