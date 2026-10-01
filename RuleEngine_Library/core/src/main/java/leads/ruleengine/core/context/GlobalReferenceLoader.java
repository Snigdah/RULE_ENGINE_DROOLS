package leads.ruleengine.core.context;

/**
 * Contract a consuming service implements to contribute reference data into the
 * {@link GlobalContext} at startup.
 *
 * <p>Each implementation is a Spring bean in the service. At startup
 * {@link GlobalContextLoader} finds them all and calls {@link #load(GlobalContext)} on each.
 * A service with no reference data simply defines no loaders - the context stays empty and
 * nothing breaks.</p>
 *
 * <p>Example (in the consuming service):</p>
 * <pre>
 * &#64;Component
 * class BlockedUsersLoader implements GlobalReferenceLoader {
 *     private final UserBlockRepository repo;
 *     BlockedUsersLoader(UserBlockRepository repo) { this.repo = repo; }
 *     public void load(GlobalContext ctx) {
 *         ctx.register(BlockedUsers.class, new BlockedUsers(repo.findBlockedUserIds()));
 *     }
 * }
 * </pre>
 *
 * @author K M Farhat Snigdah
 */
public interface GlobalReferenceLoader {

    void load(GlobalContext context);
}
