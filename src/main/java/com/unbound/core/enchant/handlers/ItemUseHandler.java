package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item usage:
 *
 * <ul>
 *   <li>{@code MONITOR} consume (fires after the decision to consume is
 *       final): Infinity restores consumables whose effect already
 *       applied.</li>
 *   <li>{@code NORMAL} cooldown: Quick Charge shortens item cooldowns of the
 *       item that actually carries the enchantment.</li>
 * </ul>
 */
public final class ItemUseHandler implements Listener {

    private final UnboundServices services;

    public ItemUseHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.ITEM_CONSUME, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCooldown(PlayerItemCooldownEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = findMatching(player, event.getType());
        if (item == null) {
            return; // The enchanted item must be the one entering cooldown.
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.ITEM_COOLDOWN, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    private static @Nullable ItemStack findMatching(Player player, Material material) {
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

    /**
     * Infinity totem support: after a totem resurrection, restore the totem
     * to its original hand one tick later (the vanilla consumption has already
     * happened at MONITOR, so preserving is net-zero, never duplication).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (ProcessingGuard.isActive() || event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack totem = resurrectionTotem(player, event.getHand());
        if (totem == null || !services.scanner().scan(totem).contains(ItemCapability.TOTEM)
                || !services.engine().hasEnchantment(totem,
                        services.engine().get("infinity").orElse(null))) {
            return;
        }
        org.bukkit.inventory.EquipmentSlot hand = event.getHand();
        ItemStack snapshot = totem.clone();
        org.bukkit.Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            var inventory = player.getInventory();
            if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                if (inventory.getItemInOffHand().getType().isAir()) {
                    inventory.setItemInOffHand(snapshot);
                } else {
                    giveOrDrop(player, snapshot);
                }
            } else {
                if (inventory.getItemInMainHand().getType().isAir()) {
                    inventory.setItemInMainHand(snapshot);
                } else {
                    giveOrDrop(player, snapshot);
                }
            }
        });
    }

    private static @Nullable ItemStack resurrectionTotem(Player player,
                                                         org.bukkit.inventory.EquipmentSlot hand) {
        if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            return nonAir(player.getInventory().getItemInOffHand());
        }
        if (hand == org.bukkit.inventory.EquipmentSlot.HAND) {
            return nonAir(player.getInventory().getItemInMainHand());
        }
        // Vanilla checks main hand first.
        return nonAir(player.getInventory().getItemInMainHand()) != null
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }

    private static @Nullable ItemStack nonAir(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item;
    }

    private static void giveOrDrop(Player player, ItemStack snapshot) {
        var leftover = player.getInventory().addItem(snapshot);
        leftover.values().forEach(rest ->
                player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
