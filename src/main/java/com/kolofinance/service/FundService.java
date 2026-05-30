package com.kolofinance.service;

import com.kolofinance.model.Fund;
import com.kolofinance.model.Organization;
import com.kolofinance.model.User;
import com.kolofinance.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final OrganizationService organizationService;
    private final UserService userService;

    public Fund create(Long orgId, Long agentId, Long amount, String description) {
        Organization org = organizationService.findById(orgId);
        User agent = userService.findById(agentId);
        Fund fund = Fund.builder()
                .organization(org)
                .agent(agent)
                .initialAmount(amount)
                .balance(amount)
                .description(description)
                .build();
        return fundRepository.save(fund);
    }

    public List<Fund> findByOrganization(Long orgId) {
        return fundRepository.findByOrganizationId(orgId);
    }

    public Optional<Fund> findActiveFundForAgent(Long agentId) {
        return fundRepository.findFirstByAgentIdAndBalanceGreaterThanOrderByCreatedAtDesc(agentId, 0L);
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
}
