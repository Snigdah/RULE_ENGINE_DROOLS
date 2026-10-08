package com.example.droolspoc.service;

import com.example.droolspoc.dto.AccountCloseRequest;
import com.example.droolspoc.dto.AccountCloseResponse;
import com.example.droolspoc.model.ClosureValidationContext;
import com.example.droolspoc.lookup.ClosureLookupService;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Account-closure flow. The account + user lookups run in PARALLEL on virtual threads via
 * {@link ClosureLookupService}. Runs the shared COMMON rules plus the CLOSURE rule.
 */
@Service
public class ClosureRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final ExecutorService lookupExecutor;
    private final ClosureLookupService lookup;

    public ClosureRuleService(RuleExecutionService ruleEngine,
                              GlobalContext globalContext,
                              @Qualifier("lookupExecutor") ExecutorService lookupExecutor,
                              ClosureLookupService lookup) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.lookupExecutor = lookupExecutor;
        this.lookup = lookup;
    }

    public AccountCloseResponse validate(AccountCloseRequest request) {
        ClosureValidationContext context = buildContext(request);
        ruleEngine.execute(context, globalContext);

        return new AccountCloseResponse(
                context.isValid(),
                context.isPermissionDenied(),
                context.getValidationMessage());
    }

    private ClosureValidationContext buildContext(AccountCloseRequest request) {
        var accountF = CompletableFuture.supplyAsync(() -> lookup.account(request.getAccountNo()), lookupExecutor);
        var userF    = CompletableFuture.supplyAsync(() -> lookup.user(request.getUserId()), lookupExecutor);

        try {
            CompletableFuture.allOf(accountF, userF).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ResponseStatusException rse) throw rse;
            throw e;
        }

        ClosureValidationContext context = new ClosureValidationContext();
        context.setUserId(request.getUserId());
        context.setUser(userF.join());
        context.setAccount(accountF.join());
        return context;
    }
}
