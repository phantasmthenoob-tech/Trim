package com.unbound.core.enchant.effects;

import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.VeilState;
import com.unbound.core.enchant.VeilType;
import com.unbound.core.math.EffectMath;

import org.bukkit.entity.Player;

/**
 * Protection-family veils (Protection, Fire/Blast/Projectile Protection,
 * Feather Falling), Unbound-style.
 *
 * <p>The wearer keeps the enchanted item in a hand/armor slot; whenever they
 * are involved in combat (attacking, being hit, or scoring a kill), a "veil"
 * wraps them for a few seconds (scaled by level). While veiled, damage of the
 * veil's family is reduced — as if an extra armor piece with that protection
 * at that level were worn. The veil lives in {@link VeilState} and is applied
 * by {@code VeilHandler} at damage time; this effect only grants/refreshes it.</p>
 */
public final class VeilEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    private final VeilType type;
    private final VeilState state;

    public VeilEffect(Supplier<UnboundConfig> config, VeilType type) {
        this(config, type, new VeilState());
    }

    /** Public: built by VanillaDefinitions with the shared veil store. */
    public VeilEffect(Supplier<UnboundConfig> config, VeilType type, VeilState state) {
        this.config = config;
        this.type = type;
        this.state = state;
    }

    /** The shared veil state (read by VeilHandler to reduce damage). */
    public VeilState state() {
        return state;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (!config.get().veil().enabled()) {
            return false;
        }
        return context.action() == ActionType.MELEE_ATTACK
                || context.action() == ActionType.PROJECTILE_HIT
                || context.action() == ActionType.ENTITY_DEATH;
    }

    @Override
    public void execute(EnchantmentContext context) {
        Player player = context.player().orElse(null);
        if (player == null) {
            return;
        }
        double seconds = Math.min(config.get().veil().maxDurationSeconds(),
                EffectMath.veilSeconds(context.level()));
        if (seconds <= 0) {
            return;
        }
        long nowTick = player.getWorld().getFullTime();
        state.apply(player.getUniqueId(), type, context.level(), nowTick, seconds);
    }
}
