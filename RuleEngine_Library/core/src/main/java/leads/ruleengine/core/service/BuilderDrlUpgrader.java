package leads.ruleengine.core.service;

import leads.ruleengine.core.model.RuleDefinition;
import leads.ruleengine.core.model.RuleGroup;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time, automatic upgrade of BUILDER rules saved by an older library version.
 *
 * <p>Older generated DRL wrote the decision onto the caller's context
 * ({@code $ctx.setValid(false)}), which forced every context class to carry the decision fields.
 * Current DRL writes to the library-owned {@code RuleResult} global instead. At startup, before
 * the rule base is compiled, every BUILDER rule whose DRL is still in the old form is regenerated
 * from its stored {@code SOURCE_JSON}. Hand-written DRL rules are never touched.</p>
 *
 * <p>Idempotent: a rule already in the current form is skipped. A rule that cannot be regenerated
 * is logged and left unchanged, so this never blocks startup.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class BuilderDrlUpgrader {

    private static final Logger log = LoggerFactory.getLogger(BuilderDrlUpgrader.class);

    /** Marker present in every DRL produced by the current generator. */
    static final String CURRENT_MARKER = RuleDrlGenerator.RESULT_GLOBAL_DECL;

    private final RuleDefinitionRepository ruleRepository;
    private final RuleDrlGenerator drlGenerator;

    public BuilderDrlUpgrader(RuleDefinitionRepository ruleRepository, RuleDrlGenerator drlGenerator) {
        this.ruleRepository = ruleRepository;
        this.drlGenerator = drlGenerator;
    }

    @Transactional
    public void upgradeLegacyBuilderRules() {
        int upgraded = 0;
        for (RuleDefinition rule : ruleRepository.findAll()) {
            if (!RuleDefinition.SOURCE_BUILDER.equals(rule.getSourceType())) {
                continue;
            }
            String drl = rule.getDrlText();
            if (drl != null && drl.contains(CURRENT_MARKER)) {
                continue;
            }
            try {
                RuleGroup group = rule.getGroup();
                if (group == null || group.getFlow() == null || rule.getSourceJson() == null) {
                    continue;
                }
                String qualifier = group.getFlow().getFlowCode() + "." + group.getGroupCode();
                rule.setDrlText(drlGenerator.fromJson(rule.getSourceJson(),
                        group.getFlow().getFactType(), group.getGroupCode(), qualifier));
                ruleRepository.save(rule);
                upgraded++;
            } catch (RuntimeException e) {
                log.warn("Could not upgrade builder rule {} ('{}') to the RuleResult form - left unchanged: {}",
                        rule.getId(), rule.getRuleName(), e.getMessage());
            }
        }
        if (upgraded > 0) {
            log.info("Upgraded {} builder rule(s) to write the library RuleResult.", upgraded);
        }
    }
}
