package com.unbound.core.enchant.effects;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentDefinition;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DurabilityMath;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Mending, Unbound-style: when XP is collected, every damaged durable item
 * carrying Mending is eligible — main hand, off hand, armor and (configured)
 * hotbar, in that priority order. XP is converted to durability and consumed;
 * leftover XP stays on the bar. No XP is ever generated, so no loop is
 * possible.
 */
public final class MendingEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public MendingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return context.action() == ActionType.XP_GAIN
                && config.get().mending().enabled()
                && context.player().isPresent()
                && context.xpAmount() > 0
                && context.definition().map(EnchantmentDefinition::handle).isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        PlayerExpChangeEvent event = context.event()
                .filter(PlayerExpChangeEvent.class::isInstance)
                .map(PlayerExpChangeEvent.class::cast)
                .orElse(null);
        Enchantment handle = context.definition().map(EnchantmentDefinition::handle).orElse(null);
        Player player = context.player().orElse(null);
        if (event == null || handle == null || player == null) {
            return;
        }
        UnboundConfig.MendingSettings settings = config.get().mending();
        int remaining = event.getAmount();
        if (remaining <= 0) {
            return;
        }

        Set<ItemStack> candidates = new LinkedHashSet<>();
        candidates.add(player.getInventory().getItemInMainHand());
        candidates.add(player.getInventory().getItemInOffHand());
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            candidates.add(armor);
        }
        if (settings.scanHotbar()) {
            for (int slot = 0; slot < 9; slot++) {
                candidates.add(player.getInventory().getItem(slot));
            }
        }

        int before = remaining;
        for (ItemStack item : candidates) {
            if (remaining <= 0) {
                break;
            }
            remaining -= repair(item, handle, remaining, settings.durabilityPerXp());
        }
        if (remaining != before) {
            event.setAmount(remaining);
        }
    }

    /** Repairs one item; returns the XP spent. */
    private int repair(@Nullable ItemStack item, Enchantment handle, int xpAvailable, double durabilityPerXp) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !meta.hasEnchant(handle)) {
            return 0;
        }
        int damage = damageable.getDamage();
        if (damage <= 0) {
            return 0;
        }
        DurabilityMath.MendResult result = DurabilityMath.mend(damage, xpAvailable, durabilityPerXp);
        if (!result.didRepair()) {
            return 0;
        }
        damageable.setDamage(damage - result.durabilityRepaired());
        item.setItemMeta(damageable);
        return result.xpSpent();
    }
}
