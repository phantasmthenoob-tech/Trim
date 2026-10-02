package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;

/**
 * Thorns, Unbound-style: instead of poking back the attacker, the wearer
 * sweeps nearby enemies (armor version of Sweeping Edge) when hit. The
 * {@code ThornsHandler} dispatches THORNS_TRIGGER with the attacker as the
 * actor when the wearer carries Thorns on any armor piece.
 */
public final class ThornsEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    private final SweepEffect sweepDelegate;

    public ThornsEffect(Supplier<UnboundConfig> config) {
        this.config = config;
        this.sweepDelegate = new SweepEffect(config);
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return config.get().thorns().enabled()
                && context.action() == ActionType.THORNS_TRIGGER;
    }

    @Override
    public void execute(EnchantmentContext context) {
        // Same area-sweep behavior as Sweeping Edge, driven by the
        // ThornsHandler-built context (attacker = wearer).
        sweepDelegate.executeThorns(context, config.get().thorns().maxTargets(),
                config.get().thorns().damageFraction());
    }
}
