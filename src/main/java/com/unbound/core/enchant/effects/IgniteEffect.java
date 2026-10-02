package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.EffectMath;

import org.bukkit.entity.Entity;

/**
 * Fire Aspect and Flame, Unbound-style — each works on the other's domain.
 *
 * <ul>
 *   <li><b>Fire Aspect</b> (normally melee-only) now also ignites targets hit
 *       by the holder's projectiles (snowballs, wind charges, ...).</li>
 *   <li><b>Flame</b> (normally bow-only) now also ignites melee targets.</li>
 *   <li><b>Combo</b>: when both are present, the victim keeps burning until
 *       they enter water (infinite fire ticks refreshed while both
 *       enchantments contributed to the hit).</li>
 * </ul>
 */
public final class IgniteEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    private final boolean meleeSide;
    /** Marks hits that carried the other side too (infinite burn trigger). */
    private final com.unbound.core.enchant.IgniteCombo combo;

    public IgniteEffect(Supplier<UnboundConfig> config, boolean meleeSide) {
        this(config, meleeSide, new com.unbound.core.enchant.IgniteCombo());
    }

    IgniteEffect(Supplier<UnboundConfig> config, boolean meleeSide, com.unbound.core.enchant.IgniteCombo combo) {
        this.config = config;
        this.meleeSide = meleeSide;
        this.combo = combo;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        UnboundConfig.IgniteSettings settings = config.get().ignite();
        if (!settings.enabled()) {
            return false;
        }
        if (meleeSide) {
            return settings.flameOnMelee() && context.action() == ActionType.MELEE_IMPACT;
        }
        return settings.fireAspectOnProjectiles() && context.action() == ActionType.PROJECTILE_HIT;
    }

    @Override
    public void execute(EnchantmentContext context) {
        Entity victim = context.target().orElse(null);
        if (victim == null || victim.isDead() || !(victim instanceof org.bukkit.entity.LivingEntity living)) {
            return;
        }
        UnboundConfig.IgniteSettings settings = config.get().ignite();
        // The "other side" context: Flame's melee hit sees a fire-aspect item
        // and vice versa. Combo => infinite burn until water.
        boolean otherSidePresent = context.definition() != null
                && context.item() != null
                && hasCounterpart(context);
        int seconds;
        if (otherSidePresent && settings.infiniteCombo()) {
            // Very long burn; entering water clears fire naturally. Cap for safety.
            seconds = settings.maxBurnSeconds();
            combo.markInfinite(living.getUniqueId());
        } else {
            seconds = EffectMath.igniteSeconds(context.level(), settings.maxBurnSeconds());
        }
        if (seconds > 0) {
            living.setFireTicks(Math.max(living.getFireTicks(), seconds * 20));
        }
    }

    private boolean hasCounterpart(EnchantmentContext context) {
        // Handled by the dispatcher wiring: the melee Flame context was built
        // from an item that also carries fire-aspect (and vice versa).
        return combo != null && combo.wasComboHit(context);
    }

    /** Shared combo marker for the Flame/Fire Aspect pair. */
    public com.unbound.core.enchant.IgniteCombo combo() {
        return combo;
    }
}
