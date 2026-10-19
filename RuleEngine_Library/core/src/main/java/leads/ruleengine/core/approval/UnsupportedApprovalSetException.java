package leads.ruleengine.core.approval;

/**
 * An approval set reached the rule engine's approve endpoint that the rule engine must not process:
 * it was not submitted through changeboard's Hibernate path, or it is not a rule-engine change
 * (e.g. one of Sentinel's own sets). Mapped to HTTP 409.
 *
 * @author K M Farhat Snigdah
 */
public class UnsupportedApprovalSetException extends RuntimeException {

    public UnsupportedApprovalSetException(String message) {
        super(message);
    }
}
