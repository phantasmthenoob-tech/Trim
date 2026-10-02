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
import com.unbound.core.math.MultishotMath;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Multishot, Unbound-style — the flagship universal mechanic.
 *
 * <ul>
 *   <li><b>Melee weapons</b> (any item that meaningfully attacks): the hit
 *       splashes to additional nearby targets, radius and count scaling with
 *       level. Extras receive the main hit's damage (already including
 *       Sharpness) as passed to {@code damage()}; nested damage events are
 *       skipped by the handler guard, so nothing recurses or double-applies.</li>
 *   <li><b>Bows</b>: extra arrows in a spread on shoot. Crossbows keep
 *       vanilla Multishot; tridents are excluded (extra thrown tridents would
 *       duplicate the trident item on landing).</li>
 * </ul>
 *
 * <p>Safety: identity-deduplicated selection, invulnerability respected, the
 * attacker and the main target are never re-hit, per-action entity/projectile
 * budgets from the {@link ProcessingGuard} are enforced, and no extra arrows
 * are pickup-able.</p>
 */
public final class MultishotEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public MultishotEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        UnboundConfig.MultishotSettings settings = config.get().multishot();
        if (!settings.enabled()) {
            return false;
        }
        if (context.action() == ActionType.MELEE_IMPACT) {
            if (!settings.sword().enabled() || !context.itemCapabilities().contains(ItemCapability.WEAPON_MELEE)) {
                return false;
            }
            EntityDamageByEntityEvent event = asDamageEvent(context);
            return event != null && !event.isCancelled()
                    && event.getEntity() instanceof LivingEntity;
        }
        if (context.action() == ActionType.PROJECTILE_SHOOT) {
            if (!settings.bow().enabled()) {
                return false;
            }
            EntityShootBowEvent shoot = context.event()
                    .filter(EntityShootBowEvent.class::isInstance)
                    .map(EntityShootBowEvent.class::cast)
                    .orElse(null);
            return shoot != null && isPlainBow(shoot);
        }
        return false;
    }

    @Override
    public void execute(EnchantmentContext context) {
        if (context.action() == ActionType.MELEE_IMPACT) {
            executeMelee(context);
        } else {
            executeShoot(context);
        }
    }

    // ---- melee area attack ---------------------------------------------

    private void executeMelee(EnchantmentContext context) {
        EntityDamageByEntityEvent event = asDamageEvent(context);
        UnboundConfig.MultishotSwordSettings settings = config.get().multishot().sword();
        LivingEntity victim = (LivingEntity) event.getEntity();
        LivingEntity attacker = context.actor().orElse(null);
        if (attacker == null) {
            return;
        }
        double radius = MultishotMath.areaRadius(settings.baseRadius(), settings.radiusPerLevel(), context.level());
        if (radius <= 0) {
            return;
        }
        double damage = event.getDamage() * settings.damageFraction();
        if (damage <= 0) {
            return;
        }
        ProcessingGuard guard = context.guard();

        Location center = victim.getLocation();
        List<MultishotMath.TargetCandidate<LivingEntity>> candidates = new ArrayList<>();
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || !isEligible(living, attacker, settings)) {
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
                radius, settings.maxTargets(), excluded);

        int affected = 0;
        for (LivingEntity target : selected) {
            if (!guard.hasEntityBudget()) {
                break;
            }
            if (guard.consumeEntitySlots(1) <= 0) {
                break;
            }
            target.damage(damage, attacker);
            if (settings.knockback() && settings.knockbackStrength() > 0) {
                Vector push = DamageMath.punchVelocity(
                        attacker.getLocation().getX(), attacker.getLocation().getZ(),
                        target.getLocation().getX(), target.getLocation().getZ(),
                        settings.knockbackStrength());
                target.setVelocity(target.getVelocity().add(push));
            }
            affected++;
        }
    }

    private static boolean isEligible(LivingEntity entity, LivingEntity attacker,
                                      UnboundConfig.MultishotSwordSettings settings) {
        if (entity.isDead() || entity.isInvulnerable() || entity.getNoDamageTicks() > 0) {
            return false;
        }
        if (attacker == entity) {
            return false;
        }
        if (entity instanceof org.bukkit.entity.Player player
                && (player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE)) {
            return false;
        }
        if (settings.skipArmorStands() && entity instanceof ArmorStand) {
            return false;
        }
        if (settings.skipOwnPets() && entity instanceof Tameable tameable
                && tameable.isTamed() && tameable.getOwner() != null
                && tameable.getOwner().getUniqueId().equals(attacker.getUniqueId())) {
            return false;
        }
        return true;
    }

    // ---- bow spread ------------------------------------------------------

    private void executeShoot(EnchantmentContext context) {
        EntityShootBowEvent shoot = context.event()
                .filter(EntityShootBowEvent.class::isInstance)
                .map(EntityShootBowEvent.class::cast)
                .orElse(null);
        if (shoot == null || !(shoot.getProjectile() instanceof AbstractArrow original)) {
            return;
        }
        UnboundConfig.MultishotBowSettings settings = config.get().multishot().bow();
        int extra = MultishotMath.extraArrowCount(context.level(),
                settings.maxExtraArrowsPerLevel(), settings.maxTotalExtraArrows());
        if (extra <= 0) {
            return;
        }
        ProcessingGuard guard = context.guard();
        int slots = guard.consumeProjectileSlots(extra);
        if (slots <= 0) {
            return;
        }
        ProjectileSource shooter = original.getShooter();
        Vector baseVelocity = original.getVelocity();
        float speed = (float) baseVelocity.length();
        if (speed <= 0) {
            return;
        }
        Location origin = original.getLocation();
        for (int i = 0; i < slots; i++) {
            Vector direction = MultishotMath.spreadDirection(baseVelocity, settings.spreadDegrees(), i);
            org.bukkit.entity.Arrow arrow = origin.getWorld().spawnArrow(origin, direction, speed, 0.0f);
            arrow.setShooter(shooter);
            arrow.setDamage(original.getDamage());
            arrow.setCritical(original.isCritical());
            // Extra arrows are not pickup-able: no arrow farming.
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    private static boolean isPlainBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null) {
            ItemStack held = event.getEntity().getEquipment() == null
                    ? null
                    : event.getEntity().getEquipment().getItemInMainHand();
            return held != null && held.getType() == Material.BOW;
        }
        // Crossbows keep vanilla Multishot; only plain bows get the spread.
        return bow.getType() == Material.BOW;
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
