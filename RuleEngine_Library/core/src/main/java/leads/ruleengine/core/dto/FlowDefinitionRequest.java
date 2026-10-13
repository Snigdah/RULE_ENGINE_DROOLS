package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * JSON body for defining a flow as an ordered list of agenda groups. The list order IS the
 * firing order. {@code factType} is the flow's fact/context class (what its rules match on);
 * it is optional on update - when omitted, the flow keeps its current fact type.
 *
 * @author K M Farhat Snigdah
 */
public record FlowDefinitionRequest(
        @NotBlank String flowName,
        String factType,
        @NotEmpty List<String> groups) {
}
