package leads.ruleengine.core.controller;

import jakarta.validation.Valid;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.RuleDefinitionRequest;
import leads.ruleengine.core.dto.RulePreviewResponse;
import leads.ruleengine.core.model.RuleDefinition;
import leads.ruleengine.core.model.RuleFieldCatalog;
import leads.ruleengine.core.repository.RuleFieldCatalogRepository;
import leads.ruleengine.core.service.RuleAdminService;
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

/**
 * Runtime rule management for the Visual Rule Builder and for hand-written DRL.
 *
 * <ul>
 *   <li>{@code GET  /admin/rules} - list stored rules.</li>
 *   <li>{@code POST /admin/rules} - save a rule (BUILDER JSON or DRL). A bad rule is rejected
 *       (400) without disturbing the live engine.</li>
 *   <li>{@code DELETE /admin/rules/{ruleName}} - deactivate a rule.</li>
 *   <li>{@code GET  /admin/rules/fields?flow=FLOW} - the field catalog the builder dropdowns use,
 *       scoped to the flow plus the shared COMMON fields.</li>
 * </ul>
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default). Set it to
 * {@code false} in a service that wants rules managed elsewhere, or secure this path with the
 * AuthZ library.</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin/rules")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;
    private final RuleFieldCatalogRepository ruleFieldCatalogRepository;

    public RuleAdminController(RuleAdminService ruleAdminService,
                              RuleFieldCatalogRepository ruleFieldCatalogRepository) {
        this.ruleAdminService = ruleAdminService;
        this.ruleFieldCatalogRepository = ruleFieldCatalogRepository;
    }

    @GetMapping
    public List<RuleDefinition> list() {
        return ruleAdminService.list();
    }

    /** Save a rule: BUILDER (send {@code sourceJson}) or DRL (send {@code drl}). */
    @PostMapping
    public ResponseEntity<RuleDefinition> save(@Valid @RequestBody RuleDefinitionRequest request) {
        return ResponseEntity.ok(ruleAdminService.save(request));
    }

    /** Preview the generated DRL for a builder spec, without saving it. */
    @PostMapping("/preview")
    public RulePreviewResponse preview(@RequestBody BuilderRuleSpec spec) {
        return ruleAdminService.preview(spec);
    }

    @DeleteMapping("/{ruleName}")
    public ResponseEntity<Void> delete(@PathVariable String ruleName) {
        ruleAdminService.delete(ruleName);
        return ResponseEntity.noContent().build();
    }

    /** Fields available to the builder for a flow (flow-scoped rows + shared COMMON rows). */
    @GetMapping("/fields")
    public List<RuleFieldCatalog> fields(@RequestParam String flow) {
        return ruleFieldCatalogRepository.findByFlowNameInOrderByLabelAsc(List.of(flow, "COMMON"));
    }
}
