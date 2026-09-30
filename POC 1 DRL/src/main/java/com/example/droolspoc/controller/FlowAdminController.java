package com.example.droolspoc.controller;

import com.example.droolspoc.dto.RequestMappingRequest;
import com.example.droolspoc.dto.FlowDefinitionRequest;
import com.example.droolspoc.service.DecisionFlowResolver;
import com.example.droolspoc.service.FlowAdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Manage the flow routing config via API (no SQL). Every change reloads the
 * cache automatically. Rules are managed under /admin/rules.
 */
@RestController
@RequestMapping("/admin/flows")
public class FlowAdminController {

    private final FlowAdminService flowAdminService;
    private final DecisionFlowResolver flowResolver;

    public FlowAdminController(FlowAdminService flowAdminService,
                              DecisionFlowResolver flowResolver) {
        this.flowAdminService = flowAdminService;
        this.flowResolver = flowResolver;
    }

    // ----- flow -> groups -----

    @GetMapping
    public Map<String, List<String>> listFlows() {
        return flowAdminService.listFlows();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upsertFlow(@Valid @RequestBody FlowDefinitionRequest req) {
        flowAdminService.upsertFlow(req.flowName(), req.groups());
        return ResponseEntity.ok(Map.of("status", "reloaded", "flow", req.flowName(), "groups", req.groups()));
    }

    @DeleteMapping("/{flowName}")
    public ResponseEntity<Map<String, Object>> deleteFlow(@PathVariable String flowName) {
        flowAdminService.deleteFlow(flowName);
        return ResponseEntity.ok(Map.of("status", "reloaded", "flow", flowName));
    }

    // ----- context class -> flow -----

    @GetMapping("/mappings")
    public List<Map<String, String>> listMappings() {
        return flowAdminService.listMappings();
    }

    @PostMapping("/mappings")
    public ResponseEntity<Map<String, Object>> upsertMapping(@Valid @RequestBody RequestMappingRequest req) {
        flowAdminService.upsertMapping(req.requestClass(), req.flowName());
        return ResponseEntity.ok(Map.of("status", "reloaded",
                "requestClass", req.requestClass(), "flow", req.flowName()));
    }

    @DeleteMapping("/mappings")
    public ResponseEntity<Map<String, Object>> deleteMapping(@RequestParam String requestClass) {
        flowAdminService.deleteMapping(requestClass);
        return ResponseEntity.ok(Map.of("status", "reloaded", "requestClass", requestClass));
    }

    // ----- whole routing picture -----

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return flowAdminService.overview();
    }

    // ----- manual reload (optional; the writes above already reload) -----

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        flowResolver.reload();
        return ResponseEntity.ok(Map.of("status", "flow config reloaded"));
    }
}
