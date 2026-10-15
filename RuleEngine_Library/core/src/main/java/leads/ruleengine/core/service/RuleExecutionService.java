package leads.ruleengine.core.service;

import leads.ruleengine.core.config.RuleEngineProperties;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.dto.RequiredData;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The method a consuming service calls to run rules. Two overloads so global reference data is
 * OPTIONAL - a service uses it only if its rules need it.
 *
 * <p>Routing is driven by the REQUEST class the caller passes: its name selects the flow
 * ({@code RULE_FLOW.request_class}), the flow selects the ordered agenda groups, and the session
 * fires them in that order. The {@code fact} (context) is what the rules read; the request class
 * only decides the route.</p>
 *
 * <pre>
 *   ruleExecutionService.execute(context, LoanRequest.class);                 // rules read only the fact
 *   ruleExecutionService.execute(context, LoanRequest.class, globalContext);  // + shared reference data
 * </pre>
 *
 * <p><b>Optional - demand-driven loading</b> ({@link #requiredData(Class)}): ask which data the
 * flow's active rules read, and skip the lookups nobody needs. A service that never calls it keeps
 * working exactly as before.</p>
 *
 * <p>A fresh {@link KieSession} is created per call (sessions are not thread-safe) and disposed.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class RuleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RuleExecutionService.class);

    private final RuleBaseProvider ruleBaseProvider;
    private final DecisionFlowResolver flowResolver;
    private final RequiredDataResolver requiredDataResolver;
    private final RuleEngineProperties properties;

    public RuleExecutionService(RuleBaseProvider ruleBaseProvider,
                                DecisionFlowResolver flowResolver,
                                RequiredDataResolver requiredDataResolver,
                                RuleEngineProperties properties) {
        this.ruleBaseProvider = ruleBaseProvider;
        this.flowResolver = flowResolver;
        this.requiredDataResolver = requiredDataResolver;
        this.properties = properties;
    }

    /**
     * What the ACTIVE rules of the flow routed from {@code requestType} need. In memory, no I/O.
     *
     * <pre>
     * RequiredData need = ruleEngine.requiredData(TransactionRequest.class);
     * if (need.needs("userLimit")) { ... repository / service call ... }
     * </pre>
     *
     * <p>When {@code rule-engine.required-data.enabled} is false (the default) or {@code shadow} is
     * true, the answer is "load everything" - {@code needs(..)} is always true.</p>
     */
    public RequiredData requiredData(Class<?> requestType) {
        Long flowId = flowResolver.flowFor(requestType.getName());
        RequiredData real = requiredDataResolver.forFlow(flowId);
        RuleEngineProperties.RequiredData cfg = properties.getRequiredData();
        if (!cfg.isEnabled()) {
            return real.asEverything();
        }
        if (cfg.isShadow()) {
            log.debug("[required-data shadow] {} would load only {}", real.flowCode(),
                    real.complete() ? real.roots() : "everything");
            return real.asEverything();
        }
        return real;
    }

    /** Run the flow mapped to {@code requestType}, WITHOUT any global reference data. */
    public void execute(Object fact, Class<?> requestType) {
        run(fact, requestType, null);
    }

    /** Run the flow mapped to {@code requestType}, exposing {@code globalContext} to the rules. */
    public void execute(Object fact, Class<?> requestType, GlobalContext globalContext) {
        run(fact, requestType, globalContext);
    }

    private void run(Object fact, Class<?> requestType, GlobalContext globalContext) {
        Long flowId = flowResolver.flowFor(requestType.getName());
        List<String> groups = flowResolver.groupsFor(flowId);

        KieSession session = ruleBaseProvider.getContainer().newKieSession();
        try {
            if (globalContext != null) {
                try {
                    session.setGlobal("globalContext", globalContext);
                } catch (RuntimeException ignored) {
                    // DRL did not declare the global - nothing to inject.
                }
            }

            session.insert(fact);

            for (int i = groups.size() - 1; i >= 0; i--) {
                AgendaGroup group = session.getAgenda().getAgendaGroup(groups.get(i));
                if (group != null) {
                    group.setFocus();
                }
            }

            session.fireAllRules();
        } finally {
            session.dispose();
        }
    }
}
