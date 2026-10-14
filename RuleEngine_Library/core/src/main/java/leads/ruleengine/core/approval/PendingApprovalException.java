package leads.ruleengine.core.approval;

/**
 * A rule or group already has a change waiting for approval, so a second change to it is
 * refused until the first one is approved, declined or sent back. Mapped to HTTP 409.
 *
 * @author K M Farhat Snigdah
 */
public class PendingApprovalException extends RuntimeException {

    public PendingApprovalException(String message) {
        super(message);
    }
}
