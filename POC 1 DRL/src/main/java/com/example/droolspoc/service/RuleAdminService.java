package com.example.droolspoc.service;

import com.example.droolspoc.exception.ContextNotFoundException;
import com.example.droolspoc.model.RuleFile;
import com.example.droolspoc.repository.RuleFileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the DRL files in the DB. Every change validates first, then saves,
 * then reloads the live rule base - so an upload/update takes effect at runtime
 * with no restart, and a bad DRL is rejected before it is persisted.
 */
@Service
public class RuleAdminService {

    private final RuleFileRepository ruleFileRepository;
    private final RuleBaseProvider ruleBaseProvider;

    public RuleAdminService(RuleFileRepository ruleFileRepository,
                            RuleBaseProvider ruleBaseProvider) {
        this.ruleFileRepository = ruleFileRepository;
        this.ruleBaseProvider = ruleBaseProvider;
    }

    /** Upload a new DRL file or update an existing one (by fileName). */
    public void upsert(String fileName, String drlText) {
        // 1. VALIDATE the candidate set (existing active files + this one) before saving.
        List<RuleFile> candidate = new ArrayList<>(ruleFileRepository.findByActiveTrue());
        candidate.removeIf(f -> f.getFileName().equals(fileName));
        RuleFile incoming = new RuleFile();
        incoming.setFileName(fileName);
        incoming.setDrlText(drlText);
        incoming.setActive(true);
        candidate.add(incoming);
        ruleBaseProvider.validate(candidate);   // throws RuleCompilationException -> nothing saved

        // 2. SAVE (new or updated)
        RuleFile row = ruleFileRepository.findByFileName(fileName).orElseGet(RuleFile::new);
        row.setFileName(fileName);
        row.setDrlText(drlText);
        row.setActive(true);
        row.setVersion(row.getVersion() == null ? 1 : row.getVersion() + 1);
        row.setUpdatedAt(Instant.now());
        ruleFileRepository.save(row);

        // 3. AUTO RELOAD - live immediately
        ruleBaseProvider.reload();
    }

    /** Deactivate a DRL file and reload. */
    public void delete(String fileName) {
        RuleFile row = ruleFileRepository.findByFileName(fileName)
                .orElseThrow(() -> new ContextNotFoundException("No rule file: " + fileName));
        row.setActive(false);
        row.setUpdatedAt(Instant.now());
        ruleFileRepository.save(row);
        ruleBaseProvider.reload();
    }

    public List<RuleFile> list() {
        return ruleFileRepository.findAll();
    }
}
