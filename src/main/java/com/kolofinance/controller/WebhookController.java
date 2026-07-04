package com.kolofinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.dto.GowaWebhookPayload;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.text.Normalizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final ReportService reportService;
    private final ReportCommandService reportCommandService;
    private final ShopWhatsAppService shopWhatsAppService;
    private final PlatformWhatsAppAdminService platformWhatsAppAdminService;
    private final ObjectMapper objectMapper;

    private static final Pattern RECEIPT_ID_PATTERN = Pattern.compile("#?(\\d+)");
    private static final Duration DUPLICATE_MESSAGE_WINDOW = Duration.ofSeconds(20);
    private static final Duration DUPLICATE_MESSAGE_RETENTION = Duration.ofMinutes(3);
    private final Map<String, Instant> recentMessageKeys = new ConcurrentHashMap<>();

    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.gowa.webhook-secret:}")
    private String gowaWebhookSecret;

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
                        processMessage(msg.getFrom(), msg.getText().getBody(), msg.getId());
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
     * POST /api/webhook/gowa — Réception des messages depuis GOWA WhatsApp Web.
     */
    @PostMapping("/gowa")
    public ResponseEntity<String> receiveGowa(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        try {
            if (!isValidGowaSignature(rawBody, signature)) {
                log.warn("Signature webhook GOWA invalide");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            GowaWebhookPayload payload = objectMapper.readValue(rawBody, GowaWebhookPayload.class);
            if (!"message".equalsIgnoreCase(payload.getEvent()) || payload.getPayload() == null) {
                return ResponseEntity.ok("OK");
            }

            GowaWebhookPayload.Payload message = payload.getPayload();
            if (message.isFromMe()) {
                return ResponseEntity.ok("OK");
            }

            String from = firstNonBlank(message.getFrom(), message.getChatId());
            if (from != null && from.endsWith("@g.us")) {
                log.info("Message de groupe GOWA ignoré: {}", from);
                return ResponseEntity.ok("OK");
            }

            String phoneNumber = normalizeIncomingPhone(from);
            String text = message.getBody();
            if (phoneNumber == null || phoneNumber.isBlank() || text == null || text.isBlank()) {
                log.debug("Webhook GOWA ignoré: phoneNumber={}, textPresent={}", phoneNumber, text != null && !text.isBlank());
                return ResponseEntity.ok("OK");
            }

            processMessage(phoneNumber, text, message.getId());
        } catch (Exception e) {
            log.error("Erreur traitement webhook GOWA: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * Logique principale : identifier l'utilisateur, gérer brouillon ou nouvelle dépense.
     */
    private void processMessage(String phoneNumber, String text, String messageId) {
        log.info("Message reçu de {}: '{}'", phoneNumber, text);
        if (isDuplicateMessage(phoneNumber, text, messageId)) {
            log.info("Message WhatsApp dupliqué ignoré pour {}", phoneNumber);
            return;
        }
        if (platformWhatsAppAdminService.handleMessage(phoneNumber, text)) {
            return;
        }

        // 1. Identifier l'utilisateur
        Optional<User> userOpt = userService.findByPhone(phoneNumber);
        if (userOpt.isEmpty()) {
            log.warn("Numéro inconnu: {}", phoneNumber);
            whatsAppService.sendError(phoneNumber,
                    "Votre numéro n'est pas autorisé sur Kolo Finance. Demandez à votre responsable de vous ajouter dans l'organisation avant d'envoyer des dépenses.");
            return;
        }

        User user = userOpt.get();
        if (userService.findActiveMembershipsForUser(user.getId()).isEmpty()) {
            whatsAppService.sendError(phoneNumber,
                    "Votre compte existe mais n'est actif dans aucune organisation. Contactez votre responsable.");
            return;
        }
        String textLower = text.trim().toLowerCase();

        // 2. Vérifier s'il y a un brouillon en attente
        Optional<DraftExpense> pendingDraft = expenseService.findPendingDraft(user.getId());

        // 3. Les commandes explicites doivent passer même si une dépense attend "oui/non".
        if (handlePendingReceiptCommand(user, textLower, false)) {
            return;
        }
        if (shopWhatsAppService.handleMessage(user, text)) {
            return;
        }
        // 4. Commandes d'aide et rapports à la demande
        if (reportCommandService.isHelpCommand(textLower)) {
            whatsAppService.sendMessage(user.getPhoneNumber(), reportCommandService.helpMessage());
            return;
        }

        Optional<com.kolofinance.dto.DashboardFilter> reportFilter =
                reportCommandService.parseReportCommand(user, text);
        if (reportFilter.isPresent()) {
            try {
                reportService.sendReportToRequester(user, reportFilter.get());
            } catch (Exception e) {
                log.error("Erreur génération rapport WhatsApp: {}", e.getMessage(), e);
                whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
            }
            return;
        }

        Optional<com.kolofinance.dto.DashboardFilter> expenseListFilter =
                reportCommandService.parseExpenseListCommand(user, text);
        if (expenseListFilter.isPresent()) {
            try {
                reportService.sendExpensesExportToRequester(user, expenseListFilter.get());
            } catch (Exception e) {
                log.error("Erreur export dépenses WhatsApp: {}", e.getMessage(), e);
                whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
            }
            return;
        }
        if (reportCommandService.isReportCommand(text)) {
            whatsAppService.sendError(user.getPhoneNumber(),
                    "Je n'ai pas compris la demande de rapport. Envoyez *rapport aide* pour voir les exemples.");
            return;
        }

        // 5. Réponse au brouillon uniquement si ce n'est pas une commande.
        if (pendingDraft.isPresent()) {
            handleDraftResponse(user, pendingDraft.get(), textLower);
            return;
        }

        // 6. Traiter les réponses simples de réception de fonds quand aucun brouillon n'est ouvert.
        if (handlePendingReceiptCommand(user, textLower, true)) {
            return;
        }

        // 7. Parser comme nouvelle dépense
        handleNewExpense(user, text);
    }

    /**
     * Gère la réponse à un brouillon en attente (oui/non).
     */
    private void handleDraftResponse(User user, DraftExpense draft, String response) {
        if (messageParserService.isConfirmation(response)) {
            try {
                Expense expense = expenseService.confirmDraft(draft);
                Long newBalance = expense.getFund() != null ? expense.getFund().getBalance() : 0L;
                whatsAppService.sendExpenseConfirmed(
                        user.getPhoneNumber(), expense.getAmount(),
                        expense.getDescription(), newBalance);
            } catch (Exception e) {
                log.error("Erreur confirmation: {}", e.getMessage());
                expenseService.cancelDraft(draft);
                whatsAppService.sendError(user.getPhoneNumber(), e.getMessage()
                        + "\n\nCette dépense a été annulée. Renvoyez une nouvelle dépense avec un montant correct.");
            }
        } else if (messageParserService.isCancellation(response)) {
            expenseService.cancelPendingDrafts(user.getId());
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
        OrganizationHint organizationHint = detectOrganizationHint(user, text);
        String expenseText = organizationHint != null ? organizationHint.remainingText() : text;
        ParsedExpense parsed = messageParserService.parse(expenseText);

        if (!parsed.isParsed()) {
            whatsAppService.sendError(user.getPhoneNumber(),
                    "Je n'ai pas compris votre message. Exemples:\n• depense 25000 carburant\n• 150000 ciment chantier");
            return;
        }

        try {
            DraftExpense draft = expenseService.createDraft(
                    user,
                    parsed,
                    organizationHint != null ? organizationHint.organizationId() : null
            );
            whatsAppService.sendConfirmationRequest(
                    user.getPhoneNumber(), draft.getAmount(), draft.getDescription());
        } catch (Exception e) {
            log.error("Erreur création brouillon: {}", e.getMessage());
            whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
        }
    }

    private boolean isValidGowaSignature(String rawBody, String signature) {
        if (gowaWebhookSecret == null || gowaWebhookSecret.isBlank()) {
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(gowaWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Erreur vérification signature GOWA: {}", e.getMessage());
            return false;
        }
    }

    private String normalizeIncomingPhone(String jid) {
        if (jid == null || jid.isBlank()) {
            return null;
        }

        String phone = jid.trim();
        int atIndex = phone.indexOf('@');
        if (atIndex >= 0) {
            phone = phone.substring(0, atIndex);
        }

        phone = phone.replaceAll("[^0-9]", "");
        return phone.isBlank() ? null : phone;
    }


    private boolean handlePendingReceiptCommand(User user, String textLower, boolean allowPlainYesNo) {
        boolean forcedFundCommand = startsWithAny(textLower, "fonds ", "fond ", "affectation ", "affectations ");
        boolean listCommand = isPendingReceiptListCommand(textLower);
        String actionText = forcedFundCommand ? stripFundCommandPrefix(textLower) : textLower;

        if (listCommand) {
            sendPendingReceiptsList(user);
            return true;
        }

        if (!allowPlainYesNo && !forcedFundCommand) {
            return false;
        }

        boolean accept = isReceiptAccept(actionText);
        boolean reject = isReceiptReject(actionText);
        if (!accept && !reject) {
            return false;
        }

        List<Fund> pendingReceipts = fundService.findPendingReceiptsForAgent(user.getId());
        if (pendingReceipts.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Vous n'avez aucune affectation de fonds en attente.");
            return true;
        }

        Long requestedId = extractReceiptId(actionText);
        Fund selected = null;
        if (requestedId != null) {
            selected = pendingReceipts.stream()
                    .filter(fund -> fund.getId().equals(requestedId))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                whatsAppService.sendError(user.getPhoneNumber(),
                        "Affectation introuvable. Envoyez *affectations* pour voir la liste.");
                return true;
            }
        } else if (pendingReceipts.size() == 1) {
            selected = pendingReceipts.get(0);
        } else {
            sendPendingReceiptsList(user);
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Répondez avec le numéro de l'affectation, par exemple *fonds oui "
                            + pendingReceipts.get(0).getId() + "* ou *fonds non "
                            + pendingReceipts.get(0).getId() + "*.");
            return true;
        }

        try {
            Fund updated = accept
                    ? fundService.acceptReceipt(selected.getOrganization().getId(), selected.getId(), user.getId(), "Confirmé via WhatsApp")
                    : fundService.rejectReceipt(selected.getOrganization().getId(), selected.getId(), user.getId(), "Rejeté via WhatsApp");
            String action = accept ? "confirmée" : "rejetée";
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "✅ Réception " + action + " pour *" + updated.getOrganization().getName()
                            + "* (" + formatAmount(updated.getInitialAmount()) + " FCFA).");
        } catch (Exception e) {
            log.error("Erreur validation réception fonds: {}", e.getMessage(), e);
            whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
        }
        return true;
    }

    private void sendPendingReceiptsList(User user) {
        List<Fund> pendingReceipts = fundService.findPendingReceiptsForAgent(user.getId());
        if (pendingReceipts.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Vous n'avez aucune affectation de fonds en attente.");
            return;
        }

        StringBuilder message = new StringBuilder("💰 *Affectations en attente*\n\n");
        for (Fund fund : pendingReceipts) {
            message.append("#").append(fund.getId())
                    .append(" — ").append(fund.getOrganization().getName())
                    .append(" — ").append(formatAmount(fund.getInitialAmount())).append(" FCFA");
            if (fund.getDescription() != null && !fund.getDescription().isBlank()) {
                message.append("\n").append(fund.getDescription());
            }
            message.append("\n\n");
        }
        message.append("Répondez *fonds oui #ID* pour accepter ou *fonds non #ID* pour rejeter.");
        whatsAppService.sendMessage(user.getPhoneNumber(), message.toString());
    }

    private boolean isDuplicateMessage(String phoneNumber, String text, String messageId) {
        Instant now = Instant.now();
        recentMessageKeys.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).compareTo(DUPLICATE_MESSAGE_RETENTION) > 0);

        String key;
        if (messageId != null && !messageId.isBlank()) {
            key = "id:" + messageId;
        } else {
            long bucket = now.getEpochSecond() / DUPLICATE_MESSAGE_WINDOW.toSeconds();
            key = "fallback:" + phoneNumber + ":" + normalizeCommandText(text) + ":" + bucket;
        }

        Instant previous = recentMessageKeys.get(key);
        if (previous != null && Duration.between(previous, now).compareTo(DUPLICATE_MESSAGE_WINDOW) < 0) {
            return true;
        }
        recentMessageKeys.put(key, now);
        return false;
    }

    private OrganizationHint detectOrganizationHint(User user, String text) {
        String normalizedText = normalizeCommandText(text);
        if (normalizedText.isBlank()) {
            return null;
        }

        Map<Long, String> activeOrganizations = new LinkedHashMap<>();
        fundService.findActiveSpendableFundsForAgent(user.getId()).forEach(fund ->
                activeOrganizations.putIfAbsent(fund.getOrganization().getId(), fund.getOrganization().getName())
        );

        for (Map.Entry<Long, String> entry : activeOrganizations.entrySet()) {
            String normalizedOrganization = normalizeCommandText(entry.getValue());
            if (normalizedOrganization.length() < 2) {
                continue;
            }
            if (normalizedText.startsWith(normalizedOrganization + " ")) {
                String remaining = normalizedText.substring(normalizedOrganization.length()).trim();
                if (!remaining.isBlank()) {
                    return new OrganizationHint(entry.getKey(), remaining);
                }
            }
        }
        return null;
    }

    private boolean isPendingReceiptListCommand(String textLower) {
        String normalized = normalizeCommandText(textLower);
        return normalized.equals("affectations")
                || normalized.equals("affectation")
                || normalized.equals("fonds")
                || normalized.equals("fond")
                || normalized.equals("fonds attente")
                || normalized.equals("affectations attente")
                || normalized.equals("affectations en attente");
    }

    private boolean isReceiptAccept(String response) {
        return messageParserService.isConfirmation(response)
                || startsWithAny(response, "oui ", "ok ", "yes ", "confirmer ", "valider ");
    }

    private boolean isReceiptReject(String response) {
        return messageParserService.isCancellation(response)
                || startsWithAny(response, "non ", "no ", "annuler ", "rejeter ");
    }

    private Long extractReceiptId(String response) {
        Matcher matcher = RECEIPT_ID_PATTERN.matcher(response);
        while (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stripFundCommandPrefix(String textLower) {
        return textLower
                .replaceFirst("^(fonds?|affectations?)\\s+", "")
                .trim();
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeCommandText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String formatAmount(Long amount) {
        return String.format(Locale.FRANCE, "%,d", amount);
    }
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private record OrganizationHint(Long organizationId, String remainingText) {}
}
