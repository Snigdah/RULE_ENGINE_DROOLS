package leads.ruleengine.core.service;

import leads.ruleengine.core.context.GlobalContext;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaGroup;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The method a consuming service calls to run rules. Two overloads so global reference data
 * is OPTIONAL - a service uses it only if its rules need it.
 *
 * <p>Without reference data:</p>
 * <pre>
 *   ruleExecutionService.execute(context, TransactionRequest.class);
 * </pre>
 *
 * <p>With reference data (e.g. a blocked-user set the rules read as a {@code global}):</p>
 * <pre>
 *   ruleExecutionService.execute(context, TransactionRequest.class, globalContext);
 * </pre>
 *
 * <p><b>Why the fact is {@code Object}:</b> the fact/context is business-specific - each
 * service inserts its own type. The library stays business-agnostic by taking it as
 * {@code Object} and letting each service's DRL match on its own concrete type.</p>
 *
 * <p>Routing is driven by {@code requestType}: its class name selects the flow, the flow
 * selects the ordered agenda groups, and the session fires them in that order. A fresh
 * {@link KieSession} is created per call (sessions are not thread-safe) and always disposed.</p>
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

    /**
     * Run the flow mapped to {@code requestType} against {@code fact}, WITHOUT any global
     * reference data. Use this when the rules read only the inserted fact.
     */
    public void execute(Object fact, Class<?> requestType) {
        run(fact, requestType, null);
    }

    /**
     * Run the flow mapped to {@code requestType} against {@code fact}, exposing
     * {@code globalContext} to the rules as the {@code global GlobalContext globalContext}.
     * Use this when the rules read shared reference data.
     */
    public void execute(Object fact, Class<?> requestType, GlobalContext globalContext) {
        run(fact, requestType, globalContext);
    }

    private void run(Object fact, Class<?> requestType, GlobalContext globalContext) {
        String flow = flowResolver.flowFor(requestType.getName());
        List<String> groups = flowResolver.groupsFor(flow);

        KieSession session = ruleBaseProvider.getContainer().newKieSession();
        try {
            if (globalContext != null) {
                // A DRL may or may not declare `global GlobalContext globalContext;`. Setting
                // a global the DRL did not declare throws, so guard it.
                try {
                    session.setGlobal("globalContext", globalContext);
                } catch (RuntimeException ignored) {
                    // DRL did not declare the global - nothing to inject.
                }
            }

            session.insert(fact);

            // setFocus is a stack: focus the LAST group first so the FIRST group ends up on
            // top and fires first. This makes orderNo the real firing order.
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
