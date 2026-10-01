package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * JSON body for defining a flow as an ordered list of agenda groups. The list order IS the
 * firing order.
 *
 * @author K M Farhat Snigdah
 */
public record FlowDefinitionRequest(
        @NotBlank String flowName,
        @NotEmpty List<String> groups) {
}
