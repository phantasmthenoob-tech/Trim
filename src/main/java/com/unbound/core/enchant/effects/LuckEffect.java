package com.unbound.core.enchant.effects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.math.EffectMath;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * Luck of the Sea, Unbound-style: holding the enchanted item grants Luck for
 * a few seconds, refreshed on every held-item-change/join dispatch — so it
 * lasts exactly as long as the item stays equipped, and vanishes right after
 * switching away (no per-tick task; the 5s tail is by design).
 */
public final class LuckEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    /** Players that had the luck item last dispatch; used to know who to clear. */
    private final Map<UUID, Boolean> applied = new ConcurrentHashMap<>();

    public LuckEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return (context.action() == ActionType.HELD_ITEM_CHANGE
                || context.action() == ActionType.PLAYER_JOIN
                || context.action() == ActionType.PLAYER_QUIT)
                && config.get().luck().enabled()
                && context.player().isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        Player player = context.player().orElse(null);
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (context.action() == ActionType.PLAYER_QUIT) {
            applied.remove(id);
            return;
        }
        // The context level is the clamped enchantment level of the item the
        // dispatcher read; 0 means the held item does not carry Luck.
        boolean carrying = context.level() > 0
                && context.itemCapabilities().contains(ItemCapability.WEAPON_MELEE)
                || context.level() > 0;
        int seconds = EffectMath.luckSeconds();
        if (carrying && seconds > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK,
                    seconds * 20 + 40, // small tail so refreshes never flicker
                    0, false, false, true));
            applied.put(id, true);
        } else if (applied.remove(id) != null) {
            removeLuck(player);
        }
    }

    private static void removeLuck(Player player) {
        PotionEffectType luck = PotionEffectType.LUCK;
        if (luck != null && player.hasPotionEffect(luck)) {
            @Nullable PotionEffect effect = player.getPotionEffect(luck);
            if (effect != null && effect.getDuration() <= EffectMath.luckSeconds() * 20 + 40) {
                player.removePotionEffect(luck);
            }
        }
    }

    /** True when this effect is currently tracking the player (tests/debug). */
    public boolean isTracking(UUID id) {
        return applied.containsKey(id);
    }
}
