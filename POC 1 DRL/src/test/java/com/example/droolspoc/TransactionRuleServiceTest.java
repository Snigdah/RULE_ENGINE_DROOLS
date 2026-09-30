package com.example.droolspoc;

import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.context.data.BlockedUsers;
import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.FlowGroup;
import com.example.droolspoc.model.RequestFlowMap;
import com.example.droolspoc.model.RuleFile;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.FlowGroupRepository;
import com.example.droolspoc.repository.RequestFlowMapRepository;
import com.example.droolspoc.repository.RuleFileRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.service.DecisionFlowResolver;
import com.example.droolspoc.service.RuleBaseProvider;
import com.example.droolspoc.service.RuleExecutionService;
import com.example.droolspoc.service.TransactionRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionRuleServiceTest {

    // Self-contained DRL for the test (mirrors rules/transaction-rules.drl).
    private static final String DRL = """
            package com.example.droolspoc.rules
            import com.example.droolspoc.model.ValidationContext
            import com.example.droolspoc.context.data.BlockedUsers
            global com.example.droolspoc.context.GlobalContext globalContext;

            rule "Block transaction for a blocked user"
                agenda-group "COMMON"
                salience 100
                when
                    $ctx : ValidationContext()
                    eval(globalContext.get(BlockedUsers.class).isBlocked($ctx.getTransaction().getUserId()))
                then
                    $ctx.getTransaction().setValid(false);
                    $ctx.getTransaction().setPermissionDenied(true);
                    $ctx.getTransaction().setValidationMessage("Blocked: user is blocked");
                    drools.halt();
            end

            rule "Block over-limit debit-restricted BDT transaction"
                agenda-group "TRANSFER"
                salience 10
                when
                    $ctx : ValidationContext(
                        transaction.currency == "BDT",
                        product.drRes == 1,
                        transaction.amount > userLimit.limit
                    )
                then
                    $ctx.getTransaction().setValid(false);
                    $ctx.getTransaction().setPermissionDenied(true);
                    $ctx.getTransaction().setValidationMessage("Blocked: amount exceeds limit on a debit-restricted BDT account");
            end
            """;

    private UserLimitRepository userLimitRepository;
    private ProductRepository productRepository;
    private GlobalContext globalContext;
    private TransactionRuleService service;

    @BeforeEach
    void setUp() {
        RuleFile ruleFile = new RuleFile();
        ruleFile.setFileName("transaction-rules.drl");
        ruleFile.setDrlText(DRL);
        ruleFile.setActive(true);
        RuleFileRepository ruleFileRepository = mock(RuleFileRepository.class);
        when(ruleFileRepository.findByActiveTrue()).thenReturn(List.of(ruleFile));

        RuleBaseProvider ruleBaseProvider = new RuleBaseProvider(ruleFileRepository);
        ruleBaseProvider.reload();

        userLimitRepository = mock(UserLimitRepository.class);
        productRepository = mock(ProductRepository.class);
        globalContext = new GlobalContext();
        globalContext.register(BlockedUsers.class, new BlockedUsers(Set.of()));

        // DB-driven flow config (mocked): TransactionRequest -> TRANSFER_TRANSACTION -> [COMMON, TRANSFER]
        RequestFlowMap mapping = new RequestFlowMap();
        mapping.setRequestClass("com.example.droolspoc.dto.TransactionRequest");
        mapping.setFlowName("TRANSFER_TRANSACTION");
        RequestFlowMapRepository requestFlowMapRepository = mock(RequestFlowMapRepository.class);
        when(requestFlowMapRepository.findAll()).thenReturn(List.of(mapping));

        FlowGroup common = new FlowGroup();
        common.setFlowName("TRANSFER_TRANSACTION");
        common.setAgendaGroup("COMMON");
        common.setOrderNo(1);
        FlowGroup transfer = new FlowGroup();
        transfer.setFlowName("TRANSFER_TRANSACTION");
        transfer.setAgendaGroup("TRANSFER");
        transfer.setOrderNo(2);
        FlowGroupRepository flowGroupRepository = mock(FlowGroupRepository.class);
        when(flowGroupRepository.findAll()).thenReturn(List.of(common, transfer));

        DecisionFlowResolver flowResolver =
                new DecisionFlowResolver(requestFlowMapRepository, flowGroupRepository);
        flowResolver.reload();

        RuleExecutionService ruleExecutionService =
                new RuleExecutionService(ruleBaseProvider, globalContext, flowResolver);
        service = new TransactionRuleService(
                userLimitRepository, productRepository,
                Executors.newVirtualThreadPerTaskExecutor(), ruleExecutionService);
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
        stubContext("100", 1);
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
    void missingUserLimitThrows() {
        when(userLimitRepository.findByUserIdAndTransactionModeAndDrCrType(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(ContextNotFoundException.class,
                () -> service.validate(request("USER-001", "BDT", "500")));
    }
}
