package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to stored rules ({@code RULE_DEFINITION}).
 *
 * @author K M Farhat Snigdah
 */
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {

    /** Active rules only - these are what the running rule base is built from. */
    List<RuleDefinition> findByActiveTrue();

    /** All rules in one exact group (active and inactive) - the admin list, so toggles are visible. */
    List<RuleDefinition> findByGroup_IdOrderByRuleNameAsc(Long groupId);

    /** A rule by (group, name) - the per-group uniqueness key used on save. */
    Optional<RuleDefinition> findByGroup_IdAndRuleName(Long groupId, String ruleName);

    /** Every rule row for a group (active or not) - used to guard group deletion (FK). */
    long countByGroup_Id(Long groupId);

    /** Hard-delete every rule row of a group (used by force-delete of a group). */
    void deleteByGroup_Id(Long groupId);
}
