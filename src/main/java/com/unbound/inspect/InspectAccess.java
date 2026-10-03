package com.unbound.inspect;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Access gate for the admin inspection commands (/inspect, /enderchest edit
 * and sneak-right-click inspection). Per server request, everything is
 * restricted to the single account {@code ImNotAllocate} — change
 * {@link #ALLOWED} if that ever needs to be more than one person.
 *
 * <p>Offline editing note: Paper 26.2's public API cannot open or edit an
 * offline player's inventory or ender chest ({@code OfflinePlayer} exposes
 * no such accessors). Targets must be online; offline attempts get a clear
 * error message instead of silently doing nothing.</p>
 */
public final class InspectAccess {

    /** Accounts allowed to inspect/edit other players' inventories. */
    private static final String ALLOWED = "ImNotAllocate";

    private InspectAccess() {
    }

    public static boolean isAllowed(CommandSender viewer) {
        return viewer != null && ALLOWED.equalsIgnoreCase(viewer.getName());
    }

    /** Standard denial message for non-allowed viewers. */
    public static String denialMessage() {
        return "§cYou are not allowed to inspect other players.";
    }

    /** Offline targets cannot be opened via the public API. */
    public static String offlineMessage(String name) {
        return "§c" + name + " is offline; Paper's API cannot open offline "
                + "player data. Ask them to join and try again.";
    }
}
