package leads.ruleengine.core.approval;

import jakarta.servlet.http.HttpServletRequest;
import leads.ruleengine.core.config.RuleEngineProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is making the current admin request, and for which app/function, as changeboard needs it.
 *
 * <ul>
 *   <li><b>user</b> - {@code X-User-Id} header, else the JWT's {@code preferred_username}. Required:
 *       changeboard silently skips approver lookup when the maker is unknown.</li>
 *   <li><b>app</b> - {@code X-App-Id} header, else {@code rule-engine.approval.app-id}.</li>
 *   <li><b>function</b> - always {@code rule-engine.approval.function-id}. The UI's function header
 *       is not used: the approval workflow is mapped to the rule engine's own function.</li>
 * </ul>
 *
 * @author K M Farhat Snigdah
 */
public class ApprovalRequestContext {

    static final String USER_HEADER = "X-User-Id";
    static final String APP_HEADER = "X-App-Id";

    private final RuleEngineProperties.Approval config;

    public ApprovalRequestContext(RuleEngineProperties properties) {
        this.config = properties.getApproval();
    }

    public String requireUserId() {
        String user = header(USER_HEADER);
        if (isBlank(user)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                user = jwt.getToken().getClaimAsString("preferred_username");
            }
        }
        if (isBlank(user)) {
            throw new IllegalArgumentException(
                    "Approval needs the maker's user id: send the " + USER_HEADER + " header or a bearer token.");
        }
        return user.trim();
    }

    public Long appId() {
        String header = header(APP_HEADER);
        if (!isBlank(header)) {
            try {
                return Long.valueOf(header.trim());
            } catch (NumberFormatException ignored) {
                // fall back to the configured app id
            }
        }
        if (config.getAppId() == null) {
            throw new IllegalStateException(
                    "No app id: send the " + APP_HEADER + " header or set rule-engine.approval.app-id.");
        }
        return config.getAppId();
    }

    public Long functionId() {
        if (config.getFunctionId() == null) {
            throw new IllegalStateException("rule-engine.approval.function-id is not configured.");
        }
        return config.getFunctionId();
    }

    private static String header(String name) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader(name);
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
