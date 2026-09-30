package com.example.droolspoc.service;

import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.model.ValidationContext;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs the rule engine. The flow is resolved from the REQUEST DTO class via the
 * DB (DecisionFlowResolver); the ValidationContext is the fact inserted into the
 * session. No flow name is hardcoded in the calling service.
 */
@Service
public class RuleExecutionService {

    private final RuleBaseProvider ruleBaseProvider;
    private final GlobalContext globalContext;
    private final DecisionFlowResolver flowResolver;

    public RuleExecutionService(RuleBaseProvider ruleBaseProvider,
                                GlobalContext globalContext,
                                DecisionFlowResolver flowResolver) {
        this.ruleBaseProvider = ruleBaseProvider;
        this.globalContext = globalContext;
        this.flowResolver = flowResolver;
    }

    /**
     * @param context     the fact inserted into the session
     * @param requestType the request DTO class - decides the flow (from DB)
     */
    public void execute(ValidationContext context, Class<?> requestType) {
        String flow = flowResolver.flowFor(requestType.getName());
        List<String> groups = flowResolver.groupsFor(flow);

        KieSession session = ruleBaseProvider.getContainer().newKieSession();
        try {
            // set the global only if the loaded rules declare it (uploaded DRLs may not)
            try {
                session.setGlobal("globalContext", globalContext);
            } catch (RuntimeException ignored) {
                // no 'global globalContext' declared in the active rules - fine
            }
            session.insert(context);

            // focus in reverse so the FIRST group in the flow fires first (focus is a stack)
            for (int i = groups.size() - 1; i >= 0; i--) {
                var group = session.getAgenda().getAgendaGroup(groups.get(i));
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
