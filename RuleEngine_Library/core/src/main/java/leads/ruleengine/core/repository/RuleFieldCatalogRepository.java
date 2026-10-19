package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleFieldCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Access to the field catalog ({@code RULE_FIELD_CATALOG}) that powers the builder dropdowns.
 *
 * @author K M Farhat Snigdah
 */
public interface RuleFieldCatalogRepository extends JpaRepository<RuleFieldCatalog, Long> {

    /**
     * Fields visible when authoring for a given flow: the flow's own fields plus the shared
     * fields ({@code flow_id IS NULL}), ordered by label.
     */
    @Query("select c from RuleFieldCatalog c where c.flow.id = :flowId or c.flow is null order by c.label asc")
    List<RuleFieldCatalog> findForFlow(@Param("flowId") Long flowId);

    /** First catalog row for a field key - used by the DRL generator to resolve path + type. */
    Optional<RuleFieldCatalog> findFirstByFieldKey(String fieldKey);

    /** Count of a flow's own fields (shared fields, flow_id NULL, are not counted). */
    long countByFlow_Id(Long flowId);

    /** Hard-delete a flow's own fields (used by force-delete of a flow). */
    void deleteByFlow_Id(Long flowId);
}
