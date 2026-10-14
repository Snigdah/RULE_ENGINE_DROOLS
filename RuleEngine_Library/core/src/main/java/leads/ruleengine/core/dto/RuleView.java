package leads.ruleengine.core.dto;

/**
 * Read model for one rule: safe to serialize (resolves the group/flow to plain fields, so no
 * lazy JPA association is touched during JSON writing). Carries both forms so the UI can show the
 * editable {@code sourceJson} and the generated {@code drlText}.
 *
 * @author K M Farhat Snigdah
 */
public record RuleView(
        Long id,
        Long groupId,
        String groupCode,
        String flowCode,
        String ruleName,
        String sourceType,
        boolean active,
        Integer version,
        String sourceJson,
        String drlText) {
}
