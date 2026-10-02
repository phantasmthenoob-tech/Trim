package com.unbound.core.enchant.effects;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DurabilityMath;

import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Unbreaking. Vanilla already skips durability damage with probability
 * {@code 0.6 + 0.04 * level} for any item carrying the enchantment; Unbound
 * adds an extra configurable skip chance on top (0.0 = exact vanilla). Items
 * are therefore never made permanently indestructible unless the server
 * explicitly configures very high values.
 *
 * <p>If Infinity prevented the damage, the underlying event is already
 * cancelled and this effect backs off — the interaction is deterministic.</p>
 */
public final class UnbreakingEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public UnbreakingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (context.action() != ActionType.DURABILITY_DAMAGE) {
            return false;
        }
        PlayerItemDamageEvent event = context.event()
                .filter(PlayerItemDamageEvent.class::isInstance)
                .map(PlayerItemDamageEvent.class::cast)
                .orElse(null);
        if (event == null || event.isCancelled()) {
            return false;
        }
        return config.get().unbreaking().enabled();
    }

    @Override
    public void execute(EnchantmentContext context) {
        double chance = DurabilityMath.extraSkipChance(context.level(), config.get().unbreaking().extraSkipChancePerLevel());
        if (chance <= 0.0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            context.cancelAction();
        }
    }
}
