package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to agenda groups ({@code RULE_GROUP}).
 *
 * @author K M Farhat Snigdah
 */
public interface RuleGroupRepository extends JpaRepository<RuleGroup, Long> {

    /** A flow's own groups (shared COMMON excluded - it has no flow). */
    List<RuleGroup> findByFlow_IdOrderByOrderNoAsc(Long flowId);

    /** Shared groups (flow_id NULL) that every flow runs first. */
    List<RuleGroup> findByFlowIsNullOrderByOrderNoAsc();

    /** A specific group of a flow, by its code. */
    Optional<RuleGroup> findByFlow_IdAndGroupCode(Long flowId, String groupCode);

    /** A specific shared group by its code. */
    Optional<RuleGroup> findByFlowIsNullAndGroupCode(String groupCode);
}
