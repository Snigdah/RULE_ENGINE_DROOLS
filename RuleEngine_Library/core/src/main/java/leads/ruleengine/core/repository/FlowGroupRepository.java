package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.FlowGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to flow-to-agenda-group rows.
 *
 * @author K M Farhat Snigdah
 */
public interface FlowGroupRepository extends JpaRepository<FlowGroup, Long> {

    /** Replace-in-place helper for flow updates (delete then re-insert the ordered groups). */
    void deleteByFlowName(String flowName);

    /** Any row for a flow - used by the DRL generator to read the flow's FACT_TYPE. */
    Optional<FlowGroup> findFirstByFlowName(String flowName);
}
