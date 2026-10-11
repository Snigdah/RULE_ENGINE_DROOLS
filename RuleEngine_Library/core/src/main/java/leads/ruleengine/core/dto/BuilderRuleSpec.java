package leads.ruleengine.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape the Visual Rule Builder saves as {@code SOURCE_JSON}, and the input the
 * {@code RuleDrlGenerator} turns into DRL.
 *
 * <p>Deliberately simple and tolerant ({@code ignoreUnknown}) so the UI can add presentation
 * fields without breaking generation.</p>
 *
 * <pre>
 * {
 *   "ruleName": "Over-limit debit-restricted BDT",
 *   "flow": "TRANSFER_TRANSACTION",
 *   "agendaGroup": "TRANSFER",
 *   "salience": 10,
 *   "match": "all",                    // all = AND, any = OR
 *   "conditions": [
 *     { "field": "transaction.currency", "operator": "==", "value": "BDT" },
 *     { "field": "product.drRes",        "operator": "==", "value": 1 },
 *     { "field": "transaction.amount",   "operator": ">",  "valueField": "userLimit.limit" }
 *   ],
 *   "then":      { "decision": "block", "message": "Amount exceeds limit..." },
 *   "otherwise": { "decision": "allow" }   // optional - generates a second (negated) rule
 * }
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuilderRuleSpec {

    public String ruleName;
    public String flow;
    public String agendaGroup;
    public Integer salience = 0;
    /** {@code all} (AND) or {@code any} (OR). */
    public String match = "all";
    public List<Condition> conditions = new ArrayList<>();
    public Outcome then;
    /** Optional ELSE branch. When present the generator emits a second rule with negated conditions. */
    public Outcome otherwise;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        /** Field key from RULE_FIELD_CATALOG, e.g. {@code transaction.amount}. */
        public String field;
        /** One of {@code >, <, >=, <=, ==, !=}. */
        public String operator;
        /** A literal value (number / string / boolean). Ignored when {@code valueField} is set. */
        public Object value;
        /** Compare against another field instead of a literal, e.g. {@code userLimit.limit}. */
        public String valueField;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Outcome {
        /** {@code block} (reject) or {@code allow}. */
        public String decision;
        /** Optional message set on the result. */
        public String message;
    }
}
