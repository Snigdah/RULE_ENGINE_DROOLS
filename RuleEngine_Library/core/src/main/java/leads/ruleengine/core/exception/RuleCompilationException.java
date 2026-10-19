package leads.ruleengine.core.exception;

/**
 * Thrown when a DRL file fails to compile. Because uploads are validated BEFORE the
 * running rule base is swapped, this never takes the live engine down - a bad upload is
 * simply rejected with this error.
 *
 * @author K M Farhat Snigdah
 */
public class RuleCompilationException extends RuntimeException {

    public RuleCompilationException(String message) {
        super(message);
    }
}
