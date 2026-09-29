package com.example.droolspoc.service;

import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.model.ValidationContext;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Runs a named decision flow against the rule engine. The KieContainer comes
 * from RuleBaseProvider, so it always reflects the latest uploaded rules.
 */
@Service
public class RuleExecutionService {

    // Flow name -> agenda groups it runs, in order (first one runs first).
    private static final Map<String, List<String>> FLOWS = Map.of(
            "TRANSFER_TRANSACTION", List.of("COMMON", "TRANSFER")
    );

    private final RuleBaseProvider ruleBaseProvider;
    private final GlobalContext globalContext;

    public RuleExecutionService(RuleBaseProvider ruleBaseProvider, GlobalContext globalContext) {
        this.ruleBaseProvider = ruleBaseProvider;
        this.globalContext = globalContext;
    }

    public void execute(ValidationContext context, String flow) {
        execute(context, flow, true);
    }

    public void execute(ValidationContext context, String flow, boolean withGlobalContext) {
        List<String> groups = FLOWS.get(flow);
        if (groups == null) {
            throw new IllegalArgumentException("Unknown decision flow: " + flow);
        }

        // Fresh session from the CURRENT container (picks up any reloaded rules).
        KieSession session = ruleBaseProvider.getContainer().newKieSession();
        try {
            if (withGlobalContext) {
                session.setGlobal("globalContext", globalContext);
            }
            session.insert(context);

            for (int i = groups.size() - 1; i >= 0; i--) {
                var group = session.getAgenda().getAgendaGroup(groups.get(i));
                if (group != null) {
                    group.setFocus();   // group may not exist if no rule uses it yet
                }
            }

            session.fireAllRules();
        } finally {
            session.dispose();
        }
    }
}
