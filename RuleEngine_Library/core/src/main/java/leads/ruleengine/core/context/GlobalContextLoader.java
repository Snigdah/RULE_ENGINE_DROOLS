package leads.ruleengine.core.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Populates the shared {@link GlobalContext} from every {@link GlobalReferenceLoader} the
 * consuming service defines - at startup, and again on demand via {@link #reload()}.
 *
 * <p>Spring injects the full list of loaders. If the service defines none, the list is
 * empty and the context stays empty - which is fine; a DRL that does not use a global
 * simply never asks for one.</p>
 *
 * <p>{@link #reload()} re-runs all loaders (each re-reads its data from the DB), so a change
 * to the underlying reference data (e.g. a newly blocked user) takes effect without a
 * restart. It is exposed as {@code POST /admin/global-context/reload}.</p>
 *
 * @author K M Farhat Snigdah
 */
@Component
public class GlobalContextLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GlobalContextLoader.class);

    private final GlobalContext globalContext;
    private final List<GlobalReferenceLoader> loaders;

    public GlobalContextLoader(GlobalContext globalContext, List<GlobalReferenceLoader> loaders) {
        this.globalContext = globalContext;
        this.loaders = loaders;
    }

    /** Startup population. */
    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    /**
     * Clear and re-populate the global context from all loaders. Each loader re-reads its
     * source (usually the DB), so this picks up data that changed since the last load.
     */
    public synchronized void reload() {
        globalContext.clear();
        for (GlobalReferenceLoader loader : loaders) {
            loader.load(globalContext);
        }
        log.info("GlobalContext (re)loaded: {} reference object(s) registered by {} loader(s).",
                globalContext.size(), loaders.size());
    }
}
