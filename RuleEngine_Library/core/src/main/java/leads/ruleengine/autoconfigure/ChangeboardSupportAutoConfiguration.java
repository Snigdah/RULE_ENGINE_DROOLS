package leads.ruleengine.autoconfigure;

import com.leads.changeboard.hibernate.HibernateAggregateGraphService;
import com.leads.changeboard.hibernate.HibernateCreateIdentityValidator;
import com.leads.config.ChangeBoardAutoConfiguration;
import com.leads.dataAccess.ChangeBoardDataAccess;
import com.leads.dataAccess.ChangeBoardDataAccessImpl;
import com.leads.utils.HibernateMetaDataUtil;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Beans changeboard needs but does not reliably create on its own.
 *
 * <p>RuleGroup/RuleDefinition extend changeboard's base entity, so changeboard's auto-configuration
 * always loads with this library. It guards its data beans with {@code @ConditionalOnBean(DataSource
 * / JdbcTemplate)} but declares no ordering, so it can be evaluated before Boot has created those
 * beans - the guards then fail and the context does not start. This configuration runs after Boot's
 * DataSource/JPA/JDBC setup and before changeboard, and supplies the beans; changeboard's own
 * definitions are {@code @ConditionalOnMissingBean} and step aside.</p>
 *
 * <p>It also supplies {@link HibernateCreateIdentityValidator}, which changeboard's Hibernate
 * approval service requires but only registers through component scanning.</p>
 *
 * <p>Deliberately outside {@code leads.ruleengine.core} so the library's component scan does not
 * pick it up as a plain configuration (that would lose the ordering).</p>
 *
 * @author K M Farhat Snigdah
 */
@AutoConfiguration(
        after = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class},
        before = ChangeBoardAutoConfiguration.class)
public class ChangeboardSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public HibernateMetaDataUtil hibernateMetaDataUtil(DataSource dataSource) {
        return new HibernateMetaDataUtil(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public ChangeBoardDataAccess changeBoardDataAccess(EntityManager entityManager, JdbcTemplate jdbcTemplate) {
        return new ChangeBoardDataAccessImpl(entityManager, jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public HibernateAggregateGraphService hibernateAggregateGraphService() {
        return new HibernateAggregateGraphService();
    }

    @Bean
    @ConditionalOnMissingBean
    public HibernateCreateIdentityValidator hibernateCreateIdentityValidator(HibernateAggregateGraphService graphService) {
        return new HibernateCreateIdentityValidator(graphService);
    }
}
