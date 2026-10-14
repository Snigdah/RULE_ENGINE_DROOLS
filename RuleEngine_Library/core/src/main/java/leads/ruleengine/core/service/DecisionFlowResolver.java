package leads.ruleengine.core.service;

import jakarta.annotation.PostConstruct;
import leads.ruleengine.core.exception.FlowNotConfiguredException;
import leads.ruleengine.core.model.RuleFlow;
import leads.ruleengine.core.model.RuleGroup;
import leads.ruleengine.core.repository.RuleFlowRepository;
import leads.ruleengine.core.repository.RuleGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves "which flow, and which agenda groups in what order" for a given request class,
 * from the database - so routing is configuration, not code.
 *
 * <p><b>Active/inactive is enforced here.</b> Only ACTIVE flows and ACTIVE groups contribute to
 * the firing order: an inactive group is skipped, and an inactive flow contributes no groups at
 * all (so a business request mapped to an inactive flow runs nothing and simply passes through -
 * it is not an error). Inactive individual rules are dropped separately, in {@code RuleBaseProvider}.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class DecisionFlowResolver {

    private static final Logger log = LoggerFactory.getLogger(DecisionFlowResolver.class);

    private final RuleFlowRepository flowRepository;
    private final RuleGroupRepository groupRepository;

    /** request class -> flow id (every flow, so an inactive flow is "known" and passes through) */
    private volatile Map<String, Long> requestToFlow = Map.of();
    /** flow id -> ordered ACTIVE agenda-group codes (active shared first, then the flow's own) */
    private volatile Map<Long, List<String>> flowToGroups = Map.of();

    public DecisionFlowResolver(RuleFlowRepository flowRepository,
                                RuleGroupRepository groupRepository) {
        this.flowRepository = flowRepository;
        this.groupRepository = groupRepository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** Rebuild both caches from the DB. Called at startup and after every admin change. */
    public synchronized void reload() {
        List<RuleFlow> flows = flowRepository.findAll();

        Map<String, Long> freshRequestToFlow = new HashMap<>();
        for (RuleFlow f : flows) {
            if (f.getRequestClass() != null && !f.getRequestClass().isBlank()) {
                freshRequestToFlow.put(f.getRequestClass(), f.getId());
            }
        }

        List<RuleGroup> allGroups = groupRepository.findAll();

        // active shared groups (flow_id NULL) run first, in their own order
        List<String> sharedCodes = allGroups.stream()
                .filter(g -> g.isShared() && g.isActive())
                .sorted(Comparator.comparingInt(RuleGroup::getOrderNo))
                .map(RuleGroup::getGroupCode)
                .collect(Collectors.toList());

        // a flow's own ACTIVE groups, by flow id
        Map<Long, List<RuleGroup>> ownByFlow = allGroups.stream()
                .filter(g -> g.getFlow() != null && g.isActive())
                .collect(Collectors.groupingBy(g -> g.getFlow().getId()));

        Map<Long, List<String>> freshFlowToGroups = new HashMap<>();
        for (RuleFlow f : flows) {
            if (!f.isActive()) {
                continue;   // inactive flow -> no groups -> business hit runs nothing
            }
            List<String> ordered = new ArrayList<>(sharedCodes);
            ownByFlow.getOrDefault(f.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(RuleGroup::getOrderNo))
                    .forEach(g -> ordered.add(g.getGroupCode()));
            freshFlowToGroups.put(f.getId(), ordered);
        }

        this.requestToFlow = freshRequestToFlow;
        this.flowToGroups = freshFlowToGroups;
        log.info("Flow routing reloaded: {} request mapping(s), {} active flow(s) with groups, {} shared group(s).",
                freshRequestToFlow.size(), freshFlowToGroups.size(), sharedCodes.size());
    }

    /** Flow id for a request class, or fail clearly if the request class is not registered at all. */
    public Long flowFor(String requestClass) {
        Long flowId = requestToFlow.get(requestClass);
        if (flowId == null) {
            throw new FlowNotConfiguredException(
                    "No flow mapped for request class '" + requestClass + "'. Seed a RULE_FLOW row with this "
                            + "request_class (or POST /admin/flows with requestClass set).");
        }
        return flowId;
    }

    /**
     * Ordered ACTIVE agenda-group codes for a flow id. Empty when the flow is inactive, or has no
     * active groups - the caller then focuses nothing and the request passes through. Never throws:
     * "inactive" is a deliberate state, not a misconfiguration.
     */
    public List<String> groupsFor(Long flowId) {
        return flowToGroups.getOrDefault(flowId, List.of());
    }
}
