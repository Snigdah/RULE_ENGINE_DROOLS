package leads.ruleengine.core.dto;

import java.util.List;

/**
 * Read model for a flow and the groups it runs, in firing order (shared groups first).
 * Safe to serialize - no JPA associations leak out.
 *
 * @author K M Farhat Snigdah
 */
public record FlowView(
        Long id,
        String flowCode,
        String requestClass,
        String factType,
        String label,
        boolean active,
        List<GroupView> groups) {
}
