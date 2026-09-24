package com.example.droolspoc.context.data;

/**
 * TEMPLATE - how to add a new global context. Not a bean; does nothing.
 * Real working example: see BlockedUsers + BlockedUsersLoader in this package.
 *
 * 1) Immutable holder:
 *      public final class AllowedCurrencies {
 *          private final Set<String> codes;
 *          public AllowedCurrencies(Set<String> c) { this.codes = Set.copyOf(c); }
 *          public boolean isAllowed(String c) { return codes.contains(c); }
 *      }
 *
 * 2) @Component loader that registers it at startup:
 *      @Component
 *      class AllowedCurrenciesLoader implements GlobalReferenceLoader {
 *          public void load(GlobalContext ctx) {
 *              ctx.register(AllowedCurrencies.class, new AllowedCurrencies(...));
 *          }
 *      }
 *
 * 3) Use it in a rule:
 *      globalContext.get(AllowedCurrencies.class).isAllowed(...)
 */
final class ExampleGlobalContextTemplate {
    private ExampleGlobalContextTemplate() { }
}
