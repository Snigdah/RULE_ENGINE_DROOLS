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
 *   required-data:
 *     enabled: false         # demand-driven loading: needs() can answer false (default false)
 *     shadow: false          # compute + log, but needs() still answers true (default false)
 *     trust-drl-hints: true  # read "// @requires:" in hand-written DRL (default true)
 *   approval:
 *     enabled: true          # rule/group changes go through changeboard maker-checker (default false)
 *     app-id: 1              # used when the request has no X-App-Id header
 *     function-id: 101022    # changeboard / Centrino function the approval workflow is mapped to
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
    private final RequiredData requiredData = new RequiredData();

    private final Approval approval = new Approval();

    public Admin getAdmin() {
        return admin;
    }

    public RequiredData getRequiredData() {
        return requiredData;
    }

    public Approval getApproval() {
        return approval;
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

    /**
     * Demand-driven context building. Off by default: {@code requiredData(..).needs(root)} then
     * always answers true, so a service behaves exactly as before. Services that never call
     * {@code requiredData(..)} are not affected by any of these settings.
     */
    public static class RequiredData {
        private boolean enabled = false;
        private boolean shadow = false;
        private boolean trustDrlHints = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isShadow() {
            return shadow;
        }

        public void setShadow(boolean shadow) {
            this.shadow = shadow;
        }

        public boolean isTrustDrlHints() {
            return trustDrlHints;
        }

        public void setTrustDrlHints(boolean trustDrlHints) {
            this.trustDrlHints = trustDrlHints;
        }
    }

    /**
     * Maker-checker approval for rule and group changes. Off by default: every admin write is
     * applied immediately, exactly as before. When on, rule create/update/delete/toggle and group
     * create/rename/delete/toggle are submitted to changeboard and only reach the rule tables
     * after final approval. Flows, group reorder and the field catalog are never gated.
     */
    public static class Approval {
        private boolean enabled = false;
        private Long appId;
        private Long functionId;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Long getAppId() {
            return appId;
        }

        public void setAppId(Long appId) {
            this.appId = appId;
        }

        public Long getFunctionId() {
            return functionId;
        }

        public void setFunctionId(Long functionId) {
            this.functionId = functionId;
        }
    }
}
