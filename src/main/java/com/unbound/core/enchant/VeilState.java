package com.unbound.core.enchant;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every player's currently active protection veil(s): which family,
 * what level, and until when (Bukkit tick time, read live by the veil tick
 * task — no per-player scheduled tasks). Thread-safe: events and the shared
 * tick task touch it from the main thread only, but the map stays safe anyway.
 */
public final class VeilState {

    /** One active veil on one player. */
    public record Active(VeilType type, int level, long expireTick) {
        public boolean covers(EntityDamageCauseName cause) {
            return type.covers(cause);
        }
    }

    private final Map<UUID, Active> veils = new ConcurrentHashMap<>();

    /** (Re)applies the veil of the given family, extending the duration. */
    public void apply(UUID playerId, VeilType type, int level, long nowTick, double seconds) {
        if (playerId == null || type == null || level < 1 || seconds <= 0) {
            return;
        }
        long expire = nowTick + (long) Math.ceil(seconds * 20.0);
        Active existing = veils.get(playerId);
        // A broader family veil never replaces a matching one — same-type veils
        // extend; different-family veils keep the one with more time left.
        if (existing != null && existing.type() == type) {
            veils.put(playerId, new Active(type, Math.max(level, existing.level()),
                    Math.max(expire, existing.expireTick())));
            return;
        }
        if (existing != null && existing.expireTick() > expire) {
            return; // keep the longer-lived veil; the new one was weaker/shorter
        }
        veils.put(playerId, new Active(type, level, expire));
    }

    /** The currently active veil, or null when none/expired. */
    public Active active(UUID playerId, long nowTick) {
        Active active = veils.get(playerId);
        if (active == null) {
            return null;
        }
        if (active.expireTick() <= nowTick) {
            veils.remove(playerId);
            return null;
        }
        return active;
    }

    /** Removes any veil (player quit, death...). */
    public void clear(UUID playerId) {
        veils.remove(playerId);
    }

    /** Number of players currently carrying a veil (debug/tests). */
    public int tracked() {
        return veils.size();
    }
}
