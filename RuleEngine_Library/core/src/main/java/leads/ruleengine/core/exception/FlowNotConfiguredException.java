package leads.ruleengine.core.exception;

/**
 * Thrown at execution time when a request class has no flow mapping, or a flow has no
 * agenda groups configured. It means the routing tables are missing a row - fix it via
 * the admin API ({@code /admin/flows}), not in code.
 *
 * <p>This propagates to the consumer's own controller so the service decides how to render
 * it (the library does not impose a global handler on business endpoints).</p>
 *
 * @author K M Farhat Snigdah
 */
public class FlowNotConfiguredException extends RuntimeException {

    public FlowNotConfiguredException(String message) {
        super(message);
    }
}
