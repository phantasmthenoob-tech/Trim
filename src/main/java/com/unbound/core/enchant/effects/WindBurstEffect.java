package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.math.EffectMath;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WindCharge;
import org.bukkit.util.Vector;

/**
 * Wind Burst, Unbound-style: impacts burst into a ring of wind charges that
 * knock everything nearby upward and outward (the mace smash, on anything).
 */
public final class WindBurstEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public WindBurstEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().windBurst().enabled()) {
            return false;
        }
        return (context.action() == ActionType.MELEE_IMPACT
                || context.action() == ActionType.PROJECTILE_IMPACT)
                && context.target().isPresent()
                && context.location().isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        Location center = context.target().map(Entity::getLocation).orElse(null);
        if (center == null || center.getWorld() == null) {
            return;
        }
        int charges = EffectMath.windBurstCharges(context.level(),
                config.get().windBurst().maxCharges());
        if (charges <= 0) {
            return;
        }
        if (!context.guard().hasProjectileBudget()) {
            return;
        }
        int granted = context.guard().consumeProjectileSlots(charges);
        double ringRadius = 1.2;
        for (int i = 0; i < granted; i++) {
            double angle = (2 * Math.PI / granted) * i;
            Location spawn = center.clone().add(
                    Math.cos(angle) * ringRadius, 0.4, Math.sin(angle) * ringRadius);
            WindCharge charge = center.getWorld().spawn(spawn, WindCharge.class, windCharge -> {
                windCharge.setShooter(context.actor().orElse(null));
                windCharge.setVelocity(new Vector(Math.cos(angle), 0.3, Math.sin(angle))
                        .multiply(0.9));
            });
            // Charges explode immediately at spawn for the smash feel.
            charge.explode();
        }
    }
}
