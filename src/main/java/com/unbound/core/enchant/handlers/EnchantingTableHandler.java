package com.unbound.core.enchant.handlers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.unbound.core.UnboundServices;
import com.unbound.core.debug.BukkitDebugService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

/**
 * Progression control for the vanilla enchanting table (configurable, off by
 * default). When disabled, offers are cleared and actual enchanting is
 * cancelled with a friendly, throttled message. Anvils are NOT touched.
 */
public final class EnchantingTableHandler implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 3_000L;

    private final UnboundServices services;
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();

    public EnchantingTableHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepare(PrepareItemEnchantEvent event) {
        if (services.config().enchantingTableEnabled()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnchant(EnchantItemEvent event) {
        if (services.config().enchantingTableEnabled()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getEnchanter();
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last == null || now - last >= MESSAGE_COOLDOWN_MS) {
            lastMessage.put(player.getUniqueId(), now);
            player.sendMessage(BukkitDebugService.legacy(services.config().enchantingDenyMessage()));
        }
    }
}
