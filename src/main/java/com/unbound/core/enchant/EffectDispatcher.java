package com.unbound.core.enchant;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Walks a context through the engine and runs every applicable effect.
 *
 * <pre>
 * Minecraft event -> handler -> EnchantmentContext
 *       -> ApplicabilityResolver -> effect.execute() -> guard-limited mutation
 * </pre>
 *
 * <p>Each effect runs isolated: an exception in one effect is logged and can
 * not prevent the others or break the original event. All effects share the
 * action's {@link ProcessingGuard} budgets, and the per-action timing is
 * reported to the {@link DebugSink}.</p>
 */
public final class EffectDispatcher {

    private final UniversalEnchantmentEngine engine;
    private final ApplicabilityResolver resolver;
    private final ProcessingGuard.Limits limits;
    private final DebugSink debugSink;
    private final Logger logger;

    public EffectDispatcher(UniversalEnchantmentEngine engine,
                            ApplicabilityResolver resolver,
                            ProcessingGuard.Limits limits,
                            DebugSink debugSink,
                            Logger logger) {
        this.engine = engine;
        this.resolver = resolver;
        this.limits = limits;
        this.debugSink = debugSink;
        this.logger = logger;
    }

    /**
     * Dispatches an action. Must be called inside a {@link ProcessingGuard}
     * scope opened by a handler; events without a guard are ignored (defense
     * against misbehaving integrations).
     */
    public void dispatch(EnchantmentContext context) {
        ProcessingGuard guard = context.guard();
        if (guard == null) {
            return;
        }
        if (guard.isDepthExceeded()) {
            return;
        }
        long start = System.nanoTime();
        int effectsRun = 0;
        for (EnchantmentDefinition definition : engine.definitions()) {
            if (!definition.supportedActions().contains(context.action())) {
                continue;
            }
            boolean levelAgnostic = ActionType.LEVEL_AGNOSTIC.contains(context.action());
            int level = engine.levelOf(context.item(), definition);
            if (!levelAgnostic && level <= 0) {
                continue;
            }
            EnchantmentContext bound = context.withDefinition(definition, level);
            if (!resolver.shouldRun(bound)) {
                continue;
            }
            effectsRun++;
            try {
                definition.effect().execute(bound);
            } catch (Throwable throwable) {
                logger.log(Level.WARNING, "[Unbound] Effect " + definition.key()
                        + " failed for action " + context.action(), throwable);
            } finally {
                debugSink.onEffect(bound, definition, "guard=" + describeGuard(guard));
            }
        }
        debugSink.onAction(context, System.nanoTime() - start, effectsRun);
    }

    private static String describeGuard(ProcessingGuard guard) {
        return "depth=" + guard.depth()
                + " entities=" + guard.entitiesAffected()
                + " blocks=" + guard.blocksAffected()
                + " projectiles=" + guard.projectilesCreated();
    }
}
