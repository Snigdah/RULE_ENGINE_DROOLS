package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for mapping a request DTO class to a flow.
 *
 * @author K M Farhat Snigdah
 */
public record RequestMappingRequest(
        @NotBlank String requestClass,
        @NotBlank String flowName) {
}
