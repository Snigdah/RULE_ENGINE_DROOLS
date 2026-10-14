package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One field a business user may build a rule about - the "palette" behind the Visual Rule
 * Builder's dropdowns. The UI reads these rows to populate the Field / Operator / Value
 * controls, so a non-technical user never types a DRL path.
 *
 * <p><b>Scoped by {@link #flow} (FK).</b> A row scoped to a specific flow shows only when
 * authoring for that flow; a row with {@code flow} NULL is <b>shared</b> and shows in every
 * flow. The UI query is {@code WHERE flow_id = :flowId OR flow_id IS NULL}.</p>
 *
 * <p>Defined once per service by a developer. Read-only to the UI and the engine.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_FIELD_CATALOG",
        uniqueConstraints = @UniqueConstraint(name = "UK_RULE_FIELD", columnNames = {"FLOW_ID", "FIELD_KEY"}))
public class RuleFieldCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_field_catalog_seq")
    @SequenceGenerator(name = "rule_field_catalog_seq", sequenceName = "RULE_FIELD_CATALOG_SEQ", allocationSize = 1)
    private Long id;

    /** Owning flow, or NULL for a shared field shown in every flow. EAGER - small table. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "FLOW_ID")
    private RuleFlow flow;

    /** Stable key the builder stores in SOURCE_JSON, e.g. {@code transaction.amount}. */
    @Column(name = "FIELD_KEY", nullable = false, length = 200)
    private String fieldKey;

    /** Friendly label shown in the dropdown, e.g. {@code Transaction Amount}. */
    @Column(name = "LABEL", nullable = false, length = 200)
    private String label;

    /** {@code NUMBER}, {@code ENUM}, {@code BOOLEAN} or {@code STRING} - drives the value control and quoting. */
    @Column(name = "DATA_TYPE", nullable = false, length = 20)
    private String dataType;

    /** The real path used in generated DRL, e.g. {@code transaction.amount}. */
    @Column(name = "DRL_PATH", nullable = false, length = 300)
    private String drlPath;

    /** Comma/space-separated operators the UI should allow, e.g. {@code > < >= <= ==}. */
    @Column(name = "ALLOWED_OPS", nullable = false, length = 200)
    private String allowedOps;

    /** Allowed values for ENUM/BOOLEAN fields, e.g. {@code BDT, USD, EUR, GBP}. Null for free NUMBER. */
    @Column(name = "ALLOWED_VALUES", length = 1000)
    private String allowedValues;

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

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDrlPath() {
        return drlPath;
    }

    public void setDrlPath(String drlPath) {
        this.drlPath = drlPath;
    }

    public String getAllowedOps() {
        return allowedOps;
    }

    public void setAllowedOps(String allowedOps) {
        this.allowedOps = allowedOps;
    }

    public String getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String allowedValues) {
        this.allowedValues = allowedValues;
    }
}
