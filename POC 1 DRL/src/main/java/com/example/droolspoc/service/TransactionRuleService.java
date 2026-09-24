package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Service
public class TransactionRuleService {

    private final KieContainer kieContainer;
    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;

    public TransactionRuleService(KieContainer kieContainer,
                                  UserLimitRepository userLimitRepository,
                                  ProductRepository productRepository) {
        this.kieContainer = kieContainer;
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
    }

    public TransactionResponse validate(TransactionRequest request) {
        // 1. Context building: DB lookups (error if either is missing)
        UserLimit userLimit = userLimitRepository
                .findByUserIdAndTransactionModeAndDrCrType(
                        request.getUserId(),
                        request.getTransactionMode(),
                        request.getDebitCredit())
                .orElseThrow(() -> new ContextNotFoundException(
                        "No user limit for userId=" + request.getUserId()
                                + ", transactionMode=" + request.getTransactionMode()
                                + ", drCrType=" + request.getDebitCredit()));

        Product product = productRepository
                .findBySourceAccount(request.getSourceAccount())
                .orElseThrow(() -> new ContextNotFoundException(
                        "No product for sourceAccount=" + request.getSourceAccount()));

        // 2. Assemble the single fact
        ValidationContext context = new ValidationContext();
        context.setTransaction(toFact(request));
        context.setUserLimit(userLimit);
        context.setProduct(product);

        // 3. Fire the rules on a fresh, disposed-per-call session
        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(context);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        Transaction result = context.getTransaction();
        return new TransactionResponse(
                result.isValid(),
                result.isPermissionDenied(),
                result.getValidationMessage());
    }

    private Transaction toFact(TransactionRequest r) {
        Transaction t = new Transaction();
        t.setUserId(r.getUserId());
        t.setTransactionMode(r.getTransactionMode());
        t.setDebitCredit(r.getDebitCredit());
        t.setSourceAccount(r.getSourceAccount());
        t.setSourceBranch(r.getSourceBranch());
        t.setDestinationAccount(r.getDestinationAccount());
        t.setCurrency(r.getCurrency());
        t.setAmount(r.getAmount());
        t.setExchangeRate(r.getExchangeRate());
        t.setRemarks(r.getRemarks());
        return t;
    }
}
