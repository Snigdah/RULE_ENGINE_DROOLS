package leads.ruleengine.core.approval;

import com.leads.changeboard.ApprovalRegisterService;
import com.leads.changeboard.hibernate.ApprovalHibernateService;
import com.leads.config.EligibleUserApiClient;
import com.leads.config.NotificationClient;
import jakarta.persistence.EntityManager;
import leads.ruleengine.core.config.RuleEngineProperties;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.RuleBaseProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wiring for changeboard maker-checker approval of rule and group changes, only when
 * {@code rule-engine.approval.enabled=true}: switches on changeboard's Feign clients (eligible
 * approvers from Centrino, notifications) and the submit/approve beans. Without the
 * eligible-approver client changeboard would auto-approve every change.
 *
 * <p>Beans changeboard needs in every case live in
 * {@code leads.ruleengine.autoconfigure.ChangeboardSupportAutoConfiguration}.</p>
 *
 * @author K M Farhat Snigdah
 */
public class RuleEngineApprovalConfiguration {

    private RuleEngineApprovalConfiguration() {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "rule-engine.approval", name = "enabled", havingValue = "true")
    @EnableFeignClients(clients = {EligibleUserApiClient.class, NotificationClient.class})
    public static class Enabled {

        @Bean
        public ApprovalRequestContext ruleEngineApprovalRequestContext(RuleEngineProperties properties) {
            return new ApprovalRequestContext(properties);
        }

        @Bean
        public ChangeApprovalSubmitter changeApprovalSubmitter(ApprovalHibernateService approvalHibernateService,
                                                               ApprovalRequestContext requestContext,
                                                               JdbcTemplate jdbcTemplate,
                                                               EntityManager entityManager) {
            return new ChangeApprovalSubmitter(approvalHibernateService, requestContext, jdbcTemplate, entityManager);
        }

        @Bean
        public RuleApprovalProcessor ruleApprovalProcessor(ApprovalRegisterService approvalRegisterService,
                                                           RuleDefinitionRepository ruleRepository,
                                                           RuleBaseProvider ruleBaseProvider,
                                                           DecisionFlowResolver flowResolver,
                                                           EntityManager entityManager) {
            return new RuleApprovalProcessor(approvalRegisterService, ruleRepository, ruleBaseProvider,
                    flowResolver, entityManager);
        }
    }
}
