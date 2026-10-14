package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * One rule, stored in the database so rules can be changed at RUNTIME (save a new version, the
 * engine validates and hot-swaps it - no restart, no redeploy).
 *
 * <p><b>A rule belongs to exactly one group ROW</b> ({@link #group}, a FK to {@link RuleGroup}),
 * not to a group name. That is the whole point of the redesign: two flows can each have a
 * {@code COMPLIANCE} group with completely different rules, and the same display name may exist
 * in both - {@code UNIQUE(group_id, rule_name)}, not a global unique.</p>
 *
 * <p>A rule carries BOTH forms:
 * <ul>
 *   <li>{@code sourceJson} - how the rule was authored in the Visual Rule Builder (editable
 *       source of truth). {@code null} for rules a developer hand-wrote in DRL.</li>
 *   <li>{@code drlText} - the executable DRL. For a BUILDER rule the backend generates it from
 *       {@code sourceJson}; for a DRL rule it is written by hand. <b>The engine only ever runs
 *       {@code drlText}.</b></li>
 * </ul>
 *
 * <p><b>Oracle notes:</b> {@code sourceJson}/{@code drlText} are {@link Lob} (CLOB); the id uses
 * a {@code SEQUENCE}; {@code updatedAt} is a {@link LocalDateTime} so it maps to a plain
 * {@code TIMESTAMP}; the version column is {@code VERSION_NO} to avoid clashing with JPA's
 * optimistic-locking {@code @Version}. Salience is not a column - it lives inside the DRL (and
 * the builder JSON), so it is versioned with the rule itself.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_DEFINITION",
        uniqueConstraints = @UniqueConstraint(name = "UK_RULE_DEFINITION_NAME", columnNames = {"GROUP_ID", "RULE_NAME"}))
public class RuleDefinition {

    /** Rule was hand-written in DRL by a developer. {@code sourceJson} is null. */
    public static final String SOURCE_DRL = "DRL";
    /** Rule was authored in the Visual Rule Builder. {@code drlText} is generated from {@code sourceJson}. */
    public static final String SOURCE_BUILDER = "BUILDER";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_definition_seq")
    @SequenceGenerator(name = "rule_definition_seq", sequenceName = "RULE_DEFINITION_SEQ", allocationSize = 1)
    private Long id;

    /** The single group this rule belongs to. LAZY - the rule base reads only drlText + id. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GROUP_ID", nullable = false)
    private RuleGroup group;

    /** Human-readable rule name. Unique within its group only (not globally). */
    @Column(name = "RULE_NAME", nullable = false, length = 255)
    private String ruleName;

    /** {@link #SOURCE_DRL} or {@link #SOURCE_BUILDER}. */
    @Column(name = "SOURCE_TYPE", nullable = false, length = 20)
    private String sourceType = SOURCE_DRL;

    /** The builder's saved form (editable). Null for hand-written DRL rules. */
    @Lob
    @Column(name = "SOURCE_JSON")
    private String sourceJson;

    /** The executable DRL. This is the only thing the engine compiles and runs. */
    @Lob
    @Column(name = "DRL_TEXT", nullable = false)
    private String drlText;

    /** Bumped on every successful re-save of the same rule. */
    @Column(name = "VERSION_NO", nullable = false)
    private Integer version = 1;

    /** Simple lifecycle label. Defaults to {@code ACTIVE}. */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Only active rules are compiled into the running rule base. Delete = deactivate. */
    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    /** Who last saved the rule (optional). */
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RuleGroup getGroup() {
        return group;
    }

    public void setGroup(RuleGroup group) {
        this.group = group;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceJson() {
        return sourceJson;
    }

    public void setSourceJson(String sourceJson) {
        this.sourceJson = sourceJson;
    }

    public String getDrlText() {
        return drlText;
    }

    public void setDrlText(String drlText) {
        this.drlText = drlText;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
