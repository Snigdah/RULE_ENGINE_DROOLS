package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Transaction;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Service
public class TransactionRuleService {

    private final KieContainer kieContainer;

    public TransactionRuleService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    /**
     * Maps the request to a Drools fact, fires the rules against a fresh
     * session (created and disposed per call), and returns the outcome DTO.
     */
    public TransactionResponse validate(TransactionRequest request) {
        Transaction fact = toFact(request);

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(fact);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        return new TransactionResponse(
                fact.isValid(),
                fact.isPermissionDenied(),
                fact.getValidationMessage());
    }

    private Transaction toFact(TransactionRequest r) {
        Transaction t = new Transaction();
        t.setSourceAccount(r.getSourceAccount());
        t.setSourceBranch(r.getSourceBranch());
        t.setDestinationAccount(r.getDestinationAccount());
        t.setAmount(r.getAmount());
        t.setDebitCredit(r.getDebitCredit());
        t.setCurrency(r.getCurrency());
        t.setExchangeRate(r.getExchangeRate());
        t.setRemarks(r.getRemarks());
        t.setUserId(r.getUserId());
        t.setTransferMode(r.getTransferMode());
        return t;
    }
}
