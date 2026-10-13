package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Access to the flow master ({@code RULE_FLOW}) - also the router, since {@code requestClass}
 * lives here.
 *
 * @author K M Farhat Snigdah
 */
public interface RuleFlowRepository extends JpaRepository<RuleFlow, Long> {

    /** Lookup by flow code (the stable flow identity used in admin paths). */
    Optional<RuleFlow> findByFlowCode(String flowCode);

    /** Lookup by request class - the execute() routing key. */
    Optional<RuleFlow> findByRequestClass(String requestClass);
}
