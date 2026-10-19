package leads.ruleengine.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.ConditionGroup;
import leads.ruleengine.core.dto.ConditionLeaf;
import leads.ruleengine.core.dto.ConditionNode;
import leads.ruleengine.core.model.RuleDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Answers "which fields does this one rule read?".
 *
 * <ul>
 *   <li><b>BUILDER</b> rule: walks the whole condition tree (AND/OR, any depth) and collects every
 *       leaf's {@code field} and {@code valueField} (field-to-field compare).</li>
 *   <li><b>DRL</b> rule: hand-written code is not parsed. It may declare what it reads in one
 *       comment line, e.g. {@code // @requires: user} or {@code // @requires: none}.</li>
 * </ul>
 *
 * <p>Returns {@link Optional#empty()} when usage cannot be proven (unreadable builder JSON, DRL
 * without a hint). The caller then treats the whole flow as "load everything".</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class FieldUsageAnalyzer {

    private static final Pattern REQUIRES =
            Pattern.compile("^\\s*//\\s*@requires\\s*:\\s*(.*)$", Pattern.MULTILINE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Field keys / paths the rule reads, or empty if that cannot be proven. */
    public Optional<Set<String>> fieldsOf(RuleDefinition rule) {
        return fieldsOf(rule, true);
    }

    /**
     * @param trustDrlHints when false, every DRL rule is "unknown" (its hint is ignored)
     */
    public Optional<Set<String>> fieldsOf(RuleDefinition rule, boolean trustDrlHints) {
        if (RuleDefinition.SOURCE_BUILDER.equals(rule.getSourceType())) {
            try {
                BuilderRuleSpec spec = objectMapper.readValue(rule.getSourceJson(), BuilderRuleSpec.class);
                Set<String> out = new TreeSet<>();
                walk(spec.conditionTree, out);
                return Optional.of(out);
            } catch (Exception e) {
                return Optional.empty();                         // unreadable -> unknown
            }
        }
        if (!trustDrlHints) {
            return Optional.empty();
        }
        return drlHint(rule.getDrlText());
    }

    /** The {@code // @requires:} declaration of a DRL text, or empty when there is none. */
    public Optional<Set<String>> drlHint(String drlText) {
        if (drlText == null) {
            return Optional.empty();
        }
        Matcher hint = REQUIRES.matcher(drlText);
        if (!hint.find()) {
            return Optional.empty();                             // DRL without hint -> unknown
        }
        String list = hint.group(1).trim();
        if (list.equalsIgnoreCase("none") || list.isEmpty()) {
            return Optional.of(Set.of());
        }
        return Optional.of(Arrays.stream(list.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    private void walk(ConditionNode node, Set<String> out) {
        if (node instanceof ConditionLeaf leaf) {
            if (leaf.field != null && !leaf.field.isBlank()) {
                out.add(leaf.field);
            }
            if (leaf.valueField != null && !leaf.valueField.isBlank()) {
                out.add(leaf.valueField);                        // field-to-field compare
            }
        } else if (node instanceof ConditionGroup group && group.children != null) {
            group.children.forEach(child -> walk(child, out));
        }
    }
}
