package com.unbound.inspect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;

/**
 * Admin inspection: sneak + right-click a player to open their live
 * inventory as a container — armor, main hand, off hand and storage are all
 * real slots, so taking, removing and replacing items edits the target's
 * actual inventory.
 *
 * <p>Sneak is required so normal right-click interactions (trading, riding,
 * click-based plugins) are never hijacked. Permission-gated by
 * {@code unbound.inspect}; a short per-viewer cooldown stops event spam from
 * both interaction packets (main/off hand) opening the view twice.</p>
 */
public final class InspectHandler implements Listener {

    private static final long COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInspect(PlayerInteractEntityEvent event) {
        // Only the main-hand packet opens the view; the off-hand packet is
        // ignored so the inventory is not opened twice per click.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player viewer = event.getPlayer();
        if (!viewer.isSneaking() || !viewer.hasPermission("unbound.inspect")) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        if (target == viewer) {
            return; // E + your own name makes no sense; vanilla inventory covers it.
        }
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(viewer.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        lastOpen.put(viewer.getUniqueId(), now);

        Inventory live = target.getInventory();
        if (live == null) {
            return;
        }
        viewer.openInventory(live);
        event.setCancelled(true);
    }
}
