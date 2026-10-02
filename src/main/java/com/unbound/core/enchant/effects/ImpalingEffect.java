package com.unbound.core.enchant.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ProcessingGuard;
import com.unbound.core.math.EffectMath;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Impaling, Unbound-style: a strike pierces through a line of mobs in the
 * direction of the attack — up to {@code min(5, level)} victims, each taking
 * a configured fraction of the base damage.
 */
public final class ImpalingEffect implements EnchantmentEffect {

    private static final double STEP = 1.0; // blocks between line samples

    private final Supplier<UnboundConfig> config;

    public ImpalingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().impaling().enabled()) {
            return false;
        }
        return (context.action() == ActionType.MELEE_IMPACT
                || context.action() == ActionType.PROJECTILE_IMPACT)
                && context.actor().isPresent()
                && context.target().filter(t -> t instanceof LivingEntity).isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        LivingEntity attacker = context.actor().orElse(null);
        LivingEntity victim = context.target().filter(t -> t instanceof LivingEntity)
                .map(t -> (LivingEntity) t).orElse(null);
        if (attacker == null || victim == null) {
            return;
        }
        int targets = EffectMath.impalingTargets(context.level());
        if (targets <= 1) {
            return;
        }
        UnboundConfig.ImpalingSettings settings = config.get().impaling();
        double damage = baseDamage(context) * settings.damageFraction();
        if (damage <= 0) {
            return;
        }
        Location from = attacker.getLocation();
        Vector direction = victim.getLocation().toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        direction.normalize();

        ProcessingGuard guard = context.guard();
        List<LivingEntity> hit = new ArrayList<>();
        hit.add(victim);
        Location probe = victim.getLocation().add(0, victim.getHeight() / 2.0, 0);
        int extra = targets - 1;
        for (int i = 1; i <= extra * 3 && hit.size() < targets; i++) {
            probe = probe.add(direction.clone().multiply(STEP));
            for (Entity nearby : probe.getWorld().getNearbyEntities(probe, 0.6, 0.9, 0.6)) {
                if (!(nearby instanceof LivingEntity living)
                        || hit.contains(living)
                        || living == attacker
                        || living.isDead()
                        || living.isInvulnerable()) {
                    continue;
                }
                if (living instanceof org.bukkit.entity.Player player
                        && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                    continue;
                }
                if (!guard.hasEntityBudget() || guard.consumeEntitySlots(1) <= 0) {
                    return;
                }
                hit.add(living);
                living.damage(damage, attacker);
                break;
            }
        }
    }

    private static double baseDamage(EnchantmentContext context) {
        return context.event()
                .filter(org.bukkit.event.entity.EntityDamageByEntityEvent.class::isInstance)
                .map(org.bukkit.event.entity.EntityDamageByEntityEvent.class::cast)
                .map(org.bukkit.event.entity.EntityDamageByEntityEvent::getDamage)
                .orElse(2.0 + context.level());
    }
}
