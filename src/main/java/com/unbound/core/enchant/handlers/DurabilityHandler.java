package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Durability damage. This event only fires when vanilla actually intends to
 * consume durability (its own Unblocking roll already happened), so:
 *
 * <ul>
 *   <li>Infinity cancels the event outright (deterministic precedence);</li>
 *   <li>Unbreaking adds its configurable extra skip on top of vanilla.</li>
 * </ul>
 *
 * Nested item damage (e.g. the attacker's weapon during Multishot extra
 * hits) is skipped by the guard so no effect applies twice.
 */
public final class DurabilityHandler implements Listener {

    private final UnboundServices services;

    public DurabilityHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.DURABILITY_DAMAGE, item, guard)
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
}
