package com.example.droolspoc.service;

import com.example.droolspoc.dto.LoanRequest;
import com.example.droolspoc.dto.LoanResponse;
import com.example.droolspoc.model.Customer;
import com.example.droolspoc.model.Loan;
import com.example.droolspoc.model.LoanValidationContext;
import com.example.droolspoc.model.User;
import com.example.droolspoc.repository.CustomerRepository;
import com.example.droolspoc.repository.UserRepository;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Loan flow. Same shape as the transfer service - only the request class and the context type
 * differ. The shared COMMON rules (blocked-user, KYC) fire here too, because
 * {@link LoanValidationContext} extends {@code RuleContext}.
 */
@Service
public class LoanRuleService {

    private final RuleExecutionService ruleEngine;
    private final GlobalContext globalContext;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public LoanRuleService(RuleExecutionService ruleEngine,
                           GlobalContext globalContext,
                           CustomerRepository customerRepository,
                           UserRepository userRepository) {
        this.ruleEngine = ruleEngine;
        this.globalContext = globalContext;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
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
        Customer customer = customerRepository.findFirstByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No customer for userId=" + request.getUserId()));

        User user = userRepository.findFirstByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "No user for userId=" + request.getUserId()));

        LoanValidationContext context = new LoanValidationContext();
        context.setUserId(request.getUserId());
        context.setUser(user);
        context.setCustomer(customer);
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
