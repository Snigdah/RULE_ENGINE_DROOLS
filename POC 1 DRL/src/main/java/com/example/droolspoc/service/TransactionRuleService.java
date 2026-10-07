package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.User;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.repository.UserRepository;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transfer flow. Builds a {@link ValidationContext} from this POC's tables and hands it to the
 * rule-engine library. The COMMON rules (blocked-user, KYC) and the TRANSFER rule mutate the
 * context; the outcome is read straight off it.
 */
@Service
public class TransactionRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public TransactionRuleService(RuleExecutionService ruleEngine,
                                  GlobalContext globalContext,
                                  UserLimitRepository userLimitRepository,
                                  ProductRepository productRepository,
                                  UserRepository userRepository) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public TransactionResponse validate(TransactionRequest request) {
        ValidationContext context = buildContext(request);
        ruleEngine.execute(context, globalContext);

        return new TransactionResponse(
                context.isValid(),
                context.isPermissionDenied(),
                context.getValidationMessage());
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

        User user = userRepository.findFirstByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No user for userId=" + request.getUserId()));

        ValidationContext context = new ValidationContext();
        context.setUserId(request.getUserId());   // for the COMMON blocked-user rule
        context.setUser(user);                     // for the COMMON KYC rule
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
