package leads.ruleengine.core.service;

import jakarta.transaction.Transactional;
import leads.ruleengine.core.model.FlowGroup;
import leads.ruleengine.core.model.RequestFlowMap;
import leads.ruleengine.core.repository.FlowGroupRepository;
import leads.ruleengine.core.repository.RequestFlowMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD for the routing tables: fact/context-class-to-flow mappings, and flow-to-agenda-group
 * definitions. Every write reloads {@link DecisionFlowResolver} so changes take effect
 * immediately - no restart.
 *
 * @author K M Farhat Snigdah
 */
@Service
public class FlowAdminService {

    private static final Logger log = LoggerFactory.getLogger(FlowAdminService.class);

    private final RequestFlowMapRepository requestFlowMapRepository;
    private final FlowGroupRepository flowGroupRepository;
    private final DecisionFlowResolver flowResolver;

    public FlowAdminService(RequestFlowMapRepository requestFlowMapRepository,
                            FlowGroupRepository flowGroupRepository,
                            DecisionFlowResolver flowResolver) {
        this.requestFlowMapRepository = requestFlowMapRepository;
        this.flowGroupRepository = flowGroupRepository;
        this.flowResolver = flowResolver;
    }

    // ---- Flow definitions (flow -> ordered agenda groups) --------------------------------

    @Transactional
    public void upsertFlow(String flowName, List<String> groups) {
        flowGroupRepository.deleteByFlowName(flowName);
        int order = 1;
        for (String group : groups) {
            FlowGroup fg = new FlowGroup();
            fg.setFlowName(flowName);
            fg.setAgendaGroup(group);
            fg.setOrderNo(order++);
            flowGroupRepository.save(fg);
        }
        flowResolver.reload();
        log.info("Flow '{}' defined with {} group(s); routing reloaded.", flowName, groups.size());
    }

    @Transactional
    public void deleteFlow(String flowName) {
        flowGroupRepository.deleteByFlowName(flowName);
        flowResolver.reload();
        log.info("Flow '{}' deleted; routing reloaded.", flowName);
    }

    public Map<String, List<String>> listFlows() {
        return flowGroupRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        FlowGroup::getFlowName,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingInt(FlowGroup::getOrderNo))
                                        .map(FlowGroup::getAgendaGroup)
                                        .collect(Collectors.toList()))));
    }

    // ---- Mappings (fact/context class -> flow) -------------------------------------------

    /** Map (or remap) a fact/context class to a flow. */
    @Transactional
    public RequestFlowMap upsertMapping(String factType, String flowName) {
        RequestFlowMap mapping = requestFlowMapRepository.findByFactType(factType)
                .orElseGet(RequestFlowMap::new);
        mapping.setFactType(factType);
        mapping.setFlowName(flowName);
        RequestFlowMap saved = requestFlowMapRepository.save(mapping);
        flowResolver.reload();
        log.info("Mapping '{}' -> '{}' saved; routing reloaded.", factType, flowName);
        return saved;
    }

    @Transactional
    public void deleteMapping(String factType) {
        requestFlowMapRepository.findByFactType(factType).ifPresent(m -> {
            requestFlowMapRepository.delete(m);
            flowResolver.reload();
            log.info("Mapping '{}' deleted; routing reloaded.", factType);
        });
    }

    public Map<String, String> listMappings() {
        return requestFlowMapRepository.findAll().stream()
                .collect(Collectors.toMap(RequestFlowMap::getFactType, RequestFlowMap::getFlowName));
    }

    public Map<String, Object> overview() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mappings", listMappings());
        view.put("flows", listFlows());
        return view;
    }
}
