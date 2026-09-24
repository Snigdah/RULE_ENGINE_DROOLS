package com.example.droolspoc.context;

/**
 * Extension point for global reference data. Implement this interface and mark
 * the implementation {@code @Component} to add a new dataset to the global
 * context — no existing class needs to change (open/closed principle).
 *
 * <p>Every implementation runs ONCE at startup (see {@link GlobalContextLoader}).</p>
 *
 * <pre>
 * // ---- Example: how a future developer adds "allowed currencies" ----
 * //
 * // 1) Immutable holder (in context/data/):
 * //    public final class AllowedCurrencies {
 * //        private final Set&lt;String&gt; codes;
 * //        public AllowedCurrencies(Set&lt;String&gt; codes) { this.codes = Set.copyOf(codes); }
 * //        public boolean isAllowed(String code) { return codes.contains(code); }
 * //    }
 * //
 * // 2) Loader that reads its own table/service and registers it:
 * //    @Component
 * //    public class AllowedCurrenciesLoader implements GlobalReferenceLoader {
 * //        private final CurrencyRepository repo;
 * //        public AllowedCurrenciesLoader(CurrencyRepository repo) { this.repo = repo; }
 * //        @Override public void load(GlobalContext ctx) {
 * //            Set&lt;String&gt; codes = repo.findAllEnabled().stream()
 * //                    .map(Currency::getCode).collect(toUnmodifiableSet());
 * //            ctx.register(AllowedCurrencies.class, new AllowedCurrencies(codes));
 * //        }
 * //    }
 * //
 * // 3) Use it in a DRL rule:
 * //    import com.example.droolspoc.context.data.AllowedCurrencies;
 * //    eval(globalContext.get(AllowedCurrencies.class).isAllowed($ctx.getTransaction().getCurrency()))
 * //
 * // GlobalContext, the initializer and the service stay untouched.
 * </pre>
 */
public interface GlobalReferenceLoader {

    /** Load the immutable dataset and register it into the given context. */
    void load(GlobalContext context);
}
