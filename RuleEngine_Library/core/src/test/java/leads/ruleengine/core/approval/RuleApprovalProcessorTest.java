package leads.ruleengine.core.approval;

import com.leads.changeboard.hibernate.ApprovalRegisterHibernateService;
import com.leads.model.ApprovalStatus;
import jakarta.persistence.EntityManager;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.RuleBaseProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuleApprovalProcessorTest {

    private static final String SELECT_SET =
            "SELECT PROCESSING_MODE, QUALIFIED_CLASS_NAME FROM TREG_APPROVAL WHERE SET_ID = ?";
    private static final int APPROVED = ApprovalStatus.APPROVED.getValue();

    private final ApprovalRegisterHibernateService changeboard = mock(ApprovalRegisterHibernateService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RuleDefinitionRepository ruleRepository = mock(RuleDefinitionRepository.class);
    private final RuleBaseProvider ruleBaseProvider = mock(RuleBaseProvider.class);
    private final DecisionFlowResolver flowResolver = mock(DecisionFlowResolver.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final RuleApprovalProcessor processor = new RuleApprovalProcessor(changeboard, jdbcTemplate,
            ruleRepository, ruleBaseProvider, flowResolver, entityManager);

    @BeforeEach
    void startSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void ruleEngineHibernateSetGoesStraightToProcessApprovalHibernate() {
        givenSet(10L, "HIBERNATE", "leads.ruleengine.core.model.RuleDefinition");
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of());

        processor.process(1L, 2L, 10L, 1, 0, "ok", "checker", APPROVED);

        verify(changeboard).processApprovalHibernate(1L, 2L, 10L, 1, 0, "ok", "checker", APPROVED);
        verify(entityManager).flush();
        verify(ruleBaseProvider).validate(List.of());
    }

    @Test
    void declineDoesNotCompileTheRuleSet() {
        givenSet(11L, "HIBERNATE", "leads.ruleengine.core.model.RuleGroup");

        processor.process(1L, 2L, 11L, 1, 0, "no", "checker", ApprovalStatus.DECLINED.getValue());

        verify(changeboard).processApprovalHibernate(any(), any(), eq(11L), any(), any(), anyString(), anyString(), any());
        verify(ruleBaseProvider, never()).validate(any());
    }

    @Test
    void legacySetIsRefusedBeforeChangeboardIsCalled() {
        givenSet(12L, "LEGACY", "leads.ruleengine.core.model.RuleDefinition");

        assertThatThrownBy(() -> processor.process(1L, 2L, 12L, 1, 0, "ok", "checker", APPROVED))
                .isInstanceOf(UnsupportedApprovalSetException.class)
                .hasMessageContaining("Hibernate approval path");
        verifyNoInteractions(changeboard);
    }

    @Test
    void missingModeIsRefused() {
        givenSet(13L, null, "leads.ruleengine.core.model.RuleDefinition");

        assertThatThrownBy(() -> processor.process(1L, 2L, 13L, 1, 0, "ok", "checker", APPROVED))
                .isInstanceOf(UnsupportedApprovalSetException.class);
        verifyNoInteractions(changeboard);
    }

    @Test
    void setFromAnotherServiceIsRefused() {
        givenSet(14L, "HIBERNATE", "com.leads.security.model.SomeSentinelEntity");

        assertThatThrownBy(() -> processor.process(1L, 2L, 14L, 1, 0, "ok", "checker", APPROVED))
                .isInstanceOf(UnsupportedApprovalSetException.class)
                .hasMessageContaining("not a rule-engine change");
        verifyNoInteractions(changeboard);
    }

    @Test
    void unknownSetIsRejected() {
        when(jdbcTemplate.queryForList(SELECT_SET, 15L)).thenReturn(List.of());

        assertThatThrownBy(() -> processor.process(1L, 2L, 15L, 1, 0, "ok", "checker", APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No approval set 15");
        verifyNoInteractions(changeboard);
    }

    private void givenSet(Long setId, String mode, String className) {
        Map<String, Object> row = new HashMap<>();
        row.put("PROCESSING_MODE", mode);
        row.put("QUALIFIED_CLASS_NAME", className);
        when(jdbcTemplate.queryForList(SELECT_SET, setId)).thenReturn(List.of(row));
    }
}
