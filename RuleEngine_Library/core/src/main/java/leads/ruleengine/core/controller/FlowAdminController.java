package leads.ruleengine.core.controller;

import jakarta.validation.Valid;
import leads.ruleengine.core.dto.ActiveRequest;
import leads.ruleengine.core.dto.FlowDefinitionRequest;
import leads.ruleengine.core.dto.FlowView;
import leads.ruleengine.core.dto.GroupOrderRequest;
import leads.ruleengine.core.dto.GroupRequest;
import leads.ruleengine.core.dto.GroupView;
import leads.ruleengine.core.dto.RequiredData;
import leads.ruleengine.core.service.DecisionFlowResolver;
import leads.ruleengine.core.service.FlowAdminService;
import leads.ruleengine.core.service.RequiredDataResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Runtime routing management: flows (the master rows) and the agenda groups inside them.
 * Every change takes effect immediately.
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default).</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowAdminController {

    private final FlowAdminService flowAdminService;
    private final DecisionFlowResolver flowResolver;
    private final RequiredDataResolver requiredDataResolver;

    public FlowAdminController(FlowAdminService flowAdminService, DecisionFlowResolver flowResolver,
                               RequiredDataResolver requiredDataResolver) {
        this.flowAdminService = flowAdminService;
        this.flowResolver = flowResolver;
        this.requiredDataResolver = requiredDataResolver;
    }

    /**
     * What a flow's active rules read right now - roots, field keys, known roots and whether usage
     * was proven ({@code complete}). Shows the real analysis even when the feature is switched off.
     */
    @GetMapping("/flows/{flowCode}/required-data")
    public RequiredData requiredData(@PathVariable String flowCode) {
        return requiredDataResolver.forFlowCode(flowCode)
                .orElseThrow(() -> new IllegalArgumentException("No flow with code '" + flowCode + "'."));
    }

    // ---- Flows ---------------------------------------------------------------------------

    @GetMapping("/flows")
    public List<FlowView> listFlows() {
        return flowAdminService.listFlows();
    }

    @PostMapping("/flows")
    public FlowView upsertFlow(@Valid @RequestBody FlowDefinitionRequest request) {
        return flowAdminService.upsertFlow(request);
    }

    /** Activate / deactivate a flow (the UI on/off radio). An inactive flow runs no rules. */
    @PutMapping("/flows/{flowCode}/active")
    public FlowView setFlowActive(@PathVariable String flowCode, @Valid @RequestBody ActiveRequest request) {
        return flowAdminService.setFlowActive(flowCode, request.active());
    }

    /** Permanently delete a flow. 409 unless {@code force}, which cascades to its groups and rules. */
    @DeleteMapping("/flows/{flowCode}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String flowCode,
                                           @RequestParam(defaultValue = "false") boolean force) {
        flowAdminService.deleteFlow(flowCode, force);
        return ResponseEntity.noContent().build();
    }

    // ---- Groups --------------------------------------------------------------------------

    /** Create a SHARED group (flow_id NULL, e.g. COMMON) that every flow runs first. */
    @PostMapping("/groups/shared")
    public GroupView addSharedGroup(@Valid @RequestBody GroupRequest request) {
        return flowAdminService.addSharedGroup(request.groupCode(), request.orderNo());
    }

    @GetMapping("/flows/{flowCode}/groups")
    public List<GroupView> groups(@PathVariable String flowCode) {
        return flowAdminService.groupsOfFlow(flowCode);
    }

    @PostMapping("/flows/{flowCode}/groups")
    public GroupView addGroup(@PathVariable String flowCode, @Valid @RequestBody GroupRequest request) {
        return flowAdminService.addGroup(flowCode, request.groupCode(), request.orderNo());
    }

    @PutMapping("/flows/{flowCode}/groups/order")
    public ResponseEntity<Void> reorder(@PathVariable String flowCode, @Valid @RequestBody GroupOrderRequest request) {
        flowAdminService.reorder(flowCode, request.groupIds());
        return ResponseEntity.ok().build();
    }

    /** Activate / deactivate a group (the UI on/off radio). An inactive group is skipped at runtime. */
    @PutMapping("/groups/{groupId}/active")
    public GroupView setGroupActive(@PathVariable Long groupId, @Valid @RequestBody ActiveRequest request) {
        return flowAdminService.setGroupActive(groupId, request.active());
    }

    /** Permanently delete a group. 409 unless {@code force}, which deletes its rules too. */
    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId,
                                            @RequestParam(defaultValue = "false") boolean force) {
        flowAdminService.deleteGroup(groupId, force);
        return ResponseEntity.noContent().build();
    }

    // ---- Manual reload -------------------------------------------------------------------

    /** Force a routing cache refresh from the DB (rarely needed; every write reloads already). */
    @PostMapping("/flows/reload")
    public ResponseEntity<Void> reload() {
        flowResolver.reload();
        return ResponseEntity.ok().build();
    }
}
