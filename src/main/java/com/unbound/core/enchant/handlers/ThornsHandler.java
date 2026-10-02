package com.unbound.core.enchant.handlers;

import java.util.function.Supplier;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentDefinition;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Thorns, Unbound-style: when the *wearer* is hit (any armor piece carrying
 * Thorns), the wearer sweeps nearby enemies. Dispatched at MONITOR with the
 * attacker as the impact source, only for attacks that actually landed.
 */
public final class ThornsHandler implements Listener {

    private final UnboundServices services;

    public ThornsHandler(UnboundServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWearerHit(EntityDamageByEntityEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        if (!(event.getEntity() instanceof Player wearer)) {
            return;
        }
        int level = highestThornsLevel(wearer);
        if (level <= 0) {
            return;
        }
        EnchantmentDefinition thorns = services.engine().get("thorns").orElse(null);
        if (thorns == null || !thorns.enabled()) {
            return;
        }
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            // Context item = the thorns armor piece; actor = the wearer (the
            // sweeper), target = the attacker (the sweep center).
            ItemStack armor = thornsArmor(wearer);
            EnchantmentContext context = EnchantmentContext.builder(ActionType.THORNS_TRIGGER, armor, guard)
                    .player(wearer)
                    .actor(wearer)
                    .itemCapabilities(services.scanner().scan(armor))
                    .level(level)
                    .target(event.getDamager())
                    .damageAmount(event.getDamage())
                    .location(wearer.getLocation())
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }

    private int highestThornsLevel(Player wearer) {
        int best = 0;
        EnchantmentDefinition thorns = services.engine().get("thorns").orElse(null);
        if (thorns == null) {
            return 0;
        }
        for (ItemStack armor : wearer.getInventory().getArmorContents()) {
            int level = services.engine().levelOf(armor, thorns);
            if (level > best) {
                best = level;
            }
        }
        return best;
    }

    private static ItemStack thornsArmor(Player wearer) {
        for (ItemStack armor : wearer.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                return armor;
            }
        }
        return new ItemStack(org.bukkit.Material.AIR);
    }
}
