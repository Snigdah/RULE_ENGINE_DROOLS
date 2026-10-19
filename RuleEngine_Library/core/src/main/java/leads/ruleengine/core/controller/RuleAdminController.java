package leads.ruleengine.core.controller;

import jakarta.validation.Valid;
import leads.ruleengine.core.dto.ActiveRequest;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.FieldRequest;
import leads.ruleengine.core.dto.RuleDefinitionRequest;
import leads.ruleengine.core.dto.RulePreviewResponse;
import leads.ruleengine.core.dto.RuleView;
import leads.ruleengine.core.model.RuleFieldCatalog;
import leads.ruleengine.core.service.RuleAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Runtime rule management for the Visual Rule Builder and for hand-written DRL. Rules are
 * addressed by group (to create) and by id (to read / update / delete).
 *
 * <ul>
 *   <li>{@code GET  /admin/groups/{groupId}/rules} - rules in one exact group.</li>
 *   <li>{@code POST /admin/groups/{groupId}/rules} - save a rule into that group (BUILDER or DRL).</li>
 *   <li>{@code GET/PUT/DELETE /admin/rules/{id}} - read / update / permanently delete one rule.</li>
 *   <li>{@code PUT /admin/rules/{id}/active} - activate/deactivate a rule (the on/off toggle).</li>
 *   <li>{@code POST /admin/rules/preview} - generate + compile-check DRL without saving.</li>
 *   <li>{@code POST /admin/rules/reload} - recompile the live rule base from the table.</li>
 *   <li>{@code GET  /admin/flows/{flowCode}/fields} - builder field palette (flow + shared).</li>
 * </ul>
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default).</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;

    public RuleAdminController(RuleAdminService ruleAdminService) {
        this.ruleAdminService = ruleAdminService;
    }

    @GetMapping("/groups/{groupId}/rules")
    public List<RuleView> listByGroup(@PathVariable Long groupId) {
        return ruleAdminService.listByGroup(groupId);
    }

    /** Save a rule into a group. With approval on: 202 + {@code ApprovalSubmission}, nothing saved yet. */
    @PostMapping("/groups/{groupId}/rules")
    public ResponseEntity<?> save(@PathVariable Long groupId,
                                  @Valid @RequestBody RuleDefinitionRequest request) {
        if (ruleAdminService.approvalEnabled()) {
            return ResponseEntity.accepted().body(ruleAdminService.submitSaveInGroup(groupId, request));
        }
        return ResponseEntity.ok(ruleAdminService.saveInGroup(groupId, request));
    }

    @GetMapping("/rules/{id}")
    public RuleView get(@PathVariable Long id) {
        return ruleAdminService.get(id);
    }

    /** New version of a rule. With approval on: 202 + {@code ApprovalSubmission}, nothing saved yet. */
    @PutMapping("/rules/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody RuleDefinitionRequest request) {
        if (ruleAdminService.approvalEnabled()) {
            return ResponseEntity.accepted().body(ruleAdminService.submitUpdate(id, request));
        }
        return ResponseEntity.ok(ruleAdminService.update(id, request));
    }

    /** Permanently delete a rule (the row is removed). Use the active toggle to merely disable it. */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (ruleAdminService.approvalEnabled()) {
            return ResponseEntity.accepted().body(ruleAdminService.submitDelete(id));
        }
        ruleAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Activate / deactivate a rule without deleting it (the UI on/off radio). */
    @PutMapping("/rules/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        if (ruleAdminService.approvalEnabled()) {
            return ResponseEntity.accepted().body(ruleAdminService.submitSetActive(id, request.active()));
        }
        return ResponseEntity.ok(ruleAdminService.setActive(id, request.active()));
    }

    /** Recompile the live rule base from the table (e.g. after changeboard auto-approved a change). */
    @PostMapping("/rules/reload")
    public ResponseEntity<Void> reload() {
        ruleAdminService.reloadRuleBase();
        return ResponseEntity.ok().build();
    }

    /** Preview the generated DRL for a builder spec, without saving it. */
    @PostMapping("/rules/preview")
    public RulePreviewResponse preview(@RequestBody BuilderRuleSpec spec) {
        return ruleAdminService.preview(spec);
    }

    /** Fields available to the builder for a flow (flow-scoped rows + shared rows). */
    @GetMapping("/flows/{flowCode}/fields")
    public List<RuleFieldCatalog> fields(@PathVariable String flowCode) {
        return ruleAdminService.fields(flowCode);
    }

    /** Add a builder field to the catalog (blank flowCode = shared). */
    @PostMapping("/fields")
    public RuleFieldCatalog addField(@Valid @RequestBody FieldRequest request) {
        return ruleAdminService.addField(request);
    }

    /** Permanently delete a catalog field. */
    @DeleteMapping("/fields/{id}")
    public ResponseEntity<Void> deleteField(@PathVariable Long id) {
        ruleAdminService.deleteField(id);
        return ResponseEntity.noContent().build();
    }
}
