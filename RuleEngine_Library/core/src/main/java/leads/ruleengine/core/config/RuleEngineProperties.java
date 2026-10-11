package leads.ruleengine.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Behaviour of the rule engine is chosen by YAML in each service, not by code.
 *
 * <p>Example ({@code application.yaml}):</p>
 * <pre>
 * rule-engine:
 *   admin:
 *     enabled: true          # expose /admin/rules and /admin/flows (default true)
 * </pre>
 *
 * <p>The builder's flow-to-fact-type mapping is NOT configured here - it lives in the database
 * ({@code REQUEST_FLOW_MAP.FACT_TYPE}), alongside the rest of the routing.</p>
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
     * Controls the runtime admin API (rule save + flow CRUD). On by default; a service
     * can switch it off - or secure it separately - with {@code rule-engine.admin.enabled: false}.
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
