package com.unbound.core.enchant.handlers;

import java.util.EnumMap;
import java.util.Map;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.EntityDamageCauseName;
import com.unbound.core.enchant.VeilState;
import com.unbound.core.enchant.VeilType;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Applies active veils to incoming damage. Runs at HIGH (after most plugins
 * modified damage, before MONITOR locks in), reads the per-player
 * {@link VeilState} and reduces matching damage like an extra armor piece of
 * that protection family.
 *
 * <p>State comes from the {@code VeilEffect}s; on player quit/death the
 * lifecycle handler clears the entry.</p>
 */
public final class VeilHandler implements Listener {

    private final UnboundServices services;
    private final Map<VeilType, VeilState> states;

    public VeilHandler(UnboundServices services, Map<VeilType, VeilState> states) {
        this.services = services;
        this.states = new EnumMap<>(states);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageCauseName cause = map(event.getCause());
        if (cause == null) {
            return;
        }
        VeilState.Active active = findActive(player, cause);
        if (active == null) {
            return;
        }
        double reduction = active.type().reductionPerLevel();
        double newDamage = Math.max(0.0, event.getDamage() * (1.0 - reduction));
        event.setDamage(newDamage);
    }

    private VeilState.Active findActive(Player player, EntityDamageCauseName cause) {
        long nowTick = player.getWorld().getFullTime();
        for (VeilState state : states.values()) {
            VeilState.Active active = state.active(player.getUniqueId(), nowTick);
            if (active != null && active.covers(cause)) {
                return active;
            }
        }
        return null;
    }

    private static EntityDamageCauseName map(EntityDamageEvent.DamageCause cause) {
        try {
            return EntityDamageCauseName.valueOf(cause.name());
        } catch (IllegalArgumentException ignored) {
            return null; // causes outside the veil families
        }
    }
}
