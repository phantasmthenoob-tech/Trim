package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.SpeedMath;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;

/**
 * Quick Charge, Unbound-style.
 *
 * <ul>
 *   <li><b>Crossbows</b> keep exactly vanilla Quick Charge (reload is
 *       shortened by the vanilla code path, item-agnostic).</li>
 *   <li><b>Other items</b>: item cooldowns (ender pearls, chorus fruit,
 *       shields...) are reduced by the configured fraction per level.</li>
 * </ul>
 *
 * <p>Bow <i>draw time</i> lives client-side and cannot be shortened through
 * the public Paper API; that mechanic is reserved for the (optional) NMS
 * compatibility layer — see README. Everything implemented here stays inside
 * the public API.</p>
 */
public final class QuickChargeEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public QuickChargeEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return context.action() == ActionType.ITEM_COOLDOWN
                && config.get().quickCharge().enabled()
                && context.event()
                        .filter(PlayerItemCooldownEvent.class::isInstance)
                        .isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        PlayerItemCooldownEvent event = context.event()
                .filter(PlayerItemCooldownEvent.class::isInstance)
                .map(PlayerItemCooldownEvent.class::cast)
                .orElse(null);
        if (event == null) {
            return;
        }
        UnboundConfig.QuickChargeSettings settings = config.get().quickCharge();
        int current = event.getCooldown();
        int reduced = SpeedMath.reduceCooldown(current, context.level(),
                settings.useTimeReductionPerLevel(), settings.maxCooldownReduction(),
                settings.minimumCooldownTicks());
        if (reduced != current) {
            event.setCooldown(reduced);
        }
    }
}
