package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for uploading a DRL file by name.
 *
 * @author K M Farhat Snigdah
 */
public record RuleUploadRequest(
        @NotBlank String fileName,
        @NotBlank String drl) {
}
