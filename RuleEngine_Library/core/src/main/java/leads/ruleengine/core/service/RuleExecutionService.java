package leads.ruleengine.core.service;

import leads.ruleengine.core.config.RuleEngineProperties;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.context.RuleResult;
import leads.ruleengine.core.dto.RequiredData;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *   RuleResult r = ruleExecutionService.execute(context, LoanRequest.class);                // rules read only the fact
 *   RuleResult r = ruleExecutionService.execute(context, LoanRequest.class, globalContext); // + shared reference data
 *   if (!r.isValid()) { ... r.getMessage() ... }
 * </pre>
 *
 * <p><b>The decision is library-owned.</b> Each call returns a fresh {@link RuleResult}
 * (valid / permissionDenied / message). The context object only carries the data the rules read;
 * it needs no decision fields. For compatibility, if the context still has
 * {@code setValid / setPermissionDenied / setValidationMessage}, the result is also copied onto it.</p>
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
    public RuleResult execute(Object fact, Class<?> requestType) {
        return run(fact, requestType, null);
    }

    /** Run the flow mapped to {@code requestType}, exposing {@code globalContext} to the rules. */
    public RuleResult execute(Object fact, Class<?> requestType, GlobalContext globalContext) {
        return run(fact, requestType, globalContext);
    }

    private RuleResult run(Object fact, Class<?> requestType, GlobalContext globalContext) {
        RuleResult result = new RuleResult();
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

            try {
                session.setGlobal("result", result);
            } catch (RuntimeException ignored) {
                // no compiled rule declares the result global (e.g. empty rule base) - nothing to inject
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
        copyToLegacyContext(result, fact);
        return result;
    }

    // ---- compatibility: contexts that still carry the old decision fields ------------------

    private static final Map<Class<?>, Method[]> LEGACY_SETTERS = new ConcurrentHashMap<>();
    private static final Method[] NONE = new Method[0];

    /**
     * Older services read the decision from their own context ({@code context.isValid()}). If the
     * context still has the three setters, mirror the result onto it so those services keep working.
     */
    private static void copyToLegacyContext(RuleResult result, Object fact) {
        // only mirror a real decision: a legacy hand-written rule may have set the context itself
        if (fact == null || !result.isDecided()) {
            return;
        }
        Method[] setters = LEGACY_SETTERS.computeIfAbsent(fact.getClass(), RuleExecutionService::findLegacySetters);
        if (setters.length == 0) {
            return;
        }
        try {
            setters[0].invoke(fact, result.isValid());
            setters[1].invoke(fact, result.isPermissionDenied());
            if (result.getMessage() != null) {
                setters[2].invoke(fact, result.getMessage());
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // best effort only - the returned RuleResult is the source of truth
        }
    }

    private static Method[] findLegacySetters(Class<?> type) {
        try {
            return new Method[] {
                    type.getMethod("setValid", boolean.class),
                    type.getMethod("setPermissionDenied", boolean.class),
                    type.getMethod("setValidationMessage", String.class)
            };
        } catch (NoSuchMethodException e) {
            return NONE;
        }
    }
}
