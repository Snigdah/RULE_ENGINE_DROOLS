package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RequestFlowMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to request-class-to-flow mappings.
 *
 * @author K M Farhat Snigdah
 */
public interface RequestFlowMapRepository extends JpaRepository<RequestFlowMap, Long> {

    Optional<RequestFlowMap> findByRequestClass(String requestClass);
}
