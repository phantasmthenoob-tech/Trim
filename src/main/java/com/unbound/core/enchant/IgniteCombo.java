package com.unbound.core.enchant;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks victims of hits that carried both Fire Aspect and Flame so their fire
 * is refreshed to effectively infinite (until water extinguishes it).
 */
public final class IgniteCombo {

    private final Map<UUID, Long> infiniteUntil = new ConcurrentHashMap<>();

    /** Flags the victim as part of a combo hit for a short detection window. */
    public void markInfinite(UUID victimId) {
        infiniteUntil.put(victimId, System.currentTimeMillis() + 1_000L);
    }

    /** True when the context's victim was combo-marked within the window. */
    public boolean wasComboHit(EnchantmentContext context) {
        // The pair of effects runs on the same event; each side sees the
        // other's mark within the 1s window.
        UUID victim = context.target().map(t -> t.getUniqueId()).orElse(null);
        if (victim == null) {
            return false;
        }
        Long until = infiniteUntil.get(victim);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            infiniteUntil.remove(victim);
            return false;
        }
        return true;
    }
}
