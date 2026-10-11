package leads.ruleengine.core.service;

import jakarta.transaction.Transactional;
import leads.ruleengine.core.dto.BuilderRuleSpec;
import leads.ruleengine.core.dto.RuleDefinitionRequest;
import leads.ruleengine.core.dto.RulePreviewResponse;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RuleDefinition;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages stored rules ({@code RULE_DEFINITION}): save/replace, deactivate, list.
 *
 * <p><b>Two source types:</b>
 * <ul>
 *   <li>{@code BUILDER} - the payload carries {@code sourceJson} from the Visual Rule Builder; the
 *       {@link RuleDrlGenerator} turns it into the {@code drlText} the engine runs. The JSON is
 *       kept so the rule can be re-opened and edited in the builder.</li>
 *   <li>{@code DRL} - the payload carries hand-written {@code drl}; it is stored as-is with no JSON.</li>
 * </ul>
 *
 * <p><b>Validate-before-swap:</b> on save the candidate rule is compiled together with the other
 * active rules FIRST. Only if the whole set compiles is the row saved and the rule base reloaded.
 * A rule with a syntax error is rejected and the running engine is untouched.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class RuleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RuleAdminService.class);

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RuleBaseProvider ruleBaseProvider;
    private final RuleDrlGenerator drlGenerator;

    public RuleAdminService(RuleDefinitionRepository ruleDefinitionRepository,
                            RuleBaseProvider ruleBaseProvider,
                            RuleDrlGenerator drlGenerator) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.ruleBaseProvider = ruleBaseProvider;
        this.drlGenerator = drlGenerator;
    }

    /**
     * Create or replace a rule by name. Generates DRL from JSON for BUILDER rules, validates the
     * resulting active set before it is persisted, then reloads the rule base.
     */
    @Transactional
    public RuleDefinition save(RuleDefinitionRequest request) {
        RuleDefinition entity = ruleDefinitionRepository.findByRuleName(request.ruleName())
                .orElseGet(RuleDefinition::new);

        entity.setRuleName(request.ruleName());
        entity.setAgendaGroup(request.agendaGroup());

        String sourceType = request.sourceType() == null
                ? RuleDefinition.SOURCE_DRL
                : request.sourceType().trim().toUpperCase();
        entity.setSourceType(sourceType);

        if (RuleDefinition.SOURCE_BUILDER.equals(sourceType)) {
            if (isBlank(request.sourceJson())) {
                throw new IllegalArgumentException("sourceJson is required when sourceType is BUILDER.");
            }
            entity.setSourceJson(request.sourceJson());
            entity.setDrlText(drlGenerator.fromJson(request.sourceJson()));
        } else {
            if (isBlank(request.drl())) {
                throw new IllegalArgumentException("drl is required when sourceType is DRL.");
            }
            entity.setSourceJson(null);
            entity.setDrlText(request.drl());
        }

        entity.setActive(true);
        entity.setStatus("ACTIVE");
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + 1);
        entity.setCreatedBy(request.createdBy());
        entity.setUpdatedAt(LocalDateTime.now());

        // Compile the full active set WITH this candidate before committing anything.
        ruleBaseProvider.validate(candidateActiveSet(entity));

        RuleDefinition saved = ruleDefinitionRepository.save(entity);
        ruleBaseProvider.reload();
        log.info("Rule '{}' saved (type {}, version {}); rule base reloaded.",
                saved.getRuleName(), saved.getSourceType(), saved.getVersion());
        return saved;
    }

    /** Deactivate a rule (soft delete) and reload the rule base. */
    @Transactional
    public void delete(String ruleName) {
        ruleDefinitionRepository.findByRuleName(ruleName).ifPresent(rule -> {
            rule.setActive(false);
            rule.setStatus("INACTIVE");
            rule.setUpdatedAt(LocalDateTime.now());
            ruleDefinitionRepository.save(rule);
            ruleBaseProvider.reload();
            log.info("Rule '{}' deactivated; rule base reloaded.", ruleName);
        });
    }

    /**
     * Preview: generate the DRL for a builder spec WITHOUT saving, and compile-check it so the UI
     * can show the generated DRL and whether it is valid. The live engine is never touched.
     */
    public RulePreviewResponse preview(BuilderRuleSpec spec) {
        String drl;
        try {
            drl = drlGenerator.generate(spec);
        } catch (RuntimeException e) {
            return new RulePreviewResponse(null, false, e.getMessage());
        }
        try {
            RuleDefinition probe = new RuleDefinition();
            probe.setRuleName(isBlank(spec.ruleName) ? "preview" : spec.ruleName);
            probe.setAgendaGroup(isBlank(spec.agendaGroup) ? "PREVIEW" : spec.agendaGroup);
            probe.setSourceType(RuleDefinition.SOURCE_BUILDER);
            probe.setDrlText(drl);
            ruleBaseProvider.validate(List.of(probe));
            return new RulePreviewResponse(drl, true, null);
        } catch (RuleCompilationException e) {
            return new RulePreviewResponse(drl, false, e.getMessage());
        }
    }

    public List<RuleDefinition> list() {
        return ruleDefinitionRepository.findByActiveTrue();   // live rules only; soft-deleted are hidden
    }

    /**
     * The active set as it WOULD look with the candidate applied: all currently-active rules,
     * with the candidate replacing any same-named entry (or added if new).
     */
    private List<RuleDefinition> candidateActiveSet(RuleDefinition candidate) {
        List<RuleDefinition> set = new ArrayList<>();
        for (RuleDefinition existing : ruleDefinitionRepository.findByActiveTrue()) {
            if (!existing.getRuleName().equals(candidate.getRuleName())) {
                set.add(existing);
            }
        }
        set.add(candidate);
        return set;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
