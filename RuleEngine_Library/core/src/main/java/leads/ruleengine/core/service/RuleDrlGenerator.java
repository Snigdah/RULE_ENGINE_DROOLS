package leads.ruleengine.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RequestFlowMap;
import leads.ruleengine.core.model.RuleFieldCatalog;
import leads.ruleengine.core.repository.RequestFlowMapRepository;
import leads.ruleengine.core.repository.RuleFieldCatalogRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns a Visual Rule Builder spec ({@code SOURCE_JSON}) into executable DRL ({@code DRL_TEXT}).
 *
 * <p><b>Fully DB-driven.</b> The condition fields come from {@code RULE_FIELD_CATALOG}, and the
 * fact/context type the rule matches comes from {@code REQUEST_FLOW_MAP.FACT_TYPE} for the rule's
 * flow. There is no per-service YAML - the mapping is routing data, like everything else.</p>
 *
 * <p><b>Result contract.</b> The generated {@code then} calls {@code setValid(boolean)},
 * {@code setPermissionDenied(boolean)} and (with a message) {@code setValidationMessage(String)}
 * on the inserted fact. A service that authors builder rules exposes those setters on its context.</p>
 *
 * <p><b>AND / OR and IF / ELSE.</b> {@code match=all} joins constraints with {@code ,} (AND),
 * {@code match=any} with {@code ||} (OR). An optional {@code otherwise} branch emits a second rule
 * whose constraints are the De Morgan negation of the first.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class RuleDrlGenerator {

    private static final Map<String, String> NEG = Map.of(
            ">", "<=", "<", ">=", ">=", "<", "<=", ">", "==", "!=", "!=", "==");

    private final RequestFlowMapRepository requestFlowMapRepository;
    private final RuleFieldCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuleDrlGenerator(RequestFlowMapRepository requestFlowMapRepository,
                            RuleFieldCatalogRepository catalogRepository) {
        this.requestFlowMapRepository = requestFlowMapRepository;
        this.catalogRepository = catalogRepository;
    }

    /** Parse the builder JSON and generate DRL. */
    public String fromJson(String sourceJson) {
        BuilderRuleSpec spec;
        try {
            spec = objectMapper.readValue(sourceJson, BuilderRuleSpec.class);
        } catch (Exception e) {
            throw new RuleCompilationException("Invalid builder JSON: " + e.getMessage());
        }
        return generate(spec);
    }

    /** Generate DRL from an already-parsed spec. */
    public String generate(BuilderRuleSpec spec) {
        if (spec.conditions == null || spec.conditions.isEmpty()) {
            throw new RuleCompilationException("Rule '" + spec.ruleName + "' has no conditions.");
        }
        String factFqn = resolveFactType(spec);
        String pkg = factFqn.contains(".") ? factFqn.substring(0, factFqn.lastIndexOf('.')) : "rules";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append(ruleBlock(spec.ruleName, spec, factFqn, pattern(spec, false), spec.then));
        if (spec.otherwise != null && spec.otherwise.decision != null) {
            sb.append("\n\n");
            sb.append(ruleBlock(spec.ruleName + " - otherwise", spec, factFqn, pattern(spec, true), spec.otherwise));
        }
        return sb.toString();
    }

    /** Fact/context type for the rule's flow, from REQUEST_FLOW_MAP.fact_type - DB-driven. */
    private String resolveFactType(BuilderRuleSpec spec) {
        if (spec.flow == null || spec.flow.isBlank()) {
            throw new RuleCompilationException("Builder rule '" + spec.ruleName + "' has no flow.");
        }
        String factFqn = requestFlowMapRepository.findFirstByFlowName(spec.flow)
                .map(RequestFlowMap::getFactType)
                .orElse(null);
        if (factFqn == null || factFqn.isBlank()) {
            throw new RuleCompilationException(
                    "No fact type for flow '" + spec.flow + "'. Set FACT_TYPE on the REQUEST_FLOW_MAP row "
                            + "for that flow (or POST /admin/flows/mappings with factType).");
        }
        return factFqn;
    }

    private String ruleBlock(String name, BuilderRuleSpec spec, String factFqn, String pattern,
                             BuilderRuleSpec.Outcome outcome) {
        int salience = spec.salience == null ? 0 : spec.salience;
        return "rule \"" + name + "\"\n"
                + "    agenda-group \"" + spec.agendaGroup + "\"\n"
                + "    salience " + salience + "\n"
                + "    when\n"
                + "        $ctx : " + factFqn + "(\n"
                + "            " + pattern + "\n"
                + "        )\n"
                + "    then\n"
                + thenBody(outcome) + "\n"
                + "end";
    }

    private String pattern(BuilderRuleSpec spec, boolean negate) {
        boolean all = spec.match == null || "all".equalsIgnoreCase(spec.match);
        List<String> parts = spec.conditions.stream()
                .map(c -> constraint(c, negate))
                .collect(Collectors.toList());
        String joiner;
        if (negate) {
            joiner = all ? " || " : ",\n            ";
        } else {
            joiner = all ? ",\n            " : " || ";
        }
        return String.join(joiner, parts);
    }

    private String constraint(BuilderRuleSpec.Condition c, boolean negate) {
        Optional<RuleFieldCatalog> field = catalogRepository.findFirstByFieldKey(c.field);
        String drlPath = field.map(RuleFieldCatalog::getDrlPath).orElse(c.field);
        String dataType = field.map(RuleFieldCatalog::getDataType).orElse("NUMBER").toUpperCase();
        String op = negate ? NEG.getOrDefault(c.operator, c.operator) : c.operator;
        return drlPath + " " + op + " " + literal(c, dataType);
    }

    private String literal(BuilderRuleSpec.Condition c, String dataType) {
        if (c.valueField != null && !c.valueField.isBlank()) {
            return catalogRepository.findFirstByFieldKey(c.valueField)
                    .map(RuleFieldCatalog::getDrlPath).orElse(c.valueField);
        }
        if ("ENUM".equals(dataType) || "STRING".equals(dataType)) {
            return "\"" + String.valueOf(c.value) + "\"";
        }
        return String.valueOf(c.value);
    }

    private String thenBody(BuilderRuleSpec.Outcome outcome) {
        String decision = (outcome == null || outcome.decision == null) ? "block" : outcome.decision.toLowerCase();
        StringBuilder b = new StringBuilder();
        if ("allow".equals(decision)) {
            b.append("        $ctx.setValid(true);");
        } else {
            b.append("        $ctx.setValid(false);\n");
            b.append("        $ctx.setPermissionDenied(true);");
        }
        if (outcome != null && outcome.message != null && !outcome.message.isBlank()) {
            b.append("\n        $ctx.setValidationMessage(\"").append(escape(outcome.message)).append("\");");
        }
        return b.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
