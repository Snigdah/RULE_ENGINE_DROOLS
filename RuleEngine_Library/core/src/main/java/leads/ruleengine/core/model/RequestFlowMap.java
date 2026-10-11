package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Maps a fact/context class to its flow.
 *
 * <p>The service inserts a context (e.g. {@code ValidationContext}) and calls
 * {@code ruleExecutionService.execute(context, ...)}. The engine looks that context's class up
 * here to find the flow, then runs the flow's agenda groups. The SAME value is the type the
 * rules' DRL matches on ({@code $ctx : <factType>(...)}), so one column drives both routing and
 * code generation - no separate request class.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "REQUEST_FLOW_MAP")
public class RequestFlowMap {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "request_flow_map_seq")
    @SequenceGenerator(name = "request_flow_map_seq", sequenceName = "REQUEST_FLOW_MAP_SEQ", allocationSize = 1)
    private Long id;

    /** Fully-qualified fact/context class - the routing key AND the type the DRL matches. Unique. */
    @Column(name = "FACT_TYPE", nullable = false, unique = true, length = 500)
    private String factType;

    @Column(name = "FLOW_NAME", nullable = false, length = 255)
    private String flowName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFactType() {
        return factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
}
