package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotNull;

/**
 * JSON body for the activate/deactivate endpoints (the UI's on/off radio): {@code {"active": true}}
 * or {@code {"active": false}}. Separate from delete - this only flips the flag; the row stays.
 *
 * @author K M Farhat Snigdah
 */
public record ActiveRequest(
        @NotNull Boolean active) {
}
