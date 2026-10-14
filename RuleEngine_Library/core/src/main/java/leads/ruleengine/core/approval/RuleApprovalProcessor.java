package leads.ruleengine.core.approval;

import com.leads.changeboard.ApprovalRegisterService;
import com.leads.model.ApprovalStatus;
import jakarta.persistence.EntityManager;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.RuleBaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a checker's decision for a rule-engine approval set, in THIS service.
 *
 * <p>It must run here, not in Sentinel: on final approval changeboard rebuilds the rule/group
 * entity from its class name and writes the rule tables, and only this service has those classes
 * and the live rule base.</p>
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

    private final ApprovalRegisterService approvalRegisterService;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleBaseProvider ruleBaseProvider;
    private final DecisionFlowResolver flowResolver;
    private final EntityManager entityManager;

    public RuleApprovalProcessor(ApprovalRegisterService approvalRegisterService,
                                 RuleDefinitionRepository ruleRepository,
                                 RuleBaseProvider ruleBaseProvider,
                                 DecisionFlowResolver flowResolver,
                                 EntityManager entityManager) {
        this.approvalRegisterService = approvalRegisterService;
        this.ruleRepository = ruleRepository;
        this.ruleBaseProvider = ruleBaseProvider;
        this.flowResolver = flowResolver;
        this.entityManager = entityManager;
    }

    /** Same parameters as changeboard's {@code processApproval} (and Sentinel's endpoint). */
    @Transactional
    public void process(Long officeId, Long appId, Long setId, Integer approvalLevel, Integer nextApprovalLevel,
                        String remarks, String userId, Integer approvalFlag) {
        approvalRegisterService.processApproval(officeId, appId, setId, approvalLevel, nextApprovalLevel,
                remarks, userId, approvalFlag);

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
}
