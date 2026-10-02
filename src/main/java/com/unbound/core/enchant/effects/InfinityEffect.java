package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Infinity, Unbound-style.
 *
 * <ul>
 *   <li><b>Consumables</b> — food, drinkable/splash potions, ender pearls,
 *       eggs, snowballs, XP bottles: the item's normal effect still runs
 *       (vanilla performs it), and the consumed item is restored one tick
 *       later, so it is preserved but never duplicated (net-zero).</li>
 *   <li><b>Durability</b> — cancels all durability damage on the item,
 *       deterministically taking precedence over Unbreaking (which therefore
 *       never applies to the same damage event).</li>
 * </ul>
 */
public final class InfinityEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    private final Plugin plugin;

    public InfinityEffect(Supplier<UnboundConfig> config, Plugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        UnboundConfig.InfinitySettings settings = config.get().infinity();
        if (context.action() == ActionType.DURABILITY_DAMAGE) {
            return settings.durabilityEnabled() && settings.preventDurabilityLoss();
        }
        if (context.action() != ActionType.ITEM_CONSUME && context.action() != ActionType.ITEM_LAUNCH) {
            return false;
        }
        if (!settings.consumablesEnabled() || !settings.preventConsumption()) {
            return false;
        }
        return context.itemCapabilities().contains(ItemCapability.CONSUMABLE)
                || context.itemCapabilities().contains(ItemCapability.THROWABLE);
    }

    @Override
    public void execute(EnchantmentContext context) {
        if (context.action() == ActionType.DURABILITY_DAMAGE) {
            context.cancelAction();
            return;
        }
        Player player = context.player().orElse(null);
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            // Creative never consumes items; restoring would duplicate.
            return;
        }
        UnboundConfig.InfinitySettings settings = config.get().infinity();
        ItemStack item = context.item();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Material type = item.getType();
        if (settings.excludedMaterials().contains(type.name().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        ItemStack restore = item.clone();
        restore.setAmount(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            var leftover = player.getInventory().addItem(restore);
            leftover.values().forEach(rest ->
                    player.getWorld().dropItemNaturally(player.getLocation(), rest));
        });
    }
}
