package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DamageMath;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Punch, Unbound-style.
 *
 * <p>Arrows keep vanilla Punch. Unbound adapts Punch to everything else it
 * can meaningfully interact with:</p>
 * <ul>
 *   <li><b>Melee attacks</b> — Punch now pushes the victim away like the
 *       Knockback enchantment does (vanilla never allows Punch on melee
 *       items, so there is no overlap).</li>
 *   <li><b>Other projectiles</b> (wind charges, snowballs, eggs —
 *       configurable) — knockback scaled by level on hit.</li>
 * </ul>
 *
 * <p>Knockback is applied at most once per hit because the effect only runs
 * on the top-level damage event (nested events are guarded).</p>
 */
public final class PunchEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public PunchEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        UnboundConfig.PunchSettings settings = config.get().punch();
        if (!settings.enabled()) {
            return false;
        }
        if (context.action() == ActionType.MELEE_IMPACT) {
            return settings.meleeEnabled()
                    && asDamageEvent(context) != null
                    && context.target().filter(LivingEntity.class::isInstance).isPresent();
        }
        if (context.action() == ActionType.PROJECTILE_IMPACT) {
            Projectile projectile = context.projectile().orElse(null);
            if (projectile == null) {
                return false;
            }
            String typeName = projectile.getType().name().toLowerCase(java.util.Locale.ROOT);
            return settings.projectiles().contains(typeName) && asDamageEvent(context) != null;
        }
        return false;
    }

    @Override
    public void execute(EnchantmentContext context) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        Entity victim = context.target().orElse(null);
        if (event == null || victim == null || event.isCancelled()) {
            return;
        }
        Entity source = context.actor().map(actor -> (Entity) actor)
                .orElseGet(() -> context.projectile().orElse(null));
        if (source == null || !(victim instanceof LivingEntity living)) {
            return;
        }
        UnboundConfig.PunchSettings settings = config.get().punch();
        double strength = context.action() == ActionType.MELEE_IMPACT
                ? settings.meleeVelocityPerLevel() * context.level()
                : settings.projectileVelocityPerLevel() * context.level();
        if (strength <= 0) {
            return;
        }
        Vector push = DamageMath.punchVelocity(
                source.getLocation().getX(), source.getLocation().getZ(),
                living.getLocation().getX(), living.getLocation().getZ(),
                strength);
        living.setVelocity(living.getVelocity().add(push));
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
