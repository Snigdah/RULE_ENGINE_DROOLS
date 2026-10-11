package leads.ruleengine.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A branch node: an AND/OR over its children. Children may be leaves or further groups, so the
 * structure expresses any nested boolean expression.
 *
 * <pre>
 * { "type":"GROUP", "operator":"OR", "children":[ {GROUP AND ...}, {GROUP AND ...} ] }
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionGroup implements ConditionNode {

    /** {@code AND} or {@code OR}. */
    public String operator = "AND";

    /** Child nodes (leaves and/or nested groups). */
    public List<ConditionNode> children = new ArrayList<>();
}
