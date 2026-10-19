package leads.ruleengine.core.approval;

import com.leads.changeboard.hibernate.ApprovalRegisterHibernateService;
import com.leads.model.ApprovalStatus;
import jakarta.persistence.EntityManager;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.RuleBaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Runs a checker's decision for a rule-engine approval set, in THIS service.
 *
 * <p>It must run here, not in Sentinel: on final approval changeboard rebuilds the rule/group
 * entity from its class name and writes the rule tables, and only this service has those classes
 * and the live rule base.</p>
 *
 * <p>Calls changeboard's documented Hibernate API ({@code processApprovalHibernate}) directly.
 * Before that, the set is checked to be a rule-engine set submitted through the Hibernate path:
 * {@code processApprovalHibernate} itself hands LEGACY sets over to the legacy SQL flow, which would
 * not apply a rule-engine change correctly, so anything else is refused here.</p>
 *
 * <p>One transaction: changeboard records the decision (and on final approval applies the change);
 * then the whole active rule set is compiled. If it no longer compiles the exception rolls the
 * approval back, so a broken rule can never be approved into the tables. After commit the rule
 * base and routing are reloaded so the approved change is live immediately.</p>
 *
 * @author K M Farhat Snigdah
 */
public class RuleApprovalProcessor {

    private static final Logger log = LoggerFactory.getLogger(RuleApprovalProcessor.class);

    /** {@code TREG_APPROVAL.PROCESSING_MODE} of a set submitted with {@code sendForApprovalHibernate}. */
    static final String HIBERNATE_MODE = "HIBERNATE";

    /** Package of every entity this service submits for approval. */
    static final String RULE_ENGINE_CLASS_PREFIX = "leads.ruleengine.";

    private final ApprovalRegisterHibernateService approvalRegisterHibernateService;
    private final JdbcTemplate jdbcTemplate;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleBaseProvider ruleBaseProvider;
    private final DecisionFlowResolver flowResolver;
    private final EntityManager entityManager;

    public RuleApprovalProcessor(ApprovalRegisterHibernateService approvalRegisterHibernateService,
                                 JdbcTemplate jdbcTemplate,
                                 RuleDefinitionRepository ruleRepository,
                                 RuleBaseProvider ruleBaseProvider,
                                 DecisionFlowResolver flowResolver,
                                 EntityManager entityManager) {
        this.approvalRegisterHibernateService = approvalRegisterHibernateService;
        this.jdbcTemplate = jdbcTemplate;
        this.ruleRepository = ruleRepository;
        this.ruleBaseProvider = ruleBaseProvider;
        this.flowResolver = flowResolver;
        this.entityManager = entityManager;
    }

    /** Same parameters as changeboard's {@code processApprovalHibernate} (and Sentinel's endpoint). */
    @Transactional
    public void process(Long officeId, Long appId, Long setId, Integer approvalLevel, Integer nextApprovalLevel,
                        String remarks, String userId, Integer approvalFlag) {
        assertRuleEngineHibernateSet(setId);

        approvalRegisterHibernateService.processApprovalHibernate(officeId, appId, setId, approvalLevel,
                nextApprovalLevel, remarks, userId, approvalFlag);

        if (approvalFlag != null && approvalFlag == ApprovalStatus.APPROVED.getValue()) {
            // On a final approval changeboard has just written the rule tables in this transaction.
            entityManager.flush();
            ruleBaseProvider.validate(ruleRepository.findByActiveTrue());
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reloadAll();
            }
        });
        log.info("Approval set {} processed by {} (flag={}); rule base reload scheduled after commit.",
                setId, userId, approvalFlag);
    }

    /** Rebuild the rule base and routing from the tables (also exposed for manual use). */
    public void reloadAll() {
        flowResolver.reload();
        ruleBaseProvider.reload();
    }

    /** Refuse any set that is not a rule-engine change submitted through the Hibernate path. */
    private void assertRuleEngineHibernateSet(Long setId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT PROCESSING_MODE, QUALIFIED_CLASS_NAME FROM TREG_APPROVAL WHERE SET_ID = ?", setId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No approval set " + setId + ".");
        }
        Object mode = rows.get(0).get("PROCESSING_MODE");
        Object className = rows.get(0).get("QUALIFIED_CLASS_NAME");
        if (!HIBERNATE_MODE.equals(mode == null ? null : mode.toString().trim())) {
            throw new UnsupportedApprovalSetException("Approval set " + setId + " was not submitted through the "
                    + "Hibernate approval path (processing mode " + mode + "); the rule engine cannot process it.");
        }
        if (className == null || !className.toString().startsWith(RULE_ENGINE_CLASS_PREFIX)) {
            throw new UnsupportedApprovalSetException("Approval set " + setId + " is not a rule-engine change ("
                    + className + "); process it in the service that submitted it.");
        }
    }
}
