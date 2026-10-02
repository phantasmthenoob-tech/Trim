package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Projectile events:
 *
 * <ul>
 *   <li>{@code PROJECTILE_SHOOT} from EntityShootBowEvent (MONITOR):
 *       Multishot extra arrows for plain bows.</li>
 *   <li>{@code ITEM_LAUNCH} from ProjectileLaunchEvent (MONITOR): Infinity
 *       restores thrown consumables (pearls, eggs, snowballs, potions, XP
 *       bottles) after their normal effect ran.</li>
 * </ul>
 */
public final class ProjectileHandler implements Listener {

    private final UnboundServices services;

    public ProjectileHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType().isAir()) {
            bow = player.getInventory().getItemInMainHand();
        }
        if (bow.getType().isAir()) {
            return;
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.PROJECTILE_SHOOT, bow, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(bow))
                    .projectile(event.getProjectile() instanceof Projectile projectile ? projectile : null)
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return; // Dispenser-launched projectiles consume no player item.
        }
        Material material = materialFor(projectile);
        if (material == null) {
            return;
        }
        ItemStack item = matchingItem(player, material);
        if (item == null) {
            return;
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.ITEM_LAUNCH, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .projectile(projectile)
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    /** Map a projectile entity to the consumable item that produced it. */
    private static @Nullable Material materialFor(Entity entity) {
        if (entity instanceof EnderPearl) {
            return Material.ENDER_PEARL;
        }
        if (entity instanceof Egg) {
            return Material.EGG;
        }
        if (entity instanceof Snowball) {
            return Material.SNOWBALL;
        }
        if (entity instanceof ThrownExpBottle) {
            return Material.EXPERIENCE_BOTTLE;
        }
        if (entity instanceof ThrownPotion potion) {
            return potion.getItem() != null && !potion.getItem().getType().isAir()
                    ? potion.getItem().getType()
                    : Material.SPLASH_POTION;
        }
        return null;
    }

    private static @Nullable ItemStack matchingItem(Player player, Material material) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!main.getType().isAir() && main.getType() == material) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!off.getType().isAir() && off.getType() == material) {
            return off;
        }
        return null;
    }
}
