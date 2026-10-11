package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for mapping a fact/context class to a flow. {@code factType} is the fully-qualified
 * context class the service inserts into {@code execute(...)} - both the routing key and the type
 * the rules' DRL matches on.
 *
 * @author K M Farhat Snigdah
 */
public record RequestMappingRequest(
        @NotBlank String factType,
        @NotBlank String flowName) {
}
