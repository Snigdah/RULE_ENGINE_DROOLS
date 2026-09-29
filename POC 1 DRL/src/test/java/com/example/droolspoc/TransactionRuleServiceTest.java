package com.example.droolspoc;

import com.example.droolspoc.config.DmnConfig;
import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.UserBlock;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserBlockRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.service.DmnEngine;
import com.example.droolspoc.service.TransactionRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNRuntime;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionRuleServiceTest {

    private UserLimitRepository userLimitRepository;
    private ProductRepository productRepository;
    private UserBlockRepository userBlockRepository;
    private TransactionRuleService service;

    @BeforeEach
    void setUp() {
        DMNRuntime dmnRuntime = new DmnConfig().dmnRuntime();
        DmnEngine dmnEngine = new DmnEngine(dmnRuntime);

        userLimitRepository = mock(UserLimitRepository.class);
        productRepository = mock(ProductRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        when(userBlockRepository.findByUserId(any())).thenReturn(Optional.empty()); // not blocked by default

        service = new TransactionRuleService(
                userLimitRepository, productRepository, userBlockRepository,
                Executors.newVirtualThreadPerTaskExecutor(), dmnEngine);
    }

    private TransactionRequest request(String currency, String amount) {
        TransactionRequest r = new TransactionRequest();
        r.setUserId("USER-001");
        r.setTransactionMode("TRANSFER");
        r.setDebitCredit("DR");
        r.setSourceAccount("100001");
        r.setCurrency(currency);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    private void stubContext(String limit, int drRes) {
        UserLimit ul = new UserLimit();
        ul.setLimit(new BigDecimal(limit));
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.of(ul));

        Product p = new Product();
        p.setDrRes(drRes);
        when(productRepository.findBySourceAccount(any())).thenReturn(Optional.of(p));
    }

    @Test
    void blockedUserIsBlocked() {
        stubContext("100", 1);
        UserBlock ub = new UserBlock();
        ub.setBlocked(true);
        when(userBlockRepository.findByUserId(any())).thenReturn(Optional.of(ub));

        TransactionResponse res = service.validate(request("BDT", "10"));
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
        assertEquals("Blocked: user is blocked", res.message());
    }

    @Test
    void overLimitIsBlocked() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("BDT", "500"));
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
        assertEquals("Blocked: amount exceeds limit on a debit-restricted BDT account", res.message());
    }

    @Test
    void withinLimitIsAllowed() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("BDT", "50"));
        assertTrue(res.valid());
        assertFalse(res.permissionDenied());
    }

    @Test
    void nonBdtIsAllowed() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("USD", "500"));
        assertTrue(res.valid());
    }

    @Test
    void missingUserLimitThrows() {
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(ContextNotFoundException.class, () -> service.validate(request("BDT", "500")));
    }
}
