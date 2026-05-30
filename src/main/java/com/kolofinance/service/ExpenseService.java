package com.kolofinance.service;

import com.kolofinance.dto.ParsedExpense;
import com.kolofinance.model.*;
import com.kolofinance.model.enums.DraftStatus;
import com.kolofinance.repository.DraftExpenseRepository;
import com.kolofinance.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final DraftExpenseRepository draftExpenseRepository;
    private final FundService fundService;

    /**
     * Crée un brouillon de dépense pour un agent.
     */
    public DraftExpense createDraft(User agent, ParsedExpense parsed) {
        Optional<Fund> fundOpt = fundService.findActiveFundForAgent(agent.getId());
        if (fundOpt.isEmpty()) {
            throw new RuntimeException("Aucun fonds actif pour l'agent " + agent.getName());
        }

        Fund fund = fundOpt.get();
        DraftExpense draft = DraftExpense.builder()
                .organization(agent.getOrganization())
                .agent(agent)
                .fund(fund)
                .amount(parsed.getAmount())
                .description(parsed.getDescription())
                .category(parsed.getCategory())
                .status(DraftStatus.PENDING)
                .build();

        draft = draftExpenseRepository.save(draft);
        log.info("Brouillon créé: {} FCFA pour '{}' (agent: {})", parsed.getAmount(), parsed.getDescription(), agent.getName());
        return draft;
    }

    /**
     * Confirme un brouillon : crée la dépense finale et débite le fonds.
     */
    @Transactional
    public Expense confirmDraft(DraftExpense draft) {
        // Débiter le fonds
        fundService.debit(draft.getFund().getId(), draft.getAmount());

        // Créer la dépense finale
        Expense expense = Expense.builder()
                .organization(draft.getOrganization())
                .agent(draft.getAgent())
                .fund(draft.getFund())
                .amount(draft.getAmount())
                .description(draft.getDescription())
                .category(draft.getCategory())
                .build();
        expense = expenseRepository.save(expense);

        // Marquer le brouillon comme confirmé
        draft.setStatus(DraftStatus.CONFIRMED);
        draftExpenseRepository.save(draft);

        log.info("Dépense confirmée: {} FCFA pour '{}'", expense.getAmount(), expense.getDescription());
        return expense;
    }

    /**
     * Annule un brouillon.
     */
    public void cancelDraft(DraftExpense draft) {
        draft.setStatus(DraftStatus.CANCELLED);
        draftExpenseRepository.save(draft);
        log.info("Brouillon annulé: {}", draft.getId());
    }

    /**
     * Cherche un brouillon en attente pour un agent.
     */
    public Optional<DraftExpense> findPendingDraft(Long agentId) {
        return draftExpenseRepository.findFirstByAgentIdAndStatusOrderByCreatedAtDesc(agentId, DraftStatus.PENDING);
    }

    public List<Expense> findByOrganization(Long orgId) {
        return expenseRepository.findByOrganizationIdOrderByConfirmedAtDesc(orgId);
    }

    public List<DraftExpense> findDraftsByOrganization(Long orgId) {
        return draftExpenseRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
    }
}
