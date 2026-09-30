package com.example.droolspoc.service;

import com.example.droolspoc.model.FlowGroup;
import com.example.droolspoc.model.RequestFlowMap;
import com.example.droolspoc.repository.FlowGroupRepository;
import com.example.droolspoc.repository.RequestFlowMapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRUD for the DB-driven flow config. Every change reloads the in-memory cache,
 * so routing changes take effect immediately (no restart).
 *
 *  - flow  = flowName + ordered agenda groups     (flow_group table)
 *  - mapping = requestClass -> flowName            (request_flow_map table)
 */
@Service
public class FlowAdminService {

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

    // ----- flow -> groups -----

    /** Define (or replace) a flow's ordered agenda groups. */
    @Transactional
    public void upsertFlow(String flowName, List<String> groups) {
        flowGroupRepository.deleteByFlowName(flowName);   // replace existing
        int order = 1;
        for (String group : groups) {
            FlowGroup fg = new FlowGroup();
            fg.setFlowName(flowName);
            fg.setAgendaGroup(group);
            fg.setOrderNo(order++);
            flowGroupRepository.save(fg);
        }
        flowResolver.reload();
    }

    @Transactional
    public void deleteFlow(String flowName) {
        flowGroupRepository.deleteByFlowName(flowName);
        flowResolver.reload();
    }

    public Map<String, List<String>> listFlows() {
        Map<String, List<FlowGroup>> grouped = new LinkedHashMap<>();
        flowGroupRepository.findAll()
                .forEach(fg -> grouped.computeIfAbsent(fg.getFlowName(), k -> new ArrayList<>()).add(fg));
        Map<String, List<String>> out = new LinkedHashMap<>();
        grouped.forEach((flow, list) -> {
            list.sort(Comparator.comparingInt(fg -> fg.getOrderNo() == null ? 0 : fg.getOrderNo()));
            out.put(flow, list.stream().map(FlowGroup::getAgendaGroup).toList());
        });
        return out;
    }

    // ----- request DTO class -> flow -----

    /** Map (or re-map) a request DTO class to a flow. */
    @Transactional
    public void upsertMapping(String requestClass, String flowName) {
        RequestFlowMap row = requestFlowMapRepository.findByRequestClass(requestClass)
                .orElseGet(RequestFlowMap::new);
        row.setRequestClass(requestClass);
        row.setFlowName(flowName);
        requestFlowMapRepository.save(row);
        flowResolver.reload();
    }

    @Transactional
    public void deleteMapping(String requestClass) {
        requestFlowMapRepository.findByRequestClass(requestClass)
                .ifPresent(requestFlowMapRepository::delete);
        flowResolver.reload();
    }

    public List<Map<String, String>> listMappings() {
        return requestFlowMapRepository.findAll().stream()
                .map(m -> Map.of("requestClass", m.getRequestClass(), "flowName", m.getFlowName()))
                .toList();
    }

    /** The whole routing picture: mappings + flow definitions. */
    public Map<String, Object> overview() {
        return Map.of("mappings", listMappings(), "flows", listFlows());
    }
}
