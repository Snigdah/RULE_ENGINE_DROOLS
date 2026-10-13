package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Maps an incoming <b>request class</b> to its flow.
 *
 * <p>The consuming service builds a context and calls
 * {@code ruleExecutionService.execute(context, <RequestClass>.class, ...)}. The engine looks the
 * request class up here to find the flow, then runs the flow's agenda groups. This table is
 * purely routing - two columns, no fact type. The type the rules' DRL matches on
 * ({@code $ctx : <factType>(...)}) lives on the flow's {@code FLOW_GROUP} rows instead.</p>
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

    /** Fully-qualified request class - the execute() routing key. Unique. */
    @Column(name = "REQUEST_TYPE", nullable = false, unique = true, length = 500)
    private String requestType;

    @Column(name = "FLOW_NAME", nullable = false, length = 255)
    private String flowName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
}
