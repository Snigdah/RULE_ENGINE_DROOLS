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
 * CRUD for the routing tables: request-class-to-flow mappings, and flow-to-agenda-group
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

    /** Define or replace a flow's ordered agenda groups (delete-then-reinsert in order). */
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

    /** All flows as {@code flowName -> ordered agenda groups}. */
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

    // ---- Request mappings (request class -> flow) ----------------------------------------

    /** Map (or remap) a request class to a flow. */
    @Transactional
    public RequestFlowMap upsertMapping(String requestClass, String flowName) {
        RequestFlowMap mapping = requestFlowMapRepository.findByRequestClass(requestClass)
                .orElseGet(RequestFlowMap::new);
        mapping.setRequestClass(requestClass);
        mapping.setFlowName(flowName);
        RequestFlowMap saved = requestFlowMapRepository.save(mapping);
        flowResolver.reload();
        log.info("Mapping '{}' -> '{}' saved; routing reloaded.", requestClass, flowName);
        return saved;
    }

    @Transactional
    public void deleteMapping(String requestClass) {
        requestFlowMapRepository.findByRequestClass(requestClass).ifPresent(m -> {
            requestFlowMapRepository.delete(m);
            flowResolver.reload();
            log.info("Mapping '{}' deleted; routing reloaded.", requestClass);
        });
    }

    /** All mappings as {@code requestClass -> flowName}. */
    public Map<String, String> listMappings() {
        return requestFlowMapRepository.findAll().stream()
                .collect(Collectors.toMap(RequestFlowMap::getRequestClass, RequestFlowMap::getFlowName));
    }

    /** Both tables in one view, handy for a config dashboard. */
    public Map<String, Object> overview() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mappings", listMappings());
        view.put("flows", listFlows());
        return view;
    }
}
