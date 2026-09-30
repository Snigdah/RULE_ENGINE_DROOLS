package com.example.droolspoc.service;

import com.example.droolspoc.model.FlowGroup;
import com.example.droolspoc.model.RequestFlowMap;
import com.example.droolspoc.repository.FlowGroupRepository;
import com.example.droolspoc.repository.RequestFlowMapRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DB-driven flow configuration, cached in memory.
 *
 * <ul>
 *   <li>request_flow_map : requestClass -> flowName            (one-to-one)</li>
 *   <li>flow_group       : flowName -> [agendaGroup...] ordered (one-to-many)</li>
 * </ul>
 *
 * Edit those tables (via /admin/flows) and the cache reloads.
 */
@Service
public class DecisionFlowResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionFlowResolver.class);

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

    public synchronized void reload() {
        Map<String, String> r2f = new HashMap<>();
        for (RequestFlowMap row : requestFlowMapRepository.findAll()) {
            r2f.put(row.getRequestClass(), row.getFlowName());
        }

        Map<String, List<FlowGroup>> grouped = new HashMap<>();
        for (FlowGroup fg : flowGroupRepository.findAll()) {
            grouped.computeIfAbsent(fg.getFlowName(), k -> new ArrayList<>()).add(fg);
        }
        Map<String, List<String>> f2g = new HashMap<>();
        grouped.forEach((flow, list) -> {
            list.sort(Comparator.comparingInt(fg -> fg.getOrderNo() == null ? 0 : fg.getOrderNo()));
            f2g.put(flow, list.stream().map(FlowGroup::getAgendaGroup).toList());
        });

        this.requestToFlow = r2f;
        this.flowToGroups = f2g;
        LOGGER.info("Decision flow config reloaded: {} request mapping(s), {} flow(s)",
                r2f.size(), f2g.size());
    }

    /** Flow name for a request DTO class name. */
    public String flowFor(String requestClass) {
        String flow = requestToFlow.get(requestClass);
        if (flow == null) {
            throw new IllegalStateException(
                    "No decision flow mapped for request " + requestClass
                            + " (add a mapping via /admin/flows/mappings)");
        }
        return flow;
    }

    /** Ordered agenda groups for a flow. */
    public List<String> groupsFor(String flowName) {
        List<String> groups = flowToGroups.get(flowName);
        if (groups == null || groups.isEmpty()) {
            throw new IllegalStateException(
                    "No agenda groups for flow " + flowName
                            + " (define it via /admin/flows)");
        }
        return groups;
    }
}
