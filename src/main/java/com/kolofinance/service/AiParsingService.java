package com.kolofinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.dto.ParsedExpense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiParsingService {

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.api-url}")
    private String apiUrl;

    @Value("${openrouter.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            Tu es un assistant financier. Tu analyses des messages WhatsApp pour extraire des informations de dépense.
            Réponds UNIQUEMENT en JSON valide, sans markdown, sans explication.
            Format: {"amount": <montant entier>, "description": "<description courte>", "category": "<CARBURANT|TRANSPORT|MATERIAUX|NOURRITURE|PIECES|DIVERS>"}
            Si tu ne peux pas extraire un montant, réponds: {"amount": 0, "description": "", "category": ""}
            """;

    /**
     * Parse un message ambigu via OpenRouter (format OpenAI-compatible).
     */
    public ParsedExpense parseExpenseMessage(String message) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", message)
                    ),
                    "temperature", 0.1,
                    "max_tokens", 200
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Erreur OpenRouter: {}", e.getMessage());
            return ParsedExpense.builder().parsed(false).build();
        }
    }

    private ParsedExpense parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("choices").get(0)
                    .path("message").path("content").asText();

            text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            JsonNode parsed = objectMapper.readTree(text);
            long amount = parsed.path("amount").asLong(0);

            if (amount <= 0) {
                return ParsedExpense.builder().parsed(false).build();
            }

            return ParsedExpense.builder()
                    .amount(amount)
                    .description(parsed.path("description").asText(""))
                    .category(parsed.path("category").asText("DIVERS"))
                    .parsed(true)
                    .build();
        } catch (Exception e) {
            log.error("Erreur parsing réponse OpenRouter: {}", e.getMessage());
            return ParsedExpense.builder().parsed(false).build();
        }
    }
}
