package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for saving a rule into a group. The target group is the {@code groupId} path variable
 * ({@code POST /admin/groups/{groupId}/rules}); on update ({@code PUT /admin/rules/{id}}) the group
 * stays the rule's current group.
 *
 * <ul>
 *   <li>{@code sourceType = "BUILDER"} - send {@code sourceJson} (the builder's output); the
 *       engine generates the DRL from it. Only allowed in a flow group (needs a fact type).</li>
 *   <li>{@code sourceType = "DRL"} - send {@code drl} (hand-written); it is stored as-is. The only
 *       option for the shared COMMON group.</li>
 * </ul>
 *
 * @author K M Farhat Snigdah
 */
public record RuleDefinitionRequest(
        @NotBlank String ruleName,
        @NotBlank String sourceType,
        String sourceJson,
        String drl,
        String createdBy) {
}
