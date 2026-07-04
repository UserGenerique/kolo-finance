package com.kolofinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.dto.ParsedExpense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final String SYSTEM_PROMPT =
            "Tu es un assistant financier. Tu analyses des messages WhatsApp pour extraire des informations de dépense.\n"
                    + "Réponds UNIQUEMENT en JSON valide, sans markdown, sans explication.\n"
                    + "Format: {\"amount\": <montant entier>, \"description\": \"<description courte>\", \"category\": \"<CARBURANT|TRANSPORT|MATERIAUX|NOURRITURE|PIECES|DIVERS>\"}\n"
                    + "Si tu ne peux pas extraire un montant, réponds: {\"amount\": 0, \"description\": \"\", \"category\": \"\"}";

    private static final String SHOP_SALE_SYSTEM_PROMPT =
            "Tu es un assistant ERP boutique WhatsApp. Tu extrais une intention de vente depuis une phrase naturelle.\n"
                    + "Réponds UNIQUEMENT en JSON valide, sans markdown, sans explication.\n"
                    + "Format strict: {\"intent\":\"SALE|UNKNOWN\",\"items\":[{\"product\":\"nom produit\",\"quantity\":nombre entier}],"
                    + "\"product\":\"nom produit ou vide\",\"quantity\":nombre entier,"
                    + "\"customer\":\"nom ou téléphone client ou vide\",\"credit\":true/false,\"paidAmount\":montant entier,"
                    + "\"discount\":montant entier,\"dueDate\":\"YYYY-MM-DD ou vide\"}.\n"
                    + "Utilise les produits et clients fournis comme contexte. Si une info manque, mets une valeur vide/0.\n"
                    + "Pour une vente multi-produits, mets chaque ligne dans items. Garde aussi product/quantity avec le premier produit pour compatibilité.\n"
                    + "Exemples:\n"
                    + "- \"vente de 2 cocas a mounina barry elle a payé 100f\" => items=[{\"product\":\"coca\",\"quantity\":2}], product=\"coca\", quantity=2, customer=\"mounina barry\", paidAmount=100.\n"
                    + "- \"vente d'une coca à moussa remise 100\" => product=\"coca\", quantity=1, customer=\"moussa\", discount=100.\n"
                    + "- \"deux cocas à crédit pour awa payé 500\" => product=\"coca\", quantity=2, customer=\"awa\", credit=true, paidAmount=500.\n"
                    + "- \"vente de 2 cocas et 3 jus à mounina payé 1000\" => items=[{\"product\":\"coca\",\"quantity\":2},{\"product\":\"jus\",\"quantity\":3}], customer=\"mounina\", paidAmount=1000.\n"
                    + "Ne mets pas toute la phrase dans customer: customer doit contenir seulement le nom ou le numéro du client.";

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


    public Optional<ShopSaleAiResult> parseShopSaleMessage(String message, List<String> productNames, List<String> customerNames) {
        try {
            String context = "Produits connus: " + String.join(", ", productNames.stream().limit(80).toList())
                    + "\nClients connus: " + String.join(", ", customerNames.stream().limit(80).toList());
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SHOP_SALE_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", context + "\n\nMessage WhatsApp: " + message)
                    ),
                    "temperature", 0.1,
                    "max_tokens", 250
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            return parseShopSaleResponse(response.getBody());
        } catch (Exception e) {
            log.warn("Erreur OpenRouter vente boutique: {}", e.getMessage());
            return Optional.empty();
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

    private Optional<ShopSaleAiResult> parseShopSaleResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("choices").get(0)
                    .path("message").path("content").asText();
            text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode parsed = objectMapper.readTree(text);
            String intent = parsed.path("intent").asText("UNKNOWN");
            if (!"SALE".equalsIgnoreCase(intent)) {
                return Optional.empty();
            }
            List<ShopSaleAiItem> items = new ArrayList<>();
            JsonNode itemsNode = parsed.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    String itemProduct = itemNode.path("product").asText("");
                    long itemQuantity = itemNode.path("quantity").asLong(0);
                    if (!itemProduct.isBlank() && itemQuantity > 0) {
                        items.add(new ShopSaleAiItem(itemProduct, itemQuantity));
                    }
                }
            }
            String product = parsed.path("product").asText("");
            long quantity = parsed.path("quantity").asLong(0);
            if (items.isEmpty() && !product.isBlank() && quantity > 0) {
                items.add(new ShopSaleAiItem(product, quantity));
            }
            return Optional.of(new ShopSaleAiResult(
                    product,
                    quantity,
                    parsed.path("customer").asText(""),
                    parsed.path("credit").asBoolean(false),
                    parsed.path("paidAmount").asLong(0),
                    parsed.path("discount").asLong(0),
                    parsed.path("dueDate").asText(""),
                    items
            ));
        } catch (Exception e) {
            log.warn("Erreur parsing réponse vente boutique OpenRouter: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record ShopSaleAiResult(
            String product,
            Long quantity,
            String customer,
            Boolean credit,
            Long paidAmount,
            Long discount,
            String dueDate,
            List<ShopSaleAiItem> items
    ) {}

    public record ShopSaleAiItem(String product, Long quantity) {}
}
