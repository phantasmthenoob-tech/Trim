package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
}
