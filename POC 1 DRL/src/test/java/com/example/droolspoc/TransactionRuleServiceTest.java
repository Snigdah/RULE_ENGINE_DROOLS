package com.example.droolspoc;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.User;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.model.ValidationContext;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.repository.UserRepository;
import com.example.droolspoc.service.TransactionRuleService;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.service.RuleExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionRuleServiceTest {

    private UserLimitRepository userLimitRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private RuleExecutionService ruleEngine;
    private GlobalContext globalContext;
    private TransactionRuleService service;

    @BeforeEach
    void setUp() {
        userLimitRepository = mock(UserLimitRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        ruleEngine = mock(RuleExecutionService.class);
        globalContext = new GlobalContext();
        service = new TransactionRuleService(
                ruleEngine, globalContext, userLimitRepository, productRepository, userRepository);
    }

    @Test
    void validateBuildsContextAndExecutesTheMappedFlow() {
        UserLimit limit = limit("USER-001", new BigDecimal("10000"));
        Product product = product("100001", 1);
        when(userLimitRepository.findFirstByUserId("USER-001")).thenReturn(Optional.of(limit));
        when(productRepository.findBySourceAccount("100001")).thenReturn(Optional.of(product));
        when(userRepository.findFirstByUserId("USER-001")).thenReturn(Optional.of(user("USER-001", true)));
        doAnswer(invocation -> {
            ValidationContext ctx = invocation.getArgument(0);
            ctx.setValid(false);
            ctx.setPermissionDenied(true);
            ctx.setValidationMessage("Blocked: user is blocked");
            return null;
        }).when(ruleEngine).execute(any(), eq(TransactionRequest.class), eq(globalContext));

        TransactionResponse response = service.validate(request("USER-001", "100001", new BigDecimal("15000")));

        ArgumentCaptor<ValidationContext> context = ArgumentCaptor.forClass(ValidationContext.class);
        verify(ruleEngine).execute(context.capture(), eq(TransactionRequest.class), eq(globalContext));
        assertSame(limit, context.getValue().getUserLimit());
        assertSame(product, context.getValue().getProduct());
        assertEquals("USER-001", context.getValue().getUserId());
        assertEquals("USER-001", context.getValue().getTransaction().getUserId());
        assertEquals(new BigDecimal("15000"), context.getValue().getTransaction().getAmount());
        assertFalse(response.valid());
        assertEquals("Blocked: user is blocked", response.message());
    }

    @Test
    void missingUserLimitReturns422() {
        when(userLimitRepository.findFirstByUserId(any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validate(request("UNKNOWN", "100001", new BigDecimal("10"))));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void missingProductReturns422() {
        when(userLimitRepository.findFirstByUserId(any()))
                .thenReturn(Optional.of(limit("USER-001", new BigDecimal("10000"))));
        when(productRepository.findBySourceAccount(any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validate(request("USER-001", "999999", new BigDecimal("10"))));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    private static TransactionRequest request(String userId, String sourceAccount, BigDecimal amount) {
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setTransactionMode("ONLINE");
        request.setDebitCredit("D");
        request.setSourceAccount(sourceAccount);
        request.setCurrency("BDT");
        request.setAmount(amount);
        return request;
    }

    private static UserLimit limit(String userId, BigDecimal amount) {
        UserLimit limit = new UserLimit();
        limit.setUserId(userId);
        limit.setTransactionMode("ONLINE");
        limit.setDrCrType("D");
        limit.setLimit(amount);
        return limit;
    }

    private static Product product(String sourceAccount, int drRes) {
        Product product = new Product();
        product.setSourceAccount(sourceAccount);
        product.setDrRes(drRes);
        return product;
    }

    private static User user(String userId, boolean kyc) {
        User user = new User();
        user.setUserId(userId);
        user.setKycVerified(kyc);
        return user;
    }
}
