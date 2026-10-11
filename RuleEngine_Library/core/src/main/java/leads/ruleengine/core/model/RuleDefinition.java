package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One rule, stored in the database so rules can be changed at RUNTIME (save a new version, the
 * engine validates and hot-swaps it - no restart, no redeploy).
 *
 * <p>This replaces the older "one DRL file per row" model. A rule now carries BOTH forms:
 * <ul>
 *   <li>{@code sourceJson} - how the rule was authored in the Visual Rule Builder (the editable
 *       source of truth). {@code null} for rules a developer hand-wrote in DRL.</li>
 *   <li>{@code drlText} - the executable DRL. For a BUILDER rule the backend generates it from
 *       {@code sourceJson}; for a DRL rule it is written by hand. <b>The engine only ever runs
 *       {@code drlText}.</b></li>
 * </ul>
 *
 * <p><b>A rule belongs to one group, never a flow.</b> {@code agendaGroup} is the only routing
 * link a rule stores. A flow (see {@link FlowGroup}) pulls in groups, so a rule placed in a
 * shared group (e.g. {@code COMMON}) is reused by every flow that includes that group - with no
 * duplication and no many-to-many table.</p>
 *
 * <p><b>Oracle notes:</b> {@code sourceJson}/{@code drlText} are {@link Lob} (CLOB); the id uses
 * a {@code SEQUENCE}; {@code updatedAt} is a {@link LocalDateTime} so it maps to a plain
 * {@code TIMESTAMP} (an {@code Instant} would be read as timestamp-with-time-zone and throw
 * {@code ORA-18716}); the version column is {@code VERSION_NO} to avoid clashing with JPA's
 * optimistic-locking {@code @Version}.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_DEFINITION")
public class RuleDefinition {

    /** Rule was hand-written in DRL by a developer. {@code sourceJson} is null. */
    public static final String SOURCE_DRL = "DRL";
    /** Rule was authored in the Visual Rule Builder. {@code drlText} is generated from {@code sourceJson}. */
    public static final String SOURCE_BUILDER = "BUILDER";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_definition_seq")
    @SequenceGenerator(name = "rule_definition_seq", sequenceName = "RULE_DEFINITION_SEQ", allocationSize = 1)
    private Long id;

    /** Human-readable rule name, unique. Also used as the DRL resource name when compiling. */
    @Column(name = "RULE_NAME", nullable = false, unique = true, length = 255)
    private String ruleName;

    /** The single group this rule belongs to (e.g. COMMON, TRANSFER, LOAN). */
    @Column(name = "AGENDA_GROUP", nullable = false, length = 255)
    private String agendaGroup;

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

    /** Bumped on every successful re-save of the same rule name. */
    @Column(name = "VERSION_NO", nullable = false)
    private Integer version = 1;

    /**
     * Simple lifecycle label. Defaults to {@code ACTIVE}. Kept as a plain string so a fuller
     * maker-checker lifecycle (DRAFT/APPROVED/RETIRED...) can be layered on later without a
     * schema change - but nothing heavier is built now.
     */
    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Only active rules are compiled into the running rule base. Delete = deactivate. */
    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    /** Who last saved the rule (optional - filled from the admin call when available). */
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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getAgendaGroup() {
        return agendaGroup;
    }

    public void setAgendaGroup(String agendaGroup) {
        this.agendaGroup = agendaGroup;
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
