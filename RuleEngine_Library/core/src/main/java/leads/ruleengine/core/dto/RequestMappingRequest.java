package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for mapping a request class to a flow. {@code requestType} is the fully-qualified
 * request class the service passes to {@code execute(context, requestType, ...)} - the routing key.
 *
 * @author K M Farhat Snigdah
 */
public record RequestMappingRequest(
        @NotBlank String requestType,
        @NotBlank String flowName) {
}
