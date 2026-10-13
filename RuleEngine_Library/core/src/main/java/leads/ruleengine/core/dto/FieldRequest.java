package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for adding a builder field to the catalog ({@code POST /admin/fields}).
 * {@code flowCode} is optional - omit (or blank) for a <b>shared</b> field shown in every flow.
 *
 * @author K M Farhat Snigdah
 */
public record FieldRequest(
        String flowCode,
        @NotBlank String fieldKey,
        @NotBlank String label,
        @NotBlank String dataType,
        @NotBlank String drlPath,
        @NotBlank String allowedOps,
        String allowedValues) {
}
