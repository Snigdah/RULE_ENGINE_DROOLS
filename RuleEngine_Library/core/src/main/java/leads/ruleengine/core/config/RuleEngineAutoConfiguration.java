package leads.ruleengine.core.config;

import leads.ruleengine.core.context.GlobalContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Single entry point that wires the rule engine into any consuming microservice.
 *
 * <p>Registered in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * The consumer adds the {@code com.leads:rule-engine} dependency and everything below
 * self-registers - no glue code, same idea as the AuthZ library.</p>
 *
 * <p><b>Why this goes beyond the AuthZ pattern:</b> AuthZ is stateless and ships no JPA.
 * This library ships its OWN entities ({@code rule_file}, {@code flow_group},
 * {@code request_flow_map}), which live in {@code leads.ruleengine.core.model} - a
 * different package from the host app. Spring would not scan them by default, so we
 * explicitly point {@link EntityScan} and {@link EnableJpaRepositories} at the library
 * packages. The consumer keeps its own {@code @SpringBootApplication} scanning its own
 * business entities; both coexist.</p>
 *
 * <p>{@link ComponentScan} picks up the library's services, controllers, context loaders
 * and exception handling. It is scoped to {@code leads.ruleengine.core}, so it never
 * reaches - and never clashes with - the consumer's own components.</p>
 *
 * @author K M Farhat Snigdah
 */
@AutoConfiguration
@ComponentScan(basePackages = "leads.ruleengine.core")
@EntityScan(basePackages = "leads.ruleengine.core.model")
@EnableJpaRepositories(basePackages = "leads.ruleengine.core.repository")
@EnableConfigurationProperties(RuleEngineProperties.class)
public class RuleEngineAutoConfiguration {

    /**
     * The shared reference-data registry rules read as a {@code global}. Plain object
     * (not component-scanned) so it is exposed as an explicit bean here. Populated at
     * startup by {@code GlobalContextLoader}.
     */
    @Bean
    public GlobalContext globalContext() {
        return new GlobalContext();
    }
}
