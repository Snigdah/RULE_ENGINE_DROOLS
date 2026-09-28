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
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@Service
public class TransactionRuleService {

    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;
    private final ExecutorService virtualThreadExecutor;
    private final RuleExecutionService ruleExecutionService;

    public TransactionRuleService(UserLimitRepository userLimitRepository,
                                  ProductRepository productRepository,
                                  ExecutorService virtualThreadExecutor,
                                  RuleExecutionService ruleExecutionService) {
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.ruleExecutionService = ruleExecutionService;
    }

    /** Three steps: build the context, run the decision flow, map the result. */
    public TransactionResponse validate(TransactionRequest request) {
        ValidationContext context = buildContext(request);

        // This API only names the flow; RuleExecutionService runs the right groups.
        ruleExecutionService.execute(context, "TRANSFER_TRANSACTION");

        Transaction result = context.getTransaction();
        return new TransactionResponse(
                result.isValid(),
                result.isPermissionDenied(),
                result.getValidationMessage());
    }

    /**
     * Context building. The two DB lookups are independent, so they run in
     * PARALLEL on virtual threads. A missing row surfaces as ContextNotFoundException
     * (unwrapped from CompletionException) -> handled as HTTP 422.
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
