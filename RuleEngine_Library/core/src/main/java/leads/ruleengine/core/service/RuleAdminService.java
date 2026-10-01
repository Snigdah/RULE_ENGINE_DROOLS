package leads.ruleengine.core.service;

import jakarta.transaction.Transactional;
import leads.ruleengine.core.model.RuleFile;
import leads.ruleengine.core.repository.RuleFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages stored DRL files: upload/replace, deactivate, list.
 *
 * <p><b>Validate-before-swap:</b> on upload the candidate DRL is compiled together with the
 * other active files FIRST. Only if the whole set compiles is the row saved and the live
 * rule base reloaded. A DRL with a syntax error is rejected and the running engine is
 * untouched.</p>
 *
 * @author K M Farhat Snigdah
 */
@Service
public class RuleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RuleAdminService.class);

    private final RuleFileRepository ruleFileRepository;
    private final RuleBaseProvider ruleBaseProvider;

    public RuleAdminService(RuleFileRepository ruleFileRepository, RuleBaseProvider ruleBaseProvider) {
        this.ruleFileRepository = ruleFileRepository;
        this.ruleBaseProvider = ruleBaseProvider;
    }

    /**
     * Create or replace a DRL file by name. Validates the resulting active set before it is
     * persisted, then reloads the rule base.
     */
    @Transactional
    public RuleFile upsert(String fileName, String drlText) {
        RuleFile entity = ruleFileRepository.findByFileName(fileName).orElseGet(RuleFile::new);
        entity.setFileName(fileName);
        entity.setDrlText(drlText);
        entity.setActive(true);
        entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion() + 1);
        entity.setUpdatedAt(LocalDateTime.now());

        // Compile the full active set WITH this candidate before committing anything.
        ruleBaseProvider.validate(candidateActiveSet(entity));

        RuleFile saved = ruleFileRepository.save(entity);
        ruleBaseProvider.reload();
        log.info("DRL '{}' upserted (version {}); rule base reloaded.", fileName, saved.getVersion());
        return saved;
    }

    /** Deactivate a file (soft delete) and reload the rule base. */
    @Transactional
    public void delete(String fileName) {
        ruleFileRepository.findByFileName(fileName).ifPresent(file -> {
            file.setActive(false);
            file.setUpdatedAt(LocalDateTime.now());
            ruleFileRepository.save(file);
            ruleBaseProvider.reload();
            log.info("DRL '{}' deactivated; rule base reloaded.", fileName);
        });
    }

    public List<RuleFile> list() {
        return ruleFileRepository.findAll();
    }

    /**
     * The active set as it WOULD look with the candidate applied: all currently-active files,
     * with the candidate replacing any same-named entry (or added if new).
     */
    private List<RuleFile> candidateActiveSet(RuleFile candidate) {
        List<RuleFile> set = new ArrayList<>();
        for (RuleFile existing : ruleFileRepository.findByActiveTrue()) {
            if (!existing.getFileName().equals(candidate.getFileName())) {
                set.add(existing);
            }
        }
        set.add(candidate);
        return set;
    }
}
