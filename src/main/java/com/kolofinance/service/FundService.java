package com.kolofinance.service;

import com.kolofinance.model.Fund;
import com.kolofinance.model.Organization;
import com.kolofinance.model.User;
import com.kolofinance.model.enums.FundStatus;
import com.kolofinance.model.enums.Role;
import com.kolofinance.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final WhatsAppService whatsAppService;

    public Fund create(Long orgId, Long agentId, Long amount, String description) {
        Organization org = organizationService.findById(orgId);
        User agent = userService.findById(agentId);
        ensureAgentCanReceiveFunds(orgId, agent);
        validateAmount(amount);
        Fund fund = Fund.builder()
                .organization(org)
                .agent(agent)
                .initialAmount(amount)
                .balance(amount)
                .description(description)
                .status(FundStatus.PENDING_RECEIPT)
                .build();
        fund = fundRepository.save(fund);
        notifyReceiptRequest(fund);
        return fund;
    }

    public List<Fund> findByOrganization(Long orgId) {
        return fundRepository.findByOrganizationId(orgId);
    }

    public Optional<Fund> findActiveFundForAgent(Long agentId) {
        return fundRepository.findFirstByAgentIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(agentId, FundStatus.ACTIVE, 0L);
    }
    public List<Fund> findActiveSpendableFundsForAgent(Long agentId) {
        return fundRepository.findByAgentIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(agentId, FundStatus.ACTIVE, 0L);
    }

    public Optional<Fund> findActiveFundForAgent(Long agentId, Long orgId) {
        return fundRepository.findFirstByAgentIdAndOrganizationIdAndStatusAndBalanceGreaterThanOrderByCreatedAtDesc(agentId, orgId, FundStatus.ACTIVE, 0L);
    }

    public List<Fund> findPendingReceiptsForAgent(Long agentId) {
        return fundRepository.findByAgentIdAndStatusOrderByCreatedAtAsc(agentId, FundStatus.PENDING_RECEIPT);
    }

    public List<Fund> findPendingReceiptsByOrganization(Long orgId) {
        return fundRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, FundStatus.PENDING_RECEIPT);
    }

    @Transactional
    public Fund topUp(Long orgId, Long fundId, Long amount, String description) {
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new RuntimeException("Fonds introuvable: " + fundId));
        if (fund.getOrganization() == null || !fund.getOrganization().getId().equals(orgId)) {
            throw new RuntimeException("Fonds introuvable dans cette organisation");
        }
        validateAmount(amount);
        Fund recharge = Fund.builder()
                .organization(fund.getOrganization())
                .agent(fund.getAgent())
                .initialAmount(amount)
                .balance(amount)
                .description(description != null && !description.trim().isEmpty()
                        ? "Recharge: " + description.trim()
                        : "Recharge du fonds #" + fund.getId())
                .status(FundStatus.PENDING_RECEIPT)
                .build();
        recharge = fundRepository.save(recharge);
        notifyReceiptRequest(recharge);
        return recharge;
    }

    @Transactional
    public Fund debit(Long fundId, Long amount) {
        Fund fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new RuntimeException("Fonds introuvable: " + fundId));
        if (fund.getBalance() < amount) {
            throw new RuntimeException("Solde insuffisant. Solde: " + fund.getBalance() + ", Montant: " + amount);
        }
        fund.setBalance(fund.getBalance() - amount);
        return fundRepository.save(fund);
    }

    @Transactional
    public Fund acceptReceipt(Long orgId, Long fundId, Long agentId, String note) {
        Fund fund = findReceiptFund(orgId, fundId, agentId);
        fund.setStatus(FundStatus.ACTIVE);
        fund.setReceiptConfirmedAt(LocalDateTime.now());
        fund.setReceiptNote(note);
        return fundRepository.save(fund);
    }

    @Transactional
    public Fund rejectReceipt(Long orgId, Long fundId, Long agentId, String note) {
        Fund fund = findReceiptFund(orgId, fundId, agentId);
        fund.setStatus(FundStatus.REJECTED);
        fund.setReceiptRejectedAt(LocalDateTime.now());
        fund.setReceiptNote(note);
        return fundRepository.save(fund);
    }

    private Fund findReceiptFund(Long orgId, Long fundId, Long agentId) {
        Fund fund = fundRepository.findByIdAndOrganizationId(fundId, orgId)
                .orElseThrow(() -> new RuntimeException("Affectation introuvable"));
        if (agentId != null && !fund.getAgent().getId().equals(agentId)) {
            throw new RuntimeException("Cette affectation ne concerne pas cet agent");
        }
        if (fund.getStatus() != FundStatus.PENDING_RECEIPT) {
            throw new RuntimeException("Cette affectation n'est plus en attente");
        }
        return fund;
    }

    private void ensureAgentCanReceiveFunds(Long orgId, User agent) {
        com.kolofinance.model.OrganizationMembership membership = userService.findMembership(orgId, agent.getId())
                .orElseThrow(() -> new RuntimeException("Agent introuvable dans cette organisation"));
        if (!Boolean.TRUE.equals(membership.getActive())) {
            throw new RuntimeException("Impossible de confier des fonds à un agent inactif");
        }
        if (membership.getRole() != Role.AGENT) {
            throw new RuntimeException("Les fonds doivent être confiés à un utilisateur de rôle AGENT");
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Le montant doit être supérieur à zéro");
        }
    }

    private void notifyReceiptRequest(Fund fund) {
        String message = String.format(
                "💰 *Nouvelle affectation de fonds*%n%nOrganisation : *%s*%nMontant : *%,d FCFA*%nDescription : %s%n%nRépondez *oui* pour confirmer la réception ou *non* pour rejeter.%nVous pouvez aussi vous connecter à Kolo pour voir vos affectations en attente.",
                fund.getOrganization().getName(),
                fund.getInitialAmount(),
                fund.getDescription() != null ? fund.getDescription() : "—"
        );
        whatsAppService.sendMessage(fund.getAgent().getPhoneNumber(), message);
    }
}
