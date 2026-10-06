package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a fact from this POC's tables and hands it to the rule-engine library.
 * Rules mutate the fact in place; the response is read off that same object.
 */
@Service
public class TransactionRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;

    public TransactionRuleService(RuleExecutionService ruleEngine,
                                  GlobalContext globalContext,
                                  UserLimitRepository userLimitRepository,
                                  ProductRepository productRepository) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
    }

    public TransactionResponse validate(TransactionRequest request) {
        ValidationContext context = buildContext(request);
        ruleEngine.execute(context, TransactionRequest.class, globalContext);

        Transaction result = context.getTransaction();
        return new TransactionResponse(
                result.isValid(),
                result.isPermissionDenied(),
                result.getValidationMessage());
    }

    private ValidationContext buildContext(TransactionRequest request) {
        UserLimit userLimit = userLimitRepository.findFirstByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No user limit for userId=" + request.getUserId()));

        Product product = productRepository.findBySourceAccount(request.getSourceAccount())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No product for sourceAccount=" + request.getSourceAccount()));

        ValidationContext context = new ValidationContext();
        context.setTransaction(toFact(request));
        context.setUserLimit(userLimit);
        context.setProduct(product);
        return context;
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
