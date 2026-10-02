package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DamageMath;

import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Sharpness, Unbound-style.
 *
 * <p>Vanilla already adds {@code 0.5 + 0.5 * level} damage to <b>any</b>
 * melee attack whose held item has Sharpness (it never checks the item class,
 * only compatibility at enchanting time). Unbound therefore removes vanilla's
 * contribution and re-applies its own configured formula — with the default
 * config the delta is exactly zero and the damage event is left untouched, so
 * nothing is ever double-counted. Changing {@code base-bonus} /
 * {@code bonus-per-level} reshapes Sharpness server-wide.</p>
 */
public final class SharpnessEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public SharpnessEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (context.action() != ActionType.MELEE_ATTACK) {
            return false;
        }
        EntityDamageByEntityEvent event = asDamageEvent(context);
        return event != null && !event.isCancelled()
                && config.get().sharpness().enabled();
    }

    @Override
    public void execute(EnchantmentContext context) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        UnboundConfig.SharpnessSettings settings = config.get().sharpness();
        double delta = DamageMath.sharpnessDelta(context.level(), settings.baseBonus(), settings.bonusPerLevel());
        if (Math.abs(delta) < 1.0E-9) {
            return;
        }
        event.setDamage(Math.max(0.0, event.getDamage() + delta));
    }

    private static EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
