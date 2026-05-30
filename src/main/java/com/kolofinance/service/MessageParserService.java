package com.kolofinance.service;

import com.kolofinance.dto.ParsedExpense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageParserService {

    private final AiParsingService aiParsingService;

    // Pattern: optionnel "depense/dep/dépense" + montant + description
    private static final Pattern EXPENSE_PATTERN = Pattern.compile(
            "(?:d[eé]pense|dep)?\\s*(\\d[\\d\\s.]*)\\s+(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Mots-clés → catégories
    private static final Map<String, String> CATEGORY_KEYWORDS = Map.ofEntries(
            Map.entry("carburant", "CARBURANT"),
            Map.entry("essence", "CARBURANT"),
            Map.entry("gasoil", "CARBURANT"),
            Map.entry("transport", "TRANSPORT"),
            Map.entry("taxi", "TRANSPORT"),
            Map.entry("bus", "TRANSPORT"),
            Map.entry("ciment", "MATERIAUX"),
            Map.entry("sable", "MATERIAUX"),
            Map.entry("fer", "MATERIAUX"),
            Map.entry("brique", "MATERIAUX"),
            Map.entry("gravier", "MATERIAUX"),
            Map.entry("nourriture", "NOURRITURE"),
            Map.entry("repas", "NOURRITURE"),
            Map.entry("manger", "NOURRITURE"),
            Map.entry("pièce", "PIECES"),
            Map.entry("piece", "PIECES"),
            Map.entry("pièces", "PIECES"),
            Map.entry("pieces", "PIECES")
    );

    /**
     * Parse un message WhatsApp pour en extraire une dépense.
     * Étape 1: regex simple. Étape 2: fallback IA via OpenRouter.
     */
    public ParsedExpense parse(String message) {
        if (message == null || message.isBlank()) {
            return ParsedExpense.builder().parsed(false).build();
        }

        String cleaned = message.trim().toLowerCase();

        // Vérifier si c'est une confirmation/annulation
        if (isConfirmation(cleaned) || isCancellation(cleaned)) {
            return ParsedExpense.builder().parsed(false).build();
        }

        // Étape 1: Regex
        ParsedExpense regexResult = parseWithRegex(cleaned);
        if (regexResult.isParsed()) {
            log.debug("Parsing regex réussi: {} FCFA - {}", regexResult.getAmount(), regexResult.getDescription());
            return regexResult;
        }

        // Étape 2: IA fallback via OpenRouter
        log.debug("Regex échoué, tentative OpenRouter pour: '{}'", message);
        return aiParsingService.parseExpenseMessage(message);
    }

    public boolean isConfirmation(String message) {
        String lower = message.trim().toLowerCase();
        return lower.equals("oui") || lower.equals("ok") || lower.equals("yes")
                || lower.equals("confirmer") || lower.equals("c'est bon")
                || lower.equals("valider") || lower.equals("correct");
    }

    public boolean isCancellation(String message) {
        String lower = message.trim().toLowerCase();
        return lower.equals("non") || lower.equals("no") || lower.equals("annuler")
                || lower.equals("cancel") || lower.equals("supprimer");
    }

    private ParsedExpense parseWithRegex(String message) {
        Matcher matcher = EXPENSE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return ParsedExpense.builder().parsed(false).build();
        }

        String amountStr = matcher.group(1).replaceAll("[\\s.]", "");
        String description = matcher.group(2).trim();

        try {
            long amount = Long.parseLong(amountStr);
            if (amount <= 0) {
                return ParsedExpense.builder().parsed(false).build();
            }

            String category = detectCategory(description);

            return ParsedExpense.builder()
                    .amount(amount)
                    .description(description)
                    .category(category)
                    .parsed(true)
                    .build();
        } catch (NumberFormatException e) {
            return ParsedExpense.builder().parsed(false).build();
        }
    }

    private String detectCategory(String description) {
        String lower = description.toLowerCase();
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "DIVERS";
    }
}
