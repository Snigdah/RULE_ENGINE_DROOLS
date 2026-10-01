package leads.ruleengine.core.controller;

import jakarta.validation.Valid;
import leads.ruleengine.core.dto.FlowDefinitionRequest;
import leads.ruleengine.core.dto.RequestMappingRequest;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.FlowAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Runtime routing management: define flows (ordered agenda groups) and map request classes
 * to flows. Every change takes effect immediately.
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default).</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin/flows")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowAdminController {

    private final FlowAdminService flowAdminService;
    private final DecisionFlowResolver flowResolver;

    public FlowAdminController(FlowAdminService flowAdminService, DecisionFlowResolver flowResolver) {
        this.flowAdminService = flowAdminService;
        this.flowResolver = flowResolver;
    }

    // ---- Flows ---------------------------------------------------------------------------

    @GetMapping
    public Map<String, List<String>> listFlows() {
        return flowAdminService.listFlows();
    }

    @PostMapping
    public ResponseEntity<Void> upsertFlow(@Valid @RequestBody FlowDefinitionRequest request) {
        flowAdminService.upsertFlow(request.flowName(), request.groups());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{flowName}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String flowName) {
        flowAdminService.deleteFlow(flowName);
        return ResponseEntity.noContent().build();
    }

    // ---- Request mappings ----------------------------------------------------------------

    @GetMapping("/mappings")
    public Map<String, String> listMappings() {
        return flowAdminService.listMappings();
    }

    @PostMapping("/mappings")
    public ResponseEntity<Void> upsertMapping(@Valid @RequestBody RequestMappingRequest request) {
        flowAdminService.upsertMapping(request.requestClass(), request.flowName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/mappings")
    public ResponseEntity<Void> deleteMapping(@RequestParam String requestClass) {
        flowAdminService.deleteMapping(requestClass);
        return ResponseEntity.noContent().build();
    }

    // ---- Combined view + manual reload ---------------------------------------------------

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return flowAdminService.overview();
    }

    /** Force a routing cache refresh from the DB (rarely needed; every write reloads already). */
    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
        flowResolver.reload();
        return ResponseEntity.ok().build();
    }
}
