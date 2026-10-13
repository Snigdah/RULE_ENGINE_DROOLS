package leads.ruleengine.core.dto;

/**
 * Read model for one agenda group: safe to serialize (no JPA associations). {@code shared} is
 * true for the COMMON-style groups that every flow runs ({@code flow_id NULL}); {@code active}
 * is the on/off toggle (an inactive group is skipped at runtime).
 *
 * @author K M Farhat Snigdah
 */
public record GroupView(
        Long id,
        String groupCode,
        Integer orderNo,
        boolean shared,
        boolean active) {
}
