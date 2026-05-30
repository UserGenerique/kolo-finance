package com.kolofinance.controller;

import com.kolofinance.dto.ParsedExpense;
import com.kolofinance.dto.WhatsAppWebhookPayload;
import com.kolofinance.model.DraftExpense;
import com.kolofinance.model.Expense;
import com.kolofinance.model.Fund;
import com.kolofinance.model.User;
import com.kolofinance.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final MessageParserService messageParserService;
    private final WhatsAppService whatsAppService;
    private final FundService fundService;

    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    /**
     * GET /api/webhook — Vérification du webhook par WhatsApp.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook vérifié avec succès");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Échec vérification webhook: mode={}, token={}", mode, token);
        return ResponseEntity.status(403).body("Verification failed");
    }

    /**
     * POST /api/webhook — Réception des messages WhatsApp.
     */
    @PostMapping
    public ResponseEntity<String> receive(@RequestBody WhatsAppWebhookPayload payload) {
        try {
            if (payload.getEntry() == null) {
                return ResponseEntity.ok("OK");
            }

            for (WhatsAppWebhookPayload.Entry entry : payload.getEntry()) {
                if (entry.getChanges() == null) continue;

                for (WhatsAppWebhookPayload.Change change : entry.getChanges()) {
                    if (change.getValue() == null || change.getValue().getMessages() == null) continue;

                    for (WhatsAppWebhookPayload.Message msg : change.getValue().getMessages()) {
                        if (!"text".equals(msg.getType())) continue;
                        processMessage(msg.getFrom(), msg.getText().getBody());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erreur traitement webhook: {}", e.getMessage(), e);
        }

        // Toujours répondre 200 pour éviter les retries WhatsApp
        return ResponseEntity.ok("OK");
    }

    /**
     * Logique principale : identifier l'utilisateur, gérer brouillon ou nouvelle dépense.
     */
    private void processMessage(String phoneNumber, String text) {
        log.info("Message reçu de {}: '{}'", phoneNumber, text);

        // 1. Identifier l'utilisateur
        Optional<User> userOpt = userService.findByPhone(phoneNumber);
        if (userOpt.isEmpty()) {
            log.warn("Numéro inconnu: {}", phoneNumber);
            whatsAppService.sendError(phoneNumber,
                    "Votre numéro n'est pas enregistré. Contactez votre responsable.");
            return;
        }

        User user = userOpt.get();
        String textLower = text.trim().toLowerCase();

        // 2. Vérifier s'il y a un brouillon en attente
        Optional<DraftExpense> pendingDraft = expenseService.findPendingDraft(user.getId());

        if (pendingDraft.isPresent()) {
            handleDraftResponse(user, pendingDraft.get(), textLower);
            return;
        }

        // 3. Parser comme nouvelle dépense
        handleNewExpense(user, text);
    }

    /**
     * Gère la réponse à un brouillon en attente (oui/non).
     */
    private void handleDraftResponse(User user, DraftExpense draft, String response) {
        if (messageParserService.isConfirmation(response)) {
            try {
                Expense expense = expenseService.confirmDraft(draft);
                Fund fund = fundService.findActiveFundForAgent(user.getId()).orElse(null);
                Long newBalance = fund != null ? fund.getBalance() : 0L;
                whatsAppService.sendExpenseConfirmed(
                        user.getPhoneNumber(), expense.getAmount(),
                        expense.getDescription(), newBalance);
            } catch (Exception e) {
                log.error("Erreur confirmation: {}", e.getMessage());
                whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
            }
        } else if (messageParserService.isCancellation(response)) {
            expenseService.cancelDraft(draft);
            whatsAppService.sendExpenseCancelled(user.getPhoneNumber());
        } else {
            // Ni oui ni non → rappeler qu'on attend une confirmation
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "⏳ Vous avez une dépense en attente. Répondez *oui* pour confirmer ou *non* pour annuler.");
        }
    }

    /**
     * Gère un nouveau message de dépense.
     */
    private void handleNewExpense(User user, String text) {
        ParsedExpense parsed = messageParserService.parse(text);

        if (!parsed.isParsed()) {
            whatsAppService.sendError(user.getPhoneNumber(),
                    "Je n'ai pas compris votre message. Exemples:\n• depense 25000 carburant\n• 150000 ciment chantier");
            return;
        }

        try {
            DraftExpense draft = expenseService.createDraft(user, parsed);
            whatsAppService.sendConfirmationRequest(
                    user.getPhoneNumber(), draft.getAmount(), draft.getDescription());
        } catch (Exception e) {
            log.error("Erreur création brouillon: {}", e.getMessage());
            whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
        }
    }
}
