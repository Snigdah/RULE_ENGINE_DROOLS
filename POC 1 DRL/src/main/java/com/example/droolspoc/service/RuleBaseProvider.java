package com.example.droolspoc.service;

import com.example.droolspoc.exception.RuleCompilationException;
import com.example.droolspoc.model.RuleFile;
import com.example.droolspoc.repository.RuleFileRepository;
import jakarta.annotation.PostConstruct;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Holds the LIVE KieContainer and rebuilds it from the DB on demand.
 *
 * <p>Rules live in the {@code rule_file} table and are managed at runtime via
 * the admin endpoints. {@link #reload()} compiles a fresh container from all
 * active rows and swaps it in atomically (volatile reference); a compile failure
 * keeps the old container, so a bad upload never breaks the running rules.</p>
 *
 * <p>No seeding: on a fresh deployment the table is empty and the container has
 * no rules (every transaction passes) until rules are uploaded via
 * {@code POST /admin/rules}. Upload the rules right after deploy.</p>
 */
@Service
public class RuleBaseProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleBaseProvider.class);

    private final RuleFileRepository ruleFileRepository;
    private volatile KieContainer container;

    public RuleBaseProvider(RuleFileRepository ruleFileRepository) {
        this.ruleFileRepository = ruleFileRepository;
    }

    /** At startup: build the container from whatever active rows exist (may be none). */
    @PostConstruct
    public void init() {
        reload();
    }

    /** The current live container. Callers create sessions from this. */
    public KieContainer getContainer() {
        KieContainer c = this.container;
        if (c == null) {
            throw new IllegalStateException("Rule container is not built yet");
        }
        return c;
    }

    /** Rebuild from all active DB rows and swap in atomically. */
    public synchronized void reload() {
        List<RuleFile> active = ruleFileRepository.findByActiveTrue();
        KieContainer rebuilt = build(active);      // throws on compile error -> old kept
        KieContainer previous = this.container;
        this.container = rebuilt;                   // atomic swap
        if (previous != null) {
            previous.dispose();
        }
        if (active.isEmpty()) {
            LOGGER.warn("Rule base reloaded with NO active rules - upload rules via POST /admin/rules");
        } else {
            LOGGER.info("Rule base reloaded: {} active DRL file(s)", active.size());
        }
    }

    /** Compile a candidate set WITHOUT swapping - used to validate an upload first. */
    public void validate(List<RuleFile> candidateFiles) {
        build(candidateFiles).dispose();
    }

    private KieContainer build(List<RuleFile> files) {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        for (RuleFile f : files) {
            kfs.write("src/main/resources/rules/" + f.getFileName(), f.getDrlText());
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new RuleCompilationException("DRL compilation failed: " + results.getMessages());
        }
        return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    }
}
