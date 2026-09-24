package com.example.droolspoc;

import com.example.droolspoc.config.DroolsConfig;
import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.service.TransactionRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionRuleServiceTest {

    private UserLimitRepository userLimitRepository;
    private ProductRepository productRepository;
    private TransactionRuleService service;

    @BeforeEach
    void setUp() {
        KieContainer container = new DroolsConfig().kieContainer();
        userLimitRepository = mock(UserLimitRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new TransactionRuleService(container, userLimitRepository, productRepository);
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
        ul.setUserId("USER-001");
        ul.setTransactionMode("TRANSFER");
        ul.setDrCrType("DR");
        ul.setLimit(new BigDecimal(limit));
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.of(ul));

        Product p = new Product();
        p.setSourceAccount("100001");
        p.setProductType("DEPOSIT");
        p.setFrequency("MONTHLY");
        p.setDrRes(drRes);
        when(productRepository.findBySourceAccount(any())).thenReturn(Optional.of(p));
    }

    @Test
    void overLimitRestrictedBdtIsBlocked() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("BDT", "500"));
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
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
    void notDebitRestrictedIsAllowed() {
        stubContext("100", 0);
        TransactionResponse res = service.validate(request("BDT", "500"));
        assertTrue(res.valid());
    }

    @Test
    void missingUserLimitThrows() {
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(ContextNotFoundException.class, () -> service.validate(request("BDT", "500")));
    }
}
