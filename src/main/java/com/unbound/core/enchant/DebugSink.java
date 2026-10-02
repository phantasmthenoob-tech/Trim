package com.unbound.core.enchant;

/**
 * Receives debug/tracing output from the dispatcher. Kept as a tiny pure
 * interface so the core has no dependency on the Bukkit debug implementation
 * and so tests can plug in a recording sink.
 */
public interface DebugSink {

    DebugSink NOOP = new DebugSink() {
        @Override
        public void onEffect(EnchantmentContext context, EnchantmentDefinition definition, String detail) {
        }

        @Override
        public void onAction(EnchantmentContext context, long nanos, int effectsRun) {
        }
    };

    /** Called after an effect ran for a context. */
    void onEffect(EnchantmentContext context, EnchantmentDefinition definition, String detail);

    /** Called once after all effects for a top-level action were processed. */
    void onAction(EnchantmentContext context, long nanos, int effectsRun);
}
