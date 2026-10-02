package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.EffectMath;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

/**
 * Riptide, Unbound-style.
 *
 * <ul>
 *   <li><b>Projectiles</b>: shots fly at riptide speed but their base damage
 *       is reduced (default 30% kept) so the speed is not free power.</li>
 *   <li><b>Melee / anything else</b>: right-click charges up and launches the
 *       player exactly like a trident riptide (same velocity mechanics), with
 *       one crucial difference — off a trident it works without water.</li>
 * </ul>
 */
public final class RiptideEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public RiptideEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().riptide().enabled()) {
            return false;
        }
        if (context.action() == ActionType.PROJECTILE_SHOOT) {
            return context.projectile().filter(p -> p instanceof AbstractArrow).isPresent();
        }
        return context.action() == ActionType.RIPTIDE_LAUNCH;
    }

    @Override
    public void execute(EnchantmentContext context) {
        if (context.action() == ActionType.PROJECTILE_SHOOT) {
            boostProjectile(context);
        } else {
            selfLaunch(context);
        }
    }

    private void boostProjectile(EnchantmentContext context) {
        Projectile projectile = context.projectile().orElse(null);
        if (!(projectile instanceof AbstractArrow arrow)) {
            return;
        }
        double strength = EffectMath.riptideLaunchStrength(context.level());
        if (strength <= 0) {
            return;
        }
        Vector velocity = arrow.getVelocity();
        double speed = velocity.length();
        if (speed <= 0) {
            return;
        }
        // Riptide speed multiplier on the projectile, damage nerf as configured.
        arrow.setVelocity(velocity.normalize().multiply(speed * (1.0 + strength)));
        arrow.setDamage(arrow.getDamage() * config.get().riptide().projectileDamageFraction());
    }

    private void selfLaunch(EnchantmentContext context) {
        org.bukkit.entity.Player player = context.player().orElse(null);
        if (player == null || player.isInsideVehicle() || player.isGliding()) {
            return;
        }
        double strength = EffectMath.riptideLaunchStrength(context.level());
        if (strength <= 0) {
            return;
        }
        // Same mechanic as vanilla trident riptide: launch in look direction,
        // spin animation included via the riptide velocity profile.
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.clone().multiply(strength);
        velocity.setY(Math.max(velocity.getY(), strength * 0.4));
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
    }
}
