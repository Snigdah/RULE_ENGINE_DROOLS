package leads.ruleengine.core.service;

import jakarta.annotation.PostConstruct;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RuleDefinition;
import leads.ruleengine.core.repository.RuleDefinitionRepository;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the live Drools rule base and rebuilds it from the database at RUNTIME.
 *
 * <p>The source is every active {@link RuleDefinition}: the engine compiles each rule's
 * {@code drlText} (the only form it runs) into one container.</p>
 *
 * <p><b>The safety guarantee:</b> a candidate set is compiled into a throw-away container FIRST
 * ({@link #validate(List)}). Only if that succeeds does {@link #reload()} build a fresh container
 * and atomically swap it in. A rule that does not compile is rejected before it can touch the
 * running engine - so a bad save can never break rules that are already live.</p>
 *
 * <p>The container reference is {@code volatile}: {@link #getContainer()} always returns the
 * current one, and readers in flight keep using the old container until they finish.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class RuleBaseProvider {

    private static final Logger log = LoggerFactory.getLogger(RuleBaseProvider.class);

    /** Where each DRL is placed in the virtual KieFileSystem before compilation. */
    private static final String RULES_PATH = "src/main/resources/rules/";

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final KieServices kieServices = KieServices.Factory.get();

    /** Unique release id per build so containers never collide in the KIE repository. */
    private final AtomicLong buildCounter = new AtomicLong();

    private volatile KieContainer container;

    public RuleBaseProvider(RuleDefinitionRepository ruleDefinitionRepository) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** The current rule base. A new {@code KieSession} is created per request from this. */
    public KieContainer getContainer() {
        return container;
    }

    /**
     * Rebuild the rule base from all active rules and swap it in atomically.
     * Called at startup and after every successful rule save/delete.
     */
    public synchronized void reload() {
        List<RuleDefinition> active = ruleDefinitionRepository.findByActiveTrue();
        if (active.isEmpty()) {
            log.warn("No active rules - rule base is empty. Rules will fire nothing until one is saved.");
        }
        KieContainer fresh = build(active);
        KieContainer old = this.container;
        this.container = fresh;
        if (old != null) {
            old.dispose();
        }
        log.info("Rule base reloaded from {} active rule(s).", active.size());
    }

    /**
     * Compile a candidate set WITHOUT swapping the live container. Used to validate a save
     * before committing it. Throws {@link RuleCompilationException} on any compile error;
     * disposes the throw-away container either way.
     */
    public void validate(List<RuleDefinition> candidates) {
        KieContainer probe = build(candidates);
        probe.dispose();
    }

    /**
     * Compile the given rules into a fresh {@link KieContainer}. Shared by {@link #reload()}
     * and {@link #validate(List)}.
     */
    private KieContainer build(List<RuleDefinition> rules) {
        // Every build gets a UNIQUE release id. This is what keeps validate() safe: a probe
        // compile never overwrites the live module in the shared KIE repository, because it
        // deploys under its own version. reload() then swaps to its own fresh version.
        ReleaseId releaseId = kieServices.newReleaseId(
                "leads.ruleengine.runtime", "rules", "1.0." + buildCounter.incrementAndGet());

        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        for (RuleDefinition rule : rules) {
            // Each rule becomes its own .drl resource; DRL type is inferred from the extension.
            kfs.write(RULES_PATH + resourceName(rule), rule.getDrlText());
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        if (!errors.isEmpty()) {
            throw new RuleCompilationException("DRL compilation failed: " + errors);
        }

        return kieServices.newKieContainer(releaseId);
    }

    /**
     * A filesystem-safe, UNIQUE .drl resource name. Keyed on the rule id (rule names are no
     * longer globally unique - the same name can exist in two groups), with a hash fallback for
     * an unsaved probe rule that has no id yet (preview / validate-before-save).
     */
    private String resourceName(RuleDefinition rule) {
        String base = rule.getId() != null
                ? "rule_" + rule.getId()
                : "rule_new_" + Integer.toHexString(System.identityHashCode(rule));
        return base + ".drl";
    }
}
