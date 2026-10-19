package leads.ruleengine.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leads.model.ApprovalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * One agenda group that belongs to a flow (or is shared across all flows), with its firing order.
 *
 * <p>A group is now a real row with its own id, not just a name string. This is what lets two
 * flows each have their OWN {@code COMPLIANCE} group - two rows, two ids, different rules - even
 * though both carry the same {@code groupCode} (the Drools agenda-group name).</p>
 *
 * <ul>
 *   <li>{@code flow} set - the group belongs to that one flow.</li>
 *   <li>{@code flow} NULL - a <b>shared</b> group (e.g. COMMON) that every flow runs first.</li>
 * </ul>
 *
 * <p>Firing order: shared groups first, then the flow's own groups by {@code orderNo}.</p>
 *
 * <p><b>Approval:</b> extends changeboard's {@link ApprovalBaseEntity} so a group change can go
 * through maker-checker approval. {@link #rules} makes the group and its rules one approval
 * aggregate: a rename re-submits its builder rules, a force-delete removes them with it.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_GROUP",
        uniqueConstraints = @UniqueConstraint(name = "UK_RULE_GROUP", columnNames = {"FLOW_ID", "GROUP_CODE"}))
public class RuleGroup extends ApprovalBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_group_seq")
    @SequenceGenerator(name = "rule_group_seq", sequenceName = "RULE_GROUP_SEQ", allocationSize = 1)
    private Long id;

    /** Owning flow, or NULL for a shared group (COMMON). EAGER - small table, read in bulk. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "FLOW_ID")
    private RuleFlow flow;

    /** The Drools agenda-group name, e.g. {@code COMPLIANCE}. Unique per flow, not globally. */
    @Column(name = "GROUP_CODE", nullable = false, length = 100)
    private String groupCode;

    /** Firing order within the flow. */
    @Column(name = "ORDER_NO", nullable = false)
    private Integer orderNo;

    /** When false the group is skipped at runtime - none of its rules are focused/evaluated. */
    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    /**
     * The group's rules. Only used to submit a group and its rules as ONE approval aggregate -
     * LAZY, never read by the engine. A rule serializes its group without this list (see
     * {@link RuleDefinition#getGroup()}), which keeps the approval JSON free of cycles.
     */
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private List<RuleDefinition> rules = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RuleFlow getFlow() {
        return flow;
    }

    public void setFlow(RuleFlow flow) {
        this.flow = flow;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public Integer getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(Integer orderNo) {
        this.orderNo = orderNo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<RuleDefinition> getRules() {
        return rules;
    }

    public void setRules(List<RuleDefinition> rules) {
        this.rules = rules;
    }

    /** True when this group is shared across every flow (no owning flow). Derived - not serialized. */
    @JsonIgnore
    public boolean isShared() {
        return flow == null;
    }
}
