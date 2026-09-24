package com.example.droolspoc;

import com.example.droolspoc.config.DroolsConfig;
import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.context.data.BlockedUsers;
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
import java.util.Set;

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
    private GlobalContext globalContext;
    private TransactionRuleService service;

    @BeforeEach
    void setUp() {
        KieContainer container = new DroolsConfig().kieContainer();
        userLimitRepository = mock(UserLimitRepository.class);
        productRepository = mock(ProductRepository.class);
        globalContext = new GlobalContext();
        globalContext.register(BlockedUsers.class, new BlockedUsers(Set.of())); // no blocked users by default
        service = new TransactionRuleService(
                container, userLimitRepository, productRepository, globalContext);
    }

    private TransactionRequest request(String userId, String currency, String amount) {
        TransactionRequest r = new TransactionRequest();
        r.setUserId(userId);
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
    void blockedUserIsRejectedByRule() {
        globalContext.register(BlockedUsers.class, new BlockedUsers(Set.of("USER-002")));
        stubContext("100", 1); // context exists; blocked-user rule fires first
        TransactionResponse res = service.validate(request("USER-002", "BDT", "10"));
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
        assertEquals("Blocked: user is blocked", res.message());
    }

    @Test
    void overLimitRestrictedBdtIsBlocked() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("USER-001", "BDT", "500"));
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
    }

    @Test
    void withinLimitIsAllowed() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("USER-001", "BDT", "50"));
        assertTrue(res.valid());
        assertFalse(res.permissionDenied());
    }

    @Test
    void nonBdtIsAllowed() {
        stubContext("100", 1);
        TransactionResponse res = service.validate(request("USER-001", "USD", "500"));
        assertTrue(res.valid());
    }

    @Test
    void notDebitRestrictedIsAllowed() {
        stubContext("100", 0);
        TransactionResponse res = service.validate(request("USER-001", "BDT", "500"));
        assertTrue(res.valid());
    }

    @Test
    void missingUserLimitThrows() {
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(ContextNotFoundException.class,
                () -> service.validate(request("USER-001", "BDT", "500")));
    }
}
