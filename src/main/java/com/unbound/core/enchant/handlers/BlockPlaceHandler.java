package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Block placing. Dispatched at MONITOR so only placements that actually
 * happened reach the effects; Infinity's block-restore path then gives the
 * placed block back one tick later if it is still there and not excluded
 * (see InfinityEffect for the anti-duplication rules).
 */
public final class BlockPlaceHandler implements Listener {

    private final UnboundServices services;

    public BlockPlaceHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType().isAir()) {
            return;
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.BLOCK_PLACE, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .block(event.getBlockPlaced())
                    .location(event.getBlockPlaced().getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    /** Material-name helper shared with InfinityEffect (visible for tests). */
    static @Nullable Material materialOf(Block block) {
        return block == null ? null : block.getType();
    }
}
