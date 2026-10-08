package com.example.droolspoc.service;

import com.example.droolspoc.dto.LoanRequest;
import com.example.droolspoc.dto.LoanResponse;
import com.example.droolspoc.model.Loan;
import com.example.droolspoc.model.LoanValidationContext;
import com.example.droolspoc.lookup.LoanLookupService;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Loan flow. The customer + user lookups run in PARALLEL on virtual threads via
 * {@link LoanLookupService}. Shared COMMON rules (blocked-user, KYC) still fire here because
 * {@link LoanValidationContext} extends {@code RuleContext}.
 */
@Service
public class LoanRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final ExecutorService lookupExecutor;
    private final LoanLookupService lookup;

    public LoanRuleService(RuleExecutionService ruleEngine,
                           GlobalContext globalContext,
                           @Qualifier("lookupExecutor") ExecutorService lookupExecutor,
                           LoanLookupService lookup) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.lookupExecutor = lookupExecutor;
        this.lookup = lookup;
    }

    public LoanResponse validate(LoanRequest request) {
        LoanValidationContext context = buildContext(request);
        ruleEngine.execute(context, globalContext);

        return new LoanResponse(
                context.isValid(),
                context.isPermissionDenied(),
                context.getValidationMessage());
    }

    private LoanValidationContext buildContext(LoanRequest request) {
        var customerF = CompletableFuture.supplyAsync(() -> lookup.customer(request.getUserId()), lookupExecutor);
        var userF     = CompletableFuture.supplyAsync(() -> lookup.user(request.getUserId()), lookupExecutor);

        try {
            CompletableFuture.allOf(customerF, userF).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof ResponseStatusException rse) throw rse;
            throw e;
        }

        LoanValidationContext context = new LoanValidationContext();
        context.setUserId(request.getUserId());
        context.setUser(userF.join());
        context.setCustomer(customerF.join());
        context.setLoan(toFact(request));
        return context;
    }

    private Loan toFact(LoanRequest r) {
        Loan loan = new Loan();
        loan.setUserId(r.getUserId());
        loan.setAmount(r.getAmount());
        loan.setTenureMonths(r.getTenureMonths());
        loan.setPurpose(r.getPurpose());
        return loan;
    }
}
