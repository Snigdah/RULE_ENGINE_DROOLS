package leads.ruleengine.core.repository;

import leads.ruleengine.core.model.RuleFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Access to stored DRL files.
 *
 * @author K M Farhat Snigdah
 */
public interface RuleFileRepository extends JpaRepository<RuleFile, Long> {

    /** Active files only - these are what the running rule base is built from. */
    List<RuleFile> findByActiveTrue();

    Optional<RuleFile> findByFileName(String fileName);
}
