package leads.ruleengine.core.service;

import jakarta.annotation.PostConstruct;
import leads.ruleengine.core.exception.FlowNotConfiguredException;
import leads.ruleengine.core.model.FlowGroup;
import leads.ruleengine.core.model.RequestFlowMap;
import leads.ruleengine.core.repository.FlowGroupRepository;
import leads.ruleengine.core.repository.RequestFlowMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves "which flow, and which agenda groups in what order" for a given request class,
 * from the database - so routing is configuration, not code.
 *
 * <p>Two lookups are cached in memory and refreshed by {@link #reload()} whenever the admin
 * API changes a mapping or a flow:
 * <ul>
 *   <li>{@code requestClass -> flowName}</li>
 *   <li>{@code flowName -> ordered list of agenda groups}</li>
 * </ul>
 * The caches are {@code volatile} and replaced wholesale on reload, so lookups are lock-free
 * and always see a consistent snapshot.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class DecisionFlowResolver {

    private static final Logger log = LoggerFactory.getLogger(DecisionFlowResolver.class);

    private final RequestFlowMapRepository requestFlowMapRepository;
    private final FlowGroupRepository flowGroupRepository;

    private volatile Map<String, String> requestToFlow = Map.of();
    private volatile Map<String, List<String>> flowToGroups = Map.of();

    public DecisionFlowResolver(RequestFlowMapRepository requestFlowMapRepository,
                                FlowGroupRepository flowGroupRepository) {
        this.requestFlowMapRepository = requestFlowMapRepository;
        this.flowGroupRepository = flowGroupRepository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** Rebuild both caches from the DB. Called at startup and after every admin change. */
    public synchronized void reload() {
        Map<String, String> freshRequestToFlow = requestFlowMapRepository.findAll().stream()
                .collect(Collectors.toMap(RequestFlowMap::getRequestClass, RequestFlowMap::getFlowName));

        Map<String, List<String>> freshFlowToGroups = flowGroupRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        FlowGroup::getFlowName,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingInt(FlowGroup::getOrderNo))
                                        .map(FlowGroup::getAgendaGroup)
                                        .collect(Collectors.toList()))));

        this.requestToFlow = freshRequestToFlow;
        this.flowToGroups = freshFlowToGroups;
        log.info("Flow routing reloaded: {} request mapping(s), {} flow(s).",
                freshRequestToFlow.size(), freshFlowToGroups.size());
    }

    /** Flow name for a request class, or fail clearly if the mapping row is missing. */
    public String flowFor(String requestClass) {
        String flow = requestToFlow.get(requestClass);
        if (flow == null) {
            throw new FlowNotConfiguredException(
                    "No flow mapped for request class '" + requestClass + "'. Add one via POST /admin/flows/mappings.");
        }
        return flow;
    }

    /** Ordered agenda groups for a flow, or fail clearly if the flow has no groups. */
    public List<String> groupsFor(String flowName) {
        List<String> groups = flowToGroups.get(flowName);
        if (groups == null || groups.isEmpty()) {
            throw new FlowNotConfiguredException(
                    "Flow '" + flowName + "' has no agenda groups. Define it via POST /admin/flows.");
        }
        return groups;
    }
}
