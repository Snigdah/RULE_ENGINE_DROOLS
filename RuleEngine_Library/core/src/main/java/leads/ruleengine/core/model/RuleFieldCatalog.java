package leads.ruleengine.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * One field a business user may build a rule about - the "palette" behind the Visual Rule
 * Builder's dropdowns. The UI reads these rows to populate the Field / Operator / Value
 * controls, so a non-technical user never types a DRL path.
 *
 * <p><b>Scoped by {@code flowName}.</b> A row scoped to a specific flow (e.g.
 * {@code TRANSFER_TRANSACTION}) shows only when authoring for that flow; a row scoped to
 * {@code COMMON} shows in every flow (used by shared rules). The UI query is simply
 * {@code WHERE FLOW_NAME IN (:flow, 'COMMON')}.</p>
 *
 * <p>This table is defined once per service by a developer. It is read-only to the UI and to
 * the rule engine - it never holds a rule, only the fields rules may reference.</p>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_FIELD_CATALOG")
public class RuleFieldCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_field_catalog_seq")
    @SequenceGenerator(name = "rule_field_catalog_seq", sequenceName = "RULE_FIELD_CATALOG_SEQ", allocationSize = 1)
    private Long id;

    /** Stable key the builder stores in SOURCE_JSON, e.g. {@code transaction.amount}. */
    @Column(name = "FIELD_KEY", nullable = false, length = 200)
    private String fieldKey;

    /** The flow this field belongs to, or {@code COMMON} to show in every flow. */
    @Column(name = "FLOW_NAME", nullable = false, length = 255)
    private String flowName;

    /** Friendly label shown in the dropdown, e.g. {@code Transaction Amount}. */
    @Column(name = "LABEL", nullable = false, length = 200)
    private String label;

    /** {@code NUMBER}, {@code ENUM} or {@code BOOLEAN} - drives the value control and quoting. */
    @Column(name = "DATA_TYPE", nullable = false, length = 20)
    private String dataType;

    /** The real path used in generated DRL, e.g. {@code transaction.amount}. */
    @Column(name = "DRL_PATH", nullable = false, length = 300)
    private String drlPath;

    /** Comma-separated operators the UI should allow, e.g. {@code >,<,>=,<=,==}. */
    @Column(name = "ALLOWED_OPS", nullable = false, length = 200)
    private String allowedOps;

    /** Comma-separated allowed values for ENUM/BOOLEAN fields, e.g. {@code BDT,USD,EUR,GBP}. Null for free NUMBER. */
    @Column(name = "ALLOWED_VALUES", length = 1000)
    private String allowedValues;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
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
