package com.unbound.core.enchant.handlers;

import java.util.List;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Held-item maintenance. These dispatches (LEVEL_AGNOSTIC actions) exist so
 * Efficiency's transient attack-speed modifier is added/removed exactly when
 * the held item changes — no per-tick tasks, no inventory scans.
 */
public final class PlayerLifecycleHandler implements Listener {

    private final UnboundServices services;

    public PlayerLifecycleHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeldChange(PlayerItemHeldEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        dispatch(ActionType.HELD_ITEM_CHANGE, player, item);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        dispatch(ActionType.HELD_ITEM_CHANGE, event.getPlayer(), event.getMainHandItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        dispatch(ActionType.PLAYER_JOIN, event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        dispatch(ActionType.PLAYER_QUIT, event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        // The item broke: force a refresh with an empty main-hand view.
        dispatch(ActionType.HELD_ITEM_CHANGE, event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean affectsHeldSlot = event.getClickedInventory() == inventory
                && event.getSlot() == inventory.getHeldItemSlot();
        boolean shiftIntoHotbar = event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")
                && event.getInventory() instanceof PlayerInventory;
        if (!affectsHeldSlot && !shiftIntoHotbar) {
            return;
        }
        // The item lands after the event; refresh one tick later.
        Bukkit.getScheduler().runTask(services.plugin(), () -> {
            if (player.isOnline() && !ProcessingGuard.isActive()) {
                dispatch(ActionType.HELD_ITEM_CHANGE, player, player.getInventory().getItemInMainHand());
            }
        });
    }

    private void dispatch(ActionType action, Player player, ItemStack item) {
        ItemStack safeItem = item == null ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR) : item;
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(action, safeItem, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(safeItem))
                    .location(player.getLocation())
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    /** Used by tests/tools to document supported actions. */
    public static List<ActionType> handledActions() {
        return List.of(ActionType.HELD_ITEM_CHANGE, ActionType.PLAYER_JOIN, ActionType.PLAYER_QUIT);
    }
}
