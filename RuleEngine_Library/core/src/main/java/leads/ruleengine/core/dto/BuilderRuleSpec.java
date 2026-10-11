package leads.ruleengine.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The shape the Visual Rule Builder saves as {@code SOURCE_JSON}, and the input the
 * {@code RuleDrlGenerator} turns into DRL.
 *
 * <p><b>Conditions are a tree</b> ({@link #conditionTree}), not a flat list — so a rule can mix
 * AND and OR to any depth, e.g. {@code (A AND B) OR (C AND D)}. The old flat
 * {@code conditions[] + match} model has been replaced by this tree.</p>
 *
 * <pre>
 * {
 *   "ruleName": "Transfer validation",
 *   "flow": "TRANSFER_TRANSACTION",
 *   "agendaGroup": "TRANSFER",
 *   "salience": 10,
 *   "conditionTree": {
 *     "type": "GROUP", "operator": "OR",
 *     "children": [
 *       { "type":"GROUP","operator":"AND","children":[
 *           {"type":"CONDITION","field":"transaction.currency","operator":"==","value":"BDT"},
 *           {"type":"CONDITION","field":"transaction.amount","operator":">","valueField":"userLimit.limit"} ]},
 *       { "type":"CONDITION","field":"customer.creditScore","operator":"&lt;","value":600 }
 *     ]
 *   },
 *   "then":      { "decision": "block", "message": "..." },
 *   "otherwise": { "decision": "allow" }   // optional - second rule with the De-Morgan negation
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

    /** Root of the condition tree (a GROUP, or a single CONDITION). */
    public ConditionNode conditionTree;

    public Outcome then;

    /** Optional ELSE branch. When present the generator emits a second rule with the negated tree. */
    public Outcome otherwise;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Outcome {
        /** {@code block} (reject) or {@code allow}. */
        public String decision;
        /** Optional message set on the result. */
        public String message;
    }
}
