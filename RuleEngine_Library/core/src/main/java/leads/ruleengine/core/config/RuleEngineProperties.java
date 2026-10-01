package leads.ruleengine.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Behaviour of the rule engine is chosen by YAML in each service, not by code -
 * the same "one library, configured per service" idea as the AuthZ {@code SecurityMode}.
 *
 * <p>Example ({@code application.yaml}):</p>
 * <pre>
 * rule-engine:
 *   admin:
 *     enabled: true          # expose /admin/rules and /admin/flows (default true)
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
@ConfigurationProperties(prefix = "rule-engine")
public class RuleEngineProperties {

    private final Admin admin = new Admin();

    public Admin getAdmin() {
        return admin;
    }

    /**
     * Controls the runtime admin API (rule upload + flow CRUD). On by default; a service
     * can switch it off - or secure it separately - with {@code rule-engine.admin.enabled: false}
     * without touching code.
     */
    public static class Admin {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
