package com.entitystudio.mobform;

import com.entitystudio.EntityStudioPlugin;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Optional LibsDisguises integration for real mob visuals: other players
 * see the mob, and the morphed player sees it themselves in F5 via the
 * self-disguise view. The plugin is a <em>soft</em> dependency — when it
 * is missing, disabled, or a type has no disguise mapping, every method
 * degrades gracefully and the caller falls back to costumes.
 */
final class DisguiseBridge {

    private final EntityStudioPlugin plugin;
    private final boolean available;

    DisguiseBridge(EntityStudioPlugin plugin) {
        this.plugin = plugin;
        boolean ok = false;
        try {
            ok = plugin.getServer().getPluginManager().isPluginEnabled("LibsDisguises");
            if (ok) {
                Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            }
        } catch (Throwable t) {
            ok = false;
        }
        this.available = ok;
        if (ok) {
            plugin.getLogger().info("LibsDisguises found: morphs use real disguises.");
        } else {
            plugin.getLogger().info(
                    "LibsDisguises not found: morphs use costume fallback.");
        }
    }

    boolean isAvailable() {
        return available;
    }

    /**
     * Disguises the player as the mob type with self-view on.
     * Returns false when unavailable or unmapped; never throws.
     */
    boolean disguise(Player player, EntityType type, double scale) {
        if (!available || player == null || type == null) {
            return false;
        }
        try {
            DisguiseType disguiseType = DisguiseType.valueOf(type.name());
            MobDisguise disguise = new MobDisguise(disguiseType);
            disguise.setSelfDisguiseVisible(true);
            try {
                LivingWatcher watcher = disguise.getWatcher();
                if (watcher != null) {
                    if (type == EntityType.SLIME && watcher instanceof SlimeWatcher slime) {
                        slime.setSize(Math.max(1, (int) Math.round(scale * 3)));
                    }
                    if (scale > 0) {
                        watcher.setScale(scale);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Disguise scale failed for " + type.name() + ": " + e.getMessage());
            }
            DisguiseAPI.disguiseToAll(player, disguise);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Disguise failed for " + type.name() + ", using costume: " + e.getMessage());
            return false;
        }
    }

    /** Removes any active disguise; never throws. */
    void undisguise(Player player) {
        if (!available || player == null) {
            return;
        }
        try {
            if (DisguiseAPI.isDisguised(player)) {
                DisguiseAPI.undisguiseToAll(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Undisguise failed: " + e.getMessage());
        }
    }
}
