package leads.ruleengine.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A leaf node: one field comparison, e.g. {@code transaction.amount > 5000}.
 *
 * <pre>
 * { "type":"CONDITION", "field":"transaction.amount", "operator":">", "value":5000 }
 * { "type":"CONDITION", "field":"transaction.amount", "operator":">", "valueField":"userLimit.limit" }
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionLeaf implements ConditionNode {

    /** Field key from RULE_FIELD_CATALOG, e.g. {@code transaction.amount}. */
    public String field;

    /** One of {@code >, <, >=, <=, ==, !=}. */
    public String operator;

    /** A literal value (number / string / boolean). Ignored when {@code valueField} is set. */
    public Object value;

    /** Compare against another field instead of a literal, e.g. {@code userLimit.limit}. */
    public String valueField;
}
