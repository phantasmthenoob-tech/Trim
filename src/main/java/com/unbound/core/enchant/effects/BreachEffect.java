package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.EffectMath;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Breach, Unbound-style: projectile hits pierce a fraction of the victim's
 * armor (16% per level, capped at 80%). Implemented on top of the damage
 * event by re-adding the armor share as a "pierced" bonus — approximating
 * vanilla breach's armor-ignoring behavior within the public API.
 */
public final class BreachEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public BreachEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().breach().enabled() || context.action() != ActionType.PROJECTILE_HIT) {
            return false;
        }
        boolean damageEventPresent = context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(e -> !((EntityDamageByEntityEvent) e).isCancelled())
                .orElse(false);
        return damageEventPresent
                && context.target().filter(t -> t instanceof LivingEntity).isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        if (event == null) {
            return;
        }
        LivingEntity victim = context.target().map(t -> (LivingEntity) t).orElse(null);
        if (victim == null) {
            return;
        }
        double armorPoints = victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR) == null
                ? 0.0
                : victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();
        if (armorPoints <= 0) {
            return;
        }
        // The share of armor this hit ignores, as extra pre-mitigation damage
        // proportional to the armor the victim wears (vanilla breach feel).
        double pierced = Math.min(armorPoints * EffectMath.breachArmorFraction(context.level()),
                event.getDamage());
        if (pierced > 0) {
            event.setDamage(event.getDamage() + pierced);
        }
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
