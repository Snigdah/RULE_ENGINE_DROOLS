package leads.ruleengine.core.service;

import jakarta.annotation.PostConstruct;
import leads.ruleengine.core.exception.RuleCompilationException;
import leads.ruleengine.core.model.RuleFile;
import leads.ruleengine.core.repository.RuleFileRepository;
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
 * <p><b>The safety guarantee:</b> a new DRL is compiled into a throw-away container FIRST
 * ({@link #validate(List)}). Only if that succeeds does {@link #reload()} build a fresh
 * container and atomically swap it in. A DRL that does not compile is rejected before it
 * can touch the running engine - so a bad upload can never break rules that are already
 * live.</p>
 *
 * <p>The container reference is {@code volatile}: {@link #getContainer()} always returns
 * the current one, and readers in flight keep using the old container until they finish.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class RuleBaseProvider {

    private static final Logger log = LoggerFactory.getLogger(RuleBaseProvider.class);

    /** Where each DRL is placed in the virtual KieFileSystem before compilation. */
    private static final String RULES_PATH = "src/main/resources/rules/";

    private final RuleFileRepository ruleFileRepository;
    private final KieServices kieServices = KieServices.Factory.get();

    /** Unique release id per build so containers never collide in the KIE repository. */
    private final AtomicLong buildCounter = new AtomicLong();

    private volatile KieContainer container;

    public RuleBaseProvider(RuleFileRepository ruleFileRepository) {
        this.ruleFileRepository = ruleFileRepository;
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
     * Rebuild the rule base from all active DRL files and swap it in atomically.
     * Called at startup and after every successful rule upload/delete.
     */
    public synchronized void reload() {
        List<RuleFile> active = ruleFileRepository.findByActiveTrue();
        if (active.isEmpty()) {
            log.warn("No active DRL files - rule base is empty. Rules will fire nothing until one is uploaded.");
        }
        KieContainer fresh = build(active);
        KieContainer old = this.container;
        this.container = fresh;
        if (old != null) {
            old.dispose();
        }
        log.info("Rule base reloaded from {} active DRL file(s).", active.size());
    }

    /**
     * Compile a candidate set of files WITHOUT swapping the live container. Used to
     * validate an upload before committing it. Throws {@link RuleCompilationException} on
     * any compile error; disposes the throw-away container either way.
     */
    public void validate(List<RuleFile> candidateFiles) {
        KieContainer probe = build(candidateFiles);
        probe.dispose();
    }

    /**
     * Compile the given DRL files into a fresh {@link KieContainer}. Shared by
     * {@link #reload()} and {@link #validate(List)}.
     */
    private KieContainer build(List<RuleFile> files) {
        // Every build gets a UNIQUE release id. This is what keeps validate() safe: a probe
        // compile never overwrites the live module in the shared KIE repository, because it
        // deploys under its own version. reload() then swaps to its own fresh version.
        ReleaseId releaseId = kieServices.newReleaseId(
                "leads.ruleengine.runtime", "rules", "1.0." + buildCounter.incrementAndGet());

        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        for (RuleFile file : files) {
            // String overload (same as the validated POC); DRL type is inferred from the
            // ".drl" path extension.
            kfs.write(RULES_PATH + file.getFileName(), file.getDrlText());
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        if (!errors.isEmpty()) {
            throw new RuleCompilationException("DRL compilation failed: " + errors);
        }

        return kieServices.newKieContainer(releaseId);
    }
}
