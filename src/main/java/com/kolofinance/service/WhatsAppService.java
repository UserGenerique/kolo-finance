package com.kolofinance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppService {

    @Value("${whatsapp.token}")
    private String token;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envoie un message texte via WhatsApp Cloud API.
     */
    public void sendMessage(String to, String text) {
        try {
            String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", Map.of("body", text)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.info("Message WhatsApp envoyé à {}", to);
        } catch (Exception e) {
            log.error("Erreur envoi WhatsApp à {}: {}", to, e.getMessage());
        }
    }

    /**
     * Envoie une demande de confirmation de dépense.
     */
    public void sendConfirmationRequest(String to, Long amount, String description) {
        String formatted = formatAmount(amount);
        String message = String.format(
                "📝 Confirmer dépense de *%s FCFA* pour *%s* ?\n\nRépondez *oui* pour confirmer ou *non* pour annuler.",
                formatted, description
        );
        sendMessage(to, message);
    }

    /**
     * Envoie une confirmation de dépense enregistrée.
     */
    public void sendExpenseConfirmed(String to, Long amount, String description, Long newBalance) {
        String formatted = formatAmount(amount);
        String balanceFormatted = formatAmount(newBalance);
        String message = String.format(
                "✅ Dépense confirmée : *%s FCFA* pour *%s*.\n\n💰 Solde restant : *%s FCFA*",
                formatted, description, balanceFormatted
        );
        sendMessage(to, message);
    }

    /**
     * Envoie un message d'annulation.
     */
    public void sendExpenseCancelled(String to) {
        sendMessage(to, "❌ Dépense annulée.");
    }

    /**
     * Envoie un message d'erreur.
     */
    public void sendError(String to, String error) {
        sendMessage(to, "⚠️ " + error);
    }

    private String formatAmount(Long amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        return nf.format(amount);
    }
}
