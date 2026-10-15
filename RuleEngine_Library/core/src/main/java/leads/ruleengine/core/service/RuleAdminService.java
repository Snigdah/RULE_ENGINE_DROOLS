package leads.ruleengine.core.service;

import com.leads.model.ActionType;
import jakarta.transaction.Transactional;
import leads.ruleengine.core.approval.ApprovalSubmission;
import leads.ruleengine.core.approval.ChangeApprovalSubmitter;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.FieldRequest;
import leads.ruleengine.core.dto.RuleDefinitionRequest;
import leads.ruleengine.core.dto.RulePreviewResponse;
import leads.ruleengine.core.dto.RuleView;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RuleDefinition;
import leads.ruleengine.core.model.RuleFieldCatalog;
import leads.ruleengine.core.model.RuleFlow;
import leads.ruleengine.core.model.RuleGroup;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.repository.RuleFieldCatalogRepository;
import leads.ruleengine.core.repository.RuleFlowRepository;
import leads.ruleengine.core.repository.RuleGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages stored rules ({@code RULE_DEFINITION}): save/replace into a group, update by id,
 * deactivate, list, preview.
 *
 * <p><b>A rule lives in a group ROW.</b> A BUILDER rule's DRL is generated with context resolved
 * from that group: the fact type from the group's flow, the agenda-group from the group's code,
 * and a unique-name qualifier ({@code FLOW.GROUP}) so two groups may hold the same display name.
 * Builder rules therefore need a flow group; the shared COMMON group takes hand-written DRL only.</p>
 *
 * <p><b>Validate-before-swap:</b> on save the candidate rule is compiled together with the other
 * active rules FIRST. Only if the whole set compiles is the row saved and the rule base reloaded.</p>
 *
 * <p><b>Approval:</b> when {@code rule-engine.approval.enabled=true} the controller calls the
 * {@code submit*} methods instead. They run the same checks, build a detached copy of the rule
 * with the change applied and hand it to changeboard - the managed row is never modified, so the
 * rule table and the live rule base only change after final approval.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class RuleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RuleAdminService.class);

    private final RuleDefinitionRepository ruleRepository;
    private final RuleGroupRepository groupRepository;
    private final RuleFlowRepository flowRepository;
    private final RuleFieldCatalogRepository catalogRepository;
    private final RuleBaseProvider ruleBaseProvider;
    private final RuleDrlGenerator drlGenerator;
    private final RequiredDataResolver requiredDataResolver;
    private final ObjectProvider<ChangeApprovalSubmitter> approvals;

    public RuleAdminService(RuleDefinitionRepository ruleRepository,
                            RuleGroupRepository groupRepository,
                            RuleFlowRepository flowRepository,
                            RuleFieldCatalogRepository catalogRepository,
                            RuleBaseProvider ruleBaseProvider,
                            RuleDrlGenerator drlGenerator,
                            RequiredDataResolver requiredDataResolver,
                            ObjectProvider<ChangeApprovalSubmitter> approvals) {
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
        this.flowRepository = flowRepository;
        this.catalogRepository = catalogRepository;
        this.ruleBaseProvider = ruleBaseProvider;
        this.drlGenerator = drlGenerator;
        this.requiredDataResolver = requiredDataResolver;
        this.approvals = approvals;
    }

    /** True when rule writes go through maker-checker approval ({@code rule-engine.approval.enabled}). */
    public boolean approvalEnabled() {
        return approvals.getIfAvailable() != null;
    }

    /** Create or replace a rule by (group, name). */
    @Transactional
    public RuleView saveInGroup(Long groupId, RuleDefinitionRequest request) {
        RuleGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("No group with id " + groupId + "."));
        RuleDefinition entity = ruleRepository.findByGroup_IdAndRuleName(groupId, request.ruleName())
                .orElseGet(RuleDefinition::new);
        apply(entity, group, request);
        return persist(entity);
    }

    /** Update an existing rule by id. Its group does not change. */
    @Transactional
    public RuleView update(Long id, RuleDefinitionRequest request) {
        RuleDefinition entity = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
        RuleGroup group = entity.getGroup();
        // renaming must not collide with another rule in the same group
        ruleRepository.findByGroup_IdAndRuleName(group.getId(), request.ruleName()).ifPresent(other -> {
            if (!other.getId().equals(entity.getId())) {
                throw new IllegalArgumentException("Group already has a rule named '" + request.ruleName() + "'.");
            }
        });
        apply(entity, group, request);
        return persist(entity);
    }

    /** Permanently delete a rule (hard delete) and reload the rule base. */
    @Transactional
    public void delete(Long id) {
        ruleRepository.findById(id).ifPresent(rule -> {
            ruleRepository.delete(rule);
            ruleBaseProvider.reload();
            log.info("Rule {} ('{}') deleted; rule base reloaded.", id, rule.getRuleName());
        });
    }

    /** Activate or deactivate a rule (the on/off toggle). An inactive rule is not compiled/evaluated. */
    @Transactional
    public RuleView setActive(Long id, boolean active) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
        rule.setActive(active);
        rule.setStatus(active ? "ACTIVE" : "INACTIVE");
        rule.setUpdatedAt(LocalDateTime.now());
        RuleDefinition saved = ruleRepository.save(rule);
        ruleBaseProvider.reload();
        log.info("Rule {} ('{}') set active={}; rule base reloaded.", id, saved.getRuleName(), active);
        return toView(saved);
    }

    /** Recompile the live rule base from the table (e.g. after a change was auto-approved). */
    public void reloadRuleBase() {
        ruleBaseProvider.reload();
    }

    // ---- approval (rule-engine.approval.enabled=true) ------------------------------------

    /** Approval version of {@link #saveInGroup}: create (new name) or new version (existing name). */
    @Transactional
    public ApprovalSubmission submitSaveInGroup(Long groupId, RuleDefinitionRequest request) {
        ChangeApprovalSubmitter submitter = submitter();
        RuleGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("No group with id " + groupId + "."));
        submitter.assertNotPending(ChangeApprovalSubmitter.GROUP_TABLE, List.of(groupId));
        Optional<RuleDefinition> existing = ruleRepository.findByGroup_IdAndRuleName(groupId, request.ruleName());
        existing.ifPresent(r -> submitter.assertNotPending(ChangeApprovalSubmitter.RULE_TABLE, List.of(r.getId())));

        RuleDefinition candidate = existing.map(RuleAdminService::copyOf).orElseGet(RuleDefinition::new);
        return submitCandidate(submitter, candidate, group, request, existing.isPresent());
    }

    /** Approval version of {@link #update}: a new version of an existing rule. */
    @Transactional
    public ApprovalSubmission submitUpdate(Long id, RuleDefinitionRequest request) {
        ChangeApprovalSubmitter submitter = submitter();
        RuleDefinition current = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
        RuleGroup group = current.getGroup();
        ruleRepository.findByGroup_IdAndRuleName(group.getId(), request.ruleName()).ifPresent(other -> {
            if (!other.getId().equals(current.getId())) {
                throw new IllegalArgumentException("Group already has a rule named '" + request.ruleName() + "'.");
            }
        });
        submitter.assertNotPending(ChangeApprovalSubmitter.RULE_TABLE, List.of(id));
        submitter.assertNotPending(ChangeApprovalSubmitter.GROUP_TABLE, List.of(group.getId()));
        return submitCandidate(submitter, copyOf(current), group, request, true);
    }

    /** Approval version of {@link #delete}. */
    @Transactional
    public ApprovalSubmission submitDelete(Long id) {
        ChangeApprovalSubmitter submitter = submitter();
        RuleDefinition current = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
        submitter.assertNotPending(ChangeApprovalSubmitter.RULE_TABLE, List.of(id));
        submitter.assertNotPending(ChangeApprovalSubmitter.GROUP_TABLE, List.of(current.getGroup().getId()));
        return submitter.submit(copyOf(current), ActionType.DELETE, "RULE", id,
                "Delete rule '" + current.getRuleName() + "' from group " + current.getGroup().getGroupCode());
    }

    /** Approval version of {@link #setActive}. Activation is compile-checked like a save. */
    @Transactional
    public ApprovalSubmission submitSetActive(Long id, boolean active) {
        ChangeApprovalSubmitter submitter = submitter();
        RuleDefinition current = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
        submitter.assertNotPending(ChangeApprovalSubmitter.RULE_TABLE, List.of(id));
        submitter.assertNotPending(ChangeApprovalSubmitter.GROUP_TABLE, List.of(current.getGroup().getId()));
        if (current.isActive() == active) {
            throw new IllegalArgumentException("Rule " + id + " is already " + (active ? "active" : "inactive") + ".");
        }
        RuleDefinition candidate = copyOf(current);
        candidate.setActive(active);
        candidate.setStatus(active ? "ACTIVE" : "INACTIVE");
        candidate.setUpdatedAt(LocalDateTime.now());
        if (active) {
            ruleBaseProvider.validate(candidateActiveSet(candidate));
        }
        return submitter.submit(candidate, ActionType.UPDATE, "RULE", id,
                (active ? "Activate" : "Deactivate") + " rule '" + current.getRuleName() + "'");
    }

    /** Apply the request to the detached candidate, compile-check it, and submit it. */
    private ApprovalSubmission submitCandidate(ChangeApprovalSubmitter submitter, RuleDefinition candidate,
                                               RuleGroup group, RuleDefinitionRequest request, boolean exists) {
        apply(candidate, group, request);
        candidate.setCreatedBy(submitter.currentUser());
        ruleBaseProvider.validate(candidateActiveSet(candidate));
        ActionType action = exists ? ActionType.UPDATE : ActionType.CREATE;
        String summary = exists
                ? "Update rule '" + candidate.getRuleName() + "' to version " + candidate.getVersion()
                : "Create rule '" + candidate.getRuleName() + "'";
        return submitter.submit(candidate, action, "RULE", candidate.getId(),
                summary + " in group " + group.getGroupCode());
    }

    private ChangeApprovalSubmitter submitter() {
        ChangeApprovalSubmitter submitter = approvals.getIfAvailable();
        if (submitter == null) {
            throw new IllegalStateException("Approval is not enabled (rule-engine.approval.enabled=false).");
        }
        return submitter;
    }

    /**
     * A detached copy of a rule (every column, same id and group). Changes are made on the copy so
     * the managed row is never dirtied - Hibernate would otherwise save it at commit and skip approval.
     */
    public static RuleDefinition copyOf(RuleDefinition r) {
        RuleDefinition c = new RuleDefinition();
        c.setId(r.getId());
        c.setGroup(r.getGroup());
        c.setRuleName(r.getRuleName());
        c.setSourceType(r.getSourceType());
        c.setSourceJson(r.getSourceJson());
        c.setDrlText(r.getDrlText());
        c.setVersion(r.getVersion());
        c.setStatus(r.getStatus());
        c.setActive(r.isActive());
        c.setCreatedBy(r.getCreatedBy());
        c.setUpdatedAt(r.getUpdatedAt());
        return c;
    }

    /** All rules in one exact group (active and inactive, so the UI can show/flip the toggle). */
    @Transactional
    public List<RuleView> listByGroup(Long groupId) {
        return ruleRepository.findByGroup_IdOrderByRuleNameAsc(groupId).stream()
                .map(RuleAdminService::toView).toList();
    }

    /** One rule by id. */
    @Transactional
    public RuleView get(Long id) {
        return ruleRepository.findById(id).map(RuleAdminService::toView)
                .orElseThrow(() -> new IllegalArgumentException("No rule with id " + id + "."));
    }

    /** Fields the builder offers for a flow (the flow's own fields plus shared fields). */
    public List<RuleFieldCatalog> fields(String flowCode) {
        RuleFlow flow = flowRepository.findByFlowCode(flowCode)
                .orElseThrow(() -> new IllegalArgumentException("No flow with code '" + flowCode + "'."));
        return catalogRepository.findForFlow(flow.getId());
    }

    /** Add a catalog field. Blank {@code flowCode} = a shared field shown in every flow. */
    @Transactional
    public RuleFieldCatalog addField(FieldRequest req) {
        RuleFieldCatalog c = new RuleFieldCatalog();
        if (req.flowCode() != null && !req.flowCode().isBlank()) {
            RuleFlow flow = flowRepository.findByFlowCode(req.flowCode())
                    .orElseThrow(() -> new IllegalArgumentException("No flow with code '" + req.flowCode() + "'."));
            c.setFlow(flow);
        }
        c.setFieldKey(req.fieldKey());
        c.setLabel(req.label());
        c.setDataType(req.dataType().trim().toUpperCase());
        c.setDrlPath(req.drlPath());
        c.setAllowedOps(req.allowedOps());
        c.setAllowedValues(req.allowedValues());
        RuleFieldCatalog saved = catalogRepository.save(c);
        requiredDataResolver.reload();   // known roots may have changed
        log.info("Catalog field '{}' added (flow={}).", saved.getFieldKey(),
                saved.getFlow() == null ? "shared" : saved.getFlow().getFlowCode());
        return saved;
    }

    /** Permanently delete a catalog field. */
    @Transactional
    public void deleteField(Long id) {
        catalogRepository.deleteById(id);
        requiredDataResolver.reload();   // known roots may have changed
        log.info("Catalog field {} deleted.", id);
    }

    /**
     * Preview: generate the DRL for a builder spec WITHOUT saving, and compile-check it. The spec
     * carries {@code flow} + {@code agendaGroup}; the fact type is resolved from the flow. The live
     * engine is never touched.
     */
    public RulePreviewResponse preview(BuilderRuleSpec spec) {
        String drl;
        try {
            String factType = resolveFactType(spec.flow);
            String qualifier = (spec.flow == null ? "PREVIEW" : spec.flow)
                    + "." + (isBlank(spec.agendaGroup) ? "PREVIEW" : spec.agendaGroup);
            String agendaGroup = isBlank(spec.agendaGroup) ? "PREVIEW" : spec.agendaGroup;
            drl = drlGenerator.generate(spec, factType, agendaGroup, qualifier);
        } catch (RuntimeException e) {
            return new RulePreviewResponse(null, false, e.getMessage());
        }
        try {
            RuleDefinition probe = new RuleDefinition();
            probe.setRuleName(isBlank(spec.ruleName) ? "preview" : spec.ruleName);
            probe.setSourceType(RuleDefinition.SOURCE_BUILDER);
            probe.setDrlText(drl);
            ruleBaseProvider.validate(List.of(probe));
            return new RulePreviewResponse(drl, true, null);
        } catch (RuleCompilationException e) {
            return new RulePreviewResponse(drl, false, e.getMessage());
        }
    }

    // ---- internals -----------------------------------------------------------------------

    /** Set all fields on the entity from the request, generating DRL for BUILDER rules. */
    private void apply(RuleDefinition entity, RuleGroup group, RuleDefinitionRequest request) {
        entity.setGroup(group);
        entity.setRuleName(request.ruleName());

        String sourceType = request.sourceType() == null
                ? RuleDefinition.SOURCE_DRL
                : request.sourceType().trim().toUpperCase();
        entity.setSourceType(sourceType);

        if (RuleDefinition.SOURCE_BUILDER.equals(sourceType)) {
            if (group.getFlow() == null) {
                throw new IllegalArgumentException(
                        "Builder rules must live in a flow group (the shared COMMON group takes DRL only).");
            }
            if (isBlank(request.sourceJson())) {
                throw new IllegalArgumentException("sourceJson is required when sourceType is BUILDER.");
            }
            String factType = group.getFlow().getFactType();
            String agendaGroup = group.getGroupCode();
            String qualifier = group.getFlow().getFlowCode() + "." + group.getGroupCode();
            entity.setSourceJson(request.sourceJson());
            entity.setDrlText(drlGenerator.fromJson(request.sourceJson(), factType, agendaGroup, qualifier));
        } else {
            if (isBlank(request.drl())) {
                throw new IllegalArgumentException("drl is required when sourceType is DRL.");
            }
            entity.setSourceJson(null);
            entity.setDrlText(request.drl());
        }

        entity.setActive(true);
        entity.setStatus("ACTIVE");
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + 1);
        entity.setCreatedBy(request.createdBy());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    /** Validate the candidate against the live set, save, reload, and return the view. */
    private RuleView persist(RuleDefinition entity) {
        ruleBaseProvider.validate(candidateActiveSet(entity));
        RuleDefinition saved = ruleRepository.save(entity);
        ruleBaseProvider.reload();
        log.info("Rule '{}' saved in group {} (type {}, version {}); rule base reloaded.",
                saved.getRuleName(), saved.getGroup().getId(), saved.getSourceType(), saved.getVersion());
        return toView(saved);
    }

    private String resolveFactType(String flowCode) {
        if (isBlank(flowCode)) {
            throw new RuleCompilationException("Builder spec has no flow to resolve a fact type from.");
        }
        return flowRepository.findByFlowCode(flowCode)
                .map(RuleFlow::getFactType)
                .orElseThrow(() -> new RuleCompilationException("No flow '" + flowCode + "' to resolve a fact type."));
    }

    /**
     * The active set as it WOULD look with the candidate applied: all currently-active rules, with
     * the candidate replacing the same row (matched by id) or added if new.
     */
    private List<RuleDefinition> candidateActiveSet(RuleDefinition candidate) {
        List<RuleDefinition> set = new ArrayList<>();
        for (RuleDefinition existing : ruleRepository.findByActiveTrue()) {
            if (candidate.getId() == null || !candidate.getId().equals(existing.getId())) {
                set.add(existing);
            }
        }
        set.add(candidate);
        return set;
    }

    private static RuleView toView(RuleDefinition r) {
        RuleGroup g = r.getGroup();
        RuleFlow f = g.getFlow();
        return new RuleView(r.getId(), g.getId(), g.getGroupCode(), f == null ? null : f.getFlowCode(),
                r.getRuleName(), r.getSourceType(), r.isActive(), r.getVersion(), r.getSourceJson(), r.getDrlText());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
