package leads.ruleengine.core.controller;

import leads.ruleengine.core.context.GlobalContextLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime refresh of the global reference data. Call this after the underlying DB values
 * change (e.g. a user was blocked/unblocked) to re-run every {@code GlobalReferenceLoader}
 * and update the in-memory {@link GlobalContextLoader} - no restart.
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default). A service
 * with no reference data still has this endpoint; it simply reloads an empty context.</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin/global-context")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobalContextAdminController {

    private final GlobalContextLoader globalContextLoader;

    public GlobalContextAdminController(GlobalContextLoader globalContextLoader) {
        this.globalContextLoader = globalContextLoader;
    }

    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
        globalContextLoader.reload();
        return ResponseEntity.ok().build();
    }
}
