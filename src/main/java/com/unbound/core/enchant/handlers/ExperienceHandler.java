package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Experience gain. XP_GAIN is dispatched level-agnostically (the context's
 * representative item may not carry Mending; the effect scans the whole
 * inventory itself). XP is only ever consumed by the effect — no XP is
 * generated, so no loop is possible.
 */
public final class ExperienceHandler implements Listener {

    private final UnboundServices services;

    public ExperienceHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        if (event.getAmount() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.XP_GAIN, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .xpAmount(event.getAmount())
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }
}
