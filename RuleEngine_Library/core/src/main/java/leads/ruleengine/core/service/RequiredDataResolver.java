package leads.ruleengine.core.service;

import leads.ruleengine.core.config.RuleEngineProperties;
import leads.ruleengine.core.dto.RequiredData;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes {@link RequiredData} for every flow ONCE per reload and serves it from memory.
 *
 * <p>The firing set of a flow mirrors exactly what {@link DecisionFlowResolver} focuses at runtime:
 * active shared groups plus the flow's own active groups, and inside them only active rules. An
 * inactive flow fires nothing, so it needs nothing.</p>
 *
 * <p><b>Order matters:</b> {@link RuleBaseProvider#reload()} and {@link DecisionFlowResolver#reload()}
 * call {@link #reload()} BEFORE they swap in new rules / routing - "data first, rules second" - so a
 * new rule never runs for a new request before its data is being loaded.</p>
 *
 * <p>Never breaks a reload and never fails a request: any error while analysing keeps the previous
 * answers (or, for a flow never analysed, "load everything").</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class RequiredDataResolver {

    private static final Logger log = LoggerFactory.getLogger(RequiredDataResolver.class);

    private final RuleFlowRepository flowRepository;
    private final RuleGroupRepository groupRepository;
    private final RuleDefinitionRepository ruleRepository;
    private final RuleFieldCatalogRepository catalogRepository;
    private final FieldUsageAnalyzer analyzer;
    private final RuleEngineProperties properties;

    /** flow id -> answer. Swapped atomically. */
    private volatile Map<Long, RequiredData> byFlowId = Map.of();
    /** flow code -> answer (admin endpoint). */
    private volatile Map<String, RequiredData> byFlowCode = Map.of();

    public RequiredDataResolver(RuleFlowRepository flowRepository,
                                RuleGroupRepository groupRepository,
                                RuleDefinitionRepository ruleRepository,
                                RuleFieldCatalogRepository catalogRepository,
                                FieldUsageAnalyzer analyzer,
                                RuleEngineProperties properties) {
        this.flowRepository = flowRepository;
        this.groupRepository = groupRepository;
        this.ruleRepository = ruleRepository;
        this.catalogRepository = catalogRepository;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    /** Per-request call: a map read, no I/O. Unknown flow -> load everything. */
    public RequiredData forFlow(Long flowId) {
        RequiredData data = byFlowId.get(flowId);
        return data != null ? data : RequiredData.everything(String.valueOf(flowId), Set.of());
    }

    /** For the admin endpoint. Empty when no flow has this code. */
    public Optional<RequiredData> forFlowCode(String flowCode) {
        return Optional.ofNullable(byFlowCode.get(flowCode));
    }

    /** Recompute every flow's answer from the DB and swap it in. */
    @Transactional(readOnly = true)
    public synchronized void reload() {
        try {
            doReload();
        } catch (RuntimeException e) {
            // keep the previous answers; never break a reload of rules/routing because of this
            log.error("RequiredData reload failed - keeping previous answers.", e);
        }
    }

    private void doReload() {
        boolean trustHints = properties.getRequiredData().isTrustDrlHints();

        List<RuleFieldCatalog> catalog = catalogRepository.findAll();
        Map<String, String> pathByKey = new HashMap<>();
        catalog.forEach(c -> pathByKey.putIfAbsent(c.getFieldKey(), c.getDrlPath()));

        Map<Long, List<RuleDefinition>> rulesByGroup = ruleRepository.findByActiveTrue().stream()
                .filter(r -> r.getGroup() != null)
                .collect(Collectors.groupingBy(r -> r.getGroup().getId()));

        List<RuleGroup> groups = groupRepository.findAll();
        List<RuleGroup> sharedActive = groups.stream()
                .filter(g -> g.isShared() && g.isActive())
                .collect(Collectors.toList());

        Map<Long, RequiredData> freshById = new HashMap<>();
        Map<String, RequiredData> freshByCode = new HashMap<>();

        for (RuleFlow flow : flowRepository.findAll()) {
            Set<String> known = knownRoots(flow, catalog, groups, rulesByGroup, pathByKey, trustHints);
            RequiredData data;
            if (!flow.isActive()) {
                data = RequiredData.nothing(flow.getFlowCode(), known);   // inactive flow fires nothing
            } else {
                List<RuleGroup> firing = new ArrayList<>(sharedActive);
                groups.stream()
                        .filter(g -> !g.isShared() && g.isActive() && g.getFlow().getId().equals(flow.getId()))
                        .forEach(firing::add);
                data = analyse(flow.getFlowCode(), firing, rulesByGroup, pathByKey, known, trustHints);
            }
            freshById.put(flow.getId(), data);
            freshByCode.put(flow.getFlowCode(), data);
            log.info("RequiredData {}: complete={} roots={} fields={}",
                    data.flowCode(), data.complete(), data.roots(), data.fields());
        }

        this.byFlowId = Map.copyOf(freshById);
        this.byFlowCode = Map.copyOf(freshByCode);
    }

    private RequiredData analyse(String flowCode, List<RuleGroup> firing,
                                 Map<Long, List<RuleDefinition>> rulesByGroup,
                                 Map<String, String> pathByKey, Set<String> known, boolean trustHints) {
        Set<String> fields = new TreeSet<>();
        for (RuleGroup group : firing) {
            for (RuleDefinition rule : rulesByGroup.getOrDefault(group.getId(), List.of())) {
                Optional<Set<String>> used = analyzer.fieldsOf(rule, trustHints);
                if (used.isEmpty()) {
                    log.info("RequiredData {}: rule '{}' ({}) usage unknown -> flow loads everything.",
                            flowCode, rule.getRuleName(), rule.getSourceType());
                    return RequiredData.everything(flowCode, known);
                }
                fields.addAll(used.get());
            }
        }
        Set<String> roots = fields.stream()
                .map(f -> rootOf(f, pathByKey))
                .collect(Collectors.toCollection(TreeSet::new));
        return new RequiredData(flowCode, roots, fields, known, true);
    }

    /** Every root this flow can have: its catalog rows (own + shared) plus roots named by DRL hints. */
    private Set<String> knownRoots(RuleFlow flow, List<RuleFieldCatalog> catalog, List<RuleGroup> groups,
                                   Map<Long, List<RuleDefinition>> rulesByGroup,
                                   Map<String, String> pathByKey, boolean trustHints) {
        Set<String> known = new TreeSet<>();
        catalog.stream()
                .filter(c -> c.getFlow() == null || c.getFlow().getId().equals(flow.getId()))
                .forEach(c -> known.add(rootOf(c.getFieldKey(), pathByKey)));
        if (trustHints) {
            groups.stream()
                    .filter(g -> g.isShared() || g.getFlow().getId().equals(flow.getId()))
                    .flatMap(g -> rulesByGroup.getOrDefault(g.getId(), List.of()).stream())
                    .filter(r -> RuleDefinition.SOURCE_DRL.equals(r.getSourceType()))
                    .map(r -> analyzer.drlHint(r.getDrlText()))
                    .flatMap(Optional::stream)
                    .flatMap(Set::stream)
                    .forEach(f -> known.add(rootOf(f, pathByKey)));
        }
        return known;
    }

    /** {@code "userLimit.limit"} -> catalog path {@code "userLimit!.limit"} -> root {@code "userLimit"}. */
    static String rootOf(String fieldKey, Map<String, String> pathByKey) {
        String path = pathByKey.getOrDefault(fieldKey, fieldKey);
        if (path == null || path.isBlank()) {
            path = fieldKey;
        }
        path = path.replace("!", "").trim();
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }
}
