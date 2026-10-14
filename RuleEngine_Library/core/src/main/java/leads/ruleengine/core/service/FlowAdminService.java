package leads.ruleengine.core.service;

import jakarta.transaction.Transactional;
import leads.ruleengine.core.dto.FlowDefinitionRequest;
import leads.ruleengine.core.dto.FlowView;
import leads.ruleengine.core.dto.GroupView;
import leads.ruleengine.core.exception.FlowNotConfiguredException;
import leads.ruleengine.core.exception.GroupInUseException;
import leads.ruleengine.core.model.RuleFlow;
import leads.ruleengine.core.model.RuleGroup;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import leads.ruleengine.core.repository.RuleFieldCatalogRepository;
import leads.ruleengine.core.repository.RuleFlowRepository;
import leads.ruleengine.core.repository.RuleGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for routing: the flow master ({@code RULE_FLOW}) and its agenda groups ({@code RULE_GROUP}).
 *
 * <p><b>Two independent operations</b> on a flow or a group (and on a rule, in {@link RuleAdminService}):
 * <ul>
 *   <li><b>delete</b> - permanently removes the row (force cascades to children);</li>
 *   <li><b>activate/deactivate</b> - flips the {@code active} flag only. An inactive flow or group
 *       is skipped at runtime (its rules are not evaluated), but the row and its rules stay.</li>
 * </ul>
 *
 * Every write reloads {@link DecisionFlowResolver} (and the rule base when rules are removed) so
 * changes take effect immediately - no restart.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class FlowAdminService {

    private static final Logger log = LoggerFactory.getLogger(FlowAdminService.class);

    private final RuleFlowRepository flowRepository;
    private final RuleGroupRepository groupRepository;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleFieldCatalogRepository catalogRepository;
    private final DecisionFlowResolver flowResolver;
    private final RuleBaseProvider ruleBaseProvider;

    public FlowAdminService(RuleFlowRepository flowRepository,
                            RuleGroupRepository groupRepository,
                            RuleDefinitionRepository ruleRepository,
                            RuleFieldCatalogRepository catalogRepository,
                            DecisionFlowResolver flowResolver,
                            RuleBaseProvider ruleBaseProvider) {
        this.flowRepository = flowRepository;
        this.groupRepository = groupRepository;
        this.ruleRepository = ruleRepository;
        this.catalogRepository = catalogRepository;
        this.flowResolver = flowResolver;
        this.ruleBaseProvider = ruleBaseProvider;
    }

    // ---- Flows ---------------------------------------------------------------------------

    /** Every flow with the groups it runs (shared groups first, then its own, in order). */
    public List<FlowView> listFlows() {
        List<GroupView> shared = groupRepository.findByFlowIsNullOrderByOrderNoAsc().stream()
                .map(FlowAdminService::toView).toList();
        List<FlowView> out = new ArrayList<>();
        for (RuleFlow f : flowRepository.findAll()) {
            List<GroupView> groups = new ArrayList<>(shared);
            groupRepository.findByFlow_IdOrderByOrderNoAsc(f.getId())
                    .forEach(g -> groups.add(toView(g)));
            out.add(toView(f, groups));
        }
        return out;
    }

    /** Create or update a flow master row. Any {@code groups} listed are ensured to exist (append-only). */
    @Transactional
    public FlowView upsertFlow(FlowDefinitionRequest req) {
        RuleFlow flow = flowRepository.findByFlowCode(req.flowCode()).orElseGet(RuleFlow::new);
        boolean isNew = flow.getId() == null;
        flow.setFlowCode(req.flowCode());
        flow.setRequestClass(blankToNull(req.requestClass()));
        flow.setFactType(req.factType());
        flow.setLabel(req.label());
        if (isNew) {
            flow.setActive(true);   // new flows start active; an existing flow keeps its flag
        }
        RuleFlow saved = flowRepository.save(flow);

        if (req.groups() != null) {
            int next = nextOrder(saved.getId());
            for (String code : req.groups()) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                if (groupRepository.findByFlow_IdAndGroupCode(saved.getId(), code).isEmpty()) {
                    RuleGroup g = new RuleGroup();
                    g.setFlow(saved);
                    g.setGroupCode(code);
                    g.setOrderNo(next++);
                    g.setActive(true);
                    groupRepository.save(g);
                }
            }
        }
        flowResolver.reload();
        log.info("Flow '{}' upserted; routing reloaded.", saved.getFlowCode());
        return toView(saved, groupsOfFlow(saved.getFlowCode()));
    }

    /** Activate or deactivate a flow. An inactive flow runs no rules when its request hits. */
    @Transactional
    public FlowView setFlowActive(String flowCode, boolean active) {
        RuleFlow flow = requireFlow(flowCode);
        flow.setActive(active);
        RuleFlow saved = flowRepository.save(flow);
        flowResolver.reload();
        log.info("Flow '{}' set active={}; routing reloaded.", flowCode, active);
        return toView(saved, groupsOfFlow(flowCode));
    }

    /** Permanently delete a flow. 409 if it still has groups or fields, unless {@code force}. */
    @Transactional
    public void deleteFlow(String flowCode, boolean force) {
        RuleFlow flow = requireFlow(flowCode);
        List<RuleGroup> groups = groupRepository.findByFlow_IdOrderByOrderNoAsc(flow.getId());
        long fields = catalogRepository.countByFlow_Id(flow.getId());
        if ((!groups.isEmpty() || fields > 0) && !force) {
            throw new GroupInUseException("Flow '" + flowCode + "' still has " + groups.size()
                    + " group(s) and " + fields + " field(s). Delete them first, or call again with ?force=true.");
        }
        boolean removedRules = false;
        for (RuleGroup g : groups) {
            if (ruleRepository.countByGroup_Id(g.getId()) > 0) {
                ruleRepository.deleteByGroup_Id(g.getId());
                removedRules = true;
            }
            groupRepository.delete(g);
        }
        if (fields > 0) {
            catalogRepository.deleteByFlow_Id(flow.getId());
        }
        flowRepository.delete(flow);
        flowResolver.reload();
        if (removedRules) {
            ruleBaseProvider.reload();
        }
        log.info("Flow '{}' deleted (force={}, {} group(s)); reloaded.", flowCode, force, groups.size());
    }

    // ---- Groups --------------------------------------------------------------------------

    /** Groups a flow runs: shared groups first, then the flow's own, in order (active and inactive). */
    public List<GroupView> groupsOfFlow(String flowCode) {
        RuleFlow flow = requireFlow(flowCode);
        List<GroupView> out = new ArrayList<>();
        groupRepository.findByFlowIsNullOrderByOrderNoAsc().forEach(g -> out.add(toView(g)));
        groupRepository.findByFlow_IdOrderByOrderNoAsc(flow.getId()).forEach(g -> out.add(toView(g)));
        return out;
    }

    /** Add a group to a flow. Appends after existing groups when {@code orderNo} is omitted. */
    @Transactional
    public GroupView addGroup(String flowCode, String groupCode, Integer orderNo) {
        RuleFlow flow = requireFlow(flowCode);
        if (groupRepository.findByFlow_IdAndGroupCode(flow.getId(), groupCode).isPresent()) {
            throw new IllegalArgumentException("Flow '" + flowCode + "' already has a group '" + groupCode + "'.");
        }
        RuleGroup g = new RuleGroup();
        g.setFlow(flow);
        g.setGroupCode(groupCode);
        g.setOrderNo(orderNo != null ? orderNo : nextOrder(flow.getId()));
        g.setActive(true);
        RuleGroup saved = groupRepository.save(g);
        flowResolver.reload();
        log.info("Group '{}' added to flow '{}'; routing reloaded.", groupCode, flowCode);
        return toView(saved);
    }

    /** Add a SHARED group (flow_id NULL, e.g. COMMON) that every flow runs first. */
    @Transactional
    public GroupView addSharedGroup(String groupCode, Integer orderNo) {
        if (groupRepository.findByFlowIsNullAndGroupCode(groupCode).isPresent()) {
            throw new IllegalArgumentException("A shared group '" + groupCode + "' already exists.");
        }
        RuleGroup g = new RuleGroup();
        g.setFlow(null);
        g.setGroupCode(groupCode);
        g.setOrderNo(orderNo != null ? orderNo : 1);
        g.setActive(true);
        RuleGroup saved = groupRepository.save(g);
        flowResolver.reload();
        log.info("Shared group '{}' added; routing reloaded.", groupCode);
        return toView(saved);
    }

    /** Activate or deactivate a group. An inactive group is skipped; its rules are not evaluated. */
    @Transactional
    public GroupView setGroupActive(Long groupId, boolean active) {
        RuleGroup g = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("No group with id " + groupId + "."));
        g.setActive(active);
        RuleGroup saved = groupRepository.save(g);
        flowResolver.reload();
        log.info("Group {} ('{}') set active={}; routing reloaded.", groupId, g.getGroupCode(), active);
        return toView(saved);
    }

    /** Rewrite the firing order of a flow's own groups from the given id order. */
    @Transactional
    public void reorder(String flowCode, List<Long> groupIds) {
        RuleFlow flow = requireFlow(flowCode);
        int order = groupRepository.findByFlowIsNullOrderByOrderNoAsc().size() + 1;
        for (Long id : groupIds) {
            RuleGroup g = groupRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("No group with id " + id + "."));
            if (g.getFlow() == null || !g.getFlow().getId().equals(flow.getId())) {
                throw new IllegalArgumentException("Group " + id + " does not belong to flow '" + flowCode + "'.");
            }
            g.setOrderNo(order++);
            groupRepository.save(g);
        }
        flowResolver.reload();
        log.info("Flow '{}' groups reordered; routing reloaded.", flowCode);
    }

    /** Permanently delete a group. 409 if it still has rules, unless {@code force} - then its rules go too. */
    @Transactional
    public void deleteGroup(Long groupId, boolean force) {
        RuleGroup g = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("No group with id " + groupId + "."));
        long rules = ruleRepository.countByGroup_Id(groupId);
        if (rules > 0 && !force) {
            throw new GroupInUseException("Group " + groupId + " ('" + g.getGroupCode() + "') has " + rules
                    + " rule(s). Delete them first, or call again with ?force=true.");
        }
        boolean hadRules = rules > 0;
        if (hadRules) {
            ruleRepository.deleteByGroup_Id(groupId);
        }
        groupRepository.delete(g);
        flowResolver.reload();
        if (hadRules) {
            ruleBaseProvider.reload();
        }
        log.info("Group {} ('{}') deleted (force={}, {} rule(s) removed); reloaded.",
                groupId, g.getGroupCode(), force, rules);
    }

    // ---- helpers -------------------------------------------------------------------------

    private RuleFlow requireFlow(String flowCode) {
        return flowRepository.findByFlowCode(flowCode)
                .orElseThrow(() -> new FlowNotConfiguredException("No flow with code '" + flowCode + "'."));
    }

    /** Next order_no for a flow's own groups: after shared groups and after existing own groups. */
    private int nextOrder(Long flowId) {
        int sharedCount = groupRepository.findByFlowIsNullOrderByOrderNoAsc().size();
        int ownMax = groupRepository.findByFlow_IdOrderByOrderNoAsc(flowId).stream()
                .mapToInt(RuleGroup::getOrderNo).max().orElse(sharedCount);
        return Math.max(sharedCount, ownMax) + 1;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static GroupView toView(RuleGroup g) {
        return new GroupView(g.getId(), g.getGroupCode(), g.getOrderNo(), g.isShared(), g.isActive());
    }

    private static FlowView toView(RuleFlow f, List<GroupView> groups) {
        return new FlowView(f.getId(), f.getFlowCode(), f.getRequestClass(), f.getFactType(),
                f.getLabel(), f.isActive(), groups);
    }
}
