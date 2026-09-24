package com.example.droolspoc.context;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global reference data for the whole application, loaded once at startup and
 * read on every request. It is a small type-keyed registry so that MANY kinds
 * of reference data can live here side by side (blocked users today; allowed
 * currencies, holiday calendars, fraud lists, ... tomorrow).
 *
 * <p>Design intent (open for extension):</p>
 * <ul>
 *   <li>Each dataset is an immutable holder object (e.g. {@code BlockedUsers}).</li>
 *   <li>Each dataset has its own {@link GlobalReferenceLoader} that registers it here.</li>
 *   <li>Adding a new dataset = add ONE new loader + holder. This class never changes.</li>
 * </ul>
 *
 * <p>Thread-safety: backed by a {@link ConcurrentHashMap}; stored holders are
 * expected to be immutable.</p>
 */
@Component
public class GlobalContext {

    private final Map<Class<?>, Object> store = new ConcurrentHashMap<>();

    /** Register (or replace) the dataset of the given type. Called by loaders at startup. */
    public <T> void register(Class<T> type, T data) {
        store.put(type, Objects.requireNonNull(data, "data"));
    }

    /**
     * Fetch a dataset by type. Throws if it was never loaded — startup is
     * expected to register every dataset, so a missing one is a wiring bug and
     * we fail loudly rather than silently mis-evaluating a rule.
     */
    public <T> T get(Class<T> type) {
        Object value = store.get(type);
        if (value == null) {
            throw new IllegalStateException(
                    "Global context [" + type.getSimpleName() + "] was not loaded. "
                    + "Add/enable a GlobalReferenceLoader that registers it at startup.");
        }
        return type.cast(value);
    }

    /** Number of datasets currently registered (handy for a startup log line). */
    public int size() {
        return store.size();
    }
}
