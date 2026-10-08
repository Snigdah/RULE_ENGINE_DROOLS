package com.example.droolspoc.service;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Transaction;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.lookup.TransferLookupService;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Transfer flow. Builds a {@link ValidationContext} from this POC's tables and hands it to the
 * rule-engine library. The three independent lookups (user limit, product, user) run in PARALLEL
 * on virtual threads via {@link TransferLookupService} — each in its own read-only transaction.
 */
@Service
public class TransactionRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final ExecutorService lookupExecutor;
    private final TransferLookupService lookup;

    public TransactionRuleService(RuleExecutionService ruleEngine,
                                  GlobalContext globalContext,
                                  @Qualifier("lookupExecutor") ExecutorService lookupExecutor,
                                  TransferLookupService lookup) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.lookupExecutor = lookupExecutor;
        this.lookup = lookup;
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
        var limitF   = CompletableFuture.supplyAsync(() -> lookup.userLimit(request.getUserId()), lookupExecutor);
        var productF = CompletableFuture.supplyAsync(() -> lookup.product(request.getSourceAccount()), lookupExecutor);
        var userF    = CompletableFuture.supplyAsync(() -> lookup.user(request.getUserId()), lookupExecutor);

        try {
            CompletableFuture.allOf(limitF, productF, userF).join();   // run in parallel, await all
        } catch (CompletionException e) {
            if (e.getCause() instanceof ResponseStatusException rse) throw rse;  // keep the 422
            throw e;
        }

        ValidationContext context = new ValidationContext();
        context.setUserId(request.getUserId());   // for the COMMON blocked-user rule
        context.setUser(userF.join());             // for the COMMON KYC rule
        context.setTransaction(toFact(request));
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
