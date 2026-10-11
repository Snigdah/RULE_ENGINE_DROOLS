package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for saving a rule.
 *
 * <ul>
 *   <li>{@code sourceType = "BUILDER"} - send {@code sourceJson} (the builder's output); the
 *       engine generates the DRL from it.</li>
 *   <li>{@code sourceType = "DRL"} - send {@code drl} (hand-written); it is stored as-is.</li>
 * </ul>
 *
 * @author K M Farhat Snigdah
 */
public record RuleDefinitionRequest(
        @NotBlank String ruleName,
        @NotBlank String agendaGroup,
        @NotBlank String sourceType,
        String sourceJson,
        String drl,
        String createdBy) {
}
