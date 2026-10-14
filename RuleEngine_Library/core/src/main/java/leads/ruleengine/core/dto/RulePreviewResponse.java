package leads.ruleengine.core.dto;

/**
 * Result of a builder preview: the generated DRL and whether it compiles. No rule is saved and
 * the live engine is untouched. The UI shows {@code drl} read-only; {@code valid=false} with
 * {@code error} lets it warn before the user hits Save.
 *
 * @author K M Farhat Snigdah
 */
public record RulePreviewResponse(
        String drl,
        boolean valid,
        String error) {
}
