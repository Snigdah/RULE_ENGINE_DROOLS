package leads.ruleengine.core.context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * A type-keyed registry of shared reference data that rules can read as a Drools
 * {@code global} (for example a blocked-user set, a holiday calendar, a fee table).
 *
 * <p><b>Open for extension:</b> a service adds a new kind of reference data by writing a
 * {@link GlobalReferenceLoader} that registers its object here - no change to this class
 * and no change to the library. Rules reach it with
 * {@code globalContext.get(BlockedUsers.class)}.</p>
 *
 * <p>Loaded at startup by {@link GlobalContextLoader}, and refreshable at runtime via the
 * same loader's {@code reload()} (exposed as an admin endpoint) when the underlying DB data
 * changes. Reads are thread-safe.</p>
 *
 * @author K M Farhat Snigdah
 */
public class GlobalContext {

    private final Map<Class<?>, Object> store = new ConcurrentHashMap<>();

    /** Register (or replace) the reference object for its type. */
    public <T> void register(Class<T> type, T value) {
        store.put(type, value);
    }

    /** Fetch the reference object for a type, or {@code null} if none was registered. */
    public <T> T get(Class<T> type) {
        return type.cast(store.get(type));
    }

    /** Drop everything - used by a reload before the loaders re-populate from the DB. */
    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
