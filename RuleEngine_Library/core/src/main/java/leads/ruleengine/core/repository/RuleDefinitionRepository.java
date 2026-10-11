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

    Optional<RuleDefinition> findByRuleName(String ruleName);
}
