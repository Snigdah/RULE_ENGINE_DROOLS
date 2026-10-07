package com.example.droolspoc.service;

import com.example.droolspoc.dto.AccountCloseRequest;
import com.example.droolspoc.dto.AccountCloseResponse;
import com.example.droolspoc.model.Account;
import com.example.droolspoc.model.ClosureValidationContext;
import com.example.droolspoc.model.User;
import com.example.droolspoc.repository.AccountRepository;
import com.example.droolspoc.repository.UserRepository;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account-closure flow. Runs the shared COMMON rules plus the CLOSURE rule (outstanding balance).
 */
@Service
public class ClosureRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public ClosureRuleService(RuleExecutionService ruleEngine,
                              GlobalContext globalContext,
                              AccountRepository accountRepository,
                              UserRepository userRepository) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
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
        Account account = accountRepository.findFirstByAccountNo(request.getAccountNo())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No account for accountNo=" + request.getAccountNo()));

        User user = userRepository.findFirstByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No user for userId=" + request.getUserId()));

        ClosureValidationContext context = new ClosureValidationContext();
        context.setUserId(request.getUserId());
        context.setUser(user);
        context.setAccount(account);
        return context;
    }
}
