package com.example.droolspoc.service;

import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.model.ValidationContext;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Runs a named decision flow against the rule engine. Callers pass the flow
 * name (e.g. "TRANSFER_TRANSACTION"); this class maps it to agenda groups and
 * runs them.
 */
@Service
public class RuleExecutionService {

    // Flow name -> agenda groups it runs, in order (first one runs first).
    private static final Map<String, List<String>> FLOWS = Map.of(
            "TRANSFER_TRANSACTION", List.of("COMMON", "TRANSFER")
    );

    private final KieContainer kieContainer;
    private final GlobalContext globalContext;

    public RuleExecutionService(KieContainer kieContainer, GlobalContext globalContext) {
        this.kieContainer = kieContainer;
        this.globalContext = globalContext;
    }

    /**
     * Run a flow WITH the global context exposed. This is the default - use it
     * whenever the flow's rules may read globalContext (e.g. the COMMON
     * blocked-user rule).
     */
    public void execute(ValidationContext context, String flow) {
        execute(context, flow, true);
    }

    /**
     * Run a flow, choosing whether to expose the global context.
     *
     * <p>Pass {@code withGlobalContext = false} ONLY for a flow whose rules
     * never use globalContext. If such a rule runs without the global set, it
     * dereferences a null global and throws NullPointerException.</p>
     */
    public void execute(ValidationContext context, String flow, boolean withGlobalContext) {
        List<String> groups = FLOWS.get(flow);
        if (groups == null) {
            throw new IllegalArgumentException("Unknown decision flow: " + flow);
        }

        KieSession session = kieContainer.newKieSession();
        try {
            if (withGlobalContext) {
                session.setGlobal("globalContext", globalContext);
            }
            session.insert(context);

            // Focus is a stack: the LAST focused group fires first. Focus the
            // list in reverse so the FIRST group (COMMON) ends up on top.
            for (int i = groups.size() - 1; i >= 0; i--) {
                session.getAgenda().getAgendaGroup(groups.get(i)).setFocus();
            }

            session.fireAllRules();
        } finally {
            session.dispose();
        }
    }
}
