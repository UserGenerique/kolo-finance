package com.kolofinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kolofinance.model.OrganizationMembership;
import com.kolofinance.model.ShopConversationSession;
import com.kolofinance.model.ShopCustomer;
import com.kolofinance.model.ShopCustomerPayment;
import com.kolofinance.model.ShopProduct;
import com.kolofinance.model.ShopSale;
import com.kolofinance.model.ShopSaleItem;
import com.kolofinance.model.ShopSupplier;
import com.kolofinance.model.ShopSupplierPayment;
import com.kolofinance.model.ShopAcquisition;
import com.kolofinance.model.ShopAcquisitionItem;
import com.kolofinance.model.ShopExpense;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopWhatsAppService {

    private static final Pattern PRODUCT_VALUE_PATTERN = Pattern.compile("\\b(prix achat|achat|prix vente|vente|gros|prix gros|wholesale|stock|sotck|stok|stoc|stck|qte|quantite|min|minimum)\\s+(\\d[\\d\\s.]*)");
    private static final Pattern SELECTION_PATTERN = Pattern.compile("(\\d{1,3})(?:\\s*[xX*]\\s*(\\d{1,4}))?");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d[\\d\\s.]*)");
    private static final Pattern DUE_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
    private static final String QUANTITY_WORDS_PATTERN = "un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|onze|douze|treize|quatorze|quinze|seize|vingt";
    private static final Pattern QUANTITY_TOKEN_PATTERN = Pattern.compile("\\b(\\d[\\d\\s.]*|" + QUANTITY_WORDS_PATTERN + ")\\b");
    private static final Map<String, Long> FRENCH_QUANTITY_WORDS = Map.ofEntries(
            Map.entry("un", 1L),
            Map.entry("une", 1L),
            Map.entry("deux", 2L),
            Map.entry("trois", 3L),
            Map.entry("quatre", 4L),
            Map.entry("cinq", 5L),
            Map.entry("six", 6L),
            Map.entry("sept", 7L),
            Map.entry("huit", 8L),
            Map.entry("neuf", 9L),
            Map.entry("dix", 10L),
            Map.entry("onze", 11L),
            Map.entry("douze", 12L),
            Map.entry("treize", 13L),
            Map.entry("quatorze", 14L),
            Map.entry("quinze", 15L),
            Map.entry("seize", 16L),
            Map.entry("vingt", 20L)
    );
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE);

    private final UserService userService;
    private final WhatsAppService whatsAppService;
    private final ShopProductService productService;
    private final ShopCustomerService customerService;
    private final ShopSaleService saleService;
    private final ShopSupplierService supplierService;
    private final ShopAcquisitionService acquisitionService;
    private final ShopExpenseService shopExpenseService;
    private final ShopConversationService conversationService;
    private final AiParsingService aiParsingService;
    private final ObjectMapper objectMapper;
    private final NumberFormat amountFormat = NumberFormat.getNumberInstance(Locale.FRANCE);

    public boolean handleMessage(User user, String text) {
        String normalized = normalize(text);
        Optional<ShopConversationSession> activeSale = conversationService.findActiveSaleSession(user.getId());
        if (activeSale.isPresent()) {
            handleSaleSession(user, activeSale.get(), normalized);
            return true;
        }

        boolean naturalSaleCandidate = looksLikeNaturalSale(normalized);
        if (!isShopCommand(normalized) && !naturalSaleCandidate) {
            return false;
        }

        OrganizationMembership membership = resolveMembership(user);
        Long orgId = membership.getOrganization().getId();
        Role role = membership.getRole();

        try {
            if (normalized.equals("menu") || normalized.equals("menu boutique") || normalized.equals("boutique")) {
                whatsAppService.sendMessage(user.getPhoneNumber(), menu(role));
                return true;
            }
            if (normalized.equals("produits") || normalized.equals("catalogue")) {
                sendCatalog(user, orgId);
                return true;
            }
            if (normalized.equals("clients")) {
                sendCustomers(user, orgId);
                return true;
            }
            if (normalized.startsWith("client ") || normalized.startsWith("ajouter client ") || normalized.startsWith("nouveau client ")) {
                handleCustomerUpsert(user, orgId, normalized);
                return true;
            }
            if (normalized.equals("dettes clients") || normalized.equals("dette clients") || normalized.equals("credits clients") || normalized.equals("credit clients")) {
                sendCustomerDebts(user, orgId);
                return true;
            }
            if (normalized.startsWith("dette ") || normalized.startsWith("credit client ")) {
                handleCustomerDebt(user, orgId, normalized);
                return true;
            }
            if (isCustomerPaymentCommand(normalized)) {
                handleCustomerPayment(user, orgId, normalized);
                return true;
            }
            if (normalized.startsWith("produit ") || normalized.startsWith("ajouter produit ") || normalized.startsWith("nouveau produit ")) {
                requireManager(role);
                handleProductUpsert(user, orgId, normalized);
                return true;
            }
            if (normalized.startsWith("annuler vente ") || normalized.startsWith("annulation vente ")) {
                requireManager(role);
                handleCancelSale(user, orgId, normalized);
                return true;
            }
            if (tryHandleNaturalSale(user, membership, text, normalized)) {
                return true;
            }
            if (isCreditSaleCommand(normalized)) {
                startSale(user, membership, true);
                return true;
            }
            if (normalized.equals("vente") || normalized.equals("vendre") || normalized.equals("vente rapide") || normalized.equals("vendre rapide")) {
                startSale(user, membership, false);
                return true;
            }
            if (normalized.equals("stock") || normalized.startsWith("stock ")) {
                handleStock(user, orgId, normalized, role);
                return true;
            }
            if (normalized.equals("fournisseurs") || normalized.equals("liste fournisseurs")) {
                requireManager(role);
                sendSuppliers(user, orgId);
                return true;
            }
            if (normalized.startsWith("fournisseur ") || normalized.startsWith("ajouter fournisseur ") || normalized.startsWith("nouveau fournisseur ")) {
                requireManager(role);
                handleSupplierUpsert(user, orgId, normalized);
                return true;
            }
            if (normalized.equals("dettes fournisseurs") || normalized.equals("dette fournisseurs") || normalized.equals("credits fournisseurs")) {
                requireManager(role);
                sendSupplierDebts(user, orgId);
                return true;
            }
            if (normalized.startsWith("dette fournisseur ")) {
                requireManager(role);
                handleSupplierDebt(user, orgId, normalized);
                return true;
            }
            if (isSupplierPaymentCommand(normalized)) {
                requireManager(role);
                handleSupplierPayment(user, orgId, normalized);
                return true;
            }
            if (normalized.startsWith("appro ") || normalized.startsWith("approvisionnement ")) {
                requireManager(role);
                handleAcquisition(user, membership, normalized);
                return true;
            }
            if (normalized.startsWith("annuler appro ") || normalized.startsWith("annulation appro ")) {
                requireManager(role);
                handleCancelAcquisition(user, orgId, normalized);
                return true;
            }
            if (normalized.startsWith("depense boutique ") || normalized.startsWith("depense ") && !normalized.startsWith("depenses")) {
                handleShopExpense(user, orgId, normalized);
                return true;
            }
            if (normalized.equals("depenses boutique") || normalized.equals("depenses aujourd hui")
                    || normalized.equals("depenses") || normalized.startsWith("depenses ")) {
                sendShopExpenses(user, membership, normalized);
                return true;
            }
            if (normalized.equals("caisse") || normalized.startsWith("caisse ") || normalized.equals("caisse aujourd hui")) {
                requireManager(role);
                sendCashRegister(user, membership, normalized);
                return true;
            }
            if (normalized.equals("rapport boutique") || normalized.startsWith("rapport boutique ") || normalized.equals("ventes") || normalized.startsWith("ventes ")) {
                sendSalesReport(user, membership, normalized);
                return true;
            }
        } catch (Exception e) {
            log.error("Erreur commande boutique WhatsApp: {}", e.getMessage(), e);
            whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
            return true;
        }

        return false;
    }

    private void handleCustomerUpsert(User user, Long orgId, String normalized) {
        String command = normalized
                .replaceFirst("^ajouter client\\s+", "")
                .replaceFirst("^nouveau client\\s+", "")
                .replaceFirst("^client\\s+", "")
                .trim();
        CustomerInput input = parseCustomerInput(command);
        if (input.name.isBlank() || isReservedCustomerName(input.name)) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Format client:\n*client Awa +22176223344*\n\nLe numéro avec indicatif pays est obligatoire.");
            return;
        }
        if (input.phoneNumber == null || !customerService.isInternationalPhone(input.phoneNumber)) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Le numéro du client est obligatoire avec l’indicatif pays.\n"
                            + "Exemples: *client Awa +22176223344*, *client Moussa +22376223344*.");
            return;
        }
        ShopCustomer customer = customerService.upsertCustomer(orgId, input.name, input.phoneNumber, input.creditLimit);
        whatsAppService.sendMessage(user.getPhoneNumber(), customerText(customer));
    }

    private boolean tryHandleNaturalSale(User user, OrganizationMembership membership, String rawText, String normalized) {
        if (!looksLikeNaturalSale(normalized)) {
            return false;
        }

        Long orgId = membership.getOrganization().getId();
        List<ShopProduct> products = productService.listActive(orgId);
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Aucun produit boutique n’est encore configuré. Ajoutez d’abord un produit, ex: *produit Bazin achat 500 vente 1000 stock 20*.");
            return true;
        }
        List<ShopCustomer> customers = customerService.listActive(orgId);

        boolean credit = normalized.contains("credit")
                || normalized.contains("dette")
                || normalized.contains("a payer")
                || normalized.contains("reste a payer")
                || normalized.contains("doit payer")
                || normalized.contains("doit regler");
        boolean wholesale = normalized.contains("en gros")
                || normalized.contains("prix gros")
                || normalized.contains("gros");
        Long paidAmount = extractKeywordAmount(normalized,
                "paye", "payer", "pye", "verse", "donne", "regle", "recu", "encaisse", "acompte", "avance");
        Long discount = extractKeywordAmount(normalized, "remise", "reduction");
        LocalDate dueDate = extractDueDate(normalized);

        Optional<AiParsingService.ShopSaleAiResult> aiResult = aiParsingService.parseShopSaleMessage(
                rawText,
                products.stream().map(ShopProduct::getName).toList(),
                customers.stream().map(ShopCustomer::getName).toList()
        );
        NaturalItemsResult itemsResult = resolveNaturalSaleItems(products, normalized, aiResult);
        if (!isBlank(itemsResult.ambiguousText())) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "J’ai trouvé plusieurs produits possibles: " + itemsResult.ambiguousText()
                            + ". Envoyez le nom exact du produit ou utilisez *vente* pour choisir dans la liste.");
            return true;
        }
        if (itemsResult.items().isEmpty()) {
            if (normalized.startsWith("vente") || normalized.contains("vendu") || normalized.contains("vendre")) {
                whatsAppService.sendMessage(user.getPhoneNumber(),
                        "Je n’ai pas retrouvé les produits dans le catalogue. Exemple accepté: *vente de 2 cocas et 3 jus à Mounina payé 1000*.");
                return true;
            }
            return false;
        }
        if (!isBlank(itemsResult.missingQuantityText())) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "J’ai reconnu " + itemsResult.missingQuantityText()
                            + ", mais pas la quantité. Exemple: *vente de 2 cocas et 3 jus*.");
            return true;
        }

        if (aiResult.isPresent()) {
            AiParsingService.ShopSaleAiResult ai = aiResult.get();
            credit = credit || Boolean.TRUE.equals(ai.credit());
            if ((paidAmount == null || paidAmount == 0) && ai.paidAmount() != null && ai.paidAmount() > 0) {
                paidAmount = ai.paidAmount();
            }
            if ((discount == null || discount == 0) && ai.discount() != null && ai.discount() > 0) {
                discount = ai.discount();
            }
            if (dueDate == null && ai.dueDate() != null && !ai.dueDate().isBlank()) {
                dueDate = parseAiDueDate(ai.dueDate());
            }
        }

        String customerQuery = findKnownCustomerInMessage(customers, normalized);
        if (isBlank(customerQuery)) {
            customerQuery = cleanCustomerCandidate(aiResult.map(AiParsingService.ShopSaleAiResult::customer).orElse(""));
        }
        if (isBlank(customerQuery)) {
            customerQuery = extractCustomerQuery(normalized, productsForItems(products, itemsResult.items()));
        } else {
            customerQuery = cleanCustomerCandidate(customerQuery);
        }

        boolean customerMentioned = !isBlank(customerQuery);
        if (customerMentioned) {
            wholesale = true;
        }

        SaleDraft draft = new SaleDraft();
        draft.items.putAll(itemsResult.items());
        draft.discount = discount == null ? 0L : discount;
        draft.wholesale = wholesale;
        draft.paidAmount = paidAmount == null ? 0L : paidAmount;
        if (dueDate != null) {
            draft.dueDate = dueDate.toString();
        }

        long total = draftTotal(draft, products);
        boolean paymentMentioned = paidAmount != null;
        if (paymentMentioned && paidAmount > total) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Le montant payé (" + amount(paidAmount) + " F) dépasse le total de la vente ("
                            + amount(total) + " F). Vérifiez la phrase ou corrigez le montant payé.");
            return true;
        }
        if (paymentMentioned && paidAmount < total) {
            credit = true;
        }
        if (customerMentioned && !paymentMentioned && !credit) {
            draft.paidAmount = total;
        }
        if (customerMentioned) {
            credit = true;
        }
        draft.credit = credit;
        draft.step = credit ? "ASK_CUSTOMER" : "SELECTING";

        if (!credit) {
            ShopConversationSession session = conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "J’ai compris cette vente. Vérifiez avant validation:\n\n"
                            + cartText(draft, products, false)
                            + "\n\nRépondez *ok* pour enregistrer ou *annuler*.");
            return true;
        }

        ShopCustomer customer = null;
        if (!isBlank(customerQuery)) {
            try {
                customer = customerService.resolveCustomer(orgId, customerQuery);
            } catch (Exception e) {
                CustomerInput input = parseCustomerInput(customerQuery);
                if (!input.name.isBlank() && !isReservedCustomerName(input.name)
                        && input.phoneNumber != null && customerService.isInternationalPhone(input.phoneNumber)) {
                    draft.pendingCustomerName = input.name;
                    draft.pendingCustomerPhone = input.phoneNumber;
                    draft.pendingCustomerCreditLimit = input.creditLimit;
                    draft.step = "CONFIRM_NEW_CUSTOMER";
                    conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
                    whatsAppService.sendMessage(user.getPhoneNumber(),
                            "J’ai compris la vente, mais le client est nouveau.\n\n"
                                    + cartText(draft, products, false)
                                    + "\n\nCréer le client *" + input.name + "* avec le numéro *" + input.phoneNumber + "* ? Répondez *oui* ou *non*.");
                    return true;
                }
                draft.step = "ASK_CUSTOMER";
                conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
                whatsAppService.sendMessage(user.getPhoneNumber(),
                        "J’ai compris la vente à crédit, mais je n’ai pas pu identifier le client.\n\n"
                                + cartText(draft, products, false)
                                + "\n\nEnvoyez le nom d’un client existant ou créez-le avec indicatif: *client Moussa +22376223344*.");
                return true;
            }
        }

        if (customer == null) {
            draft.step = "ASK_CUSTOMER";
            conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "J’ai compris la vente à crédit. Il manque le client.\n\n"
                            + cartText(draft, products, false)
                            + "\n\nEnvoyez le nom ou numéro d’un client existant, ou *client Moussa +22376223344*.");
            return true;
        }

        draft.customerId = customer.getId();
        draft.step = "CONFIRM_CREDIT";
        ShopConversationSession session = conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
        long due = Math.max(0, draftTotal(draft, products) - (draft.paidAmount == null ? 0 : draft.paidAmount));
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "J’ai compris cette " + (due > 0 ? "vente à crédit" : "vente client") + ". Vérifiez avant validation:\n\n"
                        + creditConfirmationText(session, draft, products)
                        + "\n\nRépondez *ok* pour enregistrer, *paye 5000* pour corriger l’acompte, ou *annuler*.");
        return true;
    }

    private void sendCustomers(User user, Long orgId) {
        List<ShopCustomer> customers = customerService.listActive(orgId);
        if (customers.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun client boutique configuré. Exemple: *client Awa +22176223344*.");
            return;
        }
        StringBuilder sb = new StringBuilder("👥 *Clients boutique*\n\n");
        int i = 1;
        for (ShopCustomer customer : customers.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(customer.getName());
            if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isBlank()) {
                sb.append(" — ").append(customer.getPhoneNumber());
            }
            if (nullToZero(customer.getOutstandingBalance()) > 0) {
                sb.append(" — dette ").append(amount(customer.getOutstandingBalance())).append(" F");
                java.time.LocalDateTime lastPay = customerService.lastPaymentDate(customer.getId());
                if (lastPay != null) {
                    sb.append(" — dern. paiement ").append(lastPay.format(SHORT_DATE));
                }
            }
            sb.append("\n");
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendCustomerDebts(User user, Long orgId) {
        List<ShopCustomer> debtors = customerService.listDebtors(orgId);
        if (debtors.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucune dette client ouverte.");
            return;
        }
        long total = debtors.stream().mapToLong(customer -> nullToZero(customer.getOutstandingBalance())).sum();
        StringBuilder sb = new StringBuilder("📒 *Dettes clients*\n\n");
        sb.append("Total dû: *").append(amount(total)).append(" FCFA*\n\n");
        int i = 1;
        for (ShopCustomer customer : debtors.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(customer.getName())
                    .append(" — ").append(amount(customer.getOutstandingBalance())).append(" F");
            if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isBlank()) {
                sb.append(" — ").append(customer.getPhoneNumber());
            }
            java.time.LocalDateTime lastPay = customerService.lastPaymentDate(customer.getId());
            if (lastPay != null) {
                sb.append(" — dern. paiement ").append(lastPay.format(SHORT_DATE));
            }
            sb.append("\n");
        }
        sb.append("\nPour encaisser: *paiement Awa 5000*.");
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleCustomerDebt(User user, Long orgId, String normalized) {
        String query = normalized
                .replaceFirst("^credit client\\s+", "")
                .replaceFirst("^dette\\s+", "")
                .trim();
        ShopCustomer customer = customerService.resolveCustomer(orgId, query);
        whatsAppService.sendMessage(user.getPhoneNumber(), customerDebtText(customer));
    }

    private void handleCustomerPayment(User user, Long orgId, String normalized) {
        PaymentInput input = parsePaymentInput(normalized);
        ShopCustomerPayment payment = customerService.recordPayment(orgId, user, input.customerQuery, input.amount, "Paiement client WhatsApp");
        ShopCustomer customer = payment.getCustomer();
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "✅ Paiement enregistré\n"
                        + customer.getName() + "\n"
                        + "Montant: " + amount(payment.getAmount()) + " F\n"
                        + "Reste dû: *" + amount(customer.getOutstandingBalance()) + " FCFA*");
    }

    private void handleCreditCustomerStep(User user, ShopConversationSession session, SaleDraft draft, String normalized, List<ShopProduct> products) {
        String query = normalized.replaceFirst("^client\\s+", "").trim();
        if (isOk(query) || isReservedCustomerName(query)) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Envoyez le *nom du client* ou son numéro, pas seulement *oui*.\n"
                            + "Exemples: *Awa*, *+22176223344* ou *client Awa +22176223344*.");
            return;
        }
        ShopCustomer customer;
        try {
            customer = customerService.resolveCustomer(session.getOrganization().getId(), query);
        } catch (Exception notFound) {
            CustomerInput input = parseCustomerInput(query);
            if (input.name.isBlank() || isReservedCustomerName(input.name)) {
                whatsAppService.sendMessage(user.getPhoneNumber(), "Client introuvable. Envoyez un nom clair avec indicatif, ex: *Awa +22176223344*.");
                return;
            }
            if (input.phoneNumber == null || !customerService.isInternationalPhone(input.phoneNumber)) {
                whatsAppService.sendMessage(user.getPhoneNumber(),
                        "Client introuvable. Pour créer un nouveau client, ajoutez son numéro avec indicatif pays.\n"
                                + "Exemple: *Awa +22176223344*.");
                return;
            }
            draft.pendingCustomerName = input.name;
            draft.pendingCustomerPhone = input.phoneNumber;
            draft.pendingCustomerCreditLimit = input.creditLimit;
            draft.step = "CONFIRM_NEW_CUSTOMER";
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Client introuvable: *" + input.name + "*.\n"
                            + (input.phoneNumber != null ? "Téléphone: " + input.phoneNumber + "\n" : "")
                            + "\nCréer ce client ? Répondez *oui* pour créer, *non* pour saisir un autre client.");
            return;
        }

        draft.customerId = customer.getId();
        draft.step = "ASK_PAID";
        saveDraft(session, draft);
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "Client: *" + customer.getName() + "*\n\n"
                        + cartText(draft, products)
                        + "\n\nMontant payé maintenant ? Exemple: *0* ou *5000*.\n"
                        + "Échéance optionnelle ensuite: *echeance 30/06*.");
    }

    private void handleNewCustomerConfirmationStep(User user, ShopConversationSession session, SaleDraft draft, String normalized, List<ShopProduct> products) {
        if (isCancel(normalized)) {
            draft.pendingCustomerName = null;
            draft.pendingCustomerPhone = null;
            draft.pendingCustomerCreditLimit = null;
            draft.step = "ASK_CUSTOMER";
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "D’accord, client non créé. Envoyez le nom ou numéro d’un client existant.");
            return;
        }
        if (!isOk(normalized)) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Répondez *oui* pour créer le client *" + draft.pendingCustomerName + "* ou *non* pour saisir un autre client.");
            return;
        }
        ShopCustomer customer = customerService.upsertCustomer(
                session.getOrganization().getId(),
                draft.pendingCustomerName,
                draft.pendingCustomerPhone,
                draft.pendingCustomerCreditLimit
        );
        draft.customerId = customer.getId();
        draft.pendingCustomerName = null;
        draft.pendingCustomerPhone = null;
        draft.pendingCustomerCreditLimit = null;
        draft.step = "ASK_PAID";
        saveDraft(session, draft);
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "✅ Client créé: *" + customer.getName() + "*\n\n"
                        + cartText(draft, products)
                        + "\n\nMontant payé maintenant ? Exemple: *0* ou *5000*.");
    }

    private void handleCreditPaidStep(User user, ShopConversationSession session, SaleDraft draft, String normalized, List<ShopProduct> products) {
        LocalDate dueDate = extractDueDate(normalized);
        if (dueDate != null) {
            draft.dueDate = dueDate.toString();
        }
        if (normalized.startsWith("echeance") && !containsStandaloneAmountBeforeDate(normalized)) {
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(), "Échéance notée. Montant payé maintenant ? Exemple: *0* ou *5000*.");
            return;
        }

        long total = draftTotal(draft, products);
        long paid = parseFirstAmount(normalized);
        if (paid > total) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "L’acompte dépasse le total (" + amount(total) + " F). Envoyez un montant inférieur ou égal.");
            return;
        }
        draft.paidAmount = paid;
        draft.step = "CONFIRM_CREDIT";
        saveDraft(session, draft);
        whatsAppService.sendMessage(user.getPhoneNumber(), creditConfirmationText(session, draft, products)
                + "\n\nRépondez *ok* pour enregistrer, *paye 5000* pour modifier l’acompte, *echeance 30/06* ou *annuler*.");
    }

    private void handleCreditConfirmationStep(User user, ShopConversationSession session, SaleDraft draft, String normalized, List<ShopProduct> products) {
        if (normalized.startsWith("echeance")) {
            LocalDate dueDate = extractDueDate(normalized);
            if (dueDate == null) {
                whatsAppService.sendMessage(user.getPhoneNumber(), "Format échéance: *echeance 30/06*.");
                return;
            }
            draft.dueDate = dueDate.toString();
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(), creditConfirmationText(session, draft, products)
                    + "\n\nRépondez *ok* pour enregistrer.");
            return;
        }
        if (normalized.startsWith("paye") || normalized.startsWith("acompte") || normalized.startsWith("avance")) {
            long total = draftTotal(draft, products);
            long paid = parseFirstAmount(normalized);
            if (paid > total) {
                whatsAppService.sendMessage(user.getPhoneNumber(), "L’acompte dépasse le total (" + amount(total) + " F).");
                return;
            }
            draft.paidAmount = paid;
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(), creditConfirmationText(session, draft, products)
                    + "\n\nRépondez *ok* pour enregistrer.");
            return;
        }
        if (!isOk(normalized)) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Répondez *ok* pour enregistrer, *paye 5000*, *echeance 30/06* ou *annuler*.");
            return;
        }

        ShopSale sale = saleService.confirmCreditSale(
                session.getOrganization().getId(),
                user,
                draft.customerId,
                draft.items,
                draft.discount,
                draft.paidAmount == null ? 0 : draft.paidAmount,
                draft.dueDate == null ? null : LocalDate.parse(draft.dueDate),
                Boolean.TRUE.equals(draft.wholesale)
        );
        conversationService.close(session);
        whatsAppService.sendMessage(user.getPhoneNumber(), saleConfirmedText(sale));
    }

    private void startSale(User user, OrganizationMembership membership, boolean creditSale) {
        List<ShopProduct> products = productService.listActive(membership.getOrganization().getId());
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Aucun produit boutique n’est encore configuré. Le propriétaire peut ajouter un produit avec: *produit Coca achat 350 vente 500 stock 24*.");
            return;
        }
        SaleDraft draft = new SaleDraft();
        draft.credit = creditSale;
        draft.step = "SELECTING";
        conversationService.startSaleSession(membership.getOrganization(), user, writeDraft(draft));
        whatsAppService.sendMessage(user.getPhoneNumber(), "🛒 *" + (creditSale ? "Nouvelle vente à crédit" : "Nouvelle vente") + "*\n\n" + catalogText(products)
                + "\nRépondez avec les numéros et quantités, ex: *1x2 4*.\n"
                + (creditSale
                ? "Ensuite envoyez *ok*, puis le client et l’acompte.\n"
                : "")
                + "Commandes: *ok*, *remise 500*, *retirer 1*, *modifier*, *annuler*.");
    }

    private void handleSaleSession(User user, ShopConversationSession session, String normalized) {
        Long orgId = session.getOrganization().getId();
        List<ShopProduct> products = productService.listActive(orgId);
        SaleDraft draft = readDraft(session.getPayload());

        if (isCancel(normalized) && !(Boolean.TRUE.equals(draft.credit) && "CONFIRM_NEW_CUSTOMER".equals(draft.step) && normalized.equals("non"))) {
            conversationService.close(session);
            whatsAppService.sendMessage(user.getPhoneNumber(), "Vente annulée.");
            return;
        }

        try {
            if (Boolean.TRUE.equals(draft.credit) && "CONFIRM_NEW_CUSTOMER".equals(draft.step)) {
                handleNewCustomerConfirmationStep(user, session, draft, normalized, products);
                return;
            }
            if (Boolean.TRUE.equals(draft.credit) && "ASK_CUSTOMER".equals(draft.step)) {
                handleCreditCustomerStep(user, session, draft, normalized, products);
                return;
            }
            if (Boolean.TRUE.equals(draft.credit) && "ASK_PAID".equals(draft.step)) {
                handleCreditPaidStep(user, session, draft, normalized, products);
                return;
            }
            if (Boolean.TRUE.equals(draft.credit) && "CONFIRM_CREDIT".equals(draft.step)) {
                handleCreditConfirmationStep(user, session, draft, normalized, products);
                return;
            }
            if (normalized.equals("modifier")) {
                draft.items.clear();
                draft.discount = 0L;
                draft.step = "SELECTING";
                draft.customerId = null;
                draft.paidAmount = null;
                saveDraft(session, draft);
                whatsAppService.sendMessage(user.getPhoneNumber(), "Panier vidé.\n\n" + catalogText(products)
                        + "\nRépondez par exemple: *1x2 4*.");
                return;
            }
            if (normalized.startsWith("retirer ")) {
                removeItem(draft, products, normalized);
                saveDraft(session, draft);
                whatsAppService.sendMessage(user.getPhoneNumber(), cartText(draft, products));
                return;
            }
            if (normalized.startsWith("remise ")) {
                draft.discount = parseAmount(normalized.replaceFirst("^remise\\s+", ""));
                saveDraft(session, draft);
                whatsAppService.sendMessage(user.getPhoneNumber(), cartText(draft, products));
                return;
            }
            if (isOk(normalized)) {
                if (draft.items.isEmpty()) {
                    whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun produit dans le panier. Exemple: *1x2 4* ou *annuler*.");
                    return;
                }
                if (Boolean.TRUE.equals(draft.credit)) {
                    draft.step = "ASK_CUSTOMER";
                    saveDraft(session, draft);
                    whatsAppService.sendMessage(user.getPhoneNumber(), cartText(draft, products)
                            + "\n\n👤 Client à crédit ?\nEnvoyez le nom ou le numéro d’un client existant.\n"
                            + "Pour un nouveau client: *client Awa +22176223344*.");
                    return;
                }
                ShopSale sale = saleService.confirmQuickSale(orgId, user, draft.items, draft.discount, Boolean.TRUE.equals(draft.wholesale));
                conversationService.close(session);
                whatsAppService.sendMessage(user.getPhoneNumber(), saleConfirmedText(sale));
                return;
            }

            Map<Long, Long> selected = parseSelection(products, normalized);
            if (selected.isEmpty()) {
                whatsAppService.sendMessage(user.getPhoneNumber(), "Je n’ai pas compris la sélection. Exemple: *1x2 4* ou *annuler*.");
                return;
            }
            selected.forEach((productId, quantity) -> draft.items.merge(productId, quantity, Long::sum));
            draft.step = "SELECTING";
            saveDraft(session, draft);
            whatsAppService.sendMessage(user.getPhoneNumber(), cartText(draft, products));
        } catch (Exception e) {
            log.error("Erreur session vente boutique: {}", e.getMessage(), e);
            whatsAppService.sendError(user.getPhoneNumber(), e.getMessage());
        }
    }

    private void handleProductUpsert(User user, Long orgId, String normalized) {
        String command = normalized
                .replaceFirst("^ajouter produit\\s+", "")
                .replaceFirst("^nouveau produit\\s+", "")
                .replaceFirst("^produit\\s+", "")
                .trim();
        Map<String, Long> values = values(command);
        Long salePrice = values.get("vente");
        Long stock = values.get("stock");
        Long purchase = values.getOrDefault("achat", 0L);
        Long wholesale = values.get("gros");
        Long min = values.getOrDefault("min", 0L);
        int firstKeyword = firstKeywordIndex(command);
        String name = firstKeyword >= 0 ? command.substring(0, firstKeyword).trim() : command;

        if (name.isBlank() || salePrice == null || stock == null) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Format produit:\n*produit Coca achat 350 vente 500 gros 400 stock 24*\n\n"
                            + "*gros* = prix de gros (optionnel). `achat` est conseillé pour calculer le bénéfice.");
            return;
        }

        ShopProduct product = productService.upsertProduct(orgId, name, purchase, salePrice, wholesale, stock, min);
        StringBuilder sb = new StringBuilder("✅ Produit enregistré\n")
                .append(product.getName()).append("\n")
                .append("Achat: ").append(amount(product.getPurchasePrice())).append(" F\n")
                .append("Vente: ").append(amount(product.getSalePrice())).append(" F\n");
        if (product.getWholesalePrice() != null && product.getWholesalePrice() > 0) {
            sb.append("Gros: ").append(amount(product.getWholesalePrice())).append(" F\n");
        }
        sb.append("Stock: ").append(product.getStockQuantity());
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleStock(User user, Long orgId, String normalized, Role role) {
        List<ShopProduct> products = productService.listActive(orgId);
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun produit configuré.");
            return;
        }

        String query = normalized.replaceFirst("^stock\\s*", "").trim();
        if (!query.isBlank()) {
            String normalizedQuery = productService.normalize(query);
            products = products.stream()
                    .filter(product -> product.getNormalizedName().contains(normalizedQuery)
                            || (product.getAliases() != null && productService.normalize(product.getAliases()).contains(normalizedQuery)))
                    .collect(Collectors.toList());
        }
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun produit trouvé pour: " + query);
            return;
        }

        StringBuilder sb = new StringBuilder("📦 *Stock boutique*\n\n");
        int i = 1;
        for (ShopProduct product : products.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(product.getName())
                    .append(" — ").append(product.getStockQuantity()).append(" ")
                    .append(product.getUnit() == null ? "" : product.getUnit())
                    .append(" — ").append(amount(product.getSalePrice())).append(" F");
            if (product.getWholesalePrice() != null && product.getWholesalePrice() > 0) {
                sb.append(" (gros ").append(amount(product.getWholesalePrice())).append(" F)");
            }
            if (role != Role.AGENT) {
                sb.append(" (achat ").append(amount(product.getPurchasePrice())).append(" F)");
            }
            if (product.getMinStockQuantity() != null && product.getStockQuantity() <= product.getMinStockQuantity()) {
                sb.append(" ⚠️");
            }
            sb.append("\n");
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendSalesReport(User user, OrganizationMembership membership, String normalized) {
        LocalDate today = LocalDate.now();
        LocalDate start = today;
        LocalDate end = today;
        if (normalized.contains("semaine")) {
            start = today.minusDays(6);
        } else if (normalized.contains("mois")) {
            start = today.withDayOfMonth(1);
        } else if (normalized.contains("hier")) {
            start = today.minusDays(1);
            end = start;
        }

        Long sellerId = membership.getRole() == Role.AGENT ? user.getId() : null;
        List<ShopSale> sales = saleService.salesForPeriod(membership.getOrganization().getId(), sellerId, start, end);
        long total = sales.stream().mapToLong(sale -> sale.getTotalAmount() == null ? 0 : sale.getTotalAmount()).sum();
        long profit = sales.stream().mapToLong(sale -> sale.getProfitAmount() == null ? 0 : sale.getProfitAmount()).sum();
        long discounts = sales.stream().mapToLong(sale -> sale.getDiscountAmount() == null ? 0 : sale.getDiscountAmount()).sum();

        StringBuilder sb = new StringBuilder("📊 *Rapport Boutique*\n");
        sb.append("Période: ").append(start.equals(end) ? start.format(SHORT_DATE) : start.format(SHORT_DATE) + " → " + end.format(SHORT_DATE)).append("\n\n");
        sb.append("• Ventes: *").append(amount(total)).append(" FCFA*\n");
        sb.append("• Nombre ventes: ").append(sales.size()).append("\n");
        if (membership.getRole() != Role.AGENT) {
            sb.append("• Bénéfice estimé: *").append(amount(profit)).append(" FCFA*\n");
            sb.append("• Remises: ").append(amount(discounts)).append(" FCFA\n");
        }
        if (sales.isEmpty()) {
            sb.append("\nAucune vente sur la période.");
        } else {
            sb.append("\n🧾 Dernières ventes\n");
            sales.stream().limit(5).forEach(sale ->
                    sb.append("• #").append(sale.getId()).append(" — ")
                            .append(amount(sale.getTotalAmount())).append(" F — ")
                            .append(sale.getSeller() != null ? sale.getSeller().getName() : "—")
                            .append("\n"));
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendCatalog(User user, Long orgId) {
        List<ShopProduct> products = productService.listActive(orgId);
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun produit configuré.");
            return;
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), "🛍️ *Catalogue boutique*\n\n" + catalogText(products));
    }

    private String catalogText(List<ShopProduct> products) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (ShopProduct product : products.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(product.getName());
            if (product.getWholesalePrice() != null && product.getWholesalePrice() > 0) {
                sb.append(" — Détail: ").append(amount(product.getSalePrice())).append(" F")
                        .append(" | Gros: ").append(amount(product.getWholesalePrice())).append(" F");
            } else {
                sb.append(" — ").append(amount(product.getSalePrice())).append(" F");
            }
            sb.append(" — stock ").append(product.getStockQuantity()).append("\n");
        }
        sb.append("\n_Vente à un client = prix gros auto._");
        return sb.toString();
    }

    private String cartText(SaleDraft draft, List<ShopProduct> products) {
        return cartText(draft, products, true);
    }

    private String cartText(SaleDraft draft, List<ShopProduct> products, boolean includeActions) {
        if (draft.items.isEmpty()) {
            return "Panier vide. Répondez par exemple: *1x2 4*.";
        }
        Map<Long, ShopProduct> byId = products.stream().collect(Collectors.toMap(ShopProduct::getId, p -> p));
        StringBuilder sb = new StringBuilder("🧾 *Panier vente*\n\n");
        long subtotal = 0;
        boolean wholesale = Boolean.TRUE.equals(draft.wholesale);
        for (Map.Entry<Long, Long> entry : draft.items.entrySet()) {
            ShopProduct product = byId.get(entry.getKey());
            if (product == null) continue;
            long unitPrice = wholesale && product.getWholesalePrice() != null && product.getWholesalePrice() > 0
                    ? product.getWholesalePrice() : product.getSalePrice();
            long line = unitPrice * entry.getValue();
            subtotal += line;
            sb.append("• ").append(entry.getValue()).append(" x ").append(product.getName())
                    .append(" = ").append(amount(line)).append(" F\n");
        }
        long discount = Math.max(0, Math.min(draft.discount == null ? 0 : draft.discount, subtotal));
        sb.append("\nSous-total: ").append(amount(subtotal)).append(" F");
        if (discount > 0) {
            sb.append("\nRemise: -").append(amount(discount)).append(" F");
        }
        sb.append("\nTotal: *").append(amount(subtotal - discount)).append(" FCFA*");
        if (includeActions) {
            sb.append("\n\nRépondez *ok* pour continuer, *remise 500*, *retirer 1*, *modifier* ou *annuler*.");
        }
        return sb.toString();
    }

    private String saleConfirmedText(ShopSale sale) {
        List<ShopSaleItem> items = saleService.items(sale.getId());
        StringBuilder sb = new StringBuilder("✅ *Vente enregistrée*\n");
        sb.append("Vente #").append(sale.getId()).append("\n\n");
        items.forEach(item -> sb.append("• ").append(item.getQuantity()).append(" x ")
                .append(item.getProductName()).append(" = ")
                .append(amount(item.getLineTotal())).append(" F\n"));
        if (sale.getDiscountAmount() != null && sale.getDiscountAmount() > 0) {
            sb.append("Remise: -").append(amount(sale.getDiscountAmount())).append(" F\n");
        }
        sb.append("\nTotal: *").append(amount(sale.getTotalAmount())).append(" FCFA*");
        if ("CREDIT".equals(sale.getSaleType())) {
            sb.append("\nPayé: ").append(amount(sale.getPaidAmount())).append(" F");
            sb.append("\nReste dû: *").append(amount(sale.getDueAmount())).append(" FCFA*");
            if (sale.getCustomer() != null) {
                sb.append("\nClient: ").append(sale.getCustomer().getName());
            }
            if (sale.getDueDate() != null) {
                sb.append("\nÉchéance: ").append(sale.getDueDate().format(SHORT_DATE));
            }
        }
        return sb.toString();
    }

    private Map<Long, Long> parseSelection(List<ShopProduct> products, String normalized) {
        Map<Long, Long> selected = new LinkedHashMap<>();
        Matcher matcher = SELECTION_PATTERN.matcher(normalized);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > products.size()) {
                throw new RuntimeException("Produit numéro " + index + " introuvable dans le catalogue.");
            }
            long quantity = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 1L;
            if (quantity <= 0) {
                throw new RuntimeException("Quantité invalide pour produit " + index + ".");
            }
            selected.merge(products.get(index - 1).getId(), quantity, Long::sum);
        }
        return selected;
    }

    private void removeItem(SaleDraft draft, List<ShopProduct> products, String normalized) {
        Matcher matcher = Pattern.compile("retirer\\s+(\\d{1,3})").matcher(normalized);
        if (!matcher.find()) {
            throw new RuntimeException("Exemple: *retirer 1*");
        }
        int index = Integer.parseInt(matcher.group(1));
        if (index < 1 || index > products.size()) {
            throw new RuntimeException("Produit numéro " + index + " introuvable.");
        }
        draft.items.remove(products.get(index - 1).getId());
    }

    private OrganizationMembership resolveMembership(User user) {
        List<OrganizationMembership> memberships = userService.findActiveMembershipsForUser(user.getId());
        if (memberships.isEmpty()) {
            throw new RuntimeException("Votre compte n'est actif dans aucune organisation.");
        }
        if (memberships.size() > 1) {
            throw new RuntimeException("Votre numéro est lié à plusieurs organisations. Pour Kolo Boutique MVP, utilisez un numéro lié à une seule boutique.");
        }
        return memberships.get(0);
    }

    private void handleShopExpense(User user, Long orgId, String normalized) {
        String command = normalized.replaceFirst("^depense\\s+(?:boutique\\s+)?", "").trim();
        Matcher matcher = AMOUNT_PATTERN.matcher(command);
        if (!matcher.find()) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Format: *depense 5000 transport marchandise*\nou *depense boutique 3000 loyer*.");
            return;
        }
        long expenseAmount = parseAmount(matcher.group(1));
        String description = command.substring(matcher.end()).trim();
        if (description.isBlank()) {
            description = command.substring(0, matcher.start()).trim();
        }
        if (description.isBlank()) {
            description = "Dépense boutique";
        }
        ShopExpense expense = shopExpenseService.recordExpense(orgId, user, expenseAmount, description, "DIVERS");
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "✅ Dépense enregistrée\n"
                        + "Montant: *" + amount(expense.getAmount()) + " FCFA*\n"
                        + "Description: " + expense.getDescription() + "\n"
                        + "Par: " + user.getName());
    }

    private void sendShopExpenses(User user, OrganizationMembership membership, String normalized) {
        LocalDate today = LocalDate.now();
        LocalDate start = today;
        LocalDate end = today;
        if (normalized.contains("semaine")) {
            start = today.minusDays(6);
        } else if (normalized.contains("mois")) {
            start = today.withDayOfMonth(1);
        } else if (normalized.contains("hier")) {
            start = today.minusDays(1);
            end = start;
        }
        List<ShopExpense> expenses = shopExpenseService.expensesForPeriod(membership.getOrganization().getId(), start, end);
        if (expenses.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucune dépense boutique sur la période.");
            return;
        }
        long total = expenses.stream().mapToLong(e -> e.getAmount() == null ? 0 : e.getAmount()).sum();
        StringBuilder sb = new StringBuilder("💸 *Dépenses boutique*\n");
        sb.append("Période: ").append(start.equals(end) ? start.format(SHORT_DATE) : start.format(SHORT_DATE) + " → " + end.format(SHORT_DATE));
        sb.append("\nTotal: *").append(amount(total)).append(" FCFA*\n\n");
        expenses.stream().limit(15).forEach(e ->
                sb.append("• ").append(amount(e.getAmount())).append(" F — ")
                        .append(e.getDescription())
                        .append(" — ").append(e.getRecordedBy().getName())
                        .append(" — ").append(e.getConfirmedAt().format(SHORT_DATE))
                        .append("\n"));
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendCashRegister(User user, OrganizationMembership membership, String normalized) {
        LocalDate today = LocalDate.now();
        LocalDate start = today;
        LocalDate end = today;
        if (normalized.contains("semaine")) {
            start = today.minusDays(6);
        } else if (normalized.contains("mois")) {
            start = today.withDayOfMonth(1);
        } else if (normalized.contains("hier")) {
            start = today.minusDays(1);
            end = start;
        }
        ShopExpenseService.CashRegister cr = shopExpenseService.cashRegister(membership.getOrganization().getId(), start, end);
        String period = start.equals(end) ? start.format(SHORT_DATE) : start.format(SHORT_DATE) + " → " + end.format(SHORT_DATE);
        StringBuilder sb = new StringBuilder("💰 *Caisse " + period + "*\n\n");
        sb.append("*Entrées:*\n")
                .append("• Ventes encaissées: ").append(amount(cr.salesIncome())).append(" F (").append(cr.salesCount()).append(" ventes)\n")
                .append("• Paiements clients: ").append(amount(cr.customerPayments())).append(" F\n")
                .append("Total entrées: *").append(amount(cr.totalIncome())).append(" F*\n\n");
        sb.append("*Sorties:*\n")
                .append("• Dépenses boutique: ").append(amount(cr.expenses())).append(" F (").append(cr.expensesCount()).append(")\n")
                .append("Total sorties: *").append(amount(cr.totalExpenses())).append(" F*\n\n");
        sb.append("📊 *Solde caisse: ").append(amount(cr.balance())).append(" FCFA*");
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendSuppliers(User user, Long orgId) {
        List<ShopSupplier> suppliers = supplierService.listActive(orgId);
        if (suppliers.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun fournisseur configuré. Exemple: *fournisseur Alassane +22376001122*.");
            return;
        }
        StringBuilder sb = new StringBuilder("📦 *Fournisseurs*\n\n");
        int i = 1;
        for (ShopSupplier supplier : suppliers.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(supplier.getName());
            if (supplier.getPhoneNumber() != null && !supplier.getPhoneNumber().isBlank()) {
                sb.append(" — ").append(supplier.getPhoneNumber());
            }
            if (supplier.getOutstandingBalance() != null && supplier.getOutstandingBalance() > 0) {
                sb.append(" — dette ").append(amount(supplier.getOutstandingBalance())).append(" F");
                java.time.LocalDateTime lastPay = supplierService.lastPaymentDate(supplier.getId());
                if (lastPay != null) {
                    sb.append(" — dern. paiement ").append(lastPay.format(SHORT_DATE));
                }
            }
            sb.append("\n");
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleSupplierUpsert(User user, Long orgId, String normalized) {
        String command = normalized
                .replaceFirst("^ajouter fournisseur\\s+", "")
                .replaceFirst("^nouveau fournisseur\\s+", "")
                .replaceFirst("^fournisseur\\s+", "")
                .trim();
        CustomerInput input = parseCustomerInput(command);
        if (input.name.isBlank()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Format: *fournisseur Alassane +22376001122*");
            return;
        }
        ShopSupplier supplier = supplierService.upsertSupplier(orgId, input.name, input.phoneNumber);
        StringBuilder sb = new StringBuilder("✅ Fournisseur enregistré\n").append(supplier.getName());
        if (supplier.getPhoneNumber() != null && !supplier.getPhoneNumber().isBlank()) {
            sb.append("\nTéléphone: ").append(supplier.getPhoneNumber());
        }
        sb.append("\nDette: ").append(amount(supplier.getOutstandingBalance())).append(" F");
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void sendSupplierDebts(User user, Long orgId) {
        List<ShopSupplier> debtors = supplierService.listDebtors(orgId);
        if (debtors.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucune dette fournisseur ouverte.");
            return;
        }
        long total = debtors.stream().mapToLong(s -> s.getOutstandingBalance() == null ? 0 : s.getOutstandingBalance()).sum();
        StringBuilder sb = new StringBuilder("📒 *Dettes fournisseurs*\n\nTotal dû: *").append(amount(total)).append(" FCFA*\n\n");
        int i = 1;
        for (ShopSupplier supplier : debtors.stream().limit(30).toList()) {
            sb.append(i++).append(". ").append(supplier.getName())
                    .append(" — ").append(amount(supplier.getOutstandingBalance())).append(" F");
            java.time.LocalDateTime lastPay = supplierService.lastPaymentDate(supplier.getId());
            if (lastPay != null) {
                sb.append(" — dern. paiement ").append(lastPay.format(SHORT_DATE));
            }
            sb.append("\n");
        }
        sb.append("\nPour payer: *paiement fournisseur Alassane 50000*.");
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleSupplierDebt(User user, Long orgId, String normalized) {
        String query = normalized.replaceFirst("^dette fournisseur\\s+", "").trim();
        ShopSupplier supplier = supplierService.resolveSupplier(orgId, query);
        StringBuilder sb = new StringBuilder("📒 *Dette fournisseur*\n").append(supplier.getName());
        if (supplier.getPhoneNumber() != null && !supplier.getPhoneNumber().isBlank()) {
            sb.append("\nTéléphone: ").append(supplier.getPhoneNumber());
        }
        sb.append("\nReste dû: *").append(amount(supplier.getOutstandingBalance())).append(" FCFA*");
        java.time.LocalDateTime lastPay = supplierService.lastPaymentDate(supplier.getId());
        if (lastPay != null) {
            sb.append("\nDernier paiement: ").append(lastPay.format(SHORT_DATE));
        }
        if (supplier.getOutstandingBalance() != null && supplier.getOutstandingBalance() > 0) {
            sb.append("\n\nPayer: *paiement fournisseur ").append(supplier.getName()).append(" 50000*");
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleSupplierPayment(User user, Long orgId, String normalized) {
        String cleaned = normalized.replaceFirst("^paiement fournisseur\\s+", "").trim();
        Matcher matcher = AMOUNT_PATTERN.matcher(cleaned);
        Long paymentAmount = null;
        int amountStart = -1;
        int amountEnd = -1;
        while (matcher.find()) {
            paymentAmount = parseAmount(matcher.group(1));
            amountStart = matcher.start();
            amountEnd = matcher.end();
        }
        if (paymentAmount == null) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Format: *paiement fournisseur Alassane 50000*.");
            return;
        }
        String query = (cleaned.substring(0, amountStart) + " " + cleaned.substring(amountEnd)).trim().replaceAll("\\s+", " ");
        ShopSupplierPayment payment = supplierService.recordPayment(orgId, user, query, paymentAmount, "Paiement fournisseur WhatsApp");
        ShopSupplier supplier = payment.getSupplier();
        whatsAppService.sendMessage(user.getPhoneNumber(),
                "✅ Paiement fournisseur enregistré\n"
                        + supplier.getName() + "\n"
                        + "Montant: " + amount(payment.getAmount()) + " F\n"
                        + "Reste dû: *" + amount(supplier.getOutstandingBalance()) + " FCFA*");
    }

    private void handleAcquisition(User user, OrganizationMembership membership, String normalized) {
        Long orgId = membership.getOrganization().getId();
        List<ShopProduct> products = productService.listActive(orgId);
        if (products.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Aucun produit configuré. Ajoutez d'abord un produit.");
            return;
        }

        String command = normalized.replaceFirst("^appro(?:visionnement)?\\s+", "").trim();

        // Extract supplier
        String supplierQuery = null;
        Matcher supplierMatcher = Pattern.compile("\\bfournisseur\\s+(.+?)(?:\\s+achat|\\s+paye|\\s+a\\s+credit|\\s+credit|$)").matcher(command);
        if (supplierMatcher.find()) {
            supplierQuery = supplierMatcher.group(1).trim();
        }
        ShopSupplier supplier = null;
        if (supplierQuery != null && !supplierQuery.isBlank()) {
            supplier = supplierService.resolveSupplier(orgId, supplierQuery);
        }

        // Extract paid amount
        Long paidAmount = extractKeywordAmount(normalized, "paye", "payer", "verse", "donne");
        boolean credit = normalized.contains("credit");

        // Extract unit cost (single product simplified)
        Long unitCost = extractKeywordAmount(normalized, "achat", "cout", "prix");

        // Extract products by full name match only (not individual tokens)
        Map<Long, Long> items = new LinkedHashMap<>();
        Map<Long, Long> unitCosts = new LinkedHashMap<>();
        List<ShopProduct> fullNameMatches = products.stream()
                .filter(p -> {
                    String fullName = normalize(p.getNormalizedName() == null ? p.getName() : p.getNormalizedName());
                    return fullName.length() >= 3 && command.contains(fullName);
                })
                .sorted((a, b) -> Integer.compare(
                        normalize(b.getNormalizedName() == null ? b.getName() : b.getNormalizedName()).length(),
                        normalize(a.getNormalizedName() == null ? a.getName() : a.getNormalizedName()).length()
                ))
                .toList();
        // Remove products whose name is a substring of a longer matched product name
        java.util.Set<String> coveredNames = new java.util.HashSet<>();
        for (ShopProduct product : fullNameMatches) {
            String pName = normalize(product.getNormalizedName() == null ? product.getName() : product.getNormalizedName());
            boolean alreadyCovered = coveredNames.stream().anyMatch(longer -> longer.contains(pName) && !longer.equals(pName));
            if (alreadyCovered) continue;
            coveredNames.add(pName);
            Long quantity = extractQuantity(normalized, product, null);
            if (quantity != null && quantity > 0) {
                items.put(product.getId(), quantity);
                if (unitCost != null && unitCost > 0) {
                    unitCosts.put(product.getId(), unitCost);
                }
            }
        }

        if (items.isEmpty()) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Format appro:\n*appro 10 hollantex fournisseur Alassane achat 8500 payé 50000*\n"
                            + "*appro 20 beldam fournisseur Alassane achat 3800 à crédit*");
            return;
        }

        long totalAmount = 0;
        for (Map.Entry<Long, Long> entry : items.entrySet()) {
            long cost = unitCosts.getOrDefault(entry.getKey(), 0L);
            if (cost <= 0) {
                ShopProduct p = products.stream().filter(pr -> pr.getId().equals(entry.getKey())).findFirst().orElse(null);
                cost = p != null && p.getPurchasePrice() != null && p.getPurchasePrice() > 0 ? p.getPurchasePrice() : 0;
                if (cost > 0) unitCosts.put(entry.getKey(), cost);
            }
            if (cost <= 0) {
                ShopProduct p = products.stream().filter(pr -> pr.getId().equals(entry.getKey())).findFirst().orElse(null);
                whatsAppService.sendMessage(user.getPhoneNumber(),
                        "Prix d'achat manquant pour " + (p != null ? p.getName() : "produit") + ". Précisez: *achat 8500*.");
                return;
            }
            totalAmount += entry.getValue() * cost;
        }

        long safePaid = paidAmount != null ? paidAmount : (credit ? 0 : totalAmount);

        ShopAcquisition acquisition = acquisitionService.confirmAcquisition(
                orgId, user, supplier, items, unitCosts, safePaid, null);
        List<ShopAcquisitionItem> acqItems = acquisitionService.items(acquisition.getId());

        StringBuilder sb = new StringBuilder("✅ *Approvisionnement #" + acquisition.getId() + " enregistré*\n\n");
        acqItems.forEach(item -> sb.append("• ").append(item.getQuantity()).append(" x ")
                .append(item.getProductName()).append(" à ").append(amount(item.getUnitCost()))
                .append(" F = ").append(amount(item.getLineTotal())).append(" F\n"));
        sb.append("\nTotal: *").append(amount(acquisition.getTotalAmount())).append(" FCFA*");
        sb.append("\nPayé: ").append(amount(acquisition.getPaidAmount())).append(" F");
        if (acquisition.getDueAmount() > 0) {
            sb.append("\nReste dû: *").append(amount(acquisition.getDueAmount())).append(" FCFA*");
            if (supplier != null) {
                sb.append("\nFournisseur: ").append(supplier.getName())
                        .append(" — dette totale: ").append(amount(supplier.getOutstandingBalance())).append(" F");
            }
        }
        sb.append("\n\n✏️ Stock mis à jour, coût moyen recalculé.");
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private void handleCancelAcquisition(User user, Long orgId, String normalized) {
        Matcher matcher = Pattern.compile("#?(\\d+)").matcher(normalized);
        if (!matcher.find()) {
            whatsAppService.sendMessage(user.getPhoneNumber(), "Format: *annuler appro #12*.");
            return;
        }
        Long acqId = Long.parseLong(matcher.group(1));
        ShopAcquisition acq = acquisitionService.cancelAcquisition(orgId, acqId, user);
        List<ShopAcquisitionItem> acqItems = acquisitionService.items(acqId);
        StringBuilder sb = new StringBuilder("🔄 *Appro #" + acqId + " annulé*\n\n");
        acqItems.forEach(item -> sb.append("• ").append(item.getQuantity()).append(" x ")
                .append(item.getProductName()).append(" → stock réduit\n"));
        sb.append("\nTotal annulé: *").append(amount(acq.getTotalAmount())).append(" FCFA*");
        if (acq.getSupplier() != null && acq.getDueAmount() != null && acq.getDueAmount() > 0) {
            sb.append("\nDette fournisseur réduite: ").append(amount(acq.getDueAmount())).append(" F");
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private boolean isSupplierPaymentCommand(String normalized) {
        return normalized.startsWith("paiement fournisseur ");
    }

    private void handleCancelSale(User user, Long orgId, String normalized) {
        Matcher matcher = Pattern.compile("#?(\\d+)").matcher(normalized);
        if (!matcher.find()) {
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "Format: *annuler vente #8* ou *annuler vente 8*.");
            return;
        }
        Long saleId = Long.parseLong(matcher.group(1));
        ShopSale sale = saleService.cancelSale(orgId, saleId, user);
        List<ShopSaleItem> items = saleService.items(saleId);
        StringBuilder sb = new StringBuilder("🔄 *Vente #" + saleId + " annulée*\n\n");
        items.forEach(item -> sb.append("• ").append(item.getQuantity()).append(" x ")
                .append(item.getProductName()).append(" → stock restauré\n"));
        sb.append("\nTotal annulé: *").append(amount(sale.getTotalAmount())).append(" FCFA*");
        if (sale.getCustomer() != null && nullToZero(sale.getDueAmount()) > 0) {
            sb.append("\nDette annulée: ").append(amount(sale.getDueAmount())).append(" F pour ").append(sale.getCustomer().getName());
        }
        whatsAppService.sendMessage(user.getPhoneNumber(), sb.toString());
    }

    private boolean isShopCommand(String normalized) {
        return normalized.equals("menu")
                || normalized.equals("boutique")
                || normalized.equals("menu boutique")
                || normalized.equals("produits")
                || normalized.equals("catalogue")
                || normalized.equals("clients")
                || normalized.startsWith("client ")
                || normalized.startsWith("ajouter client ")
                || normalized.startsWith("nouveau client ")
                || normalized.equals("dettes clients")
                || normalized.equals("dette clients")
                || normalized.equals("credits clients")
                || normalized.equals("credit clients")
                || normalized.startsWith("dette ")
                || normalized.startsWith("credit client ")
                || isCustomerPaymentCommand(normalized)
                || normalized.startsWith("produit ")
                || normalized.startsWith("ajouter produit ")
                || normalized.startsWith("nouveau produit ")
                || normalized.equals("vente")
                || normalized.equals("vendre")
                || normalized.startsWith("vente ")
                || normalized.equals("stock")
                || normalized.startsWith("stock ")
                || normalized.equals("credit")
                || normalized.startsWith("credit ")
                || normalized.equals("vente credit")
                || normalized.equals("vente a credit")
                || normalized.startsWith("vente a credit ")
                || normalized.equals("appro")
                || normalized.startsWith("appro ")
                || normalized.startsWith("approvisionnement ")
                || normalized.equals("fournisseurs")
                || normalized.equals("liste fournisseurs")
                || normalized.startsWith("fournisseur ")
                || normalized.startsWith("ajouter fournisseur ")
                || normalized.startsWith("nouveau fournisseur ")
                || normalized.equals("dettes fournisseurs")
                || normalized.equals("dette fournisseurs")
                || normalized.equals("credits fournisseurs")
                || normalized.startsWith("dette fournisseur ")
                || normalized.startsWith("paiement fournisseur ")
                || normalized.startsWith("annuler appro ")
                || normalized.startsWith("annulation appro ")
                || normalized.startsWith("depense ")
                || normalized.equals("depenses")
                || normalized.equals("depenses boutique")
                || normalized.startsWith("depenses ")
                || normalized.equals("caisse")
                || normalized.startsWith("caisse ")
                || normalized.equals("rapport boutique")
                || normalized.startsWith("rapport boutique ")
                || normalized.equals("ventes")
                || normalized.startsWith("ventes ")
                || normalized.startsWith("annuler vente ")
                || normalized.startsWith("annulation vente ");
    }

    private String menu(Role role) {
        StringBuilder sb = new StringBuilder("🏪 *Kolo Boutique*\n\n");
        sb.append("Commandes vendeur:\n")
                .append("• *vente* — faire une vente guidée\n")
                .append("• *vente credit* — vente à crédit client\n")
                .append("• *stock* — voir le stock\n")
                .append("• *clients* — liste des clients\n")
                .append("• *paiement Awa 5000* — encaisser un client\n")
                .append("• *ventes aujourd’hui* — mes ventes\n");
        if (role != Role.AGENT) {
            sb.append("\nCommandes propriétaire:\n")
                    .append("• *produit Coca achat 350 vente 500 stock 24*\n")
                    .append("• *client Awa +22176223344*\n")
                    .append("• *dettes clients*\n")
                    .append("• *produits* — catalogue\n")
                .append("• *rapport boutique* — ventes du jour\n")
                .append("• *annuler vente #8* — annuler une vente\n")
                .append("\nDépenses & Caisse:\n")
                .append("• *depense 5000 transport* — dépense\n")
                .append("• *depenses* — dépenses du jour\n")
                .append("• *caisse* — solde caisse\n")
                .append("\nApprovisionnement:\n")
                .append("• *fournisseur Alassane +22376001122*\n")
                .append("• *fournisseurs* — liste\n")
                .append("• *appro 10 hollantex fournisseur Alassane achat 8500 payé 50000*\n")
                .append("• *dettes fournisseurs*\n")
                .append("• *paiement fournisseur Alassane 50000*\n")
                .append("• *annuler appro #12*\n");
        }
        return sb.toString();
    }

    private void requireManager(Role role) {
        if (role == Role.AGENT) {
            throw new RuntimeException("Action réservée au propriétaire ou manager.");
        }
    }

    private boolean isCancel(String normalized) {
        return normalized.equals("annuler") || normalized.equals("non") || normalized.equals("cancel");
    }

    private boolean isOk(String normalized) {
        return normalized.equals("ok") || normalized.equals("oui") || normalized.equals("valider") || normalized.equals("confirmer");
    }

    private boolean isReservedCustomerName(String value) {
        String normalized = normalize(value);
        return normalized.equals("oui")
                || normalized.equals("non")
                || normalized.equals("ok")
                || normalized.equals("yes")
                || normalized.equals("no")
                || normalized.equals("annuler")
                || normalized.equals("confirmer")
                || normalized.equals("valider");
    }

    private SaleDraft readDraft(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                return new SaleDraft();
            }
            SaleDraft draft = objectMapper.readValue(payload, SaleDraft.class);
            if (draft.items == null) draft.items = new LinkedHashMap<>();
            if (draft.discount == null) draft.discount = 0L;
            if (draft.credit == null) draft.credit = false;
            if (draft.step == null || draft.step.isBlank()) draft.step = "SELECTING";
            return draft;
        } catch (Exception e) {
            return new SaleDraft();
        }
    }

    private String writeDraft(SaleDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            return "{\"items\":{},\"discount\":0,\"credit\":false,\"step\":\"SELECTING\"}";
        }
    }

    private void saveDraft(ShopConversationSession session, SaleDraft draft) {
        try {
            session.setPayload(objectMapper.writeValueAsString(draft));
            conversationService.save(session);
        } catch (Exception e) {
            throw new RuntimeException("Impossible d'enregistrer le panier.");
        }
    }

    private Map<String, Long> values(String text) {
        Map<String, Long> values = new LinkedHashMap<>();
        Matcher matcher = PRODUCT_VALUE_PATTERN.matcher(text);
        while (matcher.find()) {
            values.put(productValueKey(matcher.group(1)), parseAmount(matcher.group(2)));
        }
        return values;
    }

    private String productValueKey(String keyword) {
        switch (keyword) {
            case "prix achat":
                return "achat";
            case "prix vente":
                return "vente";
            case "gros":
            case "prix gros":
            case "wholesale":
                return "gros";
            case "sotck":
            case "stok":
            case "stoc":
            case "stck":
            case "qte":
            case "quantite":
                return "stock";
            case "minimum":
                return "min";
            default:
                return keyword;
        }
    }

    private int firstKeywordIndex(String text) {
        Matcher matcher = PRODUCT_VALUE_PATTERN.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private long parseAmount(String value) {
        String clean = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (clean.isBlank()) {
            throw new RuntimeException("Montant invalide.");
        }
        return Long.parseLong(clean);
    }

    private Long parseQuantityValue(String value) {
        String clean = normalize(value);
        if (clean.matches(".*\\d.*")) {
            return parseAmount(clean);
        }
        return FRENCH_QUANTITY_WORDS.get(clean);
    }

    private CustomerInput parseCustomerInput(String text) {
        String cleaned = text == null ? "" : text.trim();
        Long creditLimit = null;
        Matcher limitMatcher = Pattern.compile("\\b(?:limite|plafond)\\s+(\\d[\\d\\s.]*)").matcher(cleaned);
        if (limitMatcher.find()) {
            creditLimit = parseAmount(limitMatcher.group(1));
            cleaned = (cleaned.substring(0, limitMatcher.start()) + " " + cleaned.substring(limitMatcher.end())).trim();
        }

        String phoneNumber = null;
        int phoneStart = -1;
        int phoneEnd = -1;
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(cleaned);
        while (amountMatcher.find()) {
            String digits = amountMatcher.group(1).replaceAll("[^0-9]", "");
            if (digits.length() >= 6 && digits.length() <= 15) {
                phoneNumber = digits;
                phoneStart = amountMatcher.start();
                phoneEnd = amountMatcher.end();
            }
        }
        if (phoneNumber != null && phoneStart >= 0) {
            int start = phoneStart;
            if (start > 0 && cleaned.charAt(start - 1) == '+') {
                start--;
            }
            cleaned = (cleaned.substring(0, start) + " " + cleaned.substring(phoneEnd)).trim();
        }
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return new CustomerInput(cleaned, phoneNumber, creditLimit);
    }

    private PaymentInput parsePaymentInput(String normalized) {
        String cleaned = normalized
                .replaceFirst("^paiement\\s+", "")
                .replaceFirst("^payement\\s+", "")
                .replaceFirst("^versement\\s+", "")
                .replaceFirst("^reglement\\s+", "")
                .trim();

        Matcher matcher = AMOUNT_PATTERN.matcher(cleaned);
        Long amount = null;
        int amountStart = -1;
        int amountEnd = -1;
        while (matcher.find()) {
            amount = parseAmount(matcher.group(1));
            amountStart = matcher.start();
            amountEnd = matcher.end();
        }
        if (amount == null) {
            throw new RuntimeException("Format paiement: *paiement Awa 5000*.");
        }

        String query = (cleaned.substring(0, amountStart) + " " + cleaned.substring(amountEnd))
                .replaceAll("\\ba paye\\b", " ")
                .replaceAll("\\bpaye\\b", " ")
                .replaceAll("\\ba verse\\b", " ")
                .replaceAll("\\bverse\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (query.isBlank()) {
            throw new RuntimeException("Indiquez le client. Exemple: *paiement Awa 5000*.");
        }
        return new PaymentInput(query, amount);
    }

    private long parseFirstAmount(String normalized) {
        Matcher matcher = AMOUNT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new RuntimeException("Montant invalide.");
        }
        return parseAmount(matcher.group(1));
    }

    private boolean containsStandaloneAmountBeforeDate(String normalized) {
        Matcher dateMatcher = DUE_DATE_PATTERN.matcher(normalized);
        int dateStart = dateMatcher.find() ? dateMatcher.start() : normalized.length();
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(normalized);
        return amountMatcher.find() && amountMatcher.start() < dateStart;
    }

    private LocalDate extractDueDate(String normalized) {
        Matcher matcher = DUE_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int year;
        if (matcher.group(3) == null) {
            year = LocalDate.now().getYear();
        } else {
            year = Integer.parseInt(matcher.group(3));
            if (year < 100) {
                year += 2000;
            }
        }
        try {
            LocalDate date = LocalDate.of(year, month, day);
            if (matcher.group(3) == null && date.isBefore(LocalDate.now())) {
                date = date.plusYears(1);
            }
            return date;
        } catch (Exception e) {
            throw new RuntimeException("Date d’échéance invalide. Exemple: *echeance 30/06*.");
        }
    }

    private long draftTotal(SaleDraft draft, List<ShopProduct> products) {
        Map<Long, ShopProduct> byId = products.stream().collect(Collectors.toMap(ShopProduct::getId, p -> p));
        long subtotal = 0;
        boolean wholesale = Boolean.TRUE.equals(draft.wholesale);
        for (Map.Entry<Long, Long> entry : draft.items.entrySet()) {
            ShopProduct product = byId.get(entry.getKey());
            if (product != null) {
                long unitPrice = wholesale && product.getWholesalePrice() != null && product.getWholesalePrice() > 0
                        ? product.getWholesalePrice() : product.getSalePrice();
                subtotal += unitPrice * entry.getValue();
            }
        }
        long discount = Math.max(0, Math.min(draft.discount == null ? 0 : draft.discount, subtotal));
        return subtotal - discount;
    }

    private String creditConfirmationText(ShopConversationSession session, SaleDraft draft, List<ShopProduct> products) {
        ShopCustomer customer = customerService.findById(draft.customerId);
        long total = draftTotal(draft, products);
        long paid = draft.paidAmount == null ? 0 : draft.paidAmount;
        long due = Math.max(0, total - paid);
        StringBuilder sb = new StringBuilder("🧾 *")
                .append(due > 0 ? "Confirmation vente à crédit" : "Confirmation vente client")
                .append("*\n\n");
        sb.append(cartText(draft, products, false)).append("\n\n");
        sb.append("Client: *").append(customer.getName()).append("*\n");
        sb.append("Payé maintenant: ").append(amount(paid)).append(" F\n");
        sb.append("Reste dû: *").append(amount(due)).append(" FCFA*");
        if (draft.dueDate != null) {
            sb.append("\nÉchéance: ").append(LocalDate.parse(draft.dueDate).format(SHORT_DATE));
        }
        return sb.toString();
    }

    private String customerText(ShopCustomer customer) {
        StringBuilder sb = new StringBuilder("✅ Client enregistré\n");
        sb.append(customer.getName()).append("\n");
        if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isBlank()) {
            sb.append("Téléphone: ").append(customer.getPhoneNumber()).append("\n");
        }
        sb.append("Dette actuelle: ").append(amount(customer.getOutstandingBalance())).append(" F");
        return sb.toString();
    }

    private String customerDebtText(ShopCustomer customer) {
        StringBuilder sb = new StringBuilder("📒 *Dette client*\n");
        sb.append(customer.getName()).append("\n");
        if (customer.getPhoneNumber() != null && !customer.getPhoneNumber().isBlank()) {
            sb.append("Téléphone: ").append(customer.getPhoneNumber()).append("\n");
        }
        sb.append("Reste dû: *").append(amount(customer.getOutstandingBalance())).append(" FCFA*");
        java.time.LocalDateTime lastPay = customerService.lastPaymentDate(customer.getId());
        if (lastPay != null) {
            sb.append("\nDernier paiement: ").append(lastPay.format(SHORT_DATE));
        }
        if (nullToZero(customer.getOutstandingBalance()) > 0) {
            sb.append("\n\nEncaisser: *paiement ").append(customer.getName()).append(" 5000*");
        }
        return sb.toString();
    }

    private boolean isCreditSaleCommand(String normalized) {
        return normalized.equals("credit")
                || normalized.equals("vente credit")
                || normalized.equals("vente a credit")
                || normalized.startsWith("credit ")
                || normalized.startsWith("vente credit ")
                || normalized.startsWith("vente a credit ");
    }

    private boolean isCustomerPaymentCommand(String normalized) {
        if (looksLikeNaturalSale(normalized)) {
            return false;
        }
        if (normalized.startsWith("appro ") || normalized.startsWith("approvisionnement ")
                || normalized.startsWith("paiement fournisseur ")) {
            return false;
        }
        return normalized.startsWith("paiement ")
                || normalized.startsWith("payement ")
                || normalized.startsWith("versement ")
                || normalized.startsWith("reglement ")
                || normalized.matches(".+\\s+a\\s+paye\\s+\\d.*")
                || normalized.matches(".+\\s+paye\\s+\\d.*");
    }

    private boolean looksLikeNaturalSale(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (normalized.equals("vente") || normalized.equals("vente credit") || normalized.equals("vente a credit")
                || normalized.equals("vendre") || normalized.equals("credit")) {
            return false;
        }
        boolean saleVerb = normalized.startsWith("vente ")
                || normalized.contains(" vente ")
                || normalized.startsWith("vendu ")
                || normalized.contains(" vendu ")
                || normalized.startsWith("vendre ")
                || normalized.contains(" vendre ")
                || normalized.startsWith("vend ")
                || normalized.contains(" vend ")
                || normalized.contains(" a vendu ")
                || normalized.contains(" j ai vendu ")
                || normalized.contains(" client a pris ");
        boolean startsWithQuantity = Pattern.compile("^\\s*(\\d[\\d\\s.]*|" + QUANTITY_WORDS_PATTERN + ")\\s+\\S+").matcher(normalized).find();
        return (saleVerb || startsWithQuantity) && (AMOUNT_PATTERN.matcher(normalized).find() || QUANTITY_TOKEN_PATTERN.matcher(normalized).find());
    }

    private ProductMatch resolveProduct(List<ShopProduct> products, String normalizedMessage, String query) {
        String haystack = normalize(isBlank(query) ? normalizedMessage : query);
        int bestScore = 0;
        List<ShopProduct> best = new java.util.ArrayList<>();
        for (ShopProduct product : products) {
            int score = productScore(product, haystack);
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(product);
            } else if (score == bestScore && score > 0) {
                best.add(product);
            }
        }
        if (bestScore <= 0 || best.isEmpty()) {
            return new ProductMatch(null, false, "");
        }
        if (best.size() > 1) {
            String options = best.stream().limit(5).map(ShopProduct::getName).collect(Collectors.joining(", "));
            return new ProductMatch(null, true, options);
        }
        return new ProductMatch(best.get(0), false, "");
    }

    private int productScore(ShopProduct product, String haystack) {
        String name = normalize(product.getNormalizedName() == null ? product.getName() : product.getNormalizedName());
        if (name.isBlank() || haystack.isBlank()) {
            return 0;
        }
        if (haystack.equals(name)) {
            return 1000 + name.length();
        }
        if (haystack.contains(name)) {
            return 900 + name.length();
        }
        if (name.contains(haystack) && haystack.length() >= 3) {
            return 850 + haystack.length();
        }
        int score = 0;
        for (String token : name.split("\\s+")) {
            if (token.length() >= 3 && haystack.contains(token)) {
                score += 80 + token.length();
            }
        }
        if (product.getAliases() != null && !product.getAliases().isBlank()) {
            String aliases = normalize(product.getAliases());
            for (String token : aliases.split("\\s+")) {
                if (token.length() >= 3 && haystack.contains(token)) {
                    score += 60 + token.length();
                }
            }
        }
        return score;
    }

    private Long extractQuantity(String normalized, ShopProduct product, Long aiQuantity) {
        if (aiQuantity != null && aiQuantity > 0) {
            return aiQuantity;
        }
        for (String token : productTokens(product)) {
            Pattern beforeProduct = Pattern.compile("\\b(\\d[\\d\\s.]*|" + QUANTITY_WORDS_PATTERN + ")\\s*(?:metres?|metre|m|pieces?|piece|pcs?|unites?|unite|bouteilles?|canettes?|cartons?|paquets?|sacs?|boites?)?\\s+(?:de\\s+|du\\s+|d\\s+)?"
                    + Pattern.quote(token) + "s?\\b");
            Matcher matcher = beforeProduct.matcher(normalized);
            if (matcher.find()) {
                return parseQuantityValue(matcher.group(1));
            }
        }
        int productIndex = productIndex(normalized, product);
        if (productIndex > 0) {
            String before = normalized.substring(0, productIndex);
            Matcher matcher = QUANTITY_TOKEN_PATTERN.matcher(before);
            Long last = null;
            while (matcher.find()) {
                last = parseQuantityValue(matcher.group(1));
            }
            if (last != null && last > 0) {
                return last;
            }
        }
        return null;
    }

    private NaturalItemsResult resolveNaturalSaleItems(List<ShopProduct> products, String normalized, Optional<AiParsingService.ShopSaleAiResult> aiResult) {
        Map<Long, Long> items = new LinkedHashMap<>();
        List<String> ambiguous = new java.util.ArrayList<>();
        List<String> missingQuantity = new java.util.ArrayList<>();

        if (aiResult.isPresent()) {
            AiParsingService.ShopSaleAiResult ai = aiResult.get();
            List<AiParsingService.ShopSaleAiItem> aiItems = ai.items() == null || ai.items().isEmpty()
                    ? (isBlank(ai.product()) ? List.of() : List.of(new AiParsingService.ShopSaleAiItem(ai.product(), ai.quantity())))
                    : ai.items();
            for (AiParsingService.ShopSaleAiItem item : aiItems) {
                if (item == null || isBlank(item.product())) {
                    continue;
                }
                ProductMatch match = resolveProduct(products, normalized, item.product());
                if (match.ambiguous()) {
                    ambiguous.add(match.optionsText());
                    continue;
                }
                if (match.product() == null) {
                    continue;
                }
                Long quantity = extractQuantity(normalized, match.product(), item.quantity());
                if (quantity == null || quantity <= 0) {
                    missingQuantity.add(match.product().getName());
                    continue;
                }
                items.merge(match.product().getId(), quantity, Long::sum);
            }
        }

        for (ShopProduct product : products) {
            if (items.containsKey(product.getId()) || productIndex(normalized, product) < 0) {
                continue;
            }
            Long quantity = extractQuantity(normalized, product, null);
            if (quantity == null || quantity <= 0) {
                missingQuantity.add(product.getName());
                continue;
            }
            items.merge(product.getId(), quantity, Long::sum);
        }

        if (items.isEmpty() && aiResult.isPresent() && !isBlank(aiResult.get().product())) {
            ProductMatch match = resolveProduct(products, normalized, aiResult.get().product());
            if (match.ambiguous()) {
                ambiguous.add(match.optionsText());
            } else if (match.product() != null) {
                Long quantity = extractQuantity(normalized, match.product(), aiResult.get().quantity());
                if (quantity == null || quantity <= 0) {
                    missingQuantity.add(match.product().getName());
                } else {
                    items.merge(match.product().getId(), quantity, Long::sum);
                }
            }
        }

        return new NaturalItemsResult(
                items,
                ambiguous.stream().distinct().collect(Collectors.joining(", ")),
                missingQuantity.stream().distinct().collect(Collectors.joining(", "))
        );
    }

    private String findKnownCustomerInMessage(List<ShopCustomer> customers, String normalized) {
        for (ShopCustomer customer : customers) {
            if (customer.getPhoneNumber() != null && normalized.contains(customer.getPhoneNumber())) {
                return customer.getPhoneNumber();
            }
        }
        List<ShopCustomer> matches = customers.stream()
                .filter(customer -> {
                    String name = customerService.normalize(customer.getName());
                    return name.length() >= 3 && normalized.contains(name);
                })
                .sorted((left, right) -> Integer.compare(
                        customerService.normalize(right.getName()).length(),
                        customerService.normalize(left.getName()).length()
                ))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0).getName();
        }
        if (matches.size() > 1) {
            int longest = customerService.normalize(matches.get(0).getName()).length();
            List<ShopCustomer> best = matches.stream()
                    .filter(customer -> customerService.normalize(customer.getName()).length() == longest)
                    .toList();
            if (best.size() == 1) {
                return best.get(0).getName();
            }
            return best.stream().map(ShopCustomer::getName).collect(Collectors.joining(", "));
        }
        return "";
    }

    private String extractCustomerQuery(String normalized, ShopProduct product) {
        return extractCustomerQuery(normalized, List.of(product));
    }

    private String extractCustomerQuery(String normalized, List<ShopProduct> products) {
        if (products == null || products.isEmpty()) {
            return "";
        }
        int index = -1;
        for (ShopProduct product : products) {
            index = Math.max(index, productIndex(normalized, product));
        }
        if (index < 0) {
            return "";
        }
        String after = normalized.substring(index);
        int marker = after.indexOf(" a ");
        if (marker < 0) marker = after.indexOf(" pour ");
        if (marker < 0) marker = after.indexOf(" client ");
        if (marker < 0) marker = after.indexOf(" au client ");
        if (marker < 0) return "";
        String candidate = after.substring(marker)
                .replaceFirst("^\\s*(a|pour|client)\\s+", "")
                .trim();
        return cleanCustomerCandidate(candidate);
    }

    private String cleanCustomerCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "";
        }
        String cleaned = normalize(candidate)
                .replaceFirst("^\\s*(a|pour|client|au\\s+client)\\s+", "")
                .trim();
        cleaned = cleaned.replaceFirst("^(a\\s+credit|credit|dette|remise|reduction|mais|paye|payer|pye|verse|donne|regle|recu|encaisse|acompte|avance|echeance|il\\s+a|elle\\s+a)\\b.*$", "")
                .replaceFirst("\\s+(a\\s+credit|credit|dette|remise|reduction|mais|paye|payer|pye|verse|donne|regle|recu|encaisse|acompte|avance|echeance|il\\s+a|elle\\s+a)\\b.*$", "")
                .replaceFirst("\\s+(f|fcfa)$", "")
                .trim();
        if (cleaned.matches("\\d+")) {
            return "";
        }
        return cleaned;
    }

    private List<ShopProduct> productsForItems(List<ShopProduct> products, Map<Long, Long> items) {
        return products.stream()
                .filter(product -> items.containsKey(product.getId()))
                .toList();
    }

    private Long extractKeywordAmount(String normalized, String... keywords) {
        for (String keyword : keywords) {
            Matcher matcher = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\s+(\\d[\\d\\s.]*)").matcher(normalized);
            if (matcher.find()) {
                return parseAmount(matcher.group(1));
            }
        }
        return null;
    }

    private LocalDate parseAiDueDate(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private int productIndex(String normalized, ShopProduct product) {
        for (String token : productTokens(product)) {
            int index = normalized.indexOf(token);
            if (index >= 0) {
                return index + token.length();
            }
        }
        return -1;
    }

    private List<String> productTokens(ShopProduct product) {
        String name = normalize(product.getNormalizedName() == null ? product.getName() : product.getNormalizedName());
        List<String> tokens = new java.util.ArrayList<>();
        if (!name.isBlank()) {
            tokens.add(name);
            for (String token : name.split("\\s+")) {
                if (token.length() >= 3 && !tokens.contains(token)) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[']", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String amount(Long value) {
        return amount(value == null ? 0 : value.longValue());
    }

    private String amount(long value) {
        return amountFormat.format(value);
    }

    public static class SaleDraft {
        public Map<Long, Long> items = new LinkedHashMap<>();
        public Long discount = 0L;
        public Boolean credit = false;
        public Boolean wholesale = false;
        public String step = "SELECTING";
        public Long customerId;
        public Long paidAmount;
        public String dueDate;
        public String pendingCustomerName;
        public String pendingCustomerPhone;
        public Long pendingCustomerCreditLimit;
    }

    private record CustomerInput(String name, String phoneNumber, Long creditLimit) {}
    private record PaymentInput(String customerQuery, Long amount) {}
    private record ProductMatch(ShopProduct product, boolean ambiguous, String optionsText) {}
    private record NaturalItemsResult(Map<Long, Long> items, String ambiguousText, String missingQuantityText) {}
}
