package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * JSON body for creating or updating a flow master row ({@code RULE_FLOW}).
 *
 * <ul>
 *   <li>{@code flowCode} - stable flow identity (e.g. {@code TRANSFER_TRANSACTION}).</li>
 *   <li>{@code requestClass} - the request DTO class that routes to this flow (1:1). Optional:
 *       a flow may be defined before it is wired to a request.</li>
 *   <li>{@code factType} - the context class the flow's rules match on.</li>
 *   <li>{@code label} - friendly name for the UI (optional).</li>
 *   <li>{@code groups} - optional agenda-group codes to ensure exist for this flow, in order.
 *       Non-destructive: codes already present are left as-is; existing rules are never touched.</li>
 * </ul>
 *
 * @author K M Farhat Snigdah
 */
public record FlowDefinitionRequest(
        @NotBlank String flowCode,
        String requestClass,
        @NotBlank String factType,
        String label,
        List<String> groups) {
}
