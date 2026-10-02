package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Channeling, Unbound-style: any strike (melee or player-launched projectile)
 * summons lightning on the victim. Requires a thunderstorm like vanilla —
 * configurable off. Uses the shared guard so storm-heavy combat cannot chain.
 */
public final class ChannelingEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;

    public ChannelingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().channeling().enabled()) {
            return false;
        }
        if (context.action() != ActionType.MELEE_IMPACT
                && context.action() != ActionType.PROJECTILE_IMPACT) {
            return false;
        }
        if (config.get().channeling().requireThundering()) {
            Location location = context.location().orElse(null);
            if (location == null || location.getWorld() == null || !location.getWorld().hasStorm()) {
                return false;
            }
        }
        return context.target().isPresent() && context.location().isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        Location target = context.target().map(Entity::getLocation).orElse(null);
        if (target == null || target.getWorld() == null) {
            return;
        }
        target.getWorld().strikeLightning(target);
    }
}
