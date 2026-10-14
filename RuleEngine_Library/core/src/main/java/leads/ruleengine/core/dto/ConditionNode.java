package leads.ruleengine.core.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A node in the rule's condition tree (AST). Two concrete shapes:
 * <ul>
 *   <li>{@link ConditionGroup} — {@code "type":"GROUP"} — an AND/OR of child nodes.</li>
 *   <li>{@link ConditionLeaf}  — {@code "type":"CONDITION"} — a single field comparison.</li>
 * </ul>
 *
 * <p>Because a group's children are themselves {@code ConditionNode}s, the tree nests to any
 * depth — e.g. {@code (A AND B) OR (C AND D)}. The {@code "type"} discriminator in the JSON tells
 * Jackson which class to build.</p>
 *
 * <p>The interface is deliberately open so future node kinds (NOT, IN, BETWEEN, EXISTS…) can be
 * added later without reshaping the tree. Only GROUP and CONDITION are implemented now.</p>
 *
 * @author K M Farhat Snigdah
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConditionGroup.class, name = "GROUP"),
        @JsonSubTypes.Type(value = ConditionLeaf.class, name = "CONDITION")
})
public interface ConditionNode {
}
