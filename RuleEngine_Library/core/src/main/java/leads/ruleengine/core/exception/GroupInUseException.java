package leads.ruleengine.core.exception;

/**
 * Thrown when a group delete is refused because the group still has rules. Maps to HTTP 409.
 * The caller can retry with {@code ?force=true} to delete the group together with its rules.
 *
 * @author K M Farhat Snigdah
 */
public class GroupInUseException extends RuntimeException {

    public GroupInUseException(String message) {
        super(message);
    }
}
