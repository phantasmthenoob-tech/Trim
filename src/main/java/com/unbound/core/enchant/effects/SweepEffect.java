package com.unbound.core.enchant.effects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;
import com.unbound.core.math.DamageMath;
import com.unbound.core.math.EffectMath;
import com.unbound.core.math.MultishotMath;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Sweeping Edge, Unbound-style: the vanilla sweep becomes a real area attack
 * on melee hits, and — universal twist — projectile impacts sweep too.
 * Target selection is identical to Multishot's (nearest-first, dedup, guard
 * budgets, pets/armor-stands excluded).
 */
public final class SweepEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public SweepEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        UnboundConfig.SweepSettings settings = config.get().sweep();
        if (!settings.enabled()) {
            return false;
        }
        if (context.action() == ActionType.MELEE_IMPACT) {
            EntityDamageByEntityEvent event = asDamageEvent(context);
            return event != null && !event.isCancelled()
                    && event.getEntity() instanceof LivingEntity;
        }
        if (context.action() == ActionType.PROJECTILE_IMPACT) {
            return context.projectile().isPresent()
                    && context.actor().isPresent()
                    && context.target().filter(t -> t instanceof LivingEntity).isPresent();
        }
        return false;
    }

    @Override
    public void execute(EnchantmentContext context) {
        UnboundConfig.SweepSettings settings = config.get().sweep();
        executeSweep(context, settings.maxTargets(), settings.damageFraction(),
                settings.knockback(), settings.knockbackStrength());
    }

    /** Shared sweep entry point (also used by Thorns with its own settings). */
    void executeThorns(EnchantmentContext context, int maxTargets, double damageFraction) {
        UnboundConfig.SweepSettings settings = config.get().sweep();
        executeSweep(context, maxTargets, damageFraction, settings.knockback(), settings.knockbackStrength());
    }

    private void executeSweep(EnchantmentContext context, int maxTargets, double damageFraction,
                              boolean knockback, double knockbackStrength) {
        LivingEntity attacker = context.actor().orElse(null);
        LivingEntity victim = context.target().filter(t -> t instanceof LivingEntity)
                .map(t -> (LivingEntity) t).orElse(null);
        if (attacker == null || victim == null || attacker == victim) {
            return;
        }
        int count = EffectMath.sweepCount(context.level(), maxTargets);
        if (count <= 0) {
            return;
        }
        double radius = 2.0 + 0.5 * context.level();
        double damage = baseDamage(context, victim) * damageFraction;
        if (damage <= 0) {
            return;
        }
        Location center = victim.getLocation();
        List<MultishotMath.TargetCandidate<LivingEntity>> candidates = new ArrayList<>();
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || !isEligible(living, attacker, victim)) {
                continue;
            }
            Location location = living.getLocation();
            candidates.add(new MultishotMath.TargetCandidate<>(living, location.getX(), location.getY(), location.getZ()));
        }
        Set<LivingEntity> excluded = new HashSet<>();
        excluded.add(attacker);
        excluded.add(victim);
        List<LivingEntity> selected = MultishotMath.selectTargets(
                candidates, center.getX(), center.getY(), center.getZ(),
                radius, count, excluded);

        ProcessingGuard guard = context.guard();
        for (LivingEntity target : selected) {
            if (!guard.hasEntityBudget() || guard.consumeEntitySlots(1) <= 0) {
                break;
            }
            target.damage(damage, attacker);
            if (knockback && knockbackStrength > 0) {
                Vector push = DamageMath.punchVelocity(
                        attacker.getLocation().getX(), attacker.getLocation().getZ(),
                        target.getLocation().getX(), target.getLocation().getZ(),
                        knockbackStrength);
                target.setVelocity(target.getVelocity().add(push));
            }
        }
    }

    private static double baseDamage(EnchantmentContext context, LivingEntity victim) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        if (event != null) {
            return event.getDamage();
        }
        // Projectile impact without a damage event: use a modest level-based base.
        return 2.0 + context.level();
    }

    private static boolean isEligible(LivingEntity entity, LivingEntity attacker, LivingEntity victim) {
        if (entity.isDead() || entity.isInvulnerable() || entity.getNoDamageTicks() > 0) {
            return false;
        }
        if (entity == attacker || entity == victim) {
            return false;
        }
        if (entity instanceof org.bukkit.entity.Player player
                && (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE)) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()
                && tameable.getOwner() != null
                && tameable.getOwner().getUniqueId().equals(attacker.getUniqueId())) {
            return false;
        }
        return true;
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
