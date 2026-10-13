package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * One agenda group inside a flow, with its firing order. A flow is just an ordered list
 * of agenda groups; the engine sets focus on them in {@code orderNo} order so rules fire
 * in a predictable sequence (e.g. COMMON checks before TRANSFER checks).
 *
 * <p>Example rows for the {@code TRANSFER_TRANSACTION} flow:</p>
 * <pre>
 *   flow_name=TRANSFER_TRANSACTION, agenda_group=COMMON,   order_no=1
 *   flow_name=TRANSFER_TRANSACTION, agenda_group=TRANSFER, order_no=2
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "FLOW_GROUP")
public class FlowGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "flow_group_seq")
    @SequenceGenerator(name = "flow_group_seq", sequenceName = "FLOW_GROUP_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "FLOW_NAME", nullable = false, length = 255)
    private String flowName;

    @Column(name = "AGENDA_GROUP", nullable = false, length = 255)
    private String agendaGroup;

    @Column(name = "ORDER_NO", nullable = false)
    private Integer orderNo;

    /**
     * The fully-qualified fact/context class the rules in THIS flow match on
     * ({@code $ctx : <factType>(...)}). Same value on every row of a flow; the DRL generator
     * reads it to build the rule pattern. Set by developers when the flow is defined.
     */
    @Column(name = "FACT_TYPE", length = 500)
    private String factType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    public String getAgendaGroup() {
        return agendaGroup;
    }

    public void setAgendaGroup(String agendaGroup) {
        this.agendaGroup = agendaGroup;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }
    public String getFactType() {
        return factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }
}
