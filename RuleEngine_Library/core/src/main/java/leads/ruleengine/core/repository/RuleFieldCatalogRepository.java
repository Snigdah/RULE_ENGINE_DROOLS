package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleFieldCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Access to the field catalog ({@code RULE_FIELD_CATALOG}) that powers the builder dropdowns.
 *
 * @author K M Farhat Snigdah
 */
public interface RuleFieldCatalogRepository extends JpaRepository<RuleFieldCatalog, Long> {

    /**
     * Fields visible when authoring for a given flow. Call with {@code [flow, "COMMON"]} so the
     * flow's own fields and the shared COMMON fields both come back.
     */
    List<RuleFieldCatalog> findByFlowNameInOrderByLabelAsc(Collection<String> flowNames);

    /** First catalog row for a field key - used by the DRL generator to resolve path + type. */
    Optional<RuleFieldCatalog> findFirstByFieldKey(String fieldKey);
}
