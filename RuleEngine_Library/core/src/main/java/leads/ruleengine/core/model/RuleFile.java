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
 * One DRL file, stored in the database so rules can be changed at RUNTIME (upload a new
 * version, the engine validates and hot-swaps it - no restart, no redeploy).
 *
 * <p><b>Oracle notes:</b>
 * <ul>
 *   <li>{@code drlText} is a {@link Lob} - maps to Oracle {@code CLOB}. A DRL file can be
 *       far larger than {@code VARCHAR2(4000)}.</li>
 *   <li>Id uses a {@code SEQUENCE} (the Oracle-idiomatic strategy) with
 *       {@code allocationSize = 1} so there are no id gaps.</li>
 *   <li>{@code updatedAt} is a {@link LocalDateTime} (not {@code Instant}) so it maps to a
 *       plain Oracle {@code TIMESTAMP}. Mapping {@code Instant} makes Hibernate read it as a
 *       timestamp-with-time-zone, which throws {@code ORA-18716} against a plain TIMESTAMP
 *       column.</li>
 *   <li>The version counter column is named {@code VERSION_NO} to avoid confusion with JPA
 *       optimistic-locking {@code @Version}.</li>
 * </ul>
 *
 * @author K M Farhat Snigdah
 */
@Entity
@Table(name = "RULE_FILE")
public class RuleFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_file_seq")
    @SequenceGenerator(name = "rule_file_seq", sequenceName = "RULE_FILE_SEQ", allocationSize = 1)
    private Long id;

    /** Logical file name, unique. Used as the resource path when compiling. */
    @Column(name = "FILE_NAME", nullable = false, unique = true, length = 255)
    private String fileName;

    /** The raw DRL source, compiled at runtime. */
    @Lob
    @Column(name = "DRL_TEXT", nullable = false)
    private String drlText;

    /** Only active files are compiled into the running rule base. Delete = deactivate. */
    @Column(name = "ACTIVE", nullable = false)
    private boolean active = true;

    /** Bumped on every successful re-upload of the same file name. */
    @Column(name = "VERSION_NO", nullable = false)
    private Integer version = 1;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDrlText() {
        return drlText;
    }

    public void setDrlText(String drlText) {
        this.drlText = drlText;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
