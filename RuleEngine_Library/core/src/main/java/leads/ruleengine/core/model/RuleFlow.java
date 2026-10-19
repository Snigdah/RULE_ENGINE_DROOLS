package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * The flow master <b>and</b> the router - one row per flow.
 *
 * <p>Routing is 1:1 - one request DTO class maps to exactly one flow - so the routing key
 * ({@code requestClass}) lives here as a UNIQUE column rather than in a separate mapping table.
 * The consuming service calls {@code execute(context, <RequestClass>.class, ...)}; the engine
 * looks the class name up here to find the flow, then runs the flow's groups.</p>
 *
 * <p>{@code factType} is the fully-qualified context class the flow's own rules match on
 * ({@code $ctx : <factType>(...)}); the DRL generator reads it once from here. Shared COMMON
 * rules match the common base context and are hand-written DRL, so they need no fact type of
 * their own.</p>
 *
 * <p>Developer-managed: flows are seeded by the DBA / startup, never created by end users.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_FLOW")
public class RuleFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_flow_seq")
    @SequenceGenerator(name = "rule_flow_seq", sequenceName = "RULE_FLOW_SEQ", allocationSize = 1)
    private Long id;

    /** Stable flow code, e.g. {@code TRANSFER_TRANSACTION}. Unique. */
    @Column(name = "FLOW_CODE", nullable = false, unique = true, length = 255)
    private String flowCode;

    /** Fully-qualified request DTO class - the execute() routing key. Unique, 1:1 with the flow. */
    @Column(name = "REQUEST_CLASS", unique = true, length = 500)
    private String requestClass;

    /** Fully-qualified context class the flow's own rules match on. */
    @Column(name = "FACT_TYPE", nullable = false, length = 500)
    private String factType;

    /** Friendly label for the UI, e.g. {@code Transfer Transaction}. */
    @Column(name = "LABEL", length = 255)
    private String label;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
    }

    public String getRequestClass() {
        return requestClass;
    }

    public void setRequestClass(String requestClass) {
        this.requestClass = requestClass;
    }

    public String getFactType() {
        return factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
