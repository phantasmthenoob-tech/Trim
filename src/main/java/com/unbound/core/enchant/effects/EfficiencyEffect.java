package com.unbound.core.enchant.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.math.SpeedMath;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Efficiency, Unbound-style.
 *
 * <p>Vanilla already speeds up mining for any effective tool carrying
 * Efficiency (its mining efficiency reads the held item). Unbound adds the
 * missing piece: faster <b>attacks</b> on melee weapons, applied as a
 * transient attack-speed attribute modifier while the weapon is held. The
 * modifier is refreshed on held-item changes and removed when it no longer
 * applies or the player quits — no per-tick tasks, no item mutation.</p>
 */
public final class EfficiencyEffect implements EnchantmentEffect {

    private final Supplier<UnboundConfig> config;
    private final AttributeModifier template;
    /** Only one modifier per player at a time; removed when undesired. */
    private final Map<UUID, AttributeModifier> applied = new HashMap<>();

    public EfficiencyEffect(Supplier<UnboundConfig> config, org.bukkit.NamespacedKey modifierKey) {
        this.config = config;
        this.template = new AttributeModifier(modifierKey, 0.0, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        return (context.action() == ActionType.HELD_ITEM_CHANGE
                || context.action() == ActionType.PLAYER_JOIN
                || context.action() == ActionType.PLAYER_QUIT)
                && config.get().efficiency().enabled()
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
        UnboundConfig.EfficiencySettings settings = config.get().efficiency();
        ItemStack held = context.item();
        double desired = 0.0;
        if (held != null && !held.getType().isAir()
                && context.itemCapabilities().contains(ItemCapability.WEAPON_MELEE)) {
            desired = SpeedMath.attackSpeedBonus(context.level(), settings.attackSpeedBonusPerLevel(), settings.scaling());
        }

        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = applied.get(id);
        if (desired <= 0.0) {
            if (existing != null) {
                attribute.removeModifier(existing);
                applied.remove(id);
            }
            return;
        }
        if (existing != null) {
            if (Math.abs(existing.getAmount() - desired) < 1.0E-6) {
                return;
            }
            attribute.removeModifier(existing);
            applied.remove(id);
        }
        AttributeModifier modifier = new AttributeModifier(template.getKey(), desired, template.getOperation());
        attribute.addTransientModifier(modifier);
        applied.put(id, modifier);
    }

    /** Visible for tests/debug: how many players currently carry the bonus. */
    public int trackedPlayers() {
        return applied.size();
    }

    @Nullable
    AttributeModifier appliedFor(UUID id) {
        return applied.get(id);
    }
}
