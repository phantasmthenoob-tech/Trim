package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.event.block.BlockDropItemEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Block breaking: listens to {@link BlockDropItemEvent} (fires once with the
 * actual drop list, after the block broke) so Fortune manipulates real drops
 * and vanilla loot tables have already produced their result. Preventing
 * duplicate processing is inherent: the event fires exactly once per break.
 */
public final class BlockBreakHandler implements Listener {

    private final UnboundServices services;

    public BlockBreakHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) {
            return;
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.BLOCK_BREAK, tool, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(tool))
                    .block(event.getBlock())
                    .location(event.getBlock().getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }
}
