package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Maps a request DTO class name to the flow that should run for it.
 *
 * <p>The consumer calls {@code ruleExecutionService.execute(context, TransactionRequest.class)};
 * the engine looks up {@code request_class = 'com.example.dto.TransactionRequest'} here to
 * find the flow (e.g. {@code TRANSFER_TRANSACTION}), then runs that flow's agenda groups.
 * Routing is data, not code - a new request type is onboarded by inserting one row via the
 * admin API, never by editing the library.</p>
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

    /** Fully-qualified request DTO class name, unique. */
    @Column(name = "REQUEST_CLASS", nullable = false, unique = true, length = 500)
    private String requestClass;

    @Column(name = "FLOW_NAME", nullable = false, length = 255)
    private String flowName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestClass() {
        return requestClass;
    }

    public void setRequestClass(String requestClass) {
        this.requestClass = requestClass;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
}
