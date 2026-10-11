package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RequestFlowMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to fact/context-class-to-flow mappings.
 *
 * @author K M Farhat Snigdah
 */
public interface RequestFlowMapRepository extends JpaRepository<RequestFlowMap, Long> {

    /** Lookup by fact/context class - the execute() routing key. */
    Optional<RequestFlowMap> findByFactType(String factType);

    /** Any mapping row for a flow - used by the DRL generator to find the flow's fact type. */
    Optional<RequestFlowMap> findFirstByFlowName(String flowName);
}
