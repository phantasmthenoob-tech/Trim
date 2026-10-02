package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Piercing, Unbound-style: hits go through shields — the victim's shield is
 * temporarily disabled (same mechanic as a mace/axe shield-break) and the hit
 * still deals damage instead of being blocked. Applies to melee and
 * projectile hits.
 */
public final class PiercingEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public PiercingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().piercing().enabled()) {
            return false;
        }
        if (context.action() != ActionType.MELEE_ATTACK
                && context.action() != ActionType.PROJECTILE_HIT) {
            return false;
        }
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .isPresent()
                && context.target().filter(t -> t instanceof LivingEntity).isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        LivingEntity victim = context.target().map(t -> (LivingEntity) t).orElse(null);
        if (victim == null || !(victim instanceof Player player)) {
            return;
        }
        if (!isBlockingWithShield(player)) {
            return;
        }
        EntityDamageByEntityEvent event = asDamageEvent(context);
        if (event == null || event.isCancelled()) {
            return;
        }
        // The shield is about to block this hit; put it on cooldown instead:
        // the cooldown drops the blocking state, so the damage flows through
        // and the shield is briefly unavailable (same feel as a mace smash).
        int ticks = config.get().piercing().shieldBreakTicks();
        player.setCooldown(org.bukkit.Material.SHIELD, Math.max(10, ticks));
    }

    private static boolean isBlockingWithShield(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean carryingShield = main.getType() == org.bukkit.Material.SHIELD
                || off.getType() == org.bukkit.Material.SHIELD;
        return carryingShield && player.isBlocking();
    }

    private static @Nullable EntityDamageByEntityEvent asDamageEvent(EnchantmentContext context) {
        return context.event()
                .filter(EntityDamageByEntityEvent.class::isInstance)
                .map(EntityDamageByEntityEvent.class::cast)
                .orElse(null);
    }
}
