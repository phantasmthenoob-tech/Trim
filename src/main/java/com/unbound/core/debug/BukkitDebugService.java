package com.unbound.core.debug;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentDefinition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

/**
 * Bukkit debug output. Console output uses the logger's fine level only when
 * the global debug switch is on (never spams normal logs). Players with
 * {@code unbound.debug} can subscribe via /unbound debug and receive compact
 * action-bar lines.
 */
public final class BukkitDebugService implements com.unbound.core.enchant.DebugSink {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final UnboundServices services;
    private final java.util.logging.Logger logger;
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();

    public BukkitDebugService(UnboundServices services, java.util.logging.Logger logger) {
        this.services = services;
        this.logger = logger;
    }

    public boolean toggle(Player player) {
        if (!services.config().debugAllowPlayerToggle() || !player.hasPermission("unbound.debug")) {
            return false;
        }
        if (subscribers.remove(player.getUniqueId())) {
            return false;
        }
        subscribers.add(player.getUniqueId());
        return true;
    }

    public boolean isSubscribed(Player player) {
        return subscribers.contains(player.getUniqueId());
    }

    public Set<UUID> subscribers() {
        return Collections.unmodifiableSet(subscribers);
    }

    @Override
    public void onEffect(EnchantmentContext context, EnchantmentDefinition definition, String detail) {
        // Per-effect lines only for subscribed admins; console gets the
        // summarized onAction line.
        String line = "effect=" + definition.key() + " lvl=" + context.level()
                + " action=" + context.action() + " " + detail;
        broadcast(line);
    }

    @Override
    public void onAction(EnchantmentContext context, long nanos, int effectsRun) {
        if (!services.config().debugEnabled() && subscribers.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder("action=").append(context.action())
                .append(" item=").append(context.item().getType())
                .append(" effects=").append(effectsRun);
        if (services.config().debugShowTiming()) {
            line.append(" time=").append(nanos / 1_000.0).append("us");
        }
        String text = line.toString();
        if (services.config().debugEnabled()) {
            logger.fine(() -> "[Unbound] " + text);
        }
        broadcast(text);
    }

    private void broadcast(String text) {
        for (UUID id : subscribers) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.hasPermission(new Permission("unbound.debug"))) {
                continue;
            }
            player.sendActionBar(LEGACY.deserialize("&8[&bUnbound&8] &7" + text));
        }
    }

    /** Builds a player-facing component from legacy color codes. */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }
}
