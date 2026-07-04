package com.kolofinance.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    @Value("${whatsapp.provider:gowa}")
    private String provider;

    @Value("${whatsapp.gowa.base-url}")
    private String gowaBaseUrl;

    @Value("${whatsapp.gowa.username}")
    private String gowaUsername;

    @Value("${whatsapp.gowa.password}")
    private String gowaPassword;

    @Value("${whatsapp.gowa.device-id:}")
    private String gowaDeviceId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envoie un message texte via le provider WhatsApp configuré.
     */
    public void sendMessage(String to, String text) {
        if ("meta".equalsIgnoreCase(provider) || "cloud".equalsIgnoreCase(provider)) {
            sendCloudApiMessage(to, text);
            return;
        }

        sendGowaMessage(to, text);
    }

    public boolean sendImage(String to, String caption, byte[] imageBytes, String filename) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        if ("meta".equalsIgnoreCase(provider) || "cloud".equalsIgnoreCase(provider)) {
            return false;
        }
        return sendGowaImage(to, caption, imageBytes, filename);
    }

    public boolean sendFile(String to, String caption, byte[] fileBytes, String filename) {
        if (fileBytes == null || fileBytes.length == 0) {
            return false;
        }
        if ("meta".equalsIgnoreCase(provider) || "cloud".equalsIgnoreCase(provider)) {
            return false;
        }
        return sendGowaFile(to, caption, fileBytes, filename);
    }

    private boolean sendGowaImage(String to, String caption, byte[] imageBytes, String filename) {
        try {
            if (isBlank(gowaBaseUrl) || isBlank(gowaUsername) || isBlank(gowaPassword)) {
                log.error("Configuration GOWA incomplète: base-url, username ou password manquant");
                return false;
            }

            String url = gowaBaseUrl.replaceAll("/+$", "") + "/send/image";
            String phone = normalizeGowaRecipient(to);

            ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null && !filename.isBlank() ? filename : "rapport-kolo.png";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("phone", phone);
            body.add("caption", caption != null ? caption : "");
            body.add("compress", "true");
            body.add("image", imageResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBasicAuth(gowaUsername, gowaPassword);
            if (!isBlank(gowaDeviceId)) {
                headers.set("X-Device-Id", gowaDeviceId);
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Image WhatsApp envoyée via GOWA à {}", phone);
            return true;
        } catch (Exception e) {
            log.error("Erreur envoi image WhatsApp GOWA à {}: {}", to, e.getMessage());
            return false;
        }
    }

    private boolean sendGowaFile(String to, String caption, byte[] fileBytes, String filename) {
        try {
            if (isBlank(gowaBaseUrl) || isBlank(gowaUsername) || isBlank(gowaPassword)) {
                log.error("Configuration GOWA incomplète: base-url, username ou password manquant");
                return false;
            }

            String url = gowaBaseUrl.replaceAll("/+$", "") + "/send/file";
            String phone = normalizeGowaRecipient(to);

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename != null && !filename.isBlank() ? filename : "depenses-kolo.xlsx";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("phone", phone);
            body.add("caption", caption != null ? caption : "");
            body.add("file", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBasicAuth(gowaUsername, gowaPassword);
            if (!isBlank(gowaDeviceId)) {
                headers.set("X-Device-Id", gowaDeviceId);
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Fichier WhatsApp envoyé via GOWA à {}", phone);
            return true;
        } catch (Exception e) {
            log.error("Erreur envoi fichier WhatsApp GOWA à {}: {}", to, e.getMessage());
            return false;
        }
    }

    private void sendGowaMessage(String to, String text) {
        try {
            if (isBlank(gowaBaseUrl) || isBlank(gowaUsername) || isBlank(gowaPassword)) {
                log.error("Configuration GOWA incomplète: base-url, username ou password manquant");
                return;
            }

            String url = gowaBaseUrl.replaceAll("/+$", "") + "/send/message";
            String phone = normalizeGowaRecipient(to);

            Map<String, Object> body = Map.of(
                    "phone", phone,
                    "message", text
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(gowaUsername, gowaPassword);
            if (!isBlank(gowaDeviceId)) {
                headers.set("X-Device-Id", gowaDeviceId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.info("Message WhatsApp envoyé via GOWA à {}", phone);
        } catch (Exception e) {
            log.error("Erreur envoi WhatsApp GOWA à {}: {}", to, e.getMessage());
        }
    }

    private void sendCloudApiMessage(String to, String text) {
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

    private String normalizeGowaRecipient(String to) {
        if (to == null) {
            return "";
        }

        String trimmed = to.trim();
        if (trimmed.endsWith("@s.whatsapp.net") || trimmed.endsWith("@c.us") || trimmed.endsWith("@g.us")) {
            return trimmed;
        }

        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return trimmed;
        }

        return digits + "@s.whatsapp.net";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
