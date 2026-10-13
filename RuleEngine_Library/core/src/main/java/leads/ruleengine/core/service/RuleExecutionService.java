package leads.ruleengine.core.service;

import leads.ruleengine.core.context.GlobalContext;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaGroup;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The method a consuming service calls to run rules. Two overloads so global reference data is
 * OPTIONAL - a service uses it only if its rules need it.
 *
 * <p>Routing is driven by the REQUEST class the caller passes: its name selects the flow
 * ({@code REQUEST_FLOW_MAP.REQUEST_TYPE}), the flow selects the ordered agenda groups, and the
 * session fires them in that order. The {@code fact} (context) is what the rules read; the
 * request class only decides the route.</p>
 *
 * <pre>
 *   ruleExecutionService.execute(context, LoanRequest.class);                 // rules read only the fact
 *   ruleExecutionService.execute(context, LoanRequest.class, globalContext);  // + shared reference data
 * </pre>
 *
 * <p>A fresh {@link KieSession} is created per call (sessions are not thread-safe) and disposed.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class RuleExecutionService {

    private final RuleBaseProvider ruleBaseProvider;
    private final DecisionFlowResolver flowResolver;

    public RuleExecutionService(RuleBaseProvider ruleBaseProvider,
                                DecisionFlowResolver flowResolver) {
        this.ruleBaseProvider = ruleBaseProvider;
        this.flowResolver = flowResolver;
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
        String flow = flowResolver.flowFor(requestType.getName());
        List<String> groups = flowResolver.groupsFor(flow);

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
