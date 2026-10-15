package leads.ruleengine.core.context;

/**
 * The decision of one rule run, owned by the library.
 *
 * <p>{@code RuleExecutionService.execute(..)} creates a fresh {@code RuleResult} per call, exposes it
 * to the rules as the Drools global {@code result}, and returns it. A service therefore no longer
 * needs decision fields on its own context object:</p>
 *
 * <pre>
 * RuleResult result = ruleEngine.execute(context, TransactionRequest.class);
 * if (!result.isValid()) { ... result.getMessage() ... }
 * </pre>
 *
 * <p>Builder rules write it with {@link #block(String)} / {@link #allow(String)}. A hand-written DRL
 * rule can do the same after declaring
 * {@code global leads.ruleengine.core.context.RuleResult result;}.</p>
 *
 * <p>Starts as valid: if no rule matches, the request passes.</p>
 *
 * @author K M Farhat Snigdah
 */
public class RuleResult {

    private boolean valid = true;
    private boolean permissionDenied = false;
    private String message;
    private boolean decided = false;

    /** A rule rejected the request. A non-blank message replaces the current one. */
    public void block(String message) {
        this.decided = true;
        this.valid = false;
        this.permissionDenied = true;
        if (message != null && !message.isBlank()) {
            this.message = message;
        }
    }

    /** A rule explicitly allowed the request. A non-blank message replaces the current one. */
    public void allow(String message) {
        this.decided = true;
        this.valid = true;
        if (message != null && !message.isBlank()) {
            this.message = message;
        }
    }

    /** {@code true} when no rule blocked the request (or the last decision was allow). */
    public boolean isValid() {
        return valid;
    }

    /** {@code true} when a rule blocked the request. */
    public boolean isPermissionDenied() {
        return permissionDenied;
    }

    /** Message of the deciding rule, or {@code null} when no rule set one. */
    public String getMessage() {
        return message;
    }

    /** {@code true} once any rule called {@link #block(String)} or {@link #allow(String)}. */
    public boolean isDecided() {
        return decided;
    }

    @Override
    public String toString() {
        return "RuleResult{valid=" + valid + ", permissionDenied=" + permissionDenied + ", message='" + message + "'}";
    }
}
