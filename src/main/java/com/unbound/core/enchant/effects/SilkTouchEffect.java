package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Silk Touch, Unbound-style: killing a player with the enchanted weapon
 * yields their head as an extra drop. Only adds — never removes vanilla drops.
 */
public final class SilkTouchEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public SilkTouchEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return config.get().silkTouch().enabled()
                && context.action() == ActionType.ENTITY_DEATH
                && context.event().filter(EntityDeathEvent.class::isInstance).isPresent()
                && context.target().filter(t -> t instanceof Player).isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        Player victim = context.target().map(t -> (Player) t).orElse(null);
        if (victim == null) {
            return;
        }
        EntityDeathEvent event = context.event()
                .filter(EntityDeathEvent.class::isInstance)
                .map(EntityDeathEvent.class::cast)
                .orElse(null);
        if (event == null) {
            return;
        }
        ItemStack head = createHead(victim);
        if (head != null) {
            event.getDrops().add(head);
        }
    }

    static ItemStack createHead(Player victim) {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(victim);
                head.setItemMeta(meta);
            }
            return head;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
