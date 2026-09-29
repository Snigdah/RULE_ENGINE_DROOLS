package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.UserBlock;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserBlockRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Service
public class TransactionRuleService {

    // This service's model and the exact rules it runs (in priority order).
    private static final String DMN_NAMESPACE = "https://leads-bd.com/dmn/transaction";
    private static final String DMN_MODEL = "TransactionValidation";
    private static final List<String> RULES = List.of("blockedUserRule", "overLimitRule");

    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;
    private final UserBlockRepository userBlockRepository;
    private final ExecutorService virtualThreadExecutor;
    private final DmnEngine dmnEngine;

    public TransactionRuleService(UserLimitRepository userLimitRepository,
                                  ProductRepository productRepository,
                                  UserBlockRepository userBlockRepository,
                                  ExecutorService virtualThreadExecutor,
                                  DmnEngine dmnEngine) {
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
        this.userBlockRepository = userBlockRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.dmnEngine = dmnEngine;
    }

    public TransactionResponse validate(TransactionRequest request) {
        ValidationContext context = buildContext(request);
        Transaction tx = context.getTransaction();

        // Raw blocked value (null when no row) - the DMN decides what it means.
        Boolean blocked = userBlockRepository.findByUserId(request.getUserId())
                .map(UserBlock::isBlocked)
                .orElse(null);

        // Inputs for the rules
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("blocked", blocked);
        inputs.put("amount", tx.getAmount());
        inputs.put("currency", tx.getCurrency());
        inputs.put("userLimit", context.getUserLimit().getLimit());
        inputs.put("productDrRes", context.getProduct().getDrRes());

        // Run THIS service's 2 rules
        Map<String, Object> outputs = dmnEngine.evaluate(DMN_NAMESPACE, DMN_MODEL, RULES, inputs);

        // First rule that returned a block message wins; otherwise allowed.
        for (String rule : RULES) {
            Object message = outputs.get(rule);
            if (message != null) {
                return new TransactionResponse(false, true, (String) message);
            }
        }
        return new TransactionResponse(true, false, null);
    }

    /**
     * Context building - UNCHANGED. Two independent DB lookups in PARALLEL on
     * virtual threads. A missing row -> ContextNotFoundException -> HTTP 422.
     */
    private ValidationContext buildContext(TransactionRequest request) {
        CompletableFuture<UserLimit> userLimitFuture = CompletableFuture.supplyAsync(
                () -> userLimitRepository
                        .findByUserIdAndTransactionModeAndDrCrType(
                                request.getUserId(),
                                request.getTransactionMode(),
                                request.getDebitCredit())
                        .orElseThrow(() -> new ContextNotFoundException(
                                "No user limit for userId=" + request.getUserId()
                                        + ", transactionMode=" + request.getTransactionMode()
                                        + ", drCrType=" + request.getDebitCredit())),
                virtualThreadExecutor);

        CompletableFuture<Product> productFuture = CompletableFuture.supplyAsync(
                () -> productRepository
                        .findBySourceAccount(request.getSourceAccount())
                        .orElseThrow(() -> new ContextNotFoundException(
                                "No product for sourceAccount=" + request.getSourceAccount())),
                virtualThreadExecutor);

        UserLimit userLimit;
        Product product;
        try {
            userLimit = userLimitFuture.join();
            product = productFuture.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof ContextNotFoundException cnfe) {
                throw cnfe;
            }
            throw ex;
        }

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
