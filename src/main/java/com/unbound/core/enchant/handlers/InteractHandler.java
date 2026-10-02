package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click interactions. Currently: Riptide self-launch — right-clicking
 * with a melee item (or any non-trident item) carrying Riptide launches the
 * player like a trident, without water (tridents keep vanilla behavior and
 * never dispatch here).
 */
public final class InteractHandler implements Listener {

    private final UnboundServices services;

    public InteractHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Items with their own right-click behavior (usable/consumable/throwable)
        // keep their vanilla flow; riptide launch only applies to plain items.
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        var capabilities = services.scanner().scan(item);
        if (!capabilities.isEmpty() && !capabilities.contains(ItemCapability.WEAPON_MELEE)
                && !capabilities.contains(ItemCapability.SHEARS)
                && !capabilities.contains(ItemCapability.FISHING_ROD)
                && !capabilities.contains(ItemCapability.SHIELD)) {
            return;
        }
        if (item.getType() == org.bukkit.Material.TRIDENT) {
            return; // vanilla riptide handles tridents
        }
        if (services.engine().get("riptide").isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(ActionType.RIPTIDE_LAUNCH, item, guard)
                    .player(player)
                    .itemCapabilities(capabilities)
                    .location(player.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }
}
