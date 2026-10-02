package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DamageMath;

import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Power, Unbound-style.
 *
 * <p>Arrows keep vanilla Power: vanilla boosts arrow damage from the weapon
 * that fired them, and only bows/crossbows shoot arrows. Unbound extends
 * Power to other player-launched projectiles (wind charges, snowballs, eggs
 * by default — configurable), adding flat damage per level on hit. Those
 * projectiles never receive a vanilla Power bonus, so nothing is
 * double-counted.</p>
 */
public final class PowerEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public PowerEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (context.action() != ActionType.PROJECTILE_HIT || !config.get().power().enabled()) {
            return false;
        }
        Projectile projectile = context.projectile().orElse(null);
        if (projectile == null) {
            return false;
        }
        String typeName = projectile.getType().name().toLowerCase(java.util.Locale.ROOT);
        return config.get().power().projectiles().contains(typeName);
    }

    @Override
    public void execute(EnchantmentContext context) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        if (event == null || event.isCancelled()) {
            return;
        }
        double bonus = DamageMath.projectileDamageBonus(context.level(),
                config.get().power().projectileDamagePerLevel());
        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
