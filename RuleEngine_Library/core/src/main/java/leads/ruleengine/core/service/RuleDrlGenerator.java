package leads.ruleengine.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.ConditionGroup;
import leads.ruleengine.core.dto.ConditionLeaf;
import leads.ruleengine.core.dto.ConditionNode;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RuleFieldCatalog;
import leads.ruleengine.core.repository.RuleFieldCatalogRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a Visual Rule Builder spec (SOURCE_JSON) into executable DRL (DRL_TEXT).
 *
 * <p><b>Condition tree.</b> The spec carries a recursive {@link ConditionNode} tree, so a rule
 * can nest AND/OR to any depth - e.g. {@code (A AND B) OR (C AND D)}. The generator walks the
 * tree recursively, wrapping every group in parentheses and joining children with {@code &&}/{@code ||}.</p>
 *
 * <p><b>DB-driven, but context comes from the caller.</b> Each leaf's real path and data type
 * come from {@code RULE_FIELD_CATALOG}. The fact/context type, the agenda-group name and the
 * unique-name qualifier are resolved by {@code RuleAdminService} from the rule's <b>group</b>
 * (and the group's flow) and passed in - the generator no longer reads routing tables itself.</p>
 *
 * <p><b>Unique rule names.</b> Display names are only unique per group now, so two groups may
 * hold a rule with the same name. To keep the compiled Drools rule names unique, the generator
 * prefixes the DRL {@code rule "..."} with a {@code qualifier} (e.g.
 * {@code TRANSFER_TRANSACTION.COMPLIANCE}), while the {@code agenda-group} stays the plain code.</p>
 *
 * <p><b>IF / ELSE.</b> An optional {@code otherwise} branch emits a second rule whose tree is the
 * De-Morgan negation of the first.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class RuleDrlGenerator {

    /** Every generated rule writes its decision to this library-owned global (see {@code RuleResult}). */
    public static final String RESULT_GLOBAL_DECL = "global leads.ruleengine.core.context.RuleResult result;";

    /** Guard against pathological input: the tree may not nest deeper than this. */
    private static final int MAX_DEPTH = 10;

    private final RuleFieldCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuleDrlGenerator(RuleFieldCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /** Parse the builder JSON and generate DRL with the given resolved context. */
    public String fromJson(String sourceJson, String factFqn, String agendaGroup, String qualifier) {
        BuilderRuleSpec spec;
        try {
            spec = objectMapper.readValue(sourceJson, BuilderRuleSpec.class);
        } catch (Exception e) {
            throw new RuleCompilationException("Invalid builder JSON: " + e.getMessage());
        }
        return generate(spec, factFqn, agendaGroup, qualifier);
    }

    /**
     * Generate DRL from an already-parsed spec.
     *
     * @param spec        the builder spec (condition tree + outcomes)
     * @param factFqn     the fully-qualified context class the rule matches ({@code $ctx : factFqn(...)})
     * @param agendaGroup the Drools agenda-group name (the group's code)
     * @param qualifier   prefix that makes the compiled rule name unique, e.g. {@code FLOW.GROUP}
     */
    public String generate(BuilderRuleSpec spec, String factFqn, String agendaGroup, String qualifier) {
        if (spec.conditionTree == null) {
            throw new RuleCompilationException("Rule '" + spec.ruleName + "' has no condition tree.");
        }
        if (factFqn == null || factFqn.isBlank()) {
            throw new RuleCompilationException("Rule '" + spec.ruleName + "' has no fact type to match on.");
        }
        String pkg = factFqn.contains(".") ? factFqn.substring(0, factFqn.lastIndexOf('.')) : "rules";
        String prefix = (qualifier == null || qualifier.isBlank()) ? "" : qualifier + "::";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append(RESULT_GLOBAL_DECL).append("\n\n");
        sb.append(ruleBlock(prefix + spec.ruleName, spec, factFqn, agendaGroup,
                generateNode(spec.conditionTree, false, 0), spec.then));
        if (spec.otherwise != null && spec.otherwise.decision != null) {
            sb.append("\n\n");
            sb.append(ruleBlock(prefix + spec.ruleName + " - otherwise", spec, factFqn, agendaGroup,
                    generateNode(spec.conditionTree, true, 0), spec.otherwise));
        }
        return sb.toString();
    }

    private String ruleBlock(String name, BuilderRuleSpec spec, String factFqn, String agendaGroup,
                             String pattern, BuilderRuleSpec.Outcome outcome) {
        int salience = spec.salience == null ? 0 : spec.salience;
        return "rule \"" + name + "\"\n"
                + "    agenda-group \"" + agendaGroup + "\"\n"
                + "    salience " + salience + "\n"
                + "    when\n"
                + "        $ctx : " + factFqn + "(\n"
                + "            " + pattern + "\n"
                + "        )\n"
                + "    then\n"
                + thenBody(outcome) + "\n"
                + "end";
    }

    // ---- recursive tree walk -------------------------------------------------------------

    /** Render any node to a DRL boolean expression. negate applies De-Morgan on the way down. */
    private String generateNode(ConditionNode node, boolean negate, int depth) {
        if (node == null) {
            throw new RuleCompilationException("Condition node cannot be null.");
        }
        if (depth > MAX_DEPTH) {
            throw new RuleCompilationException("Condition tree is nested too deep (max " + MAX_DEPTH + ").");
        }
        if (node instanceof ConditionLeaf leaf) {
            return generateLeaf(leaf, negate);
        }
        if (node instanceof ConditionGroup group) {
            return generateGroup(group, negate, depth);
        }
        throw new RuleCompilationException("Unsupported condition node: " + node.getClass().getName());
    }

    private String generateGroup(ConditionGroup group, boolean negate, int depth) {
        if (group.children == null || group.children.isEmpty()) {
            throw new RuleCompilationException("A condition group cannot be empty.");
        }
        String op = group.operator == null ? "AND" : group.operator.trim().toUpperCase();
        if (!op.equals("AND") && !op.equals("OR")) {
            throw new RuleCompilationException("Group operator must be AND or OR, got: " + group.operator);
        }
        // De Morgan: NOT(a AND b) = (!a OR !b) ; NOT(a OR b) = (!a AND !b)
        String effective = negate ? (op.equals("AND") ? "OR" : "AND") : op;

        List<String> parts = new ArrayList<>();
        for (ConditionNode child : group.children) {
            parts.add(generateNode(child, negate, depth + 1));
        }
        String joiner = effective.equals("AND") ? " && " : " || ";
        return "(" + String.join(joiner, parts) + ")";
    }

    private String generateLeaf(ConditionLeaf c, boolean negate) {
        if (c.field == null || c.field.isBlank()) {
            throw new RuleCompilationException("A condition is missing its field.");
        }
        Optional<RuleFieldCatalog> field = catalogRepository.findFirstByFieldKey(c.field);
        String drlPath = field.map(RuleFieldCatalog::getDrlPath).orElse(c.field);
        String dataType = field.map(RuleFieldCatalog::getDataType).orElse("NUMBER").toUpperCase();
        String op = negate ? negateOperator(c.operator) : c.operator;
        return drlPath + " " + op + " " + literal(c, dataType);
    }

    private String literal(ConditionLeaf c, String dataType) {
        if (c.valueField != null && !c.valueField.isBlank()) {
            return catalogRepository.findFirstByFieldKey(c.valueField)
                    .map(RuleFieldCatalog::getDrlPath).orElse(c.valueField);
        }
        if ("ENUM".equals(dataType) || "STRING".equals(dataType)) {
            return "\"" + String.valueOf(c.value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return String.valueOf(c.value);
    }

    /** Opposite comparison. Fails loudly on an unknown operator (better than silent bad DRL). */
    private String negateOperator(String operator) {
        if (operator == null) {
            throw new RuleCompilationException("A condition is missing its operator.");
        }
        return switch (operator) {
            case ">"  -> "<=";
            case "<"  -> ">=";
            case ">=" -> "<";
            case "<=" -> ">";
            case "==" -> "!=";
            case "!=" -> "==";
            default -> throw new RuleCompilationException("Cannot negate operator: " + operator);
        };
    }

    private String thenBody(BuilderRuleSpec.Outcome outcome) {
        String decision = (outcome == null || outcome.decision == null) ? "block" : outcome.decision.toLowerCase();
        StringBuilder b = new StringBuilder();
        String message = (outcome == null || outcome.message == null || outcome.message.isBlank())
                ? "null"
                : "\"" + escape(outcome.message) + "\"";
        // the decision goes to the library-owned RuleResult, not to the caller's context
        b.append("        result.").append("allow".equals(decision) ? "allow" : "block")
                .append("(").append(message).append(");");
        return b.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
