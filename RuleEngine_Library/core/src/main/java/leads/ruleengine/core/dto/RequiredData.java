package leads.ruleengine.core.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * What the ACTIVE rules of one flow need - the answer a service reads before building its context.
 *
 * <p>Built by {@code RequiredDataResolver} when rules / groups / flows reload, held in memory, and
 * read per request with no I/O. A service calls {@link #needs(String)} in front of each lookup:</p>
 *
 * <pre>
 * RequiredData need = ruleEngine.requiredData(TransactionRequest.class);   // memory, no DB
 *
 * if (need.needs("userLimit")) {
 *     context.setUserLimit(userLimitRepository.findByUserId(userId));       // repository / service call
 * }
 * </pre>
 *
 * <p>A <b>root</b> is the first segment of a catalog {@code drl_path}: {@code userLimit!.limit}
 * has root {@code userLimit}. One lookup fills one root.</p>
 *
 * <p><b>Safety:</b> {@link #needs(String)} answers {@code false} only when it is PROVEN that no
 * active rule reads the root. Anything unproven - a hand-written DRL rule without a
 * {@code // @requires:} hint, an unknown root name (typo), the feature switched off - answers
 * {@code true}, i.e. the classic "load everything" behaviour.</p>
 *
 * @param flowCode   the flow this answer belongs to
 * @param roots      roots read by the flow's active rules (meaningful only when {@code complete})
 * @param fields     field keys read by the flow's active rules (for the admin endpoint)
 * @param knownRoots every root the flow can have: its catalog rows (own + shared) plus DRL hints
 * @param complete   true = usage of every active rule was proven; false = load everything
 *
 * @author K M Farhat Snigdah
 */
public record RequiredData(String flowCode,
                           Set<String> roots,
                           Set<String> fields,
                           Set<String> knownRoots,
                           boolean complete) {

    private static final Logger log = LoggerFactory.getLogger(RequiredData.class);

    public RequiredData {
        roots = roots == null ? Set.of() : Set.copyOf(roots);
        fields = fields == null ? Set.of() : Set.copyOf(fields);
        knownRoots = knownRoots == null ? Set.of() : Set.copyOf(knownRoots);
    }

    /** Usage could not be proven: the caller must load everything. */
    public static RequiredData everything(String flowCode, Set<String> knownRoots) {
        return new RequiredData(flowCode, Set.of(), Set.of(), knownRoots, false);
    }

    /** The flow fires no rules (inactive, or no active rules): nothing to load. */
    public static RequiredData nothing(String flowCode, Set<String> knownRoots) {
        return new RequiredData(flowCode, Set.of(), Set.of(), knownRoots, true);
    }

    /** Same flow and known roots, but "load everything" - used by shadow mode and the off switch. */
    public RequiredData asEverything() {
        return everything(flowCode, knownRoots);
    }

    /**
     * Should the service load this root?
     *
     * @param root the root name, e.g. {@code "userLimit"} (first segment of the catalog drl_path)
     * @return {@code false} only when it is proven that no active rule of the flow reads it
     */
    public boolean needs(String root) {
        if (root == null || root.isBlank()) {
            return true;
        }
        if (!knownRoots.isEmpty() && !knownRoots.contains(root)) {
            log.error("RequiredData.needs(\"{}\"): '{}' is not a root of flow {} (known roots: {}). "
                    + "Check the name - loading it anyway to stay safe.", root, root, flowCode, knownRoots);
            return true;
        }
        return !complete || roots.contains(root);
    }
}
