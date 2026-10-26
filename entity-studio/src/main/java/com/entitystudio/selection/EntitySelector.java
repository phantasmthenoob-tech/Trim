package com.entitystudio.selection;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.permission.Gatekeeper;
import com.entitystudio.permission.StudioPermissions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry points: shift + right-click (configurable), inspect mode, UUID lookup.
 * Selection resolves to UUID/world/type/location — never a fragile live
 * reference held past the current session validation.
 */
public class EntitySelector implements Listener {

    private final EntityStudioPlugin plugin;
    private final Set<UUID> inspectMode = ConcurrentHashMap.newKeySet();

    public EntitySelector(EntityStudioPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean toggleInspect(Player player) {
        if (inspectMode.contains(player.getUniqueId())) {
            inspectMode.remove(player.getUniqueId());
            return false;
        }
        inspectMode.add(player.getUniqueId());
        return true;
    }

    public boolean inInspectMode(Player player) {
        return inspectMode.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("settings.interaction.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("settings.interaction.main-hand-only", true)
                && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getConfig().getBoolean("settings.interaction.require-sneaking", true)
                && !player.isSneaking()) {
            return;
        }
        if (!plugin.canUse(player)) {
            return;
        }
        if (!player.hasPermission(StudioPermissions.USE)
                && !plugin.gate().ownerBypassesPermissions(player.getName())) {
            return;
        }
        Entity target = event.getRightClicked();
        event.setCancelled(true);
        if (inInspectMode(player)) {
            inspectMode.remove(player.getUniqueId());
            plugin.gui().openInspectSummary(player, target);
            return;
        }
        if (!plugin.checkTargetAccess(player, target)) {
            return;
        }
        plugin.sessions().open(player, target);
        plugin.gui().openDashboard(player, target);
    }
}
