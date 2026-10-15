package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.User;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.lookup.TransferLookupService;
import leads.ruleengine.core.dto.RequiredData;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Transfer flow. Builds a {@link ValidationContext} from this POC's tables and hands it to the
 * rule-engine library. The three independent lookups (user limit, product, user) run in PARALLEL
 * on virtual threads via {@link TransferLookupService} — each in its own read-only transaction.
 *
 * <p><b>Demand-driven (library 3.0.1):</b> before the lookups it asks the library which data the
 * flow's ACTIVE rules read ({@code ruleEngine.requiredData(..)}, in memory, no DB) and runs only the
 * lookups an active rule needs. The name in {@code needs("...")} is the first part of the catalog
 * drl_path ({@code userLimit!.limit} -> {@code "userLimit"}). Loan and Closure services keep the
 * classic style (always load everything) - both styles work side by side.</p>
 */
@Service
public class TransactionRuleService {

    private final RuleExecutionService ruleEngine;
    private final ExecutorService lookupExecutor;
    private final TransferLookupService lookup;

    public TransactionRuleService(RuleExecutionService ruleEngine,
                                  @Qualifier("lookupExecutor") ExecutorService lookupExecutor,
                                  TransferLookupService lookup) {
        this.ruleEngine = ruleEngine;
        this.lookupExecutor = lookupExecutor;
        this.lookup = lookup;
    }

    public TransactionResponse validate(TransactionRequest request) {
        ValidationContext context = buildContext(request);
        ruleEngine.execute(context, TransactionRequest.class);   // builder rules only - no global context needed

        return new TransactionResponse(
                context.isValid(),
                context.isPermissionDenied(),
                context.getValidationMessage());
    }

    private ValidationContext buildContext(TransactionRequest request) {
        RequiredData need = ruleEngine.requiredData(TransactionRequest.class);   // memory, no DB

        // same parallel code as today - each lookup now has a needs() check in front
        CompletableFuture<UserLimit> limitF = need.needs("userLimit")
                ? supplyAsync(() -> lookup.userLimit(request.getUserId()), lookupExecutor)
                : completedFuture(null);

        CompletableFuture<Product> productF = need.needs("product")
                ? supplyAsync(() -> lookup.product(request.getSourceAccount()), lookupExecutor)
                : completedFuture(null);

        CompletableFuture<User> userF = need.needs("user")
                ? supplyAsync(() -> lookup.user(request.getUserId()), lookupExecutor)
                : completedFuture(null);

        try {
            CompletableFuture.allOf(limitF, productF, userF).join();   // run in parallel, await all
        } catch (CompletionException e) {
            if (e.getCause() instanceof ResponseStatusException rse) throw rse;  // keep the 422
            throw e;
        }

        ValidationContext context = new ValidationContext();
        context.setUserId(request.getUserId());   // from the request
        context.setTransaction(toFact(request));  // from the request - always set
        context.setUser(userF.join());            // null when no active rule reads it
        context.setUserLimit(limitF.join());
        context.setProduct(productF.join());
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
        t.setChannel(r.getChannel());
        t.setCountry(r.getCountry());
        t.setDailyTxnCount(r.getDailyTxnCount() == null ? 0 : r.getDailyTxnCount());
        return t;
    }
}
